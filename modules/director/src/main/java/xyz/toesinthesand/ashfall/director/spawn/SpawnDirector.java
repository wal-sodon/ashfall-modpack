package xyz.toesinthesand.ashfall.director.spawn;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.AshfallDirector;
import xyz.toesinthesand.ashfall.director.story.StoryHooks;
import xyz.toesinthesand.ashfall.director.hydration.Hydration;
import xyz.toesinthesand.ashfall.director.claims.Claims;
import xyz.toesinthesand.ashfall.director.special.SpecialInfected;
import xyz.toesinthesand.ashfall.salvage.atlas.LoadedChunks;
import xyz.toesinthesand.ashfall.salvage.city.Zone;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Director's spawning half (spec section 1): zombies-only overworld, the per-player budget loop
 * for ambient zombies, building populations and the one-time game-rule setup.
 * <p>
 * Tick cost: one pass per player every {@link SpawnRules#CYCLE_TICKS} ticks, staggered by entity id so
 * players are spread over the cycle. A pass is two linear counts over the (capped) tracked set, at most
 * {@link SpawnRules#ATTEMPTS_PER_CYCLE} candidate spots, and a 7x7-chunk building lookup. The
 * line-of-sight raycasts, the most expensive check, run last and only for spots that passed the
 * weighted roll.
 */
public final class SpawnDirector {
	/** {@code /gamerule ashfall:director_spawning false} stops all Director spawning (the zombies-only filter stays). */
	public static final GameRule<Boolean> ENABLED = GameRuleBuilder.forBoolean(true)
			.category(GameRuleCategory.SPAWNING)
			.buildAndRegister(Identifier.fromNamespaceAndPath("ashfall", "director_spawning"));
	/** Ambient cap on day 1 and the cap reached on day {@link SpawnRules#AMBIENT_CAP_MAX_DAY}: the configurable budget. */
	public static final GameRule<Integer> AMBIENT_CAP_START = GameRuleBuilder.forInteger(SpawnRules.AMBIENT_CAP_START)
			.range(0, SpawnRules.PLAYER_HARD_CAP)
			.category(GameRuleCategory.SPAWNING)
			.buildAndRegister(Identifier.fromNamespaceAndPath("ashfall", "director_ambient_cap_start"));
	public static final GameRule<Integer> AMBIENT_CAP_MAX = GameRuleBuilder.forInteger(SpawnRules.AMBIENT_CAP_MAX)
			.range(0, SpawnRules.PLAYER_HARD_CAP)
			.category(GameRuleCategory.SPAWNING)
			.buildAndRegister(Identifier.fromNamespaceAndPath("ashfall", "director_ambient_cap_max"));

	/** Why a spot was refused, in check order (cheap first). */
	public enum Verdict { OK, INVALID, LIGHT, CLAIM, RESPAWN, VISIBLE }

	/**
	 * A multiplier on the darkness scale of one spot. Other modules use it to change how much the dark spawns
	 * without the Director knowing why: the grid lowers it in Reclaimed districts (endgame-design section 3,
	 * "darkness spawning drops by 80%"), which is gentler than a claim's full exclusion on purpose, because a
	 * lit district must never become a free safe zone.
	 */
	@FunctionalInterface
	public interface DarknessModifier {
		float scale(ServerLevel level, BlockPos pos);
	}

	private static final List<DarknessModifier> DARKNESS_MODIFIERS = new CopyOnWriteArrayList<>();
	private static Integer worldCapOverride;

	private SpawnDirector() {
	}

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ZombieVariants.ID, new ZombieVariants());
		ZombiesOnly.init();
		BuildingKeys.init();

		ServerEntityEvents.ENTITY_LOAD.register(SpawnDirector::onLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(SpawnDirector::onUnload);
		ServerLifecycleEvents.SERVER_STARTED.register(SpawnDirector::applyGameRulesOnce);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DirectorTracker.clear();
			BuildingPopulations.clearCache();
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> BuildingPopulations.clearCache());
		ServerTickEvents.END_SERVER_TICK.register(SpawnDirector::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> DirectorCommand.register(dispatcher));
	}

	private static void onLoad(Entity entity, ServerLevel level) {
		if (level.dimension() == Level.OVERWORLD && entity instanceof Mob mob && entity.hasAttached(DirectorSpawns.TAG)) {
			DirectorTracker.add(mob);
		}
	}

	/**
	 * Untracks; for building zombies anything but a chunk unload means the member is gone for good
	 * (killed, despawned, converted, changed dimension), which is what clears a building.
	 */
	private static void onUnload(Entity entity, ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		DirectorZombie tag = entity.getAttached(DirectorSpawns.TAG);
		if (tag == null) {
			return;
		}
		DirectorTracker.remove(entity);
		Entity.RemovalReason reason = entity.getRemovalReason();
		boolean unloadedOnly = reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER;
		if (tag.fromBuilding() && !unloadedOnly) {
			BuildingPopulations.memberGone(level.getServer(), tag.buildingId(), entity.getUUID());
		}
	}

	/**
	 * Patrols, phantoms and wandering traders off, once per world. The flag lives in saved data so a
	 * later admin change is respected. A fresh Lost Cities world gets them on its first start.
	 */
	public static void applyGameRulesOnce(MinecraftServer server) {
		DirectorState state = DirectorState.get(server);
		if (state.gameRulesApplied()) {
			return;
		}
		GameRules rules = server.overworld().getGameRules();
		rules.set(GameRules.SPAWN_PATROLS, false, server);
		rules.set(GameRules.SPAWN_PHANTOMS, false, server);
		rules.set(GameRules.SPAWN_WANDERING_TRADERS, false, server);
		state.markGameRulesApplied();
		AshfallDirector.LOGGER.info("Director: first start on this world, turned off patrols, phantoms and wandering traders");
	}

	public static int worldCap() {
		Integer test = worldCapOverride;
		return test != null ? test : SpawnRules.WORLD_HARD_CAP;
	}

	/** Test hook: a smaller world cap; null restores {@link SpawnRules#WORLD_HARD_CAP}. */
	public static void setWorldCapForTesting(Integer cap) {
		worldCapOverride = cap;
	}

	/** Spawning runs only with our rule and vanilla's mob/monster rules on and off Peaceful. */
	public static boolean active(ServerLevel level) {
		GameRules rules = level.getGameRules();
		return rules.get(ENABLED) && rules.get(GameRules.SPAWN_MOBS) && rules.get(GameRules.SPAWN_MONSTERS)
				&& level.getDifficulty() != Difficulty.PEACEFUL;
	}

	/**
	 * The ambient budget for one player today, scaled by the story ending's spawn multiplier
	 * ({@link StoryHooks#spawnMultiplier}: the Sever ending's "spawning falls to about 40%"). Scaling the budget rather
	 * than the roll keeps the spawn loop's cost and its caps unchanged.
	 */
	public static int ambientCap(ServerLevel level) {
		GameRules rules = level.getGameRules();
		int cap = SpawnRules.ambientCap(DirectorDay.today(level), rules.get(AMBIENT_CAP_START), rules.get(AMBIENT_CAP_MAX));
		return SpawnRules.scaleBySpawnMultiplier(cap, StoryHooks.spawnMultiplier(level.getServer()));
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long tick = server.getTickCount();
		if (tick % SpawnRules.CYCLE_TICKS == 7) {
			BuildingPopulations.tickTimers(level, SpawnRules.CYCLE_TICKS);
		}
		if (!active(level)) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if ((tick + player.getId()) % SpawnRules.CYCLE_TICKS == 0 && !player.isSpectator() && player.isAlive()) {
				BuildingPopulations.checkNear(level, player);
				ambientCycle(level, player);
			}
		}
	}

	/** Ticks until the next player's budget cycle, for the status command. */
	public static int ticksToNextCycle(ServerLevel level) {
		long tick = level.getServer().getTickCount();
		int best = -1;
		for (ServerPlayer player : level.players()) {
			int wait = (int) Math.floorMod(-(tick + player.getId()), (long) SpawnRules.CYCLE_TICKS);
			best = best < 0 ? wait : Math.min(best, wait);
		}
		return best;
	}

	/** One player's budget: room under all caps, then a few weighted candidate spots. */
	static int ambientCycle(ServerLevel level, ServerPlayer player) {
		int room = Math.min(SpawnRules.MAX_SPAWNS_PER_CYCLE, allowance(level, player.getX(), player.getY(), player.getZ()));
		if (room <= 0) {
			return 0;
		}
		RandomSource random = level.getRandom();
		int day = DirectorDay.today(level);
		int spawned = 0;
		for (int i = 0; i < SpawnRules.ATTEMPTS_PER_CYCLE && spawned < room; i++) {
			BlockPos pos = candidate(level, player, random);
			if (pos != null && tryAmbientSpawn(level, pos, day, random) != null) {
				spawned++;
			}
		}
		return spawned;
	}

	/** Zombies one player's cycle may still add, per {@link SpawnRules#allowance}. */
	public static int allowance(ServerLevel level, double x, double y, double z) {
		int ambientNear = DirectorTracker.countNear(x, y, z, SpawnRules.AMBIENT_RADIUS)[0];
		int directorNear = DirectorTracker.countNear(x, y, z, SpawnRules.PLAYER_CAP_RADIUS)[1];
		return SpawnRules.allowance(ambientNear, ambientCap(level), directorNear, SpawnRules.PLAYER_HARD_CAP,
				DirectorTracker.total(), worldCap());
	}

	/**
	 * A random spot in the 24-64 block ring: half the time the surface (or water surface), half the
	 * time the first floor found scanning down from just above the player's height, which is what
	 * finds building interiors and caves instead of only rooftops.
	 */
	static BlockPos candidate(ServerLevel level, ServerPlayer player, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2;
		double dist = SpawnRules.MIN_SPAWN_DISTANCE + random.nextDouble() * (SpawnRules.MAX_SPAWN_DISTANCE - SpawnRules.MIN_SPAWN_DISTANCE);
		int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
		int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
		if (!LoadedChunks.ready(level, x, z)) {
			return null;
		}
		if (random.nextBoolean()) {
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos surface = new BlockPos(x, top, z);
			if (validWater(level, surface.below())) {
				return surface.below();
			}
			return validLand(level, surface) ? surface : null;
		}
		int from = player.getBlockY() + SpawnRules.COLUMN_SCAN_HALF_HEIGHT;
		int to = Math.max(level.getMinY() + 1, player.getBlockY() - SpawnRules.COLUMN_SCAN_HALF_HEIGHT);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, from, z);
		for (int y = from; y >= to; y--) {
			pos.setY(y);
			if (validLand(level, pos)) {
				return pos.immutable();
			}
		}
		return null;
	}

	/** Solid spawnable floor, zombie-sized free space, no fluid at feet or head. */
	public static boolean validLand(ServerLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		if (!level.getBlockState(below).isValidSpawn(level, below, EntityTypes.ZOMBIE)) {
			return false;
		}
		if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
			return false;
		}
		return level.noCollision(EntityTypes.ZOMBIE.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
	}

	/** Two blocks of water: drowned territory. */
	public static boolean validWater(ServerLevel level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos.above()).is(FluidTags.WATER);
	}

	/** Spot kind for variant eligibility: water, hot land (husk biomes) or plain land. */
	public static ZombieVariant.Environment environmentAt(ServerLevel level, BlockPos pos) {
		if (level.getFluidState(pos).is(FluidTags.WATER)) {
			return ZombieVariant.Environment.WATER;
		}
		return level.getBiome(pos).value().getBaseTemperature() > Hydration.HOT_BIOME_TEMPERATURE
				? ZombieVariant.Environment.HOT : ZombieVariant.Environment.LAND;
	}

	/** Light-table weight with the outdoor-by-day damping. */
	public static float spotWeight(ServerLevel level, BlockPos pos) {
		int light = level.getMaxLocalRawBrightness(pos);
		return SpawnRules.spotWeight(light, level.isBrightOutside(), level.canSeeSky(pos));
	}

	/**
	 * The light weight with the spot's zone applied: darkness scale (the wasteland's x1.3 in the dark) and the
	 * gentle early days (Montford's x0.6 for the first 3 days).
	 */
	public static float zoneSpotWeight(ServerLevel level, BlockPos pos, int day) {
		Zone zone = DirectorContexts.zoneAt(pos);
		int light = level.getMaxLocalRawBrightness(pos);
		float base = SpawnRules.spotWeight(light, level.isBrightOutside(), level.canSeeSky(pos));
		return SpawnRules.zoneSpotWeight(base, light, zone.director().darknessScale() * darknessModifier(level, pos),
				zone.director().gentle(day));
	}

	/** Registers a {@link DarknessModifier}; every registered one multiplies into the zone's darkness scale. */
	public static void addDarknessModifier(DarknessModifier modifier) {
		DARKNESS_MODIFIERS.add(modifier);
	}

	/** The product of every registered darkness modifier at {@code pos} (1 with none). */
	public static float darknessModifier(ServerLevel level, BlockPos pos) {
		float scale = 1f;
		for (DarknessModifier modifier : DARKNESS_MODIFIERS) {
			scale *= Math.max(0f, modifier.scale(level, pos));
		}
		return scale;
	}

	/** Every rule except the random roll, in cost order. */
	public static Verdict check(ServerLevel level, BlockPos pos) {
		if (!validLand(level, pos) && !validWater(level, pos)) {
			return Verdict.INVALID;
		}
		if (spotWeight(level, pos) <= 0) {
			return Verdict.LIGHT;
		}
		if (Claims.isClaimed(level, pos)) {
			return Verdict.CLAIM;
		}
		if (nearRespawn(level, pos)) {
			return Verdict.RESPAWN;
		}
		if (visibleToAnyPlayer(level, pos)) {
			return Verdict.VISIBLE;
		}
		return Verdict.OK;
	}

	/**
	 * One weighted ambient spawn attempt at {@code pos}: roll against the light weight, then the
	 * exclusions, then a variant for the day and spot. Returns the zombie or null.
	 */
	public static Zombie tryAmbientSpawn(ServerLevel level, BlockPos pos, int day, RandomSource random) {
		boolean land = validLand(level, pos);
		if (!land && !validWater(level, pos)) {
			return null;
		}
		float weight = zoneSpotWeight(level, pos, day);
		if (weight <= 0 || random.nextFloat() >= weight) {
			return null;
		}
		if (Claims.isClaimed(level, pos) || nearRespawn(level, pos) || visibleToAnyPlayer(level, pos)) {
			return null;
		}
		SpawnContext ctx = DirectorContexts.at(level, pos);
		// Special infected are a per-mille layer over the variant table (special_infected/*.json); land only.
		var variant = land ? SpecialInfected.roll(level, pos, day, ctx.zone(), false, random) : null;
		if (variant == null) {
			variant = ZombieVariants.pick(ZombieVariants.all(), random.nextDouble(), day, environmentAt(level, pos), null, ctx);
		}
		if (variant == null && !land) {
			return null; // no water variant loaded: never put a land zombie in a lake
		}
		return DirectorSpawns.spawn(level, pos, variant, DirectorZombie.Source.AMBIENT, DirectorZombie.NO_BUILDING);
	}

	/** Within {@link SpawnRules#RESPAWN_EXCLUSION} of the world spawn or any online player's respawn point here. */
	public static boolean nearRespawn(ServerLevel level, BlockPos pos) {
		double r2 = (double) SpawnRules.RESPAWN_EXCLUSION * SpawnRules.RESPAWN_EXCLUSION;
		LevelData.RespawnData world = level.getRespawnData();
		if (world != null && world.dimension() == level.dimension() && world.pos().distSqr(pos) <= r2) {
			return true;
		}
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			ServerPlayer.RespawnConfig config = player.getRespawnConfig();
			if (config != null && config.respawnData().dimension() == level.dimension()
					&& config.respawnData().pos().distSqr(pos) <= r2) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True if any non-spectator player within {@link SpawnRules#SIGHT_CHECK_RADIUS} has a clear line
	 * from their eyes to the spot's feet or head. Visual shapes are used, so glass does not hide a spot.
	 */
	public static boolean visibleToAnyPlayer(ServerLevel level, BlockPos pos) {
		Vec3 feet = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		Vec3 head = new Vec3(pos.getX() + 0.5, pos.getY() + 1.7, pos.getZ() + 0.5);
		double r2 = (double) SpawnRules.SIGHT_CHECK_RADIUS * SpawnRules.SIGHT_CHECK_RADIUS;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.distanceToSqr(feet) > r2) {
				continue;
			}
			Vec3 eye = player.getEyePosition();
			if (!segmentLoaded(level, eye, feet)) {
				continue; // a ray through an unloaded chunk would load it; that far, treat it as blocked
			}
			if (clear(level, eye, head, player) || clear(level, eye, feet, player)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether every chunk a straight line crosses is loaded. {@code Level#clip} reads blocks through
	 * {@code getBlockState}, which on a server synchronously loads (or generates) a missing chunk; a
	 * second player up to {@link SpawnRules#SIGHT_CHECK_RADIUS} away can look across chunks neither
	 * player keeps loaded, so the spawn loop must check first. Sampled every 4 blocks (no chunk is
	 * narrower than 16), plus the end point.
	 */
	public static boolean segmentLoaded(ServerLevel level, Vec3 from, Vec3 to) {
		double dx = to.x - from.x, dz = to.z - from.z;
		int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / 4.0));
		long last = Long.MIN_VALUE;
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			int cx = Mth.floor(from.x + dx * t) >> 4, cz = Mth.floor(from.z + dz * t) >> 4;
			long key = ChunkPos.pack(cx, cz);
			if (key != last) {
				if (!LoadedChunks.chunkReady(level, cx, cz)) {
					return false;
				}
				last = key;
			}
		}
		return true;
	}

	private static boolean clear(ServerLevel level, Vec3 from, Vec3 to, Entity viewer) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer)).getType() == HitResult.Type.MISS;
	}
}
