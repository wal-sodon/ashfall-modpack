package xyz.toesinthesand.ashfall.director.boss;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.levelgen.Heightmap;
import xyz.toesinthesand.ashfall.director.spawn.DirectorDay;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.spawn.SpawnContext;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariant;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariants;
import xyz.toesinthesand.ashfall.salvage.site.PlacedSite;
import xyz.toesinthesand.ashfall.salvage.site.SiteState;

import java.util.List;
import java.util.Map;

/**
 * The light zombie guard of a crash site: when a player first comes within {@link #WAKE_RADIUS} blocks of a placed
 * site that still owes guards, that many Director zombies rise around it (day-appropriate variants, as ambient
 * spawns, so they count against the player's ambient budget and despawn like any other). Salvage places the sites
 * but cannot spawn Director zombies, which is why the guard lives here.
 */
public final class CrashSiteGuards {
	public static final int WAKE_RADIUS = 40;
	private static final int CHECK_TICKS = 20;

	private CrashSiteGuards() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(CrashSiteGuards::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 5) {
			return;
		}
		ServerLevel level = server.overworld();
		if (level == null || level.players().isEmpty()) {
			return;
		}
		SiteState state = SiteState.get(server);
		for (PlacedSite site : List.copyOf(state.all())) {
			if (site.guards() <= 0) {
				continue;
			}
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator() && player.blockPosition().closerThan(site.center(), WAKE_RADIUS)) {
					wake(level, site);
					break;
				}
			}
		}
	}

	/**
	 * Raises the site's guard now and records it as spent.
	 *
	 * @return how many guards spawned
	 */
	public static int wake(ServerLevel level, PlacedSite site) {
		RandomSource random = level.getRandom();
		int day = DirectorDay.today(level);
		int spawned = 0;
		for (int i = 0; i < site.guards(); i++) {
			BlockPos at = around(level, site, random);
			Map.Entry<Identifier, ZombieVariant> variant = ZombieVariants.pick(ZombieVariants.all(), random.nextDouble(), day,
					ZombieVariant.Environment.LAND, null, SpawnContext.NONE);
			Zombie zombie = DirectorSpawns.spawn(level, at, variant, DirectorZombie.Source.AMBIENT, DirectorZombie.NO_BUILDING);
			if (zombie != null) {
				spawned++;
			}
		}
		SiteState.get(level.getServer()).put(site.withGuards(0));
		return spawned;
	}

	private static BlockPos around(ServerLevel level, PlacedSite site, RandomSource random) {
		int r = Math.max(2, site.guardRadius());
		for (int attempt = 0; attempt < 8; attempt++) {
			int x = site.center().getX() + random.nextIntBetweenInclusive(-r, r);
			int z = site.center().getZ() + random.nextIntBetweenInclusive(-r, r);
			BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
					&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
				return feet;
			}
		}
		return site.center();
	}
}
