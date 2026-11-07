package xyz.toesinthesand.ashfall.director.haven;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * The Bunker Core's item. It refuses to place a second core for a party or player that already has one,
 * before the block exists, so a refused placement costs nothing and leaves no half-registered block behind.
 */
public class BunkerCoreItem extends BlockItem {
	public BunkerCoreItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		Player player = context.getPlayer();
		if (player != null && context.getLevel() instanceof ServerLevel level
				&& !BunkerCores.mayPlace(level.getServer(), player.getUUID())) {
			player.sendSystemMessage(Component.translatable("message.ashfall.bunker_core.already_have"));
			return InteractionResult.FAIL;
		}
		return super.place(context);
	}
}
