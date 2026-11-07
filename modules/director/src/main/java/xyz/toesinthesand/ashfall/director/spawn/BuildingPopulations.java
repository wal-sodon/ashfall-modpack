package xyz.toesinthesand.ashfall.director.spawn;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ChunkPos;
import xyz.toesinthesand.ashfall.director.claims.Claims;
import xyz.toesinthesand.ashfall.director.graffiti.Graffiti;
import xyz.toesinthesand.ashfall.director.story.StoryHooks;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.atlas.LoadedChunks;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.BuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.Footprint;
import xyz.toesinthesand.ashfall.salvage.building.LostCitiesBuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.Unit;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

/**
 * One-time building populations (spec section 1): the first time a player comes within
 * {@link SpawnRules#BUILDING_TRIGGER_RADIUS} of a building whose profile has a population, zombies
 * appear inside its storeys. When they are all gone the building stays cleared for
 * {@link SpawnRules#CLEARED_TICKS} of nobody being near, then gets a half-size population.
 * <p>
 * Buildings come from salvage's {@link BuildingSource}. In a Lost Cities world that is one entry per building
 * <em>instance</em> (a multibuilding's chunks share one, keyed by its origin chunk), so a 3x2 hospital is one
 * population, one record and one cleared timer rather than six. A Lost Cities building is sized by its storeys
 * times its whole footprint ({@code 16 x 16 x w x h}, times {@link SpawnRules#LC_FLOOR_FILL}); storeys above
 * the building's ruin height are left out because Lost Cities removed or broke them. The zone then scales the
 * count ({@link SpawnRules#scaledPopulation}): Mission is the most infested, Montford is gentle for its first
 * days, and the wasteland and beyond have no populations at all (darkness spawning only).
 * <p>
 * Population plans (which units, which profile, how many before the zone) are cached per building id, so the
 * per-player proximity check is a few map lookups. The zone scale is applied at spawn time because the
 * gentle period depends on the day.
 * <p>
 * A building's first population also gets a Strongbox roll ({@link BuildingKeys}) and fires {@link #FIRST_POPULATED},
 * where arms puts in its holdout survivors (director cannot see arms classes).
 */
public final class BuildingPopulations {
	/** Tries per zombie to find a free floor cell inside a unit. */
	private static final int CELL_TRIES = 16;

	@FunctionalInterface
	public interface FirstPopulated {
		void onFirstPopulated(ServerLevel level, Building building, List<UUID> members);
	}

	/** Fired once per building, right after its first population spawned (and its Strongbox roll), with the new members. */
	public static final Event<FirstPopulated> FIRST_POPULATED = EventFactory.createArrayBacked(FirstPopulated.class,
			listeners -> (level, building, members) -> {
				for (FirstPopulated listener : listeners) {
					listener.onFirstPopulated(level, building, members);
				}
			});

	/** A unit that contributes zombies: its profile, population table and share (per_100m2 x area). */
	record UnitPlan(Unit unit, Identifier profile, BuildingProfile.Population population, double weight) {
	}

	record Plan(int base, List<UnitPlan> units, double totalWeight) {
		static final Plan EMPTY = new Plan(0, List.of(), 0);
	}

	private static final Map<Long, Plan> PLANS = new HashMap<>();
	private static LongFunction<Building> lookupOverride;
	private static BiFunction<Building, Unit, BuildingProfile.Population> populationOverride;

	private BuildingPopulations() {
	}

	public static Building building(long id) {
		LongFunction<Building> test = lookupOverride;
		return test != null ? test.apply(id) : AshfallSalvage.buildings().get(id);
	}

	static void clearCache() {
		PLANS.clear();
	}

	/** Called every budget cycle for one player: populate every eligible building within range. */
	static void checkNear(ServerLevel level, ServerPlayer player) {
		BuildingSource index = AshfallSalvage.buildings();
		if (index.isEmpty()) {
			return;
		}
		int r = SpawnRules.BUILDING_TRIGGER_RADIUS;
		int px = player.getBlockX(), pz = player.getBlockZ();
		Set<Long> seen = new HashSet<>();
		for (int cx = (px - r) >> 4; cx <= (px + r) >> 4; cx++) {
			for (int cz = (pz - r) >> 4; cz <= (pz + r) >> 4; cz++) {
				// A chunk still loading can't hold anything to populate: populate() needs the building's centre loaded
				// and reconcile() all of its chunks, and a building reaching into loaded chunks is found from those.
				// Skipping it spares a Lost Cities plan build per new chunk for a player running into fresh streets.
				if (!LoadedChunks.chunkReady(level, cx, cz)) {
					continue;
				}
				for (Building building : index.inChunk(cx, cz)) {
					if (seen.add(building.id()) && withinRange(building, px, pz, r)) {
						populate(level, building);
					}
				}
			}
		}
	}

	/** Horizontal distance from a column to the building's bounding box, compared to {@code radius}. */
	public static boolean withinRange(Building building, double x, double z, double radius) {
		Footprint f = building.footprint();
		double dx = Math.max(0, Math.max(f.minX() - x, x - (f.maxX() + 1)));
		double dz = Math.max(0, Math.max(f.minZ() - z, z - (f.maxZ() + 1)));
		return dx * dx + dz * dz <= radius * radius;
	}

	/**
	 * Gives the building its population if it has never had one or its cleared period is over.
	 * Building zombies skip the ambient cap but respect the hard caps, measured at the building.
	 *
	 * @return how many zombies spawned (0 if nothing was due)
	 */
	public static int populate(ServerLevel level, Building building) {
		DirectorState state = DirectorState.get(level.getServer());
		BuildingRecord record = state.building(building.id());
		if (record != null && record.status() == BuildingRecord.Status.POPULATED) {
			reconcile(level, building, record, state);
		}
		if (record != null && record.status() != BuildingRecord.Status.READY) {
			return 0;
		}
		Plan plan = plan(building);
		int base = SpawnRules.scaledPopulation(plan.base(), zoneScale(level, building));
		if (base == 0) {
			return 0;
		}
		Footprint f = building.footprint();
		double cx = (f.minX() + f.maxX()) / 2.0 + 0.5, cz = (f.minZ() + f.maxZ()) / 2.0 + 0.5;
		BlockPos centre = BlockPos.containing(cx, building.baseY(), cz);
		if (!LoadedChunks.ready(level, centre)) {
			return 0;
		}
		int[] near = DirectorTracker.countNear(cx, building.baseY(), cz, SpawnRules.PLAYER_CAP_RADIUS);
		int room = SpawnRules.hardCapRoom(near[1], SpawnRules.PLAYER_HARD_CAP, DirectorTracker.total(), SpawnDirector.worldCap());
		if (room <= 0) {
			return 0;
		}
		boolean firstPopulation = record == null;
		record = state.getOrCreate(building.id());
		// The story ending scales populations like the ambient budget (Sever: fewer of the dead everywhere).
		int count = Math.min(SpawnRules.scaleBySpawnMultiplier(SpawnRules.populationSize(base, record.generation()),
				StoryHooks.spawnMultiplier(level.getServer())), room);
		List<UUID> spawned = spawnPopulation(level, building, plan, count);
		record.populated(base, spawned);
		state.setDirty();
		if (firstPopulation) {
			// Once per building: the record outlives every later clear and repopulation.
			BuildingKeys.firstPopulation(level, building, spawned);
			FIRST_POPULATED.invoker().onFirstPopulated(level, building, spawned);
		}
		return spawned.size();
	}

	/** The building's zone population scale today: {@code population_scale} times the gentle-days scale. */
	static double zoneScale(ServerLevel level, Building building) {
		Zone zone = DirectorContexts.zoneOf(building);
		return zone.populationScale() * zone.director().gentle(DirectorDay.today(level));
	}

	/**
	 * Unsticks a POPULATED building none of whose members can still be inside it. Members inside are
	 * persistent and live in the building's own chunks, so once every one of those chunks has its
	 * entities loaded and no member is in the loaded Director set, every member is either gone without
	 * an unload event we saw (removed while the mod was absent, a crash between saves) or wandered off
	 * into chunks nobody keeps loaded. Without this the building would stay POPULATED, and therefore
	 * never clear or respawn, for the rest of the world's life. Called only while a player is near.
	 */
	static boolean reconcile(ServerLevel level, Building building, BuildingRecord record, DirectorState state) {
		for (UUID uuid : record.members()) {
			if (DirectorTracker.isLoaded(uuid)) {
				return false;
			}
		}
		Footprint f = building.footprint();
		for (int cx = f.minX() >> 4; cx <= f.maxX() >> 4; cx++) {
			for (int cz = f.minZ() >> 4; cz <= f.maxZ() >> 4; cz++) {
				if (!level.areEntitiesLoaded(ChunkPos.pack(cx, cz))) {
					return false;
				}
			}
		}
		record.forceCleared();
		state.setDirty();
		return true;
	}

	private static List<UUID> spawnPopulation(ServerLevel level, Building building, Plan plan, int count) {
		RandomSource random = level.getRandom();
		int day = DirectorDay.today(level);
		SpawnContext ctx = DirectorContexts.inBuilding(building);
		List<UUID> spawned = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			UnitPlan unit = pickUnit(plan, random);
			BlockPos pos = findCell(level, unit.unit(), random);
			if (pos == null) {
				continue;
			}
			Zombie zombie = spawnOne(level, pos, unit, day, building.id(), ctx, random);
			if (zombie != null) {
				spawned.add(zombie.getUUID());
			}
		}
		return spawned;
	}

	private static UnitPlan pickUnit(Plan plan, RandomSource random) {
		double target = random.nextDouble() * plan.totalWeight();
		for (UnitPlan unit : plan.units()) {
			target -= unit.weight();
			if (target < 0) {
				return unit;
			}
		}
		return plan.units().getLast();
	}

	/** A random free floor cell of the unit: inside its footprint, loaded, standable, not claimed. */
	private static BlockPos findCell(ServerLevel level, Unit unit, RandomSource random) {
		Footprint f = unit.footprint();
		for (int t = 0; t < CELL_TRIES; t++) {
			int x = f.minX() + random.nextInt(f.maxX() - f.minX() + 1);
			int z = f.minZ() + random.nextInt(f.maxZ() - f.minZ() + 1);
			if (!f.contains(x, z)) {
				continue;
			}
			BlockPos pos = new BlockPos(x, unit.floorY() + 1, z);
			if (LoadedChunks.ready(level, pos) && SpawnDirector.validLand(level, pos) && !Claims.isClaimed(level, pos)) {
				return pos;
			}
		}
		return null;
	}

	/**
	 * The profile's entity weights choose the entity. Plain {@code minecraft:zombie} (and entity ids
	 * of absent mods) roll a datapack variant with the building bias; another zombie-family entity
	 * spawns as itself, with a variant whose {@code entity} matches it if there is one.
	 */
	private static Zombie spawnOne(ServerLevel level, BlockPos pos, UnitPlan unit, int day, long buildingId, SpawnContext ctx,
								   RandomSource random) {
		Identifier entityId = pickEntity(unit.population(), random);
		Optional<EntityType<?>> type = entityId == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(entityId);
		if (type.isPresent() && !entityId.equals(ZombieVariant.ZOMBIE)) {
			Map.Entry<Identifier, ZombieVariant> match = null;
			for (Map.Entry<Identifier, ZombieVariant> e : ZombieVariants.all().entrySet()) {
				if (e.getValue().entity().equals(entityId) && e.getValue().minDay() <= day && e.getValue().conditions().matches(ctx)) {
					match = e;
					break;
				}
			}
			return match != null
					? DirectorSpawns.spawn(level, pos, type.get(), match.getKey().getPath(), match.getValue(), DirectorZombie.Source.BUILDING, buildingId)
					: DirectorSpawns.spawn(level, pos, type.get(), entityId.getPath(), null, DirectorZombie.Source.BUILDING, buildingId);
		}
		Map.Entry<Identifier, ZombieVariant> variant = ZombieVariants.pick(ZombieVariants.all(), random.nextDouble(), day,
				SpawnDirector.environmentAt(level, pos), unit.profile(), ctx);
		return DirectorSpawns.spawn(level, pos, variant, DirectorZombie.Source.BUILDING, buildingId);
	}

	private static Identifier pickEntity(BuildingProfile.Population population, RandomSource random) {
		int total = 0;
		for (int w : population.entities().values()) {
			total += w;
		}
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		// Sorted for a stable order (the codec's map has none).
		List<Identifier> ids = new ArrayList<>(population.entities().keySet());
		ids.sort(null);
		for (Identifier id : ids) {
			roll -= population.entities().get(id);
			if (roll < 0) {
				return id;
			}
		}
		return ids.getLast();
	}

	/**
	 * Units, their profiles and the base count before the zone scale, computed once per building per data load.
	 * A Lost Cities storey weighs {@code per_100m2 x 16 x 16 x w x h x LC_FLOOR_FILL}, so the base is the plan's
	 * {@code per_100m2 x (floors + cellars) x footprint x 0.6 / 100}; other sources use each unit's floor area.
	 */
	static Plan plan(Building building) {
		Plan cached = PLANS.get(building.id());
		if (cached != null) {
			return cached;
		}
		boolean lostCities = LostCitiesBuildingSource.isLostCities(building);
		int ruinY = LostCitiesBuildingSource.ruinY(building);
		double lcStoreyArea = building.footprint().area() * SpawnRules.LC_FLOOR_FILL;
		List<UnitPlan> units = new ArrayList<>();
		double total = 0;
		for (Unit unit : building.units()) {
			if (unit.floorY() >= ruinY) {
				continue; // a ruined storey: Lost Cities broke or removed it, nobody is up there
			}
			Identifier profileId = BuildingProfiles.classify(building, unit);
			BuildingProfile.Population population;
			BiFunction<Building, Unit, BuildingProfile.Population> test = populationOverride;
			if (test != null) {
				population = test.apply(building, unit);
			} else {
				BuildingProfile profile = BuildingProfiles.get(profileId);
				population = profile == null ? BuildingProfile.Population.NONE : profile.population();
			}
			double weight = population.per100m2() * (lostCities ? lcStoreyArea : unit.footprint().area());
			if (weight > 0 && !population.entities().isEmpty()) {
				units.add(new UnitPlan(unit, profileId, population, weight));
				total += weight;
			}
		}
		Plan plan = units.isEmpty() ? Plan.EMPTY : new Plan(SpawnRules.basePopulation(total), List.copyOf(units), total);
		PLANS.put(building.id(), plan);
		return plan;
	}

	/** A member is gone for good (killed, despawned, converted, left the dimension). */
	static void memberGone(MinecraftServer server, long buildingId, UUID uuid) {
		DirectorState state = DirectorState.get(server);
		BuildingRecord record = state.building(buildingId);
		if (record != null && record.members().contains(uuid)) {
			if (record.memberGone(uuid)) {
				Graffiti.buildingChanged(server, buildingId);
			}
			state.setDirty();
		}
	}

	/** Advances every cleared building's timer; paused while any player is in range. */
	static void tickTimers(ServerLevel level, long dt) {
		DirectorState state = DirectorState.get(level.getServer());
		boolean dirty = false;
		for (BuildingRecord record : state.buildings()) {
			if (record.status() != BuildingRecord.Status.CLEARED) {
				continue;
			}
			Building building = building(record.id());
			boolean near = building != null && anyPlayerNear(level, building);
			if (record.tickCleared(near, dt, SpawnRules.CLEARED_TICKS)) {
				Graffiti.buildingChanged(level.getServer(), record.id());
			}
			dirty |= !near;
		}
		if (dirty) {
			state.setDirty();
		}
	}

	public static boolean anyPlayerNear(ServerLevel level, Building building) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && withinRange(building, player.getX(), player.getZ(), SpawnRules.BUILDING_TRIGGER_RADIUS)) {
				return true;
			}
		}
		return false;
	}

	/** Whether this building zombie is still inside its building (it must not despawn there). */
	public static boolean insideOwnBuilding(Mob mob, DirectorZombie tag) {
		Building building = building(tag.buildingId());
		if (building == null) {
			return false;
		}
		int y = mob.getBlockY();
		return y >= building.baseY() && y <= building.topY() + 1 && building.footprint().contains(mob.getBlockX(), mob.getBlockZ());
	}

	/** Admin clear: removes loaded members and starts the cleared timer now. */
	public static int clear(ServerLevel level, long buildingId) {
		DirectorState state = DirectorState.get(level.getServer());
		state.getOrCreate(buildingId).forceCleared();
		state.setDirty();
		int removed = discardMembers(buildingId);
		Graffiti.buildingChanged(level.getServer(), buildingId);
		return removed;
	}

	/** Admin repopulate: removes loaded members and spawns a fresh full population now if loaded. */
	public static int repopulate(ServerLevel level, long buildingId) {
		DirectorState state = DirectorState.get(level.getServer());
		state.getOrCreate(buildingId).forceReady();
		state.setDirty();
		discardMembers(buildingId);
		Building building = building(buildingId);
		int spawned = building == null ? 0 : populate(level, building);
		Graffiti.buildingChanged(level.getServer(), buildingId);
		return spawned;
	}

	private static int discardMembers(long buildingId) {
		List<Mob> members = new ArrayList<>();
		for (Mob mob : DirectorTracker.loaded()) {
			DirectorZombie tag = DirectorSpawns.tagOf(mob);
			if (tag != null && tag.buildingId() == buildingId) {
				members.add(mob);
			}
		}
		members.forEach(Mob::discard);
		return members.size();
	}

	/** Test hooks: replace building lookup and per-unit populations; null restores the real ones. */
	public static void setOverridesForTesting(LongFunction<Building> lookup, BiFunction<Building, Unit, BuildingProfile.Population> population) {
		lookupOverride = lookup;
		populationOverride = population;
		PLANS.clear();
	}
}
