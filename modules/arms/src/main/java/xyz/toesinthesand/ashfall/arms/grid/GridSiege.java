package xyz.toesinthesand.ashfall.arms.grid;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.director.pathing.SiegeRoutes;
import xyz.toesinthesand.ashfall.director.spawn.DirectorContexts;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.spawn.SpawnDirector;
import xyz.toesinthesand.ashfall.director.spawn.DirectorDay;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariant;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariants;
import xyz.toesinthesand.ashfall.director.special.SpecialInfected;

import java.util.List;
import java.util.Map;

/**
 * The District Siege (endgame-design section 3): waves of Director zombies around the substation through the
 * siege night, drawn to the core by its hum, and the core's health.
 * <p>
 * Waves are added with {@code addFreshEntity} through {@link DirectorSpawns}, never with a vanilla spawner: OPAC
 * blocks spawner spawns in claims, and the Director owns all spawning anyway. Siege zombies are persistent and
 * carry {@link #TAG} plus {@code ashfall_siege=<district>}, so the siege counts its survivors by an entity query
 * after a restart instead of remembering entity ids.
 * <p>
 * A siege zombie within the core's attack radius is attacking the core and takes health off it every second.
 * At 0 the siege is lost (back to REPAIRED); clearing every wave, or holding the core until dawn, wins it.
 * <p>
 * Siege zombies are led in through the substation's entrance ({@link SiegeRoutes}): to the nearest door or
 * ground-level opening, then along the inside to the core, so they no longer stop on the roof above the core or in
 * the cellar below it. A Breach (endgame-design section 5) is the same fight against a reclaimed core
 * ({@link DistrictRecord#breach}); losing it breaks the district.
 */
public final class GridSiege {
	public static final String TAG = "ashfall_siege";
	public static final String DISTRICT_TAG_PREFIX = "ashfall_siege=";
	/** The cache left at the core each time a Breach is held (ashfall_cache, so every player gets their own roll). */
	static final Identifier BREACH_REWARD =
			Identifier.fromNamespaceAndPath("ashfall_cache", "breach_reward");
	/** How far from the core siege zombies are counted. */
	private static final int COUNT_RADIUS = 128;

	private GridSiege() {
	}

	/**
	 * The siege tier for {@code id} today: max(day tier, Grid tier). The day is the day clock's
	 * ({@link #siegeDay}), not elapsed game time, so a siege, a Breach and a horde night on the same night agree
	 * on the day: sleeping skips the clock but not game time, and the Phase 4.5 playtest found a Breach on clock
	 * day 11 rolled as day 1, with no special infected unlocked in its waves.
	 */
	public static int tier(ServerLevel level, String id) {
		SiegeSettings siege = GridDistricts.siege(id);
		int day = siegeDay(level);
		return siege.siegeTier(day, GridDistricts.zoneTier(id), GridState.get(level.getServer()).reclaimedCount());
	}

	/**
	 * Today on the overworld day clock: {@link DirectorDay#today}, the one day the Director, horde nights, Breaches
	 * and sieges all share (phase 7 moved the ambient Director onto it too).
	 */
	static int siegeDay(ServerLevel level) {
		return DirectorDay.today(level);
	}

	/**
	 * The core's full health in {@code id}'s siege today: the siege tier's {@code core_hp}, else the settings'. Not
	 * saved with the district (its record codec is at the 16-field limit); the tier does not change during a night.
	 */
	public static int coreHp(ServerLevel level, String id) {
		return GridDistricts.siege(id).tier(tier(level, id)).coreHp(GridDistricts.settings(id).rules().coreHp());
	}

	/** Living, non-spectator players within {@code radius} of {@code pos}: the party a siege wave is sized for. */
	static int playersAround(ServerLevel level, BlockPos pos, double radius) {
		Vec3 c = Vec3.atCenterOf(pos);
		int n = 0;
		for (net.minecraft.server.level.ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(c) <= radius * radius) {
				n++;
			}
		}
		return n;
	}

	/** Marks a zombie as one of {@code district}'s siege (used for waves; public for gametests). */
	public static void tagSiegeZombie(Zombie zombie, String district) {
		zombie.addTag(TAG);
		zombie.addTag(DISTRICT_TAG_PREFIX + district);
		zombie.setPersistenceRequired();
	}

	/** Living siege zombies of {@code district} near its core. */
	public static List<Zombie> siegeZombies(ServerLevel level, String district, BlockPos core) {
		String tag = DISTRICT_TAG_PREFIX + district;
		return level.getEntitiesOfClass(Zombie.class, new AABB(core).inflate(COUNT_RADIUS),
				z -> z.isAlive() && z.entityTags().contains(tag));
	}

	static void tick(ServerLevel level, String id, long clock, long step) {
		GridState grid = GridState.get(level.getServer());
		DistrictRecord r = grid.record(id);
		if (r == null || !r.hasCore()) {
			return;
		}
		GridSettings settings = GridDistricts.settings(id);
		SiegeSettings siege = GridDistricts.siege(id);
		LoopRules rules = settings.rules();
		BlockPos core = BlockPos.of(r.corePos);
		boolean loaded = level.hasChunkAt(core);
		SiegeSettings.Tier tier = siege.tier(tier(level, id));
		boolean near = GridController.playersNear(level, core, settings.presenceRadius());
		grid.update(level, id, rec -> DistrictLoop.tickSiege(rec, near, step));

		List<Zombie> alive = loaded ? siegeZombies(level, id, core) : List.of();
		if (loaded && r.wavesSpawned < tier.waves()) {
			boolean due = r.wavesSpawned == 0 || alive.size() <= siege.nextWaveAliveAtMost()
					|| r.siegeTicks - r.lastWaveAt >= siege.waveIntervalSeconds() * 20L;
			if (due) {
				int spawned = spawnWave(level, id, core, tier, siege);
				grid.update(level, id, rec -> {
					rec.wavesSpawned++;
					rec.lastWaveAt = rec.siegeTicks;
				});
				AshfallArms.LOGGER.info("Grid: {} siege wave {}/{} ({} zombies)", id, r.wavesSpawned, tier.waves(), spawned);
				alive = siegeZombies(level, id, core);
			}
		}
		if (loaded) {
			SiegeDoors.holdOpen(level, id);
			GridController.hum(level, id, core, settings, level.getGameTime());
			Vec3 centre = Vec3.atCenterOf(core);
			int attackers = 0;
			for (Zombie zombie : alive) {
				if (attacking(zombie.position(), centre, settings.core())) {
					attackers++;
				} else {
					// goals are not saved: a siege zombie reloaded from disk gets its route back here
					SiegeRoutes.assign(zombie, core);
				}
			}
			int damage = (int) (attackers * (long) settings.core().damagePerZombieSecond() * step / 20);
			if (damage > 0 && damageCore(level, id, damage)) {
				return;
			}
		}
		// Unloaded, the survivors can't be counted: never call that "all dead".
		int survivors = loaded ? alive.size() : Integer.MAX_VALUE;
		DistrictLoop.SiegeOutcome outcome = DistrictLoop.siegeOutcome(r, rules, clock, survivors, tier.waves());
		switch (outcome) {
			case WON -> {
				GridController.hideBar(id);
				int day = DirectorDay.today(level);
				boolean breach = r.breach;
				grid.update(level, id, rec -> DistrictLoop.reclaim(rec, day));
				if (breach) {
					GridActions.broadcast(level, Component.translatable("message.ashfall.grid.breach_held", GridDistricts.name(id))
							.withStyle(ChatFormatting.GREEN));
					// Holding a Breach pays every time (a per-player cache), so late-game defence is worth doing.
					GridReclaim.placeRewardCache(level, core, BREACH_REWARD);
					AshfallArms.LOGGER.info("Grid: {} held its Breach", id);
				} else {
					AshfallArms.LOGGER.info("Grid: {} reclaimed on day {}", id, day);
				}
			}
			case ABANDONED -> {
				GridController.hideBar(id);
				grid.update(level, id, rec -> DistrictLoop.abandon(rec, clock));
				GridActions.broadcast(level, Component.translatable("message.ashfall.grid.siege_postponed", GridDistricts.name(id))
						.withStyle(ChatFormatting.YELLOW));
				AshfallArms.LOGGER.info("Grid: {} siege postponed (nobody held the core)", id);
			}
			case CONTINUE -> siegeBar(level, id, r, rules, core, tier, alive.size(), settings);
		}
	}

	/**
	 * Whether a siege zombie at {@code pos} is attacking the substation whose core centre is {@code centre}: close
	 * horizontally and at about the core's height.
	 * <p>
	 * Not "next to the core": every control room is shut behind the building's doors, so a zombie's path to the core
	 * ends at the nearest point it can reach, which is the outside of the control room's wall (the rooms all sit
	 * against an outer wall, 5 to 6 blocks from the core). In the Phase 3 playtest the River Arts waves stood
	 * against that wall, 4.3 blocks from the core, and never did any damage with the old 2.5-block radius. So a
	 * zombie battering the substation near the core counts, while one on a roof above it or in the cellar below
	 * it doesn't. Public for gametests.
	 */
	public static boolean attacking(Vec3 pos, Vec3 centre, GridSettings.Core core) {
		double dx = pos.x - centre.x, dz = pos.z - centre.z;
		return dx * dx + dz * dz <= core.attackRadius() * core.attackRadius()
				&& Math.abs(pos.y - centre.y) <= core.attackHeight();
	}

	/**
	 * Takes {@code damage} off a besieged core; at 0 the siege is lost (see {@link DistrictLoop#damageCore}).
	 * Shared by the siege tick and {@code /ashfall grid damage}, so an admin-forced loss behaves like a real one.
	 *
	 * @return true if this destroyed the core
	 */
	static boolean damageCore(ServerLevel level, String id, int damage) {
		boolean[] destroyed = new boolean[1];
		DistrictRecord before = GridState.get(level.getServer()).record(id);
		boolean breach = before != null && before.breach;
		GridState.get(level.getServer()).update(level, id, rec -> destroyed[0] = DistrictLoop.damageCore(rec, damage));
		if (destroyed[0]) {
			GridController.hideBar(id);
			if (breach) {
				GridActions.broadcast(level, Component.translatable("message.ashfall.grid.breach_lost", GridDistricts.name(id),
						DistrictLoop.reRepairCouplers(GridDistricts.settings(id).rules())).withStyle(ChatFormatting.DARK_RED));
				AshfallArms.LOGGER.info("Grid: {} lost its Breach, the district is broken", id);
			} else {
				GridActions.broadcast(level, Component.translatable("message.ashfall.grid.siege_lost", GridDistricts.name(id))
						.withStyle(ChatFormatting.RED));
				AshfallArms.LOGGER.info("Grid: {} siege lost, core destroyed", id);
			}
		}
		return destroyed[0];
	}

	private static void siegeBar(ServerLevel level, String id, DistrictRecord r, LoopRules rules, BlockPos core, SiegeSettings.Tier tier,
			int alive, GridSettings settings) {
		ServerBossEvent bar = GridController.bar(id);
		bar.setName(Component.translatable(r.breach ? "bossbar.ashfall.grid.breach" : "bossbar.ashfall.grid.siege", GridDistricts.name(id),
				r.coreHp, Math.max(r.coreHp, coreHp(level, id)),
				r.wavesSpawned, tier.waves(), alive));
		bar.setColor(BossEvent.BossBarColor.RED);
		bar.setProgress(Math.clamp(r.coreHp / (float) Math.max(1, Math.max(r.coreHp, coreHp(level, id))), 0f, 1f));
		GridController.showNear(level, bar, core, settings.presenceRadius() * 1.5);
	}

	/** Spawns one wave around the core. @return zombies spawned */
	static int spawnWave(ServerLevel level, String id, BlockPos core, SiegeSettings.Tier tier, SiegeSettings siege) {
		int spawned = 0;
		int day = siegeDay(level);
		String zone = DirectorContexts.zoneAt(core).id();
		int size = siege.waveSize(tier, playersAround(level, core, GridDistricts.settings(id).presenceRadius()));
		for (int i = 0; i < size; i++) {
			// Special infected join sieges at their files' siege weights, capped per player around the core.
			Map.Entry<Identifier, ZombieVariant> variant = SpecialInfected.rollForSiege(level, core, day, zone, level.getRandom());
			if (variant == null) {
				variant = pickVariant(level.getRandom(), tier);
			}
			Zombie zombie = spawnAround(level, core, siege.spawnRadiusMin(), siege.spawnRadiusMax(), variant,
					DirectorZombie.Source.SIEGE, false);
			if (zombie != null) {
				tagSiegeZombie(zombie, id);
				SiegeRoutes.assign(zombie, core);
				spawned++;
			}
		}
		return spawned;
	}

	/** A weighted variant from the tier's table; null (a plain zombie) if none of its variants is loaded. */
	static Map.Entry<Identifier, ZombieVariant> pickVariant(RandomSource random, SiegeSettings.Tier tier) {
		int total = 0;
		for (Map.Entry<Identifier, Integer> e : tier.variants().entrySet()) {
			if (ZombieVariants.get(e.getKey()) != null) {
				total += e.getValue();
			}
		}
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (Map.Entry<Identifier, Integer> e : tier.variants().entrySet()) {
			ZombieVariant variant = ZombieVariants.get(e.getKey());
			if (variant == null) {
				continue;
			}
			roll -= e.getValue();
			if (roll < 0) {
				return Map.entry(e.getKey(), variant);
			}
		}
		return null;
	}

	/** Spawn pressure uses the current tier's mix too, so a hold feels like the siege it leads to. */
	static Map.Entry<Identifier, ZombieVariant> pickVariant(ServerLevel level, String id) {
		return pickVariant(level.getRandom(), GridDistricts.siege(id).tier(tier(level, id)));
	}

	/**
	 * One zombie on valid ground in the ring {@code min..max} blocks around {@code centre}, only in loaded chunks.
	 * The column is scanned outward from the core's floor height (7 blocks down and up), which finds the street
	 * and the hall around a substation before any roof.
	 *
	 * @param hidden true to skip spots a player can see (spawn pressure must not pop zombies into view)
	 */
	static Zombie spawnAround(ServerLevel level, BlockPos centre, int min, int max, Map.Entry<Identifier, ZombieVariant> variant,
			DirectorZombie.Source source, boolean hidden) {
		RandomSource random = level.getRandom();
		int lo = Math.min(min, max), hi = Math.max(min, max);
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = lo + random.nextDouble() * (hi - lo);
			int x = Mth.floor(centre.getX() + 0.5 + Math.cos(angle) * dist);
			int z = Mth.floor(centre.getZ() + 0.5 + Math.sin(angle) * dist);
			BlockPos spot = findFloor(level, x, z, centre.getY());
			if (spot == null || (hidden && SpawnDirector.visibleToAnyPlayer(level, spot))) {
				continue;
			}
			Zombie zombie = DirectorSpawns.spawn(level, spot, variant, source, DirectorZombie.NO_BUILDING);
			if (zombie != null) {
				return zombie;
			}
		}
		return null;
	}

	private static BlockPos findFloor(ServerLevel level, int x, int z, int aroundY) {
		if (!level.hasChunkAt(new BlockPos(x, aroundY, z))) {
			return null;
		}
		// Nearest to the core's own floor first (feet one below the core, which stands on a slab), alternating
		// down and up: scanning from the top put River Arts' waves on the substation roof, right above the core and
		// out of its reach, where some also fell to their death (Phase 3 playtest).
		int feet = aroundY - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, feet, z);
		for (int i = 0; i <= 14; i++) {
			int dy = (i & 1) == 1 ? -((i + 1) / 2) : i / 2;
			pos.setY(feet + dy);
			if (SpawnDirector.validLand(level, pos)) {
				return pos.immutable();
			}
		}
		BlockPos surface = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		return SpawnDirector.validLand(level, surface) ? surface : null;
	}
}
