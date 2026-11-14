package xyz.toesinthesand.ashfall.arms.travel;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.arms.grid.DistrictRecord;
import xyz.toesinthesand.ashfall.arms.grid.GridDistricts;
import xyz.toesinthesand.ashfall.arms.grid.GridReclaim;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.director.haven.BunkerCores;
import xyz.toesinthesand.ashfall.director.haven.Havens;
import xyz.toesinthesand.ashfall.director.haven.SafeZone;
import xyz.toesinthesand.ashfall.director.haven.SafeZones;
import xyz.toesinthesand.ashfall.director.horde.HordeSchedule;
import xyz.toesinthesand.ashfall.director.horde.HordeState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The transit network's stops, all read from state that already exists: the active safe zones (the Montford
 * safehouse), the player's party Bunker Core, and the Substation Core of every reclaimed district. Nothing is
 * saved here, so a broken core or a district lost to a breach drops off the list by itself.
 */
public final class TravelNodes {
	public static final String BUNKER = "bunker";
	public static final String ZONE_PREFIX = "zone:";
	public static final String DISTRICT_PREFIX = "district:";
	/** The Montford safehouse's stop (its datapack safe zone), which the Grid Map's safehouse marker travels to. */
	public static final String SAFEHOUSE = ZONE_PREFIX + "ashfall:montford_safehouse";

	private TravelNodes() {
	}

	/**
	 * One stop.
	 *
	 * @param id     command argument: {@code zone:<id>}, {@code bunker} or {@code district:<id>}
	 * @param anchor the block the stop is centred on (core, or zone arrival point)
	 * @param arrive where a traveller lands
	 */
	public record Node(String id, Component name, ResourceKey<Level> dimension, BlockPos anchor, BlockPos arrive) {
	}

	/** Every stop open to {@code player}, safe zones first, then the Bunker Core, then districts. */
	public static List<Node> all(MinecraftServer server, ServerPlayer player) {
		List<Node> out = new ArrayList<>();
		for (Map.Entry<String, SafeZone> e : SafeZones.active(server).entrySet()) {
			if (e.getKey().startsWith(GridReclaim.compoundZoneId(""))) {
				continue; // a reclaimed compound: listed below as its district, landing beside the core
			}
			SafeZone zone = e.getValue();
			ServerLevel level = server.getLevel(zone.dimension());
			if (level != null) {
				BlockPos spot = Havens.respawnPoint(level, zone);
				out.add(new Node(ZONE_PREFIX + e.getKey(), Component.literal(zone.name()), zone.dimension(), spot, spot));
			}
		}
		BunkerCores.forPlayer(server, player.getUUID()).ifPresent(core -> {
			ServerLevel level = server.getLevel(core.dimension());
			if (level != null) {
				out.add(new Node(BUNKER, Component.translatable("message.ashfall.travel.node.bunker"), core.dimension(),
						core.pos(), BunkerCores.respawnSpot(level, core.pos())));
			}
		});
		ServerLevel overworld = server.overworld();
		GridState grid = GridState.get(server);
		for (Map.Entry<String, DistrictRecord> e : grid.records().entrySet()) {
			if (grid.isReclaimed(e.getKey()) && e.getValue().hasCore()) {
				BlockPos core = BlockPos.of(e.getValue().corePos);
				out.add(new Node(DISTRICT_PREFIX + e.getKey(),
						Component.translatable("message.ashfall.travel.node.district", GridDistricts.name(e.getKey())),
						Level.OVERWORLD, core, BunkerCores.respawnSpot(overworld, core)));
			}
		}
		return out;
	}

	/**
	 * The stop {@code player} is standing at, if any: inside a safe zone or their core's claim, or in a reclaimed
	 * district's compound (or near its core).
	 */
	public static Optional<Node> here(MinecraftServer server, ServerPlayer player, List<Node> nodes) {
		ResourceKey<Level> dim = player.level().dimension();
		BlockPos pos = player.blockPosition();
		int radius = TravelRules.get().substationRadius();
		for (Node node : nodes) {
			if (!node.dimension().equals(dim)) {
				continue;
			}
			boolean at;
			if (node.id().startsWith(ZONE_PREFIX)) {
				SafeZone zone = SafeZones.active(server).get(node.id().substring(ZONE_PREFIX.length()));
				at = zone != null && zone.contains(dim, pos);
			} else if (node.id().equals(BUNKER)) {
				at = BunkerCores.forPlayer(server, player.getUUID()).map(c -> c.area().contains(pos.getX(), pos.getZ())).orElse(false);
			} else {
				SafeZone compound = SafeZones.active(server).get(GridReclaim.compoundZoneId(node.id().substring(DISTRICT_PREFIX.length())));
				at = (compound != null && compound.contains(dim, pos)) || node.anchor().distSqr(pos) <= (double) radius * radius;
			}
			if (at) {
				return Optional.of(node);
			}
		}
		return Optional.empty();
	}

	/**
	 * The substation a Defend trip goes to: the district under a Breach now, else the one tonight's Breach siren
	 * named (before nightfall it is still reclaimed). {@code day} is the Breach's world day, so each player gets one
	 * free trip per Breach.
	 */
	public record BreachTarget(String district, int day, Node node) {
	}

	/** The Breach a Defend trip can go to right now, if any. */
	public static Optional<BreachTarget> breachTarget(MinecraftServer server) {
		return breachTarget(server, "");
	}

	/**
	 * {@link #breachTarget(MinecraftServer)} limited to district {@code only} ("" for any). Gametests pass their own
	 * district, since tests in a batch run at once and another test's Breach must not be picked.
	 */
	public static Optional<BreachTarget> breachTarget(MinecraftServer server, String only) {
		ServerLevel overworld = server.overworld();
		GridState grid = GridState.get(server);
		for (Map.Entry<String, DistrictRecord> e : grid.records().entrySet()) {
			DistrictRecord r = e.getValue();
			if (!only.isEmpty() && !only.equals(e.getKey())) {
				continue;
			}
			if (r.breach && r.hasCore() && r.siegeAt != DistrictRecord.UNSET) {
				return Optional.of(new BreachTarget(e.getKey(), HordeSchedule.day(r.siegeAt), districtNode(overworld, e.getKey(), r)));
			}
		}
		HordeState state = HordeState.get(server);
		int today = HordeSchedule.day(overworld.getOverworldClockTime());
		String warned = state.breachTarget();
		if ((only.isEmpty() || only.equals(warned)) && state.breachWarnedDay() == today && state.lastBreachDay() < today
				&& !warned.isEmpty() && grid.isReclaimed(warned)) {
			DistrictRecord r = grid.record(warned);
			if (r != null && r.hasCore()) {
				return Optional.of(new BreachTarget(warned, today, districtNode(overworld, warned, r)));
			}
		}
		return Optional.empty();
	}

	private static Node districtNode(ServerLevel overworld, String id, DistrictRecord r) {
		BlockPos core = BlockPos.of(r.corePos);
		return new Node(DISTRICT_PREFIX + id, Component.translatable("message.ashfall.travel.node.district", GridDistricts.name(id)),
				Level.OVERWORLD, core, BunkerCores.respawnSpot(overworld, core));
	}

	/** Blocks between two stops, or -1 across dimensions. */
	public static double distance(Node from, Node to) {
		if (!from.dimension().equals(to.dimension())) {
			return -1;
		}
		return Math.sqrt(from.anchor().distSqr(to.anchor()));
	}
}
