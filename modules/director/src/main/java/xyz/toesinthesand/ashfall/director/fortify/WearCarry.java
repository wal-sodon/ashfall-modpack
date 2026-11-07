package xyz.toesinthesand.ashfall.director.fortify;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Wear goes with its block (10.2.0): a worn block that is broken and placed again, or pushed by a piston, keeps its
 * wear, so pulling a battered wall down and rebuilding it is not a free repair.
 * <ul>
 *   <li><b>Breaking.</b> A worn block's drop of itself carries {@link #ITEM_WEAR} ("Worn 40%" in its tooltip, and it
 *   no longer stacks with unworn ones); placing it sets the wear again ({@code BlockItemWearMixin}). The drop hook
 *   ({@code BlockStateDropsWearMixin}) reads the wear off the block while it still stands (explosions,
 *   {@code Level.destroyBlock}) or from {@link #REMOVED}, since a player's break removes the block, and its wear,
 *   before the drops are rolled. A break that drops nothing loses the wear as before.</li>
 *   <li><b>Pistons.</b> {@code PistonBaseBlockWearMixin} reads the wear of every block a piston is about to move
 *   before any of them changes (each block lands where the next one stood) and puts it on that block's moving block
 *   entity ({@link #MOVING}, saved with it); {@code PistonMovingBlockEntityWearMixin} sets it where the block lands.</li>
 * </ul>
 */
public final class WearCarry {
	/**
	 * Wear carried by a block's item.
	 *
	 * @param wear   wear points, always below {@code points}
	 * @param points the block's points when it was broken, so the client can show a percentage without the rules
	 */
	public record ItemWear(int wear, int points) implements TooltipProvider {
		public static final Codec<ItemWear> CODEC = RecordCodecBuilder.create(i -> i.group(
				ExtraCodecs.POSITIVE_INT.fieldOf("wear").forGetter(ItemWear::wear),
				ExtraCodecs.POSITIVE_INT.fieldOf("points").forGetter(ItemWear::points)
		).apply(i, ItemWear::new));
		public static final StreamCodec<ByteBuf, ItemWear> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, ItemWear::wear,
				ByteBufCodecs.VAR_INT, ItemWear::points,
				ItemWear::new);

		/** Wear as a whole percentage, as Jade shows it on the block. */
		public int percent() {
			return (int) Math.round(100.0 * wear / points);
		}

		@Override
		public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag,
								 DataComponentGetter components) {
			tooltip.accept(Component.translatable("tooltip.ashfall.wear", percent()).withStyle(ChatFormatting.GRAY));
		}
	}

	public static final DataComponentType<ItemWear> ITEM_WEAR = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			id("wear"), DataComponentType.<ItemWear>builder().persistent(ItemWear.CODEC)
					.networkSynchronized(ItemWear.STREAM_CODEC).build());
	/** Wear of the block a piston is moving, on its moving block entity until it lands. */
	public static final AttachmentType<Integer> MOVING = AttachmentRegistry.create(id("moving_wear"),
			builder -> builder.persistent(ExtraCodecs.POSITIVE_INT));

	private record Spot(ResourceKey<Level> dimension, long pos) {
	}

	private record Removed(Block block, int wear) {
	}

	/** Worn blocks removed this tick, by their wear position; emptied every tick. */
	private static final Map<Spot, Removed> REMOVED = new HashMap<>();

	private WearCarry() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	static void init() {
		ItemComponentTooltipProviderRegistry.addLast(ITEM_WEAR);
		ServerTickEvents.END_SERVER_TICK.register(server -> REMOVED.clear());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> REMOVED.clear());
	}

	// ---- breaking and placing ----

	/** {@link FortifyWear#blockChanged}: the worn block {@code old} left {@code pos} (its wear position). */
	static void removed(ServerLevel level, BlockPos pos, BlockState old, int wear) {
		REMOVED.put(new Spot(level.dimension(), pos.asLong()), new Removed(old.getBlock(), wear));
	}

	/** Puts the wear of the {@code state} block at {@code origin} on its drops of itself (from the drops mixin). */
	public static void tagDrops(ServerLevel level, Vec3 origin, BlockState state, List<ItemStack> drops) {
		int points = FortifyWear.points(state);
		Item item = state.getBlock().asItem();
		if (points == 0 || item == Items.AIR || drops.stream().noneMatch(stack -> stack.is(item))) {
			return;
		}
		int wear = dropWear(level, BlockPos.containing(origin), state);
		if (wear <= 0) {
			return;
		}
		ItemWear carried = new ItemWear(Math.min(wear, points - 1), points);
		for (ItemStack stack : drops) {
			if (stack.is(item)) {
				stack.set(ITEM_WEAR, carried);
			}
		}
	}

	private static int dropWear(ServerLevel level, BlockPos pos, BlockState state) {
		BlockState here = level.getBlockState(pos);
		if (here.is(state.getBlock())) {
			return FortifyWear.wear(level, FortifyWear.anchor(here, pos));
		}
		// broken mid-push: the moving piston drops the block it carries
		if (level.getBlockEntity(pos) instanceof PistonMovingBlockEntity moving && moving.getMovedState().is(state.getBlock())) {
			Integer wear = moving.removeAttached(MOVING);
			return wear == null ? 0 : wear;
		}
		Removed removed = REMOVED.remove(new Spot(level.dimension(), FortifyWear.anchor(state, pos).asLong()));
		return removed != null && removed.block() == state.getBlock() ? removed.wear() : 0;
	}

	/** A block was placed at {@code pos} from {@code stack} (from {@code BlockItemWearMixin}). */
	public static void placed(ServerLevel level, BlockPos pos, ItemStack stack) {
		ItemWear carried = stack.get(ITEM_WEAR);
		if (carried != null) {
			BlockState state = level.getBlockState(pos);
			FortifyWear.restore(level, FortifyWear.anchor(state, pos), state, carried.wear());
		}
	}

	// ---- pistons ----

	/** Wear on each of the blocks a piston is about to move, read before any of them changes. */
	public static Map<BlockPos, Integer> beforeMove(ServerLevel level, List<BlockPos> toPush) {
		Map<BlockPos, Integer> out = new HashMap<>();
		for (BlockPos pos : toPush) {
			int wear = FortifyWear.wear(level, pos);
			if (wear > 0) {
				out.put(pos.immutable(), wear);
			}
		}
		return out;
	}

	/** The block from {@code from} is now moving in {@code moving}: it takes its wear along. */
	public static void startMove(BlockEntity moving, BlockPos from, Map<BlockPos, Integer> before) {
		Integer wear = before.get(from);
		if (wear != null) {
			moving.setAttached(MOVING, wear);
		}
	}

	/** After a moving block entity ticks: if its block just landed at {@code pos}, the wear goes there. */
	public static void landed(ServerLevel level, BlockPos pos, PistonMovingBlockEntity moving) {
		if (!moving.isRemoved() || !moving.hasAttached(MOVING)) {
			return;
		}
		int wear = moving.removeAttached(MOVING);
		BlockState state = level.getBlockState(pos);
		if (state.is(moving.getMovedState().getBlock())) {
			FortifyWear.restore(level, FortifyWear.anchor(state, pos), state, wear);
		}
	}
}
