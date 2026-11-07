package xyz.toesinthesand.ashfall.director.senses;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /ashfall debug stimuli [seconds]} (permission level 2): shows the stimuli around the calling
 * player as particles, only to them, so tuning noise radii and blackout curtains needs no guesswork.
 * A NOTE particle marks each SOUND, an END_ROD each LIGHT, a damage heart each BLOOD drop, with a ring of small particles at its radius.
 * <p>
 * Rendering is capped (stimuli per pulse, points per ring) and only runs while someone is watching.
 * Other Director modules register more {@code /ashfall} subcommands; Brigadier merges the literals.
 */
public final class StimuliDebugCommand {
	public static final int DEFAULT_SECONDS = 10;
	public static final int MAX_SECONDS = 600;
	private static final int PULSE_TICKS = 10;
	private static final double VIEW_RANGE = 64.0;
	private static final int MAX_SHOWN = 32;
	private static final int MAX_RING_POINTS = 48;

	/** Watching player to the game tick their view ends. */
	private static final Map<UUID, Long> WATCHERS = new HashMap<>();

	private StimuliDebugCommand() {
	}

	static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
		ServerTickEvents.END_SERVER_TICK.register(StimuliDebugCommand::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WATCHERS.clear());
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		// the permission sits on "debug", not the shared "ashfall" root, which other features also extend
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("debug")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("stimuli")
								.executes(ctx -> watch(ctx, DEFAULT_SECONDS))
								.then(Commands.argument("seconds", IntegerArgumentType.integer(0, MAX_SECONDS))
										.executes(ctx -> watch(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))));
	}

	private static int watch(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int[] count = {0};
		Stimuli.forEachActive(player.level(), s -> count[0]++);
		if (seconds == 0) {
			WATCHERS.remove(player.getUUID());
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.debug.stimuli.off"), false);
			return 0;
		}
		WATCHERS.put(player.getUUID(), (long) player.level().getServer().getTickCount() + seconds * 20L);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.debug.stimuli.on", seconds, count[0]), false);
		return count[0];
	}

	private static void tick(MinecraftServer server) {
		if (WATCHERS.isEmpty() || server.getTickCount() % PULSE_TICKS != 0) {
			return;
		}
		Iterator<Map.Entry<UUID, Long>> it = WATCHERS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> watcher = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(watcher.getKey());
			if (player == null || server.getTickCount() >= watcher.getValue()) {
				it.remove();
				continue;
			}
			render(player);
		}
	}

	private static void render(ServerPlayer player) {
		ServerLevel level = player.level();
		int[] shown = {0};
		Stimuli.forEachActive(level, s -> {
			if (shown[0] >= MAX_SHOWN || s.distanceSqr(player.getX(), player.getY(), player.getZ()) > VIEW_RANGE * VIEW_RANGE) {
				return;
			}
			shown[0]++;
			SimpleParticleType marker = switch (s.type()) {
				case SOUND -> ParticleTypes.NOTE;
				case LIGHT -> ParticleTypes.END_ROD;
				case BLOOD -> ParticleTypes.DAMAGE_INDICATOR;
			};
			level.sendParticles(player, marker, true, true, s.x(), s.y() + 0.5, s.z(), 1, 0.0, 0.0, 0.0, 0.0);
			int points = (int) Math.min(MAX_RING_POINTS, Math.max(12, s.radius() * 2));
			SimpleParticleType ring = switch (s.type()) {
				case SOUND -> ParticleTypes.ELECTRIC_SPARK;
				case LIGHT -> ParticleTypes.WAX_ON;
				case BLOOD -> ParticleTypes.CRIMSON_SPORE;
			};
			for (int i = 0; i < points; i++) {
				double angle = 2 * Math.PI * i / points;
				level.sendParticles(player, ring, true, false, s.x() + Math.cos(angle) * s.radius(), s.y(),
						s.z() + Math.sin(angle) * s.radius(), 1, 0.0, 0.0, 0.0, 0.0);
			}
		});
	}
}
