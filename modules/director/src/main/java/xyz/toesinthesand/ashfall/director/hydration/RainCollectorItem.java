package xyz.toesinthesand.ashfall.director.hydration;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import xyz.toesinthesand.ashfall.director.haven.Havens;

/**
 * The Rain Collector's item: it only places inside a Bunker Core's area. Why: clean water anywhere would make thirst
 * trivial on the road; a collector is a reason to have a base and come back to it.
 */
public class RainCollectorItem extends BlockItem {
	public RainCollectorItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		Player player = context.getPlayer();
		if (context.getLevel() instanceof ServerLevel level && !Havens.inBunker(level, context.getClickedPos())) {
			if (player != null) {
				player.sendOverlayMessage(Component.translatable("message.ashfall.rain_collector.bunker_only"));
			}
			return InteractionResult.FAIL;
		}
		return super.place(context);
	}
}
