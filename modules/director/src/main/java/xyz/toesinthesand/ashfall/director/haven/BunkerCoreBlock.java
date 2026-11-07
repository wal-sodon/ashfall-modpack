package xyz.toesinthesand.ashfall.director.haven;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Consumer;

/**
 * The Bunker Core: the heart of a player-built haven. Placing it claims the surrounding chunks, makes it a
 * respawn option for the placer's party, and keeps Director zombies from spawning nearby; breaking it undoes
 * all of that. The rules live in {@link BunkerCores}.
 * <p>
 * Removal is caught in {@code affectNeighborsAfterRemoval}, which runs for every removal that updates
 * neighbours (mining, explosions, a block placed over it). Removals without neighbour updates (an admin's
 * {@code /setblock}) are caught lazily by {@link BunkerCores#forPlayer}. Pistons cannot move the block
 * ({@link HavenBlocks}), because a moved core would leave its claim behind.
 * <p>
 * Right-clicking it with an empty hand runs the use hook, which the arms module sets to open the transit network
 * (fast travel lives there because it is paid for with Cell Pack energy; director cannot see arms).
 */
public class BunkerCoreBlock extends Block {
	private static volatile Consumer<ServerPlayer> useHook;

	/** Sets what an empty-hand right-click does (called once at mod init). */
	public static void setUseHook(Consumer<ServerPlayer> hook) {
		useHook = hook;
	}

	public BunkerCoreBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel && placer instanceof Player player) {
			BunkerCoreRecord core = BunkerCores.onPlaced(serverLevel, pos, player.getUUID());
			if (core != null) {
				int side = core.radius() * 2 + 1;
				player.sendSystemMessage(Component.translatable("message.ashfall.bunker_core.placed", side, side));
			}
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		BunkerCores.onRemoved(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		Consumer<ServerPlayer> hook = useHook;
		if (hook == null) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			hook.accept(sp);
		}
		return InteractionResult.SUCCESS;
	}
}
