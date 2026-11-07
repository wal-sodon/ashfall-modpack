package xyz.toesinthesand.ashfall.director.special;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariant;

import java.util.Map;

/**
 * The special infected's entry point and its Director integration (plan M9).
 * <ul>
 *   <li><b>Ambient spawns:</b> {@code SpawnDirector.tryAmbientSpawn} asks {@link #roll} first; when it returns a
 *   special the spawn becomes that special, otherwise the variant table decides as before.</li>
 *   <li><b>Sieges:</b> arms' District Siege waves call {@link #roll} with {@code siege = true}, which applies each
 *   file's siege multiplier.</li>
 *   <li><b>Horde nights:</b> the Director's own horde waves ({@code HordeNights.spawnWave}) call {@link #roll} with
 *   {@code siege = true} for every member, so a special replaces that member and joins the horde like any other
 *   zombie. A special's {@code min_day} is its unlock day, so the schedule's {@code unlocks} list does not name
 *   specials: one file per kind stays the single source of when and how often it appears.</li>
 * </ul>
 * Caps: each kind is limited to {@code max_alive_per_player} within {@link #CAP_RADIUS} of the spawn, times the
 * players in that radius. A kind at its cap, or too big for the spot (a 3-block brute in a 2-block corridor), is
 * skipped as if its weight were 0.
 */
public final class SpecialInfected {
	/** Radius of the per-player cap count, the same as the Director's per-player hard cap. */
	public static final double CAP_RADIUS = 64.0;
	private SpecialInfected() {
	}

	public static void init() {
		SpecialSounds.init();
		SpecialEntities.init();
		InfectionClouds.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(SpecialInfectedRules.ID, new SpecialInfectedRules());
	}

	/**
	 * Whether a Director spawn at {@code pos} becomes a special, and which.
	 *
	 * @param siege true for siege waves and hordes (the files' siege multipliers apply)
	 * @return the special as a variant entry (key = {@code ashfall:<kind>}, so the Director tag records the kind),
	 * or null for an ordinary zombie
	 */
	public static Map.Entry<Identifier, ZombieVariant> roll(ServerLevel level, BlockPos pos, int day, String zone, boolean siege,
															RandomSource random) {
		SpecialKind kind = SpecialInfectedRules.pick(SpecialInfectedRules.all(), random.nextDouble(), day, zone, siege,
				k -> fits(level, pos, k) && underCap(level, pos, k));
		return kind == null ? null : variantFor(kind);
	}

	/**
	 * {@link #roll} for a siege wave whose spawn spot is chosen after the pick, around {@code centre} (a substation
	 * core): the cap is counted around the centre and the fit check is skipped, because the centre itself is a
	 * block. A brute picked here may land in a tight spot; ZB&B lets it break out.
	 */
	public static Map.Entry<Identifier, ZombieVariant> rollForSiege(ServerLevel level, BlockPos centre, int day, String zone,
																	RandomSource random) {
		SpecialKind kind = SpecialInfectedRules.pick(SpecialInfectedRules.all(), random.nextDouble(), day, zone, true,
				k -> underCap(level, centre, k));
		return kind == null ? null : variantFor(kind);
	}

	/**
	 * A special as a {@link ZombieVariant} for {@link DirectorSpawns#spawn}: its entity, the file's attributes and no
	 * gear. Going through the ordinary spawn path means specials get the Director tag, count towards every cap,
	 * fire the spawned hook and get senses exactly like other Director zombies.
	 */
	public static Map.Entry<Identifier, ZombieVariant> variantFor(SpecialKind kind) {
		SpecialInfectedRule rule = SpecialInfectedRules.get(kind);
		ZombieVariant variant = new ZombieVariant(kind.key(), rule.weight(), rule.minDay(), ZombieVariant.Environment.LAND, 0f,
				rule.attributes(), ZombieVariant.Equipment.NONE, Map.of());
		return Map.entry(kind.key(), variant);
	}

	/** Spawns one special as a Director zombie (null if it could not be added). */
	public static SpecialInfectedEntity spawn(ServerLevel level, BlockPos pos, SpecialKind kind, DirectorZombie.Source source) {
		Zombie zombie = DirectorSpawns.spawn(level, pos, variantFor(kind), source, DirectorZombie.NO_BUILDING);
		return zombie instanceof SpecialInfectedEntity special ? special : null;
	}

	/** Whether the kind's hitbox, with its file's scale, fits at {@code pos} (feet on the block, centred). */
	public static boolean fits(ServerLevel level, BlockPos pos, SpecialKind kind) {
		EntityType<?> type = SpecialEntities.type(kind);
		double scale = SpecialInfectedRules.get(kind).attributes()
				.getOrDefault(Identifier.withDefaultNamespace("scale"), kind == SpecialKind.BRUTE ? 1.6 : 1.0);
		EntityDimensions dims = type.getDimensions().scale((float) scale);
		AABB box = dims.makeBoundingBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		return level.noCollision(box);
	}

	/** Whether the kind is under its per-player cap around {@code pos}. */
	public static boolean underCap(ServerLevel level, BlockPos pos, SpecialKind kind) {
		AABB area = new AABB(pos).inflate(CAP_RADIUS);
		Vec3 centre = Vec3.atCenterOf(pos);
		double r2 = CAP_RADIUS * CAP_RADIUS;
		int alive = level.getEntitiesOfClass(SpecialInfectedEntity.class, area,
				e -> e.kind() == kind && e.isAlive() && e.distanceToSqr(centre) <= r2).size();
		int players = 0;
		for (Player player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(centre) <= r2) {
				players++;
			}
		}
		return SpecialInfectedRules.get(kind).underCap(alive, players);
	}
}
