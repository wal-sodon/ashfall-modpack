package xyz.toesinthesand.ashfall.director.senses;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Player-centred LIGHT: every {@link #INTERVAL} ticks each player is checked once, staggered by entity
 * id so a full server spreads the work over the interval instead of spiking one tick. The light is the
 * brighter of the block light where they stand and what they hold (see {@link ItemLight}); whether it
 * shows and how far is {@link LightRules}. The stimulus sits at the player's eyes with the player as its
 * source, so the next check refreshes it in place and it expires on its own once the light goes out.
 * A torch that burns out (salvage {@code TorchBurnout}) becomes a block with no light emission, so the block light
 * here drops with it and a burnt-out torch gives nobody away.
 */
public final class LightEmitter {
	/** Matches {@link StimulusType#LIGHT}'s lifetime, so a steady light never flickers out. */
	public static final int INTERVAL = 40;

	private LightEmitter() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(LightEmitter::tick);
	}

	private static void tick(MinecraftServer server) {
		int phase = server.getTickCount() % INTERVAL;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (Math.floorMod(player.getId(), INTERVAL) == phase) {
				emit(player);
			}
		}
	}

	/** Checks one player now; returns the radius emitted (0 when their light gives nothing away). */
	public static double emit(ServerPlayer player) {
		if (player.isSpectator() || player.isRemoved() || !player.isAlive()) {
			return 0.0;
		}
		ServerLevel level = player.level();
		int light = lightAt(player);
		BlockPos head = BlockPos.containing(player.getEyePosition());
		double radius = LightRules.radius(light, level.isDarkOutside(), !level.canSeeSky(head));
		if (radius > 0.0) {
			Vec3 eyes = player.getEyePosition();
			Stimuli.emit(level, eyes, StimulusType.LIGHT, radius, player);
		}
		return radius;
	}

	/** max(block light at feet and head, both hands' item light). Sky light is deliberately ignored. */
	public static int lightAt(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		int block = Math.max(level.getBrightness(LightLayer.BLOCK, feet), level.getBrightness(LightLayer.BLOCK, feet.above()));
		int held = Math.max(ItemLight.luminance(player.getItemInHand(InteractionHand.MAIN_HAND)),
				ItemLight.luminance(player.getItemInHand(InteractionHand.OFF_HAND)));
		return Math.max(block, held);
	}
}
