package xyz.toesinthesand.ashfall.arms.story;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The ending vote at the Heart (design decision 3): every participant gets clickable {@code [Sever] [Bind]} buttons in
 * chat (they run {@code /ashfall ending vote sever|bind}) and a boss bar counting down the 30-second window. The
 * arithmetic is {@link VoteTally}'s.
 * <p>
 * <b>Solo.</b> With one eligible voter a click does not count yet: it asks for a confirmation
 * ({@code /ashfall ending confirm}), because a lone player has nobody to catch a misclick and the choice reshapes the
 * whole world. <b>Why chat.</b> Like the respawn choice, a chat prompt needs no client code and cannot leave anyone
 * stuck behind a screen that failed to open.
 * <p>
 * One vote at a time per server, kept in memory: a restart mid-vote drops it, and the finale (or an op with
 * {@code /ashfall ending begin}) simply starts it again.
 */
public final class EndingVote {
	/** The voting window, per round (design decision 3: 30 seconds). */
	public static final int WINDOW_SECONDS = 30;
	private static final int TICK_INTERVAL = 10;

	/** What a click did. */
	public enum Result {
		/** No vote is running. */
		NO_VOTE,
		/** The player is not one of the participants. */
		NOT_PARTICIPANT,
		/** The vote counts. */
		RECORDED,
		/** Solo: the choice waits for {@code /ashfall ending confirm}. */
		CONFIRM_NEEDED,
		/** {@code confirm} with nothing to confirm. */
		NOTHING_TO_CONFIRM
	}

	private static final class Session {
		final Set<UUID> participants;
		final Map<UUID, StoryEnding> votes = new HashMap<>();
		final Map<UUID, StoryEnding> unconfirmed = new HashMap<>();
		final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("bossbar.ashfall.ending_vote", 0, 0, WINDOW_SECONDS),
				BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_6);
		int round = 1;
		long endsAt;

		Session(Collection<UUID> participants) {
			this.participants = new LinkedHashSet<>(participants);
		}
	}

	private static Session session;

	private EndingVote() {
	}

	public static synchronized boolean active() {
		return session != null;
	}

	/** Starts a vote among {@code participants} (UUIDs; offline ones only count once they are online). */
	public static synchronized void start(MinecraftServer server, Collection<UUID> participants) {
		cancel();
		session = new Session(participants);
		openRound(server, session);
		AshfallArms.LOGGER.info("Ending vote started with {} participant(s)", participants.size());
		if (online(server).isEmpty()) {
			AshfallArms.LOGGER.warn("Ending vote has nobody online to vote: two silent rounds ({} s) default to Sever unless an op runs "
					+ "/ashfall ending set sever|bind", WINDOW_SECONDS * 2);
		}
	}

	/** Drops a running vote without a result (story reset, server stop). */
	public static synchronized void cancel() {
		if (session != null) {
			session.bar.removeAllPlayers();
			session = null;
		}
	}

	/** Participants who are online right now. */
	static synchronized List<ServerPlayer> online(MinecraftServer server) {
		List<ServerPlayer> out = new ArrayList<>();
		if (session != null) {
			for (UUID id : session.participants) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null) {
					out.add(p);
				}
			}
		}
		return out;
	}

	/** A click on {@code [Sever]} or {@code [Bind]}. */
	public static synchronized Result vote(ServerPlayer player, StoryEnding choice) {
		if (session == null) {
			return Result.NO_VOTE;
		}
		if (!session.participants.contains(player.getUUID()) || !choice.chosen()) {
			return Result.NOT_PARTICIPANT;
		}
		MinecraftServer server = player.level().getServer();
		if (online(server).size() == 1) {
			session.unconfirmed.put(player.getUUID(), choice);
			player.sendSystemMessage(confirmPrompt(choice));
			return Result.CONFIRM_NEEDED;
		}
		session.votes.put(player.getUUID(), choice);
		for (ServerPlayer p : online(server)) {
			p.sendSystemMessage(Component.translatable("message.ashfall.ending.voted", player.getName(),
					Component.translatable(choice.translationKey()), session.votes.size(), online(server).size()).withStyle(ChatFormatting.GRAY));
		}
		decide(server, false);
		return Result.RECORDED;
	}

	/** Solo confirmation of the last click. */
	public static synchronized Result confirm(ServerPlayer player) {
		if (session == null) {
			return Result.NO_VOTE;
		}
		StoryEnding choice = session.unconfirmed.remove(player.getUUID());
		if (choice == null) {
			return Result.NOTHING_TO_CONFIRM;
		}
		session.votes.put(player.getUUID(), choice);
		decide(player.level().getServer(), false);
		return Result.RECORDED;
	}

	/** Called from the server tick: the boss bar, departures, and the end of the window. */
	static synchronized void tick(MinecraftServer server) {
		if (session == null || server.getTickCount() % TICK_INTERVAL != 0) {
			return;
		}
		List<ServerPlayer> online = online(server);
		long left = Math.max(0, session.endsAt - server.getTickCount());
		int sever = count(StoryEnding.SEVER, online), bind = count(StoryEnding.BIND, online);
		session.bar.setName(Component.translatable("bossbar.ashfall.ending_vote", sever, bind, Mth.ceil(left / 20.0)));
		session.bar.setProgress(Mth.clamp(left / (WINDOW_SECONDS * 20f), 0f, 1f));
		for (ServerPlayer p : online) {
			session.bar.addPlayer(p);
		}
		for (ServerPlayer p : List.copyOf(session.bar.getPlayers())) {
			if (p.isRemoved() || !online.contains(p)) {
				session.bar.removePlayer(p);
			}
		}
		decide(server, left <= 0);
	}

	private static int count(StoryEnding choice, List<ServerPlayer> online) {
		int n = 0;
		for (ServerPlayer p : online) {
			if (session.votes.get(p.getUUID()) == choice) {
				n++;
			}
		}
		return n;
	}

	private static void decide(MinecraftServer server, boolean windowOver) {
		List<ServerPlayer> online = online(server);
		VoteTally.Outcome outcome = VoteTally.decide(count(StoryEnding.SEVER, online), count(StoryEnding.BIND, online),
				online.size(), windowOver, session.round >= 2);
		switch (outcome) {
			case PENDING -> {
			}
			case REPROMPT -> {
				session.round++;
				session.votes.clear();
				session.unconfirmed.clear();
				for (ServerPlayer p : online) {
					p.sendSystemMessage(Component.translatable("message.ashfall.ending.reprompt").withStyle(ChatFormatting.GOLD));
				}
				openRound(server, session);
			}
			case SEVER, BIND -> {
				Session done = session;
				done.bar.removeAllPlayers();
				session = null;
				AshfallArms.LOGGER.info("Ending vote decided: {} (round {})", outcome, done.round);
				Ending.apply(server, outcome.ending(), online);
			}
		}
	}

	private static void openRound(MinecraftServer server, Session s) {
		s.endsAt = server.getTickCount() + WINDOW_SECONDS * 20L;
		for (ServerPlayer p : online(server)) {
			p.sendSystemMessage(prompt(s.round, online(server).size() == 1));
			p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.7f);
			s.bar.addPlayer(p);
		}
	}

	/** The chat prompt: the question, the two buttons with their consequences on hover, and the window. */
	static Component prompt(int round, boolean solo) {
		MutableComponent line = Component.translatable(round > 1 ? "message.ashfall.ending.prompt_again" : "message.ashfall.ending.prompt",
				WINDOW_SECONDS).withStyle(ChatFormatting.LIGHT_PURPLE);
		line.append(" ").append(button(StoryEnding.SEVER, ChatFormatting.DARK_PURPLE, "/ashfall ending vote sever"));
		line.append(" ").append(button(StoryEnding.BIND, ChatFormatting.DARK_AQUA, "/ashfall ending vote bind"));
		if (solo) {
			line.append(" ").append(Component.translatable("message.ashfall.ending.solo_hint").withStyle(ChatFormatting.GRAY));
		}
		return line;
	}

	private static Component confirmPrompt(StoryEnding choice) {
		return Component.translatable("message.ashfall.ending.confirm", Component.translatable(choice.translationKey()))
				.withStyle(ChatFormatting.GOLD)
				.append(" ")
				.append(Component.translatable("message.ashfall.ending.confirm_button").withStyle(Style.EMPTY
						.withColor(ChatFormatting.GREEN).withUnderlined(true)
						.withClickEvent(new ClickEvent.RunCommand("/ashfall ending confirm"))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable("ending.ashfall." + choice.id() + ".summary")))));
	}

	private static Component button(StoryEnding choice, ChatFormatting colour, String command) {
		return Component.literal("[").append(Component.translatable(choice.translationKey())).append("]")
				.withStyle(Style.EMPTY.withColor(colour).withBold(true).withUnderlined(true)
						.withClickEvent(new ClickEvent.RunCommand(command))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable("ending.ashfall." + choice.id() + ".summary"))));
	}
}
