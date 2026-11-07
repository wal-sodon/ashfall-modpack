package xyz.toesinthesand.ashfall.director.haven;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.AshfallDirector;
import xyz.toesinthesand.ashfall.director.horde.ScheduledHordeSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The horde siren and countdown (plan M4): when our horde schedule ({@code HordeSchedule}, plan M8) is about to
 * start a horde night, every
 * overworld player hears a siren and sees a bossbar counting down to nightfall, so they can get to a haven;
 * when the horde night starts the siren sounds once more. Decisions are in {@link SirenLogic}; this class
 * only turns them into sound, a title and a bossbar.
 * <p>
 * The siren is played to each player at their own position (a sound packet, not a world sound), so it is
 * heard everywhere in the city at the same volume, like a civil-defence siren, and it is not a SOUND
 * stimulus: it should send players running, not pull zombies onto them.
 * <p>
 * Cost: one field read and one state update per second, bossbar membership synced on the same tick.
 */
public final class HordeSiren {
	private static final int UPDATE_TICKS = 20;
	private static final UUID BAR_ID = UUID.fromString("6d1f1c2e-6a39-4b8e-9d0e-a5f1a54e0a11");

	private static final SirenLogic LOGIC = new SirenLogic();
	private static ServerBossEvent bar;
	private static HordeSource source;
	private static @Nullable HordeSource override;

	private HordeSiren() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % UPDATE_TICKS == 3) {
				update(server, server.overworld().getOverworldClockTime());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			if (bar != null) {
				bar.removeAllPlayers();
			}
			bar = null;
			LOGIC.reset();
		});
	}

	private static HordeSource source() {
		HordeSource test = override;
		if (test != null) {
			return test;
		}
		if (source == null) {
			source = new ScheduledHordeSource();
		}
		return source;
	}

	/** One siren update at overworld clock {@code dayTime}; returns what the logic decided. Public for gametests. */
	public static SirenLogic.Step update(MinecraftServer server, long dayTime) {
		HavenSettings settings = HavenSettings.get();
		SirenLogic.Step step = LOGIC.update(source().read(server), dayTime, server.getTickCount(),
				settings.sirenWarningTicks(), settings.sirenRepeatTicks());
		ServerLevel overworld = server.overworld();
		ServerBossEvent bossbar = bar();
		switch (step.phase()) {
			case IDLE -> bossbar.removeAllPlayers();
			case WARNING -> {
				long seconds = (step.ticksToNightfall() + 19) / 20;
				bossbar.setName(Component.translatable("bossbar.ashfall.horde_warning",
						String.format("%d:%02d", seconds / 60, seconds % 60)));
				bossbar.setColor(BossEvent.BossBarColor.YELLOW);
				long window = Math.max(1, settings.sirenWarningTicks());
				bossbar.setProgress(Math.clamp(step.ticksToNightfall() / (float) window, 0f, 1f));
				syncPlayers(bossbar, overworld);
			}
			case HORDE -> {
				bossbar.setName(Component.translatable("bossbar.ashfall.horde_night"));
				bossbar.setColor(BossEvent.BossBarColor.RED);
				bossbar.setProgress(1f);
				syncPlayers(bossbar, overworld);
			}
		}
		if (step.siren()) {
			sound(overworld, settings);
		}
		if (step.entered()) {
			// Logged so an admin can see the siren fire on a server with nobody online to hear it.
			AshfallDirector.LOGGER.info("Horde siren: {} (nightfall in {} ticks)", step.phase().name().toLowerCase(java.util.Locale.ROOT),
					step.ticksToNightfall());
		}
		if (step.entered() && step.phase() != SirenLogic.Phase.IDLE) {
			boolean warning = step.phase() == SirenLogic.Phase.WARNING;
			title(overworld, Component.translatable(warning ? "title.ashfall.siren.warning" : "title.ashfall.siren.horde")
							.withStyle(ChatFormatting.RED),
					Component.translatable(warning ? "subtitle.ashfall.siren.warning" : "subtitle.ashfall.siren.horde"));
		}
		return step;
	}

	private static ServerBossEvent bar() {
		if (bar == null) {
			bar = new ServerBossEvent(BAR_ID, Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		return bar;
	}

	/** Overworld players see the bar; players elsewhere (Nether, End) are off the city's schedule. */
	private static void syncPlayers(ServerBossEvent bossbar, ServerLevel overworld) {
		Set<ServerPlayer> wanted = new HashSet<>(overworld.players());
		for (ServerPlayer player : Set.copyOf(bossbar.getPlayers())) {
			if (!wanted.remove(player)) {
				bossbar.removePlayer(player);
			}
		}
		wanted.forEach(bossbar::addPlayer);
	}

	private static void sound(ServerLevel overworld, HavenSettings settings) {
		Holder<SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.get(settings.sirenSound())
				.<Holder<SoundEvent>>map(h -> h).orElse(SoundEvents.RAID_HORN);
		for (ServerPlayer player : overworld.players()) {
			player.connection.send(new ClientboundSoundPacket(sound, SoundSource.AMBIENT, player.getX(), player.getEyeY(), player.getZ(),
					settings.sirenVolume(), 1.0f, overworld.getRandom().nextLong()));
		}
	}

	private static void title(ServerLevel overworld, Component title, Component subtitle) {
		for (ServerPlayer player : overworld.players()) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
			player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
			player.connection.send(new ClientboundSetTitleTextPacket(title));
		}
	}

	/**
	 * Sounds the siren and shows a title to every overworld player, outside the horde schedule. The grid's
	 * District Siege uses it for its own warning and start (arms {@code GridSiege}): one siren for every
	 * kind of attack, so players learn a single sound means "get to cover", and the same per-player packet
	 * means the warning itself never pulls zombies.
	 */
	public static void alert(MinecraftServer server, Component title, Component subtitle) {
		ServerLevel overworld = server.overworld();
		sound(overworld, HavenSettings.get());
		title(overworld, title.copy().withStyle(ChatFormatting.RED), subtitle);
		AshfallDirector.LOGGER.info("Siren alert: {} / {}", title.getString(), subtitle.getString());
	}

	/** The bossbar, for gametests (null before the first update). */
	public static @Nullable ServerBossEvent barForTesting() {
		return bar;
	}

	public static SirenLogic.Phase phase() {
		return LOGIC.phase();
	}

	/** Test hook: a fake horde schedule (pass null to go back to our own schedule); resets the state machine. */
	public static void setSourceForTesting(@Nullable HordeSource fake) {
		override = fake;
		LOGIC.reset();
	}
}
