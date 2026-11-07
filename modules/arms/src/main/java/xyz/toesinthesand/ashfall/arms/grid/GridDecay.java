package xyz.toesinthesand.ashfall.arms.grid;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.director.horde.HordeSchedule;
import xyz.toesinthesand.ashfall.director.spawn.DirectorDay;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * District decay (rc13): a reclaimed district is held, not finished. Its linked relays wear a little every day and a
 * horde night tears through one district's relays; with too few working relays the district flickers for a couple of
 * days and then blacks out (dark, spawning as usual). Repairing relays with wire and duct tape brings it straight
 * back: no new siege, because the district was already won and the upkeep should be a trip, not a rerun. Numbers are
 * in {@link DecaySettings}; the rules themselves are pure functions in {@link DistrictLoop}.
 */
public final class GridDecay {
	/** Ticks between decay checks; the work only happens when the world day changes. */
	public static final int CHECK_TICKS = 100;
	/** Horde nights charged at most this far back (a server that was down for weeks is not punished for all of it). */
	static final int MAX_CATCH_UP_DAYS = 14;

	private GridDecay() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 37) {
				tick(server);
			}
		});
	}

	static void tick(MinecraftServer server) {
		DecaySettings settings = DecaySettings.get();
		if (!settings.enabled()) {
			return;
		}
		ServerLevel level = server.overworld();
		long day = DirectorDay.today(server);
		GridState grid = GridState.get(server);
		hordeDamage(level, grid, settings, day);
		for (String id : grid.districts()) {
			if (!GridDistricts.isManual(id)) {
				daily(level, id, settings, day);
			}
		}
	}

	/** Today's wear and flicker check for one district, once per world day. Public for gametests. */
	public static DistrictLoop.DecayChange daily(ServerLevel level, String id, DecaySettings settings, long day) {
		GridState grid = GridState.get(level.getServer());
		DistrictRecord r = grid.record(id);
		if (r == null || r.wornThroughDay == day) {
			return DistrictLoop.DecayChange.NONE;
		}
		Set<Long> keys = GridDistricts.keyRelayChunks(id);
		LoopRules rules = GridDistricts.settings(id).rules();
		DistrictLoop.DecayChange[] change = {DistrictLoop.DecayChange.NONE};
		grid.update(level, id, rec -> {
			DistrictLoop.wear(rec, settings, keys, day);
			change[0] = DistrictLoop.updateDecay(rec, rules, settings, keys, day);
		});
		announce(level, id, change[0]);
		return change[0];
	}

	/** Re-checks a district after its relays changed (a repair, a new link, a command). */
	public static DistrictLoop.DecayChange refresh(ServerLevel level, String id) {
		long day = DirectorDay.today(level);
		Set<Long> keys = GridDistricts.keyRelayChunks(id);
		LoopRules rules = GridDistricts.settings(id).rules();
		DistrictLoop.DecayChange[] change = {DistrictLoop.DecayChange.NONE};
		GridState.get(level.getServer()).update(level, id,
				rec -> change[0] = DistrictLoop.updateDecay(rec, rules, DecaySettings.get(), keys, day));
		announce(level, id, change[0]);
		return change[0];
	}

	/**
	 * Charges each horde night since the last check to one reclaimed district, picked at random: the horde passed
	 * through there. The first check only starts the count.
	 */
	static void hordeDamage(ServerLevel level, GridState grid, DecaySettings settings, long day) {
		long through = day - 1;
		long last = grid.hordeCheckedDay();
		if (last == DistrictRecord.UNSET || through <= last) {
			if (last == DistrictRecord.UNSET) {
				grid.setHordeCheckedDay(through);
			}
			return;
		}
		HordeSchedule schedule = HordeSchedule.get(level.getServer());
		for (long k = Math.max(last + 1, through - MAX_CATCH_UP_DAYS + 1); k <= through; k++) {
			if (k >= 1 && schedule.isHordeDay((int) k, level.getSeed())) {
				hitByHorde(level, grid, settings);
			}
		}
		grid.setHordeCheckedDay(through);
	}

	/** One horde night's damage to a random reclaimed district. @return the district hit, or null */
	public static String hitByHorde(ServerLevel level, GridState grid, DecaySettings settings) {
		List<String> targets = new ArrayList<>();
		for (String id : grid.districts()) {
			if (grid.state(id) == DistrictState.RECLAIMED && !GridDistricts.isManual(id)) {
				targets.add(id);
			}
		}
		if (targets.isEmpty() || settings.hordeRelaysMax() <= 0 || settings.hordeDamage() <= 0) {
			return null;
		}
		String id = targets.get(level.getRandom().nextInt(targets.size()));
		damage(level, id, settings, new Random(level.getRandom().nextLong()));
		return id;
	}

	/** Horde damage to {@code id}'s relays, announced. Public for gametests. */
	public static int damage(ServerLevel level, String id, DecaySettings settings, Random random) {
		int min = Math.min(settings.hordeRelaysMin(), settings.hordeRelaysMax());
		int count = min + random.nextInt(settings.hordeRelaysMax() - min + 1);
		Set<Long> keys = GridDistricts.keyRelayChunks(id);
		int[] hit = new int[1];
		GridState.get(level.getServer()).update(level, id,
				rec -> hit[0] = DistrictLoop.damageRelays(rec, keys, count, settings.hordeDamage(), random));
		if (hit[0] > 0) {
			GridActions.broadcast(level, Component.translatable("message.ashfall.grid.horde_damage", GridDistricts.name(id), hit[0])
					.withStyle(ChatFormatting.YELLOW));
			AshfallArms.LOGGER.info("Grid: horde night damaged {} relay(s) in {}", hit[0], id);
		}
		return hit[0];
	}

	static void announce(ServerLevel level, String id, DistrictLoop.DecayChange change) {
		if (change == DistrictLoop.DecayChange.NONE) {
			return;
		}
		GridState grid = GridState.get(level.getServer());
		DistrictRecord r = grid.record(id);
		Set<Long> keys = GridDistricts.keyRelayChunks(id);
		int working = r == null ? 0 : DistrictLoop.workingRelays(r, keys);
		int required = GridDistricts.settings(id).rules().relaysRequired(keys);
		Component name = GridDistricts.name(id);
		Component line = switch (change) {
			case FLICKER_STARTED -> Component.translatable("message.ashfall.grid.flicker", name, working, required,
					DecaySettings.get().flickerDays()).withStyle(ChatFormatting.YELLOW);
			case FLICKER_ENDED -> Component.translatable("message.ashfall.grid.flicker_ended", name).withStyle(ChatFormatting.GREEN);
			case BLACKOUT -> Component.translatable("message.ashfall.grid.blackout", name, required).withStyle(ChatFormatting.RED);
			case RESTORED -> Component.translatable("message.ashfall.grid.restored", name, grid.gridPercent())
					.withStyle(ChatFormatting.GREEN);
			case NONE -> null;
		};
		GridActions.broadcast(level, line);
		AshfallArms.LOGGER.info("Grid: {} decay {} ({}/{} relays working)", id, change, working, required);
	}
}
