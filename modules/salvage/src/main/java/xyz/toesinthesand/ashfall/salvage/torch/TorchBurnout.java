package xyz.toesinthesand.ashfall.salvage.torch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;

import java.util.Map;

/**
 * Torches burn out (Ashfall's own, replacing the Realistic Torches mod in 10.3.0). A torch or wall torch a player
 * places burns for {@link Rules#burnTicks} (12 minutes by default, give or take {@link Rules#spreadTicks}) and then
 * becomes a {@link BurntTorches#BURNT_TORCH burnt-out torch}: no light, and it drops a stick. Right-clicking a
 * burnt-out torch with a fire starter ({@link #LIGHTERS}: Matches, Lighter Fluid, flint and steel) lights it again
 * with a fresh timer, for one use of the item.
 * <p>
 * Why salvage: the fire starters that relight torches are salvage items, and salvage is the lowest module, so the
 * Director's light rules and the arms flamethrower can both see the burnt-out blocks without a new dependency.
 * <p>
 * How: the timer is a vanilla scheduled block tick on the torch, scheduled from the torch's {@code setPlacedBy}
 * ({@code TorchBlockMixin}), which only player placement reaches with a player. Scheduled ticks are saved with the
 * chunk, so the timer survives unloading and restarts; like crops and fire it only counts down while the chunk is
 * loaded. World-generated torches (Lost Cities, structures, our own sites), {@code /setblock} and torches that were
 * already in a world before this change never get a tick, so they burn forever. Soul and copper torches, redstone
 * torches and torches held in the hand are untouched. Nothing else in vanilla schedules ticks on a torch, so any
 * tick that reaches a plain torch is ours. Removing a lit torch drops its timer ({@link #onRemoved}), so a torch put
 * back at that spot some other way does not inherit it.
 * <p>
 * The burn time is data: {@code data/ashfall/ashfall/torch_burnout/default.json}
 * ({@code {"burn_ticks": 14400, "spread_ticks": 1200}}), replaceable by a datapack.
 */
public final class TorchBurnout {
	public static final Identifier ID = AshfallSalvage.id("torch_burnout");
	/** The one rules file read: {@code ashfall:default} under {@code ashfall/torch_burnout}. */
	public static final Identifier DEFAULT_ID = AshfallSalvage.id("default");
	/** Items that relight a burnt-out torch: one durability (or, for an item without any, one item) per light. */
	public static final TagKey<Item> LIGHTERS = TagKey.create(Registries.ITEM, AshfallSalvage.id("relights_torches"));

	/**
	 * @param burnTicks   how long a placed torch burns
	 * @param spreadTicks each torch burns up to this much shorter or longer, so a row placed together dies one by one
	 */
	public record Rules(int burnTicks, int spreadTicks) {
		public static final Rules DEFAULT = new Rules(14400, 1200);
		public static final Codec<Rules> CODEC = RecordCodecBuilder.create(i -> i.group(
				ExtraCodecs.POSITIVE_INT.fieldOf("burn_ticks").forGetter(Rules::burnTicks),
				ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("spread_ticks", 0).forGetter(Rules::spreadTicks)
		).apply(i, Rules::new));

		/** One torch's burn time: {@code burnTicks} plus or minus up to {@code spreadTicks}, at least one tick. */
		public int roll(RandomSource random) {
			int spread = Math.min(spreadTicks, burnTicks - 1);
			return spread <= 0 ? burnTicks : burnTicks + random.nextIntBetweenInclusive(-spread, spread);
		}
	}

	private static volatile Rules rules = Rules.DEFAULT;
	/** Test hook: a fixed burn time with no spread; null uses {@link #rules}. */
	private static volatile @Nullable Integer testBurnTicks;

	private TorchBurnout() {
	}

	public static void init() {
		BurntTorches.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, new Loader());
	}

	public static Rules rules() {
		return rules;
	}

	/** True for the two torch blocks that burn out: the plain torch and its wall form. */
	public static boolean burnsOut(BlockState state) {
		return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH);
	}

	public static boolean isBurntOut(BlockState state) {
		return state.is(BurntTorches.BURNT_TORCH) || state.is(BurntTorches.BURNT_WALL_TORCH);
	}

	/** From the torch's {@code setPlacedBy}: starts the timer when a player placed it. */
	public static void onPlaced(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer) {
		if (level instanceof ServerLevel server && placer instanceof Player && burnsOut(state)) {
			light(server, pos, state.getBlock());
		}
	}

	/**
	 * Starts a fresh timer on the torch at {@code pos}. Any tick still pending there is dropped first: the level keeps
	 * one tick per block and position, so a torch broken and placed again would otherwise inherit the old deadline.
	 */
	public static void light(ServerLevel level, BlockPos pos, Block torch) {
		level.getBlockTicks().clearArea(new BoundingBox(pos));
		Integer test = testBurnTicks;
		level.scheduleTick(pos, torch, test != null ? test : rules.roll(level.getRandom()));
	}

	/**
	 * From the torch's {@code affectNeighborsAfterRemoval}: a lit torch left this spot (broken, washed away, burnt out),
	 * so its pending timer goes too. Otherwise a torch set here without a player before the old deadline (a command, a
	 * structure, a district relight) would pick up the leftover tick and burn out.
	 */
	public static void onRemoved(BlockState state, ServerLevel level, BlockPos pos) {
		if (burnsOut(state)) {
			level.getBlockTicks().clearArea(new BoundingBox(pos));
		}
	}

	/** From the torch's scheduled tick: the timer ran out. */
	public static void onTick(BlockState state, ServerLevel level, BlockPos pos) {
		if (!burnsOut(state)) {
			return;
		}
		level.setBlock(pos, burntFor(state), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 0.8f + level.getRandom().nextFloat() * 0.3f);
		level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 4, 0.05, 0.05, 0.05, 0.01);
	}

	/** The burnt-out block for a lit torch (same facing on a wall). */
	public static BlockState burntFor(BlockState lit) {
		return lit.is(Blocks.WALL_TORCH)
				? BurntTorches.BURNT_WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, lit.getValue(WallTorchBlock.FACING))
				: BurntTorches.BURNT_TORCH.defaultBlockState();
	}

	/** The lit torch for a burnt-out one (same facing on a wall). */
	public static BlockState litFor(BlockState burnt) {
		return burnt.is(BurntTorches.BURNT_WALL_TORCH)
				? Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, burnt.getValue(WallTorchBlock.FACING))
				: Blocks.TORCH.defaultBlockState();
	}

	/**
	 * A burnt-out torch used with an item: a {@link #LIGHTERS fire starter} lights it again with a fresh timer and
	 * wears one use, exactly as lighting a campfire would (no wear in creative), and like the campfire it needs a player
	 * who may build here (adventure mode only with an item allowed to place on this block). Anything else falls through
	 * to the item's own use.
	 */
	static InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
		if (!stack.is(LIGHTERS)
				|| !player.getAbilities().mayBuild && !stack.canPlaceOnBlockInAdventureMode(new BlockInWorld(level, pos, false))) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server) {
			BlockState lit = litFor(state);
			server.setBlock(pos, lit, Block.UPDATE_ALL_IMMEDIATE);
			light(server, pos, lit.getBlock());
			server.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f, server.getRandom().nextFloat() * 0.4f + 0.8f);
			server.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
			player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
			if (stack.isDamageableItem()) {
				stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
			} else {
				stack.consume(1, player);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Test hook: every torch lit from now on burns for exactly this many ticks; null restores the data value. */
	public static void setBurnTicksForTesting(@Nullable Integer ticks) {
		testBurnTicks = ticks;
	}

	/** Reads {@code data/<ns>/ashfall/torch_burnout/*.json}; only {@link #DEFAULT_ID} counts, the built-in default otherwise. */
	static final class Loader extends SimpleJsonResourceReloadListener<Rules> {
		Loader() {
			super(Rules.CODEC, FileToIdConverter.json("ashfall/torch_burnout"));
		}

		@Override
		protected void apply(Map<Identifier, Rules> loaded, ResourceManager manager, ProfilerFiller profiler) {
			rules = loaded.getOrDefault(DEFAULT_ID, Rules.DEFAULT);
			AshfallSalvage.LOGGER.info("Torches burn for {} ticks (+/- {})", rules.burnTicks(), rules.spreadTicks());
		}
	}
}
