package xyz.toesinthesand.ashfall.arms.story;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import xyz.toesinthesand.ashfall.arms.grid.DistrictRecord;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.mission.MissionRadio;
import xyz.toesinthesand.ashfall.salvage.atlas.AtlasData;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What the Field Radio says when used: the grid percentage, every district's state, a heading to the nearest
 * substation that is still dark, the current mission objective, the way home, the Relay Log count and a heading to
 * the nearest relay the player has not tuned into yet. Sneaking while using it gives only the objective and the way
 * home ({@link #sendShort}), for a quick check on the move.
 * <p>
 * Chat, not a screen, so it works for every player without the client mod's map, stays in the chat history for
 * the next minute of walking, and the action bar repeats the one line that matters while moving.
 */
public final class RadioReport {
	/** A relay counts as tuned if the player used a relay this close to its planned cell (the placer may shift it). */
	static final int TUNED_RADIUS = 16;

	private RadioReport() {
	}

	/** District ids in map order (River Arts, South Slope, Downtown, Mission), from the zones' district index. */
	public static List<String> orderedDistricts(GridState grid) {
		List<String> ids = new ArrayList<>(grid.districts());
		ids.sort(Comparator.comparingInt((String id) -> ZoneMap.current().byId(id).map(Zone::districtIndex).orElse(99))
				.thenComparing(id -> id));
		return ids;
	}

	public static Component districtName(String id) {
		return Component.translatable("zone.ashfall." + id);
	}

	public static Component stateName(DistrictState state) {
		return Component.translatable("district_state.ashfall." + state.id()).withStyle(stateColour(state));
	}

	public static ChatFormatting stateColour(DistrictState state) {
		return switch (state) {
			case DARK -> ChatFormatting.DARK_GRAY;
			case REPAIRED -> ChatFormatting.GOLD;
			case ENERGIZED -> ChatFormatting.YELLOW;
			case SIEGE -> ChatFormatting.RED;
			case RECLAIMED -> ChatFormatting.AQUA;
			case BROKEN -> ChatFormatting.DARK_RED;
			case BLACKOUT -> ChatFormatting.DARK_PURPLE;
		};
	}

	public static void send(ServerPlayer player) {
		GridState grid = GridState.get(player.level().getServer());
		player.sendSystemMessage(Component.translatable("radio.ashfall.header", grid.gridPercent()).withStyle(ChatFormatting.GOLD));
		for (String id : orderedDistricts(grid)) {
			player.sendSystemMessage(Component.literal("  ").append(districtName(id).copy().withStyle(ChatFormatting.GRAY))
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY)).append(stateName(grid.state(id))));
		}
		decayLines(player, grid).forEach(player::sendSystemMessage);

		Optional<Component> substation = nearestDarkSubstation(player, grid);
		Component heading = substation.orElse(Component.translatable("radio.ashfall.all_lit").withStyle(ChatFormatting.AQUA));
		player.sendSystemMessage(heading);
		player.sendSystemMessage(MissionRadio.objectiveLine(player));
		MissionRadio.homeLine(player).ifPresent(player::sendSystemMessage);

		RadioProgress progress = RadioProgress.get(player);
		player.sendSystemMessage(Component.translatable("radio.ashfall.logs", RelayLogs.collected(progress), RelayLogs.pool().size())
				.withStyle(ChatFormatting.GRAY));
		nearestUntunedRelay(player, progress).ifPresent(player::sendSystemMessage);
		supplyDrop(player).ifPresent(player::sendSystemMessage);
		xyz.toesinthesand.ashfall.arms.radio.RadioJobs.radioLine(player).ifPresent(player::sendSystemMessage);

		player.sendOverlayMessage(Component.translatable("radio.ashfall.overlay", grid.gridPercent()).append(" ").append(heading));
	}

	/** The short report (sneak + use): the objective and the way home, repeated in the action bar. */
	public static void sendShort(ServerPlayer player) {
		Component objective = MissionRadio.objectiveLine(player);
		player.sendSystemMessage(objective);
		Optional<Component> home = MissionRadio.homeLine(player);
		home.ifPresent(player::sendSystemMessage);
		player.sendOverlayMessage(home.orElse(objective));
	}

	private static Optional<Component> nearestDarkSubstation(ServerPlayer player, GridState grid) {
		MapMarker best = null;
		double bestDistance = Double.MAX_VALUE;
		for (String id : grid.districts()) {
			if (!DistrictProgress.unlit(grid.state(id))) {
				continue;
			}
			Optional<MapMarker> marker = coreMarker(grid, id).or(() -> MapMarkers.substation(id));
			if (marker.isEmpty()) {
				continue;
			}
			double d = Math.hypot(marker.get().x() - player.getX(), marker.get().z() - player.getZ());
			if (d < bestDistance) {
				bestDistance = d;
				best = marker.get();
			}
		}
		if (best == null) {
			return Optional.empty();
		}
		return Optional.of(Component.translatable("radio.ashfall.substation", districtName(best.district().orElse("")),
				Compass.roughDistance(player.getX(), player.getZ(), best.x(), best.z()), bearing(player, best.x(), best.z()))
				.withStyle(ChatFormatting.YELLOW));
	}

	/**
	 * The district's real Substation Core once the grid has registered it (the first time its chunk loaded),
	 * which beats the authored map marker: the marker is the landmark's chunk centre, the core is the spot.
	 */
	private static Optional<MapMarker> coreMarker(GridState grid, String id) {
		DistrictRecord record = grid.record(id);
		if (record == null || !record.hasCore()) {
			return Optional.empty();
		}
		BlockPos core = BlockPos.of(record.corePos);
		return Optional.of(new MapMarker(MapMarker.SUBSTATION, Optional.of(id), core.getX(), core.getZ()));
	}

	private static Optional<Component> nearestUntunedRelay(ServerPlayer player, RadioProgress progress) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (AtlasData.RelayCandidate relay : AtlasData.get(player.level().getServer()).relays()) {
			if (!relay.dimension().equals(player.level().dimension()) || tuned(progress, relay.pos())) {
				continue;
			}
			double d = relay.pos().distSqr(player.blockPosition());
			if (d < bestDistance) {
				bestDistance = d;
				best = relay.pos();
			}
		}
		if (best == null) {
			return Optional.empty();
		}
		return Optional.of(Component.translatable("radio.ashfall.carrier",
				Compass.roughDistance(player.getX(), player.getZ(), best.getX(), best.getZ()), bearing(player, best.getX(), best.getZ()))
				.withStyle(ChatFormatting.DARK_AQUA));
	}

	private static boolean tuned(RadioProgress progress, BlockPos planned) {
		for (long packed : progress.relays()) {
			BlockPos pos = BlockPos.of(packed);
			if (Math.abs(pos.getX() - planned.getX()) <= TUNED_RADIUS && Math.abs(pos.getZ() - planned.getZ()) <= TUNED_RADIUS) {
				return true;
			}
		}
		return false;
	}

	/** Upkeep: flickering and blacked-out districts, and districts with relays worn below the warning line. */
	private static java.util.List<Component> decayLines(ServerPlayer player, GridState grid) {
		var settings = xyz.toesinthesand.ashfall.arms.grid.DecaySettings.get();
		java.util.List<Component> out = new java.util.ArrayList<>();
		if (!settings.enabled()) {
			return out;
		}
		long day = xyz.toesinthesand.ashfall.director.spawn.DirectorDay.today(player.level().getServer());
		for (String id : orderedDistricts(grid)) {
			var r = grid.record(id);
			if (r == null || (r.state != DistrictState.RECLAIMED && r.state != DistrictState.BLACKOUT)) {
				continue;
			}
			var keys = xyz.toesinthesand.ashfall.arms.grid.GridDistricts.keyRelayChunks(id);
			int working = xyz.toesinthesand.ashfall.arms.grid.DistrictLoop.workingRelays(r, keys);
			int required = xyz.toesinthesand.ashfall.arms.grid.GridDistricts.settings(id).rules().relaysRequired(keys);
			if (r.state == DistrictState.BLACKOUT) {
				out.add(Component.translatable("radio.ashfall.decay.blackout", districtName(id), working, required)
						.withStyle(ChatFormatting.DARK_PURPLE));
			} else if (r.flickerSinceDay != xyz.toesinthesand.ashfall.arms.grid.DistrictRecord.UNSET) {
				out.add(Component.translatable("radio.ashfall.decay.flicker", districtName(id), working, required,
						xyz.toesinthesand.ashfall.arms.grid.DistrictLoop.flickerDaysLeft(r, settings, day)).withStyle(ChatFormatting.YELLOW));
			} else {
				int worn = 0;
				int lowest = xyz.toesinthesand.ashfall.arms.grid.DistrictRecord.FULL_CONDITION;
				for (long chunk : r.linked) {
					int c = r.condition(chunk);
					if (keys.contains(chunk) && c <= settings.warnBelow()) {
						worn++;
						lowest = Math.min(lowest, c);
					}
				}
				if (worn > 0) {
					out.add(Component.translatable("radio.ashfall.decay.worn", districtName(id), worn, lowest).withStyle(ChatFormatting.YELLOW));
				}
			}
		}
		return out;
	}

	/** Distance, bearing and time left to the supply drop under way, if any. */
	private static Optional<Component> supplyDrop(ServerPlayer player) {
		return xyz.toesinthesand.ashfall.arms.supply.SupplyDropState.get(player.level().getServer()).active().map(drop -> {
			long seconds = Math.max(0, drop.deadline() - player.level().getGameTime()) / 20;
			int metres = (int) Math.sqrt(player.distanceToSqr(drop.x() + 0.5, player.getY(), drop.z() + 0.5));
			return Component.translatable("radio.ashfall.supply_drop", metres, bearing(player, drop.x(), drop.z()),
					String.format("%d:%02d", seconds / 60, seconds % 60)).withStyle(ChatFormatting.GREEN);
		});
	}

	private static MutableComponent bearing(ServerPlayer player, double x, double z) {
		return Component.translatable("compass.ashfall." + Compass.point(player.getX(), player.getZ(), x, z));
	}
}
