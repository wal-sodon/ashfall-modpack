package xyz.toesinthesand.ashfall.director.infection;

import xyz.toesinthesand.ashfall.director.item.DirectorArmor;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import xyz.toesinthesand.ashfall.director.hydration.Hydration;
import xyz.toesinthesand.ashfall.director.skills.Skill;
import xyz.toesinthesand.ashfall.director.skills.Skills;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;
import xyz.toesinthesand.ashfall.director.spawn.DirectorContexts;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.special.SpecialInfectedEntity;
import xyz.toesinthesand.ashfall.director.story.StoryCommand;

import java.util.Optional;

/**
 * Staged infection on top of Contagion. Ashfall owns the timeline (a persistent player attachment holding
 * {@link InfectionData}) because Contagion's single countdown can't pause or step back a stage; Contagion
 * stays the base effect players see, and {@link ContagionBridge} keeps its countdown in step every tick.
 * With Contagion absent (gametests, or a pack without it) everything here still works on its own.
 * <p>
 * Sources: damaging melee hits from zombie-family mobs roll a chance by Director variant and zone (see
 * {@link InfectionRules.Sources}); dirty water rolls through {@link #infect} too. Contagion's own hit roll is
 * set to 0 in the pack config so the two never double up. Treatments arrive through the consume hook:
 * Bandages pause progress, Antibiotics knock a stage back, and after the Sever ending they cure.
 * <p>
 * Tick cost: one attachment read per online player per tick; infected players add one small write, the
 * drain and, once a second, the symptom rolls.
 */
public final class Infection {
	public static final boolean CONTAGION_LOADED = FabricLoader.getInstance().isModLoaded("contagion");
	/** Contagion's infection effect; looked up by id so director never needs Contagion at runtime. */
	public static final Identifier CONTAGION_EFFECT = Identifier.fromNamespaceAndPath("contagion", "infection");
	/** Symptoms are rolled once a second. */
	public static final int SYMPTOM_INTERVAL = 20;

	/** No {@code copyOnDeath}: death ends the infection, and an infected death is an ordinary death (items drop). */
	public static final AttachmentType<InfectionData> STATE = AttachmentRegistry.create(id("infection"), builder -> builder
			.persistent(InfectionData.CODEC)
			.initializer(() -> InfectionData.HEALTHY));

	/**
	 * The stage alone, synced to the player's own client for the HUD. Kept apart from {@link #STATE} because
	 * progress changes every tick and would otherwise send a packet every tick; the stage changes a handful of
	 * times per infection.
	 */
	public static final AttachmentType<Integer> STAGE = AttachmentRegistry.create(id("infection_stage"), builder -> builder
			.initializer(() -> 0)
			.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, id("infection"));

	private Infection() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(InfectionRulesLoader.ID, new InfectionRulesLoader());
		ServerTickEvents.END_SERVER_TICK.register(Infection::tickServer);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Infection::afterDamage);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			InfectionCommand.register(dispatcher);
			StoryCommand.register(dispatcher);
		});
	}

	public static InfectionRules rules() {
		return InfectionRulesLoader.rules();
	}

	public static InfectionData get(Player player) {
		return player.getAttachedOrElse(STATE, InfectionData.HEALTHY);
	}

	public static boolean isInfected(Player player) {
		return get(player).infected();
	}

	/** The stage on either side: on the server from the timeline, on the client from the synced copy. */
	public static int stage(Player player) {
		if (player instanceof ServerPlayer) {
			return rules().stageAt(get(player));
		}
		return player.getAttachedOrElse(STAGE, 0);
	}

	/**
	 * Stores a new state, re-syncs the HUD stage and keeps Contagion's effect matching: present while
	 * infected, removed on a cure.
	 */
	public static void set(ServerPlayer player, InfectionData data) {
		player.setAttached(STATE, data);
		syncStage(player, rules().stageAt(data));
		if (!data.infected()) {
			contagionEffect().ifPresent(player::removeEffect);
		} else if (CONTAGION_LOADED) {
			ensureContagionEffect(player);
			syncContagion(player, data);
		}
	}

	private static void syncStage(ServerPlayer player, int stage) {
		if (player.getAttachedOrElse(STAGE, 0) != stage) {
			player.setAttached(STAGE, stage);
		}
	}

	/** Infects a healthy player at stage 1. Returns false if they were already infected (progress is kept). */
	public static boolean infect(ServerPlayer player) {
		if (isInfected(player)) {
			return false;
		}
		set(player, rules().infect());
		notifyStage(player, 1);
		return true;
	}

	public static void clear(ServerPlayer player) {
		set(player, InfectionData.HEALTHY);
	}

	/** Jumps to the start of {@code stage} (1..4); the testing command. */
	public static void setStage(ServerPlayer player, int stage) {
		set(player, rules().atStage(stage));
	}

	/**
	 * Whether the infection moves for this player. Creative and spectator players are frozen, like thirst,
	 * so builders and admins can carry an infection around without it killing them.
	 */
	public static boolean progresses(ServerPlayer player) {
		return player.isAlive() && !player.isCreative() && !player.isSpectator();
	}

	private static void tickServer(MinecraftServer server) {
		long tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tick(player, tick);
		}
	}

	/** One tick for one player: adopt a Contagion-only infection, advance, drain, symptoms, death. */
	public static void tick(ServerPlayer player, long serverTick) {
		InfectionData data = get(player);
		if (!data.infected()) {
			// Contagion can still infect by its own routes (contagious flesh, its potions); adopt those.
			if (CONTAGION_LOADED && player.isAlive() && hasContagionEffect(player)) {
				infect(player);
			} else {
				syncStage(player, 0);
			}
			return;
		}
		// STAGE is not persistent: after a relog it starts at 0, and advance() only re-syncs when progress moves, so a
		// player whose infection is paused (creative, a treatment holding it) saw a healthy HUD. Cheap: one read.
		syncStage(player, rules().stageAt(data));
		if (!progresses(player)) {
			return;
		}
		advance(player, 1);
		data = get(player);
		int stage = rules().stageAt(data);
		applyDrain(player, rules().stage(stage));
		if (serverTick % SYMPTOM_INTERVAL == 0) {
			applySymptoms(player, rules().stage(stage), player.getRandom());
		}
		if (rules().isFatal(data)) {
			succumb(player);
		}
	}

	/**
	 * {@code ticks} of infection time passing at once, with stage messages for every stage crossed. The tick
	 * loop calls it with 1; gametests call it with hours to simulate a long infection in one step. Never
	 * kills by itself: death is the tick loop's job, so a test can look at a stage-4 player.
	 */
	public static void advance(ServerPlayer player, long ticks) {
		InfectionData before = get(player);
		InfectionData after = rules().advance(before, ticks);
		if (after.equals(before)) {
			return;
		}
		int oldStage = rules().stageAt(before);
		player.setAttached(STATE, after);
		int newStage = rules().stageAt(after);
		syncStage(player, newStage);
		if (newStage > oldStage) {
			notifyStage(player, newStage);
		}
		if (CONTAGION_LOADED) {
			ensureContagionEffect(player);
			syncContagion(player, after);
		}
	}

	/** Stage 2 onwards: hunger and thirst burn faster. Thirst only where thirst itself drains. */
	private static void applyDrain(ServerPlayer player, InfectionRules.Stage stage) {
		if (stage.hungerExhaustionPerTick() > 0) {
			player.getFoodData().addExhaustion(stage.hungerExhaustionPerTick());
		}
		if (stage.thirstExhaustionPerTick() > 0 && Hydration.drains(player)) {
			Hydration.set(player, Hydration.get(player).addExhaustion(stage.thirstExhaustionPerTick()));
		}
	}

	/** Rolls every symptom of the stage. Ambient and particle-free, so they read as illness, not potions. */
	public static void applySymptoms(ServerPlayer player, InfectionRules.Stage stage, RandomSource random) {
		for (InfectionRules.Symptom symptom : stage.effects()) {
			if (random.nextFloat() >= symptom.chance()) {
				continue;
			}
			BuiltInRegistries.MOB_EFFECT.get(symptom.effect()).ifPresent(effect -> player.addEffect(
					new MobEffectInstance(effect, symptom.durationTicks(), symptom.amplifier(), true, false, true)));
		}
	}

	/** The infection's end: a hit no armour or resistance stops. It is an ordinary death: items drop where the player fell. */
	public static void succumb(ServerPlayer player) {
		ServerLevel level = player.level();
		DamageSource source = level.damageSources().source(DAMAGE);
		player.hurtServer(level, source, Float.MAX_VALUE);
	}

	private static void notifyStage(ServerPlayer player, int stage) {
		player.sendSystemMessage(Component.translatable("infection.ashfall.stage." + stage).withStyle(ChatFormatting.RED), true);
	}

	// --- sources --------------------------------------------------------------------------------------

	/**
	 * Only direct melee from zombie-family mobs: a drowned's trident or anything that was blocked or did no
	 * damage is not a bite. Zombies from other mods built on the vanilla zombie use the base chance.
	 */
	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (blocked || damageTaken <= 0 || !(entity instanceof ServerPlayer player)) {
			return;
		}
		if (source.getDirectEntity() instanceof Zombie zombie) {
			onZombieHit(player, zombie, player.getRandom().nextFloat());
		}
	}

	/**
	 * One zombie hit with a given roll in [0, 1), so tests can pin both outcomes. Returns whether it infected.
	 * Already-infected players are not re-rolled: a second bite does not restart or speed up the clock.
	 */
	public static boolean onZombieHit(ServerPlayer player, Zombie zombie, float roll) {
		if (!progresses(player) || isInfected(player)) {
			return false;
		}
		if (roll < hitChance(player, zombie)) {
			infect(player);
			return true;
		}
		return false;
	}

	/**
	 * An infection roll from a source that is not a bite (a bloated's cloud, a spitter's acid): {@code chance}
	 * as given, with no armour reduction because armour does not keep out spores or acid. The same rules as a
	 * bite otherwise: frozen players and already-infected players are never rolled.
	 *
	 * @param roll uniform in [0, 1), passed in so tests can pin both outcomes
	 * @return whether it infected
	 */
	public static boolean tryInfect(ServerPlayer player, float chance, float roll) {
		if (!progresses(player) || isInfected(player)) {
			return false;
		}
		if (roll < chance * resistanceFactor(player)) {
			infect(player);
			return true;
		}
		return false;
	}

	/** What the player's Hazmat pieces leave of any infection roll: bites, spores, acid and dirty water alike. */
	public static float hazmatFactor(ServerPlayer player) {
		return DirectorArmor.perkFactor(DirectorArmor.pieces(player, DirectorArmor.HAZMAT_SET),
				rules().sources().hazmatReductionPerPiece());
	}

	/** What Hazmat pieces and the Immune System skill together leave of any infection roll. */
	public static float resistanceFactor(ServerPlayer player) {
		return hazmatFactor(player) * Skills.reduction(player, Skill.IMMUNE_SYSTEM);
	}

	/** The infection chance of one hit from {@code zombie} on {@code player} where they stand now. */
	public static float hitChance(ServerPlayer player, Zombie zombie) {
		DirectorZombie tag = zombie.getAttached(DirectorSpawns.TAG);
		String zone = DirectorContexts.zoneAt(player.blockPosition()).id();
		// a special spawned by an egg or command has no Director tag, but its kind is still its variant name
		String variant = tag != null ? tag.variant()
				: zombie instanceof SpecialInfectedEntity special ? special.kind().id() : null;
		return rules().sources().chance(variant, zone, player.getArmorValue()) * resistanceFactor(player);
	}

	// --- treatments -----------------------------------------------------------------------------------

	/** Called by the consume mixin before the stack shrinks. */
	public static void onConsume(ServerPlayer player, ItemStack stack) {
		if (stack.is(DirectorItems.BANDAGE)) {
			treatWithBandage(player);
		} else if (stack.is(DirectorItems.ANTIBIOTICS)) {
			treatWithAntibiotics(player);
		}
	}

	/**
	 * A Bandage closes the wound: a little health back and, if infected, progress held still for a while.
	 * (There is no bleeding effect in the pack yet; when one arrives, the Bandage is where it stops.)
	 */
	public static void treatWithBandage(ServerPlayer player) {
		player.heal(rules().treatments().bandageHeal() * (1f + (float) Skills.bonus(player, Skill.FIELD_MEDIC)));
		InfectionData data = get(player);
		if (data.infected()) {
			set(player, rules().bandage(data));
		}
	}

	/** Antibiotics: one stage back plus a pause, a cure at stage 1, a cure at any stage after Sever. */
	public static void treatWithAntibiotics(ServerPlayer player) {
		InfectionData before = get(player);
		if (!before.infected()) {
			return;
		}
		InfectionData after = rules().antibiotics(before, InfectionRules.severEnding(player.level().getServer()));
		set(player, after);
		player.sendSystemMessage(Component.translatable(after.infected()
				? "infection.ashfall.treated" : "infection.ashfall.cured").withStyle(ChatFormatting.GREEN), true);
	}

	// --- Contagion ------------------------------------------------------------------------------------

	private static Optional<Holder.Reference<MobEffect>> contagionEffect() {
		return BuiltInRegistries.MOB_EFFECT.get(CONTAGION_EFFECT);
	}

	private static boolean hasContagionEffect(ServerPlayer player) {
		return contagionEffect().map(player::hasEffect).orElse(false);
	}

	/** Contagion's effect is the visible "you are infected" marker; milk or a Contagion potion can't lift it. */
	private static void ensureContagionEffect(ServerPlayer player) {
		contagionEffect().ifPresent(effect -> {
			if (!player.hasEffect(effect)) {
				player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
			}
		});
	}

	private static void syncContagion(ServerPlayer player, InfectionData data) {
		long deathAt = rules().deathAt();
		ContagionBridge.sync(player, deathAt - data.progress(), deathAt);
	}
}
