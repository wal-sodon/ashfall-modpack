package xyz.toesinthesand.ashfall.arms.survivor;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * The Supply Locker's item: it only places inside a Bunker Core's area, like the Rain Collector. Survivors only use
 * lockers in their own home claim, so one anywhere else would be a chest that does nothing for them.
 */
public class SupplyLockerItem extends BlockItem {
	public SupplyLockerItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		if (context.getLevel() instanceof ServerLevel level && !Survivors.inBunker(level, context.getClickedPos())) {
			Player player = context.getPlayer();
			if (player != null) {
				player.sendOverlayMessage(Component.translatable("message.ashfall.supply_locker.bunker_only"));
			}
			return InteractionResult.FAIL;
		}
		return super.place(context);
	}
}
