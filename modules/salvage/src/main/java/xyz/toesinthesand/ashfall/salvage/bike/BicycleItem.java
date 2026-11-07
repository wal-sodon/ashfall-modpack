package xyz.toesinthesand.ashfall.salvage.bike;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Sets a picked-up bicycle back down on the clicked face, facing the way the player looks. */
public class BicycleItem extends Item {
	public BicycleItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		Bicycle bike = Bikes.BICYCLE.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (bike == null || !level.noCollision(Bikes.BICYCLE.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
			return InteractionResult.FAIL;
		}
		float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() : 0f;
		bike.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
		bike.setYHeadRot(yaw);
		bike.setYBodyRot(yaw);
		level.addFreshEntity(bike);
		context.getItemInHand().consume(1, context.getPlayer());
		return InteractionResult.SUCCESS;
	}
}
