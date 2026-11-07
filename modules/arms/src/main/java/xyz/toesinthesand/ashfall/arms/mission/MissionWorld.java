package xyz.toesinthesand.ashfall.arms.mission;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.arms.finale.FinaleBlocks;
import xyz.toesinthesand.ashfall.arms.finale.FinaleRun;
import xyz.toesinthesand.ashfall.arms.finale.FinaleState;
import xyz.toesinthesand.ashfall.arms.grid.DistrictLoop;
import xyz.toesinthesand.ashfall.arms.grid.DistrictRecord;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;
import xyz.toesinthesand.ashfall.arms.grid.GridDistricts;
import xyz.toesinthesand.ashfall.arms.grid.GridSettings;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.grid.LoopRules;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.gun.EnergyGunItem;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.item.CellPackItem;
import xyz.toesinthesand.ashfall.arms.story.EndingVote;
import xyz.toesinthesand.ashfall.arms.story.MapMarker;
import xyz.toesinthesand.ashfall.arms.story.MapMarkers;
import xyz.toesinthesand.ashfall.arms.story.RadioProgress;
import xyz.toesinthesand.ashfall.arms.story.RadioReport;
import xyz.toesinthesand.ashfall.arms.story.StoryState;
import xyz.toesinthesand.ashfall.director.boss.BossLair;
import xyz.toesinthesand.ashfall.director.boss.BossLairs;
import xyz.toesinthesand.ashfall.director.boss.BossState;
import xyz.toesinthesand.ashfall.director.boss.LairRules;
import xyz.toesinthesand.ashfall.director.haven.BunkerCores;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a {@link MissionFacts} snapshot for one player out of the live world: the radio flag, their inventory, their
 * party's Bunker Core, the {@link GridState} per district with its sub-progress, the boss lairs, the finale run and
 * the story ending. It also raises the world's sticky endgame milestones ({@link MissionState}) when it sees them, so
 * any read keeps them current. Everything here is a cheap lookup in saved data or a cached plan, so the tracker can afford to
 * run it for every player once a second.
 */
public final class MissionWorld {
	private MissionWorld() {
	}

	/** The facts for {@code player} over every scored district. */
	public static MissionFacts collect(ServerPlayer player) {
		return collect(player, RadioReport.orderedDistricts(GridState.get(player.level().getServer())));
	}

	/**
	 * The facts for {@code player} over {@code districtIds}, in that order. Gametests pass their own test district,
	 * since the real districts are shared by every test running on the server at the same time.
	 */
	public static MissionFacts collect(ServerPlayer player, List<String> districtIds) {
		MinecraftServer server = player.level().getServer();
		MissionSettings settings = MissionSettings.get();
		GridState grid = GridState.get(server);

		List<MissionFacts.District> districts = new ArrayList<>();
		for (String id : districtIds) {
			districts.add(district(player, grid, id));
		}

		List<ServerPlayer> party = party(player);
		int crystals = count(party, ArmsItems.RESONANCE_CRYSTAL);
		int banks = count(party, FinaleBlocks.CAPACITOR_BANK);
		// Seeing a milestone once makes it stick (MissionState): stored or offline items must not send the party back.
		MissionState milestones = MissionState.get(server);
		milestones.see(crystals >= settings.crystalsNeeded(), seen(party, FinaleBlocks.UPLINK_FRAME, 1),
				seen(party, FinaleBlocks.CAPACITOR_BANK, settings.banksNeeded()), seen(party, FinaleBlocks.GRID_UPLINK, 1));
		if (milestones.crystalsGathered()) {
			crystals = Math.max(crystals, settings.crystalsNeeded());
		}
		if (milestones.banksMade()) {
			banks = Math.max(banks, settings.banksNeeded());
		}
		boolean frame = milestones.frameMade();
		boolean uplink = milestones.uplinkMade();

		int bosses = 0;
		Optional<MissionPos> nearestBoss = Optional.empty();
		double best = Double.MAX_VALUE;
		BossState bossState = BossState.get(server);
		for (Identifier id : settings.storyBosses()) {
			Optional<BossState.Lair> lair = bossState.existing(id.toString());
			if (lair.isPresent() && (lair.get().status() == LairRules.Status.DEFEATED || lair.get().kills() > 0)) {
				bosses++;
				continue;
			}
			Optional<MissionPos> pos = lairPos(id, lair);
			if (pos.isPresent()) {
				double d = pos.get().distanceFrom(player.getX(), player.getZ());
				if (d < best) {
					best = d;
					nearestBoss = pos;
				}
			}
		}

		FinaleRun run = FinaleState.get(server).run();
		int pylonsTotal = run.pylons.isEmpty() ? settings.pylons() : run.pylons.size();

		return new MissionFacts(RadioProgress.get(player).radio(), MissionProgress.get(player).armed() || armedNow(player),
				bunker(player), districts, crystals, settings.crystalsNeeded(), bosses, settings.storyBosses().size(), nearestBoss, frame,
				banks, settings.banksNeeded(), uplink, finale(run), EndingVote.active(), run.pylonsBroken, pylonsTotal, grid.gridPercent(),
				StoryState.get(server).ending().id(), marker(MapMarker.SAFEHOUSE), marker(MapMarker.HULL));
	}

	/** One district's loop progress, with the numbers its own settings ask for. */
	static MissionFacts.District district(ServerPlayer player, GridState grid, String id) {
		GridSettings settings = GridDistricts.settings(id);
		LoopRules rules = settings.rules();
		DistrictRecord r = grid.record(id);
		DistrictState state = grid.state(id);
		int couplers = r == null ? 0 : r.couplers;
		int cells = r == null ? 0 : r.cells;
		long hold = r == null ? 0 : r.holdTicks;
		int holdPercent;
		if (r != null && r.holdDone(rules)) {
			holdPercent = 100;
		} else {
			// Never round up to 100 while ticks remain: "100%" must mean done.
			holdPercent = rules.holdTicks() <= 0 ? 100 : (int) Math.min(99, hold * 100 / rules.holdTicks());
		}
		Set<Long> keys = GridDistricts.keyRelayChunks(id);
		int linked = 0;
		Optional<MissionPos> nextRelay = Optional.empty();
		double best = Double.MAX_VALUE;
		for (long key : keys) {
			if (r != null && r.linked.contains(key)) {
				linked++;
				continue;
			}
			MissionPos centre = new MissionPos(ChunkPos.getX(key) * 16 + 8, ChunkPos.getZ(key) * 16 + 8);
			double d = centre.distanceFrom(player.getX(), player.getZ());
			if (d < best) {
				best = d;
				nextRelay = Optional.of(centre);
			}
		}
		int couplersNeeded = state == DistrictState.BROKEN ? DistrictLoop.reRepairCouplers(rules) : rules.couplers();
		return new MissionFacts.District(id, state, couplers, couplersNeeded, cells, rules.cells(), holdPercent, settings.holdRadius(), linked,
				keys.size(), core(r, id), nextRelay);
	}

	/** The registered Substation Core, else the authored map marker (the radio's rule, see {@code RadioReport}). */
	private static Optional<MissionPos> core(DistrictRecord r, String id) {
		if (r != null && r.hasCore()) {
			BlockPos pos = BlockPos.of(r.corePos);
			return Optional.of(new MissionPos(pos.getX(), pos.getZ()));
		}
		return MapMarkers.substation(id).map(m -> new MissionPos(m.x(), m.z()));
	}

	/** The player's own or party Bunker Core in the overworld, where every objective lives. */
	public static Optional<MissionPos> bunker(ServerPlayer player) {
		return BunkerCores.forPlayer(player.level().getServer(), player.getUUID())
				.filter(core -> core.dimension() == Level.OVERWORLD)
				.map(core -> new MissionPos(core.pos().getX(), core.pos().getZ()));
	}

	/** A weapon and a pack at once; a pack loaded into the weapon counts. */
	static boolean armedNow(ServerPlayer player) {
		boolean weapon = false, pack = false;
		for (ItemStack stack : player.getInventory()) {
			if (stack.getItem() instanceof EnergyGunItem) {
				weapon = true;
				pack |= stack.has(ArmsComponents.LOADED_PACK);
			} else if (stack.getItem() instanceof CellPackItem) {
				pack = true;
			}
		}
		return weapon && pack;
	}

	/** The player plus everyone else online: endgame items are pooled (see {@link MissionFacts}). */
	private static List<ServerPlayer> party(ServerPlayer player) {
		Set<ServerPlayer> out = new LinkedHashSet<>();
		out.add(player);
		out.addAll(player.level().getServer().getPlayerList().getPlayers());
		return List.copyOf(out);
	}

	/**
	 * At least {@code needed} of {@code item} are held right now, or one online player's stats say they crafted that
	 * many. The stat catches an item crafted and stored inside one sync interval, before it was ever seen held.
	 */
	private static boolean seen(List<ServerPlayer> players, Item item, int needed) {
		if (count(players, item) >= needed) {
			return true;
		}
		for (ServerPlayer p : players) {
			if (p.getStats().getValue(Stats.ITEM_CRAFTED, item) >= needed) {
				return true;
			}
		}
		return false;
	}

	private static int count(List<ServerPlayer> players, Item item) {
		int n = 0;
		for (ServerPlayer p : players) {
			for (ItemStack stack : p.getInventory()) {
				if (stack.is(item)) {
					n += stack.getCount();
				}
			}
		}
		return n;
	}

	private static MissionFacts.Finale finale(FinaleRun run) {
		return switch (run.status) {
			case IDLE -> run.standby != FinaleRun.NONE ? MissionFacts.Finale.STANDBY : MissionFacts.Finale.IDLE;
			case PAUSED -> MissionFacts.Finale.PAUSED;
			case COMPLETE -> MissionFacts.Finale.COMPLETE;
			case RUNNING -> switch (run.phase) {
				case SURGE -> MissionFacts.Finale.SURGE;
				case FEEDBACK -> MissionFacts.Finale.FEEDBACK;
				case HEART -> MissionFacts.Finale.HEART;
			};
		};
	}

	/**
	 * Where a lair is: the boss's recorded spawn point once it has spawned, else its fixed position, else the centre of
	 * its anchor chunks, else (for a lair tied to a site, the Juggernaut at the hull) the Wreck marker.
	 */
	private static Optional<MissionPos> lairPos(Identifier id, Optional<BossState.Lair> state) {
		Optional<BlockPos> spawned = state.flatMap(BossState.Lair::spawnPos);
		if (spawned.isPresent()) {
			return spawned.map(p -> new MissionPos(p.getX(), p.getZ()));
		}
		BossLair lair = BossLairs.get(id);
		if (lair == null) {
			return Optional.empty();
		}
		BossLair.Anchor anchor = lair.anchor();
		if (anchor.pos().isPresent()) {
			return anchor.pos().map(p -> new MissionPos(p.getX(), p.getZ()));
		}
		if (anchor.chunk().isPresent() && anchor.chunk().get().size() >= 2) {
			List<Integer> chunk = anchor.chunk().get();
			List<Integer> size = anchor.size().filter(s -> s.size() >= 2).orElse(List.of(1, 1));
			return Optional.of(new MissionPos(chunk.get(0) * 16 + size.get(0) * 8, chunk.get(1) * 16 + size.get(1) * 8));
		}
		return marker(MapMarker.HULL);
	}

	private static Optional<MissionPos> marker(String kind) {
		return MapMarkers.all().stream().filter(m -> kind.equals(m.kind())).findFirst().map(m -> new MissionPos(m.x(), m.z()));
	}
}
