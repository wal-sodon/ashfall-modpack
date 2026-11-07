package xyz.toesinthesand.ashfall.arms.survivor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Distress rescues (survivors plan, phase 1): from {@link SurvivorBalance#rescueFirstDay} (day 7), a held distress beacon leaves the caller standing
 * beside the reward chest as well as their note. The rescuer is the nearest player within {@link #RESCUER_RANGE} of the
 * beacon whose group still has room, so a full player standing closest doesn't cost a player with room the rescue; the
 * survivor takes them as leader and starts out following. (Radio jobs are taken for everyone, so there is no single job
 * holder to prefer.) No rescue (just the note, as before) before day 7, with nobody near, when every player near is at
 * their group's cap, or when the world is at its cap ({@link SurvivorRoster}); each of the last two has its own line.
 */
public final class SurvivorRescue {
	/** The rescuer must be this close to the beacon when the hold ends. */
	public static final double RESCUER_RANGE = 24.0;

	private SurvivorRescue() {
	}

	/**
	 * The survivor a finished distress hold at {@code beacon} leaves, or null when there is none (see the class comment).
	 *
	 * @param day today's Director day, passed in so gametests can run on any day
	 */
	public static @Nullable Survivor atBeacon(ServerLevel level, BlockPos beacon, int day) {
		if (day < SurvivorBalance.get().rescueFirstDay()) {
			return null;
		}
		List<ServerPlayer> near = nearby(level, beacon);
		if (near.isEmpty()) {
			return null;
		}
		SurvivorRoster roster = SurvivorRoster.get(level.getServer());
		if (!roster.worldHasRoom()) {
			Component full = Component.translatable("message.ashfall.survivor.rescue_world_full", SurvivorBalance.get().caps().world())
					.withStyle(ChatFormatting.GRAY);
			near.forEach(p -> p.sendSystemMessage(full));
			return null;
		}
		ServerPlayer rescuer = rescuer(level, beacon);
		if (rescuer == null) {
			near.getFirst().sendSystemMessage(Component.translatable("message.ashfall.survivor.rescue_full", SurvivorBalance.get().caps().group())
					.withStyle(ChatFormatting.GRAY));
			return null;
		}
		Survivor survivor = Survivors.spawn(level, standingSpot(level, beacon), SurvivorRole.SETTLER, rescuer.getUUID());
		if (survivor != null) {
			rescuer.sendSystemMessage(SurvivorTalk.line(survivor, "rescued"));
		}
		return survivor;
	}

	/** The nearest living, non-spectator player within {@link #RESCUER_RANGE} of the beacon whose group has room, or null. */
	static @Nullable ServerPlayer rescuer(ServerLevel level, BlockPos beacon) {
		SurvivorRoster roster = SurvivorRoster.get(level.getServer());
		for (ServerPlayer player : nearby(level, beacon)) {
			if (roster.groupHasRoom(level.getServer(), player.getUUID())) {
				return player;
			}
		}
		return null;
	}

	/** Living, non-spectator players within {@link #RESCUER_RANGE} of the beacon, nearest first. */
	static List<ServerPlayer> nearby(ServerLevel level, BlockPos beacon) {
		double x = beacon.getX() + 0.5, y = beacon.getY(), z = beacon.getZ() + 0.5;
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && player.distanceToSqr(x, y, z) <= RESCUER_RANGE * RESCUER_RANGE) {
				out.add(player);
			}
		}
		out.sort(Comparator.comparingDouble(p -> p.distanceToSqr(x, y, z)));
		return out;
	}

	/** Beside the beacon (now the chest) where there is floor and headroom, else on top of the chest. */
	static BlockPos standingSpot(ServerLevel level, BlockPos beacon) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos pos = beacon.relative(side);
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
					&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
				return pos;
			}
		}
		return beacon.above();
	}
}
