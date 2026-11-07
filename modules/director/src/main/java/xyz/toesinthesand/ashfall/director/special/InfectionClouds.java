package xyz.toesinthesand.ashfall.director.special;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import xyz.toesinthesand.ashfall.director.infection.Infection;

import java.util.List;

/**
 * Infection from a bloated's burst cloud. A vanilla area effect cloud can only apply mob effects, and Contagion's
 * effect would infect on any touch (Ashfall adopts a Contagion-only infection outright), which is far too harsh
 * for a single breath. So the cloud carries only poison and nausea, plus the entity tag {@link #TAG}, and this
 * once-a-second pass rolls Ashfall's own infection for each player standing in a tagged cloud.
 * <p>
 * Cost: per online player, once a second, one entity lookup in a small box around them; clouds live seconds.
 */
public final class InfectionClouds {
	/** Entity tag marking a cloud as infectious. */
	public static final String TAG = "ashfall_infection_cloud";
	public static final int INTERVAL = 20;

	private InfectionClouds() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(InfectionClouds::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (Infection.progresses(player) && !Infection.isInfected(player) && inCloud(player)) {
				Infection.tryInfect(player, chancePerSecond(), player.getRandom().nextFloat());
			}
		}
	}

	/** The bloated file's {@code cloud_infection_chance} (per second inside the cloud, default 0.1). */
	public static float chancePerSecond() {
		return SpecialInfectedRules.get(SpecialKind.BLOATED).tuning("cloud_infection_chance", 0.1f);
	}

	/** Whether the player stands inside a live infectious cloud (horizontal radius, within the cloud's height band). */
	public static boolean inCloud(ServerPlayer player) {
		ServerLevel level = player.level();
		List<AreaEffectCloud> clouds = level.getEntitiesOfClass(AreaEffectCloud.class, player.getBoundingBox().inflate(8.0, 2.0, 8.0),
				c -> c.entityTags().contains(TAG) && !c.isWaiting());
		for (AreaEffectCloud cloud : clouds) {
			double dx = player.getX() - cloud.getX();
			double dz = player.getZ() - cloud.getZ();
			float r = cloud.getRadius();
			if (dx * dx + dz * dz <= r * r && Math.abs(player.getY() - cloud.getY()) <= 2.0) {
				return true;
			}
		}
		return false;
	}
}
