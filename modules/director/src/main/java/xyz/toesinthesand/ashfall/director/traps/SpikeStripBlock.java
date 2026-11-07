package xyz.toesinthesand.ashfall.director.traps;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import xyz.toesinthesand.ashfall.director.pets.Friendly;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A carpet-thin strip of nails and bent scrap. Everything that walks over it is slowed; zombie-family mobs also
 * take {@link #DAMAGE_AMOUNT} spike damage, at most once per {@link #HIT_INTERVAL} ticks each. Players and their pets
 * ({@link Friendly}) are only slowed, so co-op friends can't hurt each other, or each other's dogs, with it.
 * <p>
 * Each hit has a 1 in {@link #WEAR_CHANCE} chance to wear the strip; a wear roll on a strip already at
 * {@link #MAX_WEAR} breaks it into a piece of Scrap Metal. An Iron Nugget takes one wear off. Zombies path over
 * it like open ground (registered in {@code DirectorBlocks}); without that they would avoid it like a berry bush.
 */
public class SpikeStripBlock extends Block {
	public static final int MAX_WEAR = 3;
	public static final IntegerProperty WEAR = IntegerProperty.create("wear", 0, MAX_WEAR);
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
			Identifier.fromNamespaceAndPath("ashfall", "spikes"));
	public static final float DAMAGE_AMOUNT = 2.0f;
	public static final int HIT_INTERVAL = 20;
	public static final int WEAR_CHANCE = 6;
	/** Movement multiplier while on the strip. */
	public static final Vec3 SLOW = new Vec3(0.6, 1.0, 0.6);
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);
	/** Game time of each mob's last spike hit. Weak keys: unloaded or dead mobs drop out by themselves. */
	private static final Map<Entity, Long> LAST_HIT = new WeakHashMap<>();

	/** Forgets the hit times when a world closes (game time restarts in the next one). */
	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_HIT.clear());
	}

	public SpikeStripBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(WEAR, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(WEAR);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		return direction == Direction.DOWN && !state.canSurvive(level, pos)
				? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		stepOn(state, level, pos, entity);
	}

	/**
	 * One tick of an entity on the strip: slow it, and hurt it if it's a zombie whose hit is due.
	 *
	 * @return whether the entity was hurt
	 */
	public static boolean stepOn(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return false;
		}
		living.makeStuckInBlock(state, SLOW);
		if (!(level instanceof ServerLevel server) || Friendly.isFriendly(living, null) || !TriplineAlarmBlockEntity.isIntruder(living)) {
			return false;
		}
		long now = server.getGameTime();
		Long last = LAST_HIT.get(living);
		if (last != null && now - last < HIT_INTERVAL && now >= last) {
			return false;
		}
		LAST_HIT.put(living, now);
		boolean hurt = living.hurtServer(server, server.damageSources().source(DAMAGE), DAMAGE_AMOUNT);
		if (hurt && server.getRandom().nextInt(WEAR_CHANCE) == 0) {
			wear(server, pos);
		}
		return hurt;
	}

	/** Adds one wear to the strip at {@code pos}, or breaks it into scrap when it is already worn out. */
	public static void wear(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.hasProperty(WEAR)) {
			return;
		}
		int wear = state.getValue(WEAR);
		if (wear >= MAX_WEAR) {
			level.destroyBlock(pos, false);
			Block.popResource(level, pos, new ItemStack(SalvageItems.SCRAP_METAL));
		} else {
			level.setBlock(pos, state.setValue(WEAR, wear + 1), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 0.7f);
		}
	}

	/** An Iron Nugget hammers out one wear. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(Items.IRON_NUGGET)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		int wear = state.getValue(WEAR);
		if (wear == 0) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel) {
			level.setBlock(pos, state.setValue(WEAR, wear - 1), Block.UPDATE_ALL);
			stack.consume(1, player);
			level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4f, 1.6f);
		}
		return InteractionResult.SUCCESS;
	}
}
