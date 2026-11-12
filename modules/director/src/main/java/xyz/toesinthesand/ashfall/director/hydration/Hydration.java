package xyz.toesinthesand.ashfall.director.hydration;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import xyz.toesinthesand.ashfall.director.infection.Infection;
import xyz.toesinthesand.ashfall.director.skills.Skill;
import xyz.toesinthesand.ashfall.director.skills.Skills;

import java.util.function.BiPredicate;

/**
 * Thirst: a second hunger bar fed by water bottles. The state is a player attachment so it saves with
 * the player, follows them through dimension changes, syncs to their own client for the HUD and the
 * sprint check, and is dropped on death (no {@code copyOnDeath}), which is the "reset to full" rule.
 * <p>
 * The server drives everything from one end-of-tick pass over online players plus three mixins:
 * activity exhaustion ({@code Player.causeFoodExhaustion}), regeneration ({@code FoodData.tick}) and
 * drinking ({@code Consumable.onConsume}). The client only reads the synced value.
 */
public final class Hydration {
	/** Thirst gets this much of whatever exhaustion hunger gets: water runs out faster than food. */
	public static final float ACTIVITY_MULTIPLIER = 1.5f;
	/** Extra exhaustion per tick in hot biomes (deserts, badlands, savanna). */
	public static final float HEAT_EXHAUSTION_PER_TICK = 0.005f;
	/** Biomes whose base temperature is above this count as hot. */
	public static final float HOT_BIOME_TEMPERATURE = 1.0f;
	/** Ticks between dehydration hits at 0 thirst, the starvation interval. */
	public static final int DAMAGE_INTERVAL = 80;
	public static final float DAMAGE_AMOUNT = 1.0f;

	public static final float NAUSEA_CHANCE = 0.4f;
	public static final int NAUSEA_TICKS = 10 * 20;
	/** Chance that one drink of dirty water starts an infection (stage 1, see {@link Infection}). */
	public static final float INFECTION_CHANCE = 0.08f;

	public static final AttachmentType<ThirstData> THIRST = AttachmentRegistry.create(id("thirst"), builder -> builder
			.persistent(ThirstData.CODEC)
			.initializer(() -> ThirstData.DEFAULT)
			.syncWith(ThirstData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** {@code /gamerule ashfall:thirst false} turns the whole system off and keeps everyone topped up. */
	public static final GameRule<Boolean> ENABLED = GameRuleBuilder.forBoolean(true)
			.category(GameRuleCategory.PLAYER)
			.buildAndRegister(id("thirst"));

	public static final ResourceKey<DamageType> DEHYDRATION = ResourceKey.create(Registries.DAMAGE_TYPE, id("dehydration"));

	/**
	 * Where a bottle filled from open water comes up clean instead of dirty. Nothing by default: open water is
	 * never safe. The grid sets it once the River Arts pump runs again (endgame-design section 3, "city pump:
	 * clean water from taps in Reclaimed zones"), so the rule stays here with the dirty-water rule it bends.
	 */
	private static volatile BiPredicate<ServerLevel, BlockPos> cleanWater = (level, pos) -> false;

	private Hydration() {
	}

	/** Replaces the clean-water rule (see {@link #cleanWaterAt}); null restores "never clean". */
	public static void setCleanWaterRule(BiPredicate<ServerLevel, BlockPos> rule) {
		cleanWater = rule == null ? (level, pos) -> false : rule;
	}

	/** Whether a bottle filled at {@code pos} comes up clean (city pump) rather than dirty. */
	public static boolean cleanWaterAt(ServerLevel level, BlockPos pos) {
		return cleanWater.test(level, pos);
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	/** Forces class loading (attachment, game rule) and hooks the server tick and the command. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Hydration::tickServer);
		SleepCost.init();
		RainCollectors.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> ThirstCommand.register(dispatcher));
	}

	/** Thirst of any player; on the client this is the synced copy (only the local player has one). */
	public static ThirstData get(Player player) {
		return player.getAttachedOrElse(THIRST, ThirstData.DEFAULT);
	}

	public static void set(Player player, ThirstData data) {
		player.setAttached(THIRST, data);
	}

	public static boolean enabled(ServerLevel level) {
		return level.getGameRules().get(ENABLED);
	}

	/**
	 * Whether this player's thirst moves at all right now. Creative and spectator are exempt like
	 * hunger; Peaceful and a disabled rule are handled separately because they also refill.
	 */
	public static boolean drains(ServerPlayer player) {
		ServerLevel level = player.level();
		return enabled(level) && level.getDifficulty() != Difficulty.PEACEFUL && !player.isCreative() && !player.isSpectator();
	}

	private static void tickServer(MinecraftServer server) {
		long tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tick(player, tick);
		}
	}

	/**
	 * One tick for one player: heat, drain, then a dehydration hit on the interval. With the rule off
	 * or on Peaceful the player is kept full, otherwise a stale empty bar would keep blocking sprint
	 * and regeneration with no way to drain or notice it.
	 */
	public static void tick(ServerPlayer player, long serverTick) {
		ServerLevel level = player.level();
		if (!enabled(level) || level.getDifficulty() == Difficulty.PEACEFUL) {
			if (!get(player).equals(ThirstData.DEFAULT)) {
				set(player, ThirstData.DEFAULT);
			}
			return;
		}
		if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
			return;
		}
		ThirstData data = get(player);
		if (isHot(player)) {
			data = data.addExhaustion(HEAT_EXHAUSTION_PER_TICK * Skills.reduction(player, Skill.IRON_GUT));
		}
		data = data.drain();
		set(player, data);
		if (data.thirst() <= 0 && serverTick % DAMAGE_INTERVAL == 0) {
			hurtFromDehydration(player);
		}
	}

	public static boolean isHot(ServerPlayer player) {
		return player.level().getBiome(player.blockPosition()).value().getBaseTemperature() > HOT_BIOME_TEMPERATURE;
	}

	/** One dehydration hit if the difficulty floor allows it. Returns whether damage was attempted. */
	public static boolean hurtFromDehydration(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!ThirstData.dehydrationHurts(player.getHealth(), level.getDifficulty())) {
			return false;
		}
		player.hurtServer(level, level.damageSources().source(DEHYDRATION), DAMAGE_AMOUNT);
		return true;
	}

	/** Called by the exhaustion mixin with the amount hunger just received. */
	public static void onFoodExhaustion(ServerPlayer player, float amount) {
		if (amount > 0 && drains(player)) {
			set(player, get(player).addExhaustion(amount * ACTIVITY_MULTIPLIER * Skills.reduction(player, Skill.IRON_GUT)));
		}
	}

	/** Called by the consume mixin before the stack shrinks. */
	public static void onConsume(ServerPlayer player, ItemStack stack) {
		Drink drink = Drink.of(stack);
		if (drink == null) {
			return;
		}
		set(player, get(player).drink(drink.thirst(), drink.saturation()));
		if (drink.dirty()) {
			applyDirtyWaterRisk(player, player.getRandom());
		}
	}

	/** Nausea is common and harmless; infection is the real reason to boil water first. Iron Gut lowers both. */
	public static void applyDirtyWaterRisk(ServerPlayer player, RandomSource random) {
		applyDirtyWaterRisk(player, random.nextFloat(), random.nextFloat());
	}

	/** The rolls passed in (each in [0, 1)), so tests can pin both outcomes. */
	public static void applyDirtyWaterRisk(ServerPlayer player, float nauseaRoll, float infectionRoll) {
		float ironGut = Skills.reduction(player, Skill.IRON_GUT);
		if (nauseaRoll < NAUSEA_CHANCE * ironGut) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS));
		}
		if (infectionRoll < INFECTION_CHANCE * ironGut * Infection.resistanceFactor(player)) {
			Infection.infect(player);
		}
	}
}
