package xyz.toesinthesand.ashfall.arms.survivor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.claims.Claims;
import xyz.toesinthesand.ashfall.director.spawn.BuildingPopulations;
import xyz.toesinthesand.ashfall.director.spawn.DirectorDay;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.BuildingFinder;
import xyz.toesinthesand.ashfall.salvage.building.LostCitiesBuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.Unit;
import xyz.toesinthesand.ashfall.salvage.city.Landmarks;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Building holdouts (survivors plan, phase 4), the second way to find survivors: from
 * {@link SurvivorBalance#rescueFirstDay} (day 7), a building whose first Director population just spawned
 * ({@link BuildingPopulations#FIRST_POPULATED}) also holds a holdout with chance {@link SurvivorBalance.Holdout#chance}
 * (6%), when it is a home, hotel, office, school or shop ({@link #PROFILES}), not a landmark and not claimed land.
 * <p>
 * The holdout stands on an upper storey ({@link BuildingFinder#floorSpot}), unled and in STAY mode, hiding
 * ({@link Survivor#holdout}): zombies cannot find it until it calls out, once, to the first player within
 * {@link SurvivorBalance.Holdout#callRange} (12 blocks), so the building's population is a reason to clear it first.
 * It joins like a rescued survivor: the first player to order it becomes its leader, unless their group is full
 * ({@link #joinRefusal}). A holdout is on the roster once spawned, but counts toward the world cap only once someone
 * takes it in ({@link SurvivorRoster#counted}), so holdouts nobody went upstairs for never block rescues. None spawns
 * while the world is at its cap, nor while as many holdouts as the cap are already hiding.
 */
public final class SurvivorHoldouts {
	/** Building profiles a holdout can hide in. */
	public static final Set<String> PROFILES = Set.of("home", "hotel", "office", "school", "shop");

	private SurvivorHoldouts() {
	}

	static void init() {
		BuildingPopulations.FIRST_POPULATED.register((level, building, members) ->
				tryHoldout(level, building, DirectorDay.today(level), level.getRandom().nextFloat()));
	}

	/**
	 * The holdout a building's first population leaves, or null: too early ({@code day} before
	 * {@link SurvivorBalance#rescueFirstDay}), the roll at or above the chance, the world at its cap, a building that
	 * doesn't suit, or no free floor.
	 *
	 * @param roll in [0, 1), passed in so gametests can force the chance
	 */
	public static @Nullable Survivor tryHoldout(ServerLevel level, Building building, int day, float roll) {
		SurvivorBalance balance = SurvivorBalance.get();
		if (day < balance.rescueFirstDay() || roll >= balance.holdout().chance()) {
			return null;
		}
		SurvivorRoster roster = SurvivorRoster.get(level.getServer());
		if (!roster.worldHasRoom() || roster.hiding() >= balance.caps().world() || !suits(building)) {
			return null;
		}
		Optional<BlockPos> spot = hidingSpot(level, building);
		if (spot.isEmpty() || Claims.isClaimed(level, spot.get())) {
			return null;
		}
		Survivor survivor = Survivors.spawn(level, spot.get(), SurvivorRole.SETTLER, null);
		if (survivor != null) {
			survivor.hideAsHoldout();
		}
		return survivor;
	}

	/** One of {@link #PROFILES}, and not one of the landmarks. */
	public static boolean suits(Building building) {
		String id = building.tags().get(LostCitiesBuildingSource.TAG_BUILDING);
		String multi = building.tags().get(LostCitiesBuildingSource.TAG_MULTI);
		if (Landmarks.isLandmarkBuilding(id) || Landmarks.isLandmarkBuilding(multi)) {
			return false;
		}
		Identifier profile = BuildingProfiles.classifyByTags(building);
		if (profile == null && !building.units().isEmpty()) {
			profile = BuildingProfiles.classify(building, building.units().getFirst());
		}
		return profile != null && PROFILES.contains(profile.getPath());
	}

	/**
	 * A free floor spot on a random upper storey (above the ground floor, below the ruin line), trying the others when
	 * that one has none; the ground floor only in a one-storey building.
	 */
	static Optional<BlockPos> hidingSpot(ServerLevel level, Building building) {
		int ruinY = LostCitiesBuildingSource.ruinY(building);
		List<Integer> upper = new ArrayList<>();
		for (Unit unit : building.units()) {
			if (unit.floor() >= 1 && unit.floorY() < ruinY && !upper.contains(unit.floor())) {
				upper.add(unit.floor());
			}
		}
		if (upper.isEmpty()) {
			return BuildingFinder.floorSpot(level, building, 0);
		}
		int start = level.getRandom().nextInt(upper.size());
		for (int i = 0; i < upper.size(); i++) {
			Optional<BlockPos> spot = BuildingFinder.floorSpot(level, building, upper.get((start + i) % upper.size()));
			if (spot.isPresent()) {
				return spot;
			}
		}
		return Optional.empty();
	}

	/** A hiding holdout's periodic look: calls out once to the nearest player in range ({@link Survivor#calledOut}). */
	static void callOut(ServerLevel level, Survivor survivor) {
		double range = SurvivorBalance.get().holdout().callRange();
		ServerPlayer nearest = null;
		double best = range * range;
		for (ServerPlayer player : level.players()) {
			double d = player.distanceToSqr(survivor);
			if (player.isAlive() && !player.isSpectator() && d <= best) {
				nearest = player;
				best = d;
			}
		}
		if (nearest != null) {
			survivor.setCalledOut();
			nearest.sendSystemMessage(SurvivorTalk.line(survivor, "holdout.call").withStyle(ChatFormatting.YELLOW));
		}
	}

	/**
	 * Why a holdout won't take {@code player} as its leader, or empty when it will: their group (with their OPAC party) is
	 * at {@link SurvivorBalance.Caps#group}, or the world is at its cap (a hiding holdout is not counted until taken in).
	 */
	static Optional<Component> joinRefusal(ServerPlayer player, Survivor survivor) {
		if (!survivor.holdout()) {
			return Optional.empty();
		}
		int group = SurvivorBalance.get().caps().group();
		SurvivorRoster roster = SurvivorRoster.get(player.level().getServer());
		if (!roster.groupHasRoom(player.level().getServer(), player.getUUID())) {
			return Optional.of(SurvivorTalk.line(survivor, "holdout.full", group));
		}
		if (!roster.worldHasRoom()) {
			return Optional.of(SurvivorTalk.line(survivor, "holdout.world_full", SurvivorBalance.get().caps().world()));
		}
		return Optional.empty();
	}
}
