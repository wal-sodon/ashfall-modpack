package xyz.toesinthesand.ashfall.arms.story;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.arms.blade.BladeCrystal;
import xyz.toesinthesand.ashfall.arms.blade.BladeStatsManager;
import xyz.toesinthesand.ashfall.arms.blade.PlasmaBladeItem;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.director.claims.Claims;
import xyz.toesinthesand.ashfall.director.haven.Havens;
import xyz.toesinthesand.ashfall.director.infection.InfectionRules;
import xyz.toesinthesand.ashfall.director.story.StoryFlags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The story's ending (endgame-design section 4, "the choice"; section 5, "after the ending"). The Black Start finale
 * calls {@link #beginChoice} when the Heart falls; the party votes ({@link EndingVote}); {@link #apply} then records
 * the ending, sets the story flags other systems read, and hands each participant the Field Report, the epilogue, the
 * "Asheville Relit" advancement and the ending's rewards. Everything the ending changes afterwards is applied live by
 * {@link StoryEffects} from the ending's data-driven profile.
 * <p>
 * {@link #reset} is the replay of section 5 ({@code /ashfall story reset}): it forgets the ending and the Black Start
 * bonus and fires {@link #RESET} so the finale can rearm. Reclaimed districts, Relay Logs, rewards already handed out
 * and the run's death counters are kept: a replay is of the finale and the choice, not of the whole arc.
 */
public final class Ending {
	/** The Grid % the Black Start adds on top of the districts' 80 %: the ending always leaves the grid at 100 %. */
	public static final int BLACK_START_BONUS = 20;
	/** Story flag of the Bind ending; Sever uses the Director's {@link InfectionRules#SEVER_FLAG}. */
	public static final String BIND_FLAG = "ashfall:bind";

	@FunctionalInterface
	public interface Chosen {
		void onChosen(MinecraftServer server, StoryEnding ending, List<ServerPlayer> participants);
	}

	@FunctionalInterface
	public interface Reset {
		void onReset(MinecraftServer server);
	}

	/** Fired once an ending is recorded (after rewards). The finale listens to wind down; other mods may too. */
	public static final Event<Chosen> CHOSEN = EventFactory.createArrayBacked(Chosen.class, listeners -> (server, ending, who) -> {
		for (Chosen l : listeners) {
			l.onChosen(server, ending, who);
		}
	});

	/** Fired by {@link #reset}: the finale rearms its controller here. */
	public static final Event<Reset> RESET = EventFactory.createArrayBacked(Reset.class, listeners -> server -> {
		for (Reset l : listeners) {
			l.onReset(server);
		}
	});

	private Ending() {
	}

	/**
	 * Opens the choice at the Heart. {@code participants} are the players who fought the finale; the vote also takes in
	 * every online member of their OPAC parties, and falls back to every online player when the list is empty.
	 * Does nothing when the world already has an ending, when a vote is running, or with {@code ashfall:objective}
	 * off (the pure sandbox prompts no one; an op may still pick with {@code /ashfall ending set}).
	 */
	public static void beginChoice(ServerLevel level, Collection<ServerPlayer> participants) {
		MinecraftServer server = level.getServer();
		if (StoryState.get(server).ending().chosen()) {
			AshfallArms.LOGGER.info("The Heart fell again, but this world already has the {} ending", StoryState.get(server).ending().id());
			return;
		}
		if (EndingVote.active()) {
			return;
		}
		if (!StoryRules.objective(server)) {
			AshfallArms.LOGGER.info("The Heart fell with ashfall:objective off: no ending vote");
			return;
		}
		EndingVote.start(server, voters(server, participants));
	}

	/**
	 * Reopens the choice for a world whose Heart fell but which has no ending yet. Why: the vote lives only in memory
	 * (it is cancelled on server stop), so a save-and-quit, a crash or a restart inside its window left the world
	 * finished with no ending, and in singleplayer without cheats nothing could reopen it ({@code /ashfall ending begin}
	 * needs an op). Called when a player joins a world whose finale is COMPLETE.
	 * <p>
	 * Opens only for one of the players who fought the finale ({@code fought}; anyone when it is empty), never over a
	 * running vote, an ending, or {@code ashfall:objective} off. Every fighter stays a voter, online or not, so a co-op
	 * partner who logs in later can still vote. @return true if a vote opened
	 */
	public static boolean resumeChoice(MinecraftServer server, Collection<UUID> fought, ServerPlayer joining) {
		if (!fought.isEmpty() && !fought.contains(joining.getUUID())) {
			return false;
		}
		if (StoryState.get(server).ending().chosen() || EndingVote.active() || !StoryRules.objective(server)) {
			return false;
		}
		List<ServerPlayer> online = new ArrayList<>();
		for (UUID id : fought) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				online.add(p);
			}
		}
		if (online.isEmpty()) {
			online.add(joining);
		}
		Set<UUID> voters = new LinkedHashSet<>(fought);
		voters.addAll(voters(server, online));
		AshfallArms.LOGGER.info("The Heart fell but the world has no ending (the vote was interrupted): reopening it for {} voter(s)",
				voters.size());
		EndingVote.start(server, voters);
		return true;
	}

	/** The participants plus the online members of their parties; every online player when the list is empty. */
	public static Set<UUID> voters(MinecraftServer server, Collection<ServerPlayer> participants) {
		Set<UUID> out = new LinkedHashSet<>();
		Set<UUID> parties = new LinkedHashSet<>();
		for (ServerPlayer p : participants) {
			out.add(p.getUUID());
			UUID party = Claims.partyId(server, p.getUUID());
			if (party != null) {
				parties.add(party);
			}
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (participants.isEmpty() || (!parties.isEmpty() && parties.contains(Claims.partyId(server, p.getUUID())))) {
				out.add(p.getUUID());
			}
		}
		return out;
	}

	/**
	 * Records {@code ending} for the world and rewards {@code participants} (the online voters). Also the op override
	 * ({@code /ashfall ending set}), so it works whether or not a vote ran.
	 */
	public static void apply(MinecraftServer server, StoryEnding ending, Collection<ServerPlayer> participants) {
		if (!ending.chosen()) {
			return;
		}
		EndingVote.cancel();
		List<ServerPlayer> who = List.copyOf(participants);
		EndingProfile profile = EndingProfiles.get(ending);
		FieldReport.Stats stats = FieldReport.collect(server, who);
		List<UUID> ids = new ArrayList<>();
		who.forEach(p -> ids.add(p.getUUID()));
		StoryState state = StoryState.get(server);
		state.choose(ending, StoryEffects.today(server), server.overworld().getGameTime(), ids, stats);

		StoryFlags flags = StoryFlags.get(server);
		flags.set(InfectionRules.SEVER_FLAG, profile.antibioticsCure());
		flags.set(BIND_FLAG, ending == StoryEnding.BIND);
		profile.flags().forEach(f -> flags.set(f, true));

		GridState grid = GridState.get(server);
		grid.setBlackStartBonus(Math.max(grid.blackStartBonus(), BLACK_START_BONUS));
		Havens.rebuild(server);

		boolean announce = StoryRules.objective(server);
		for (ServerPlayer player : who) {
			RelightTriggers.ENDING.trigger(player, ending);
			give(player, FieldReport.book(stats, ending, player));
			give(player, Epilogue.book(player, ending));
			for (Identifier id : profile.rewards()) {
				reward(id).ifPresent(stack -> give(player, stack));
			}
			if (announce) {
				title(player, Component.translatable("title.ashfall.ending." + ending.id()),
						Component.translatable("subtitle.ashfall.ending." + ending.id()));
			}
			player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
		}
		if (announce) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.sendSystemMessage(Component.translatable("message.ashfall.ending.chosen." + ending.id()).withStyle(
						ending == StoryEnding.BIND ? ChatFormatting.AQUA : ChatFormatting.LIGHT_PURPLE));
			}
		}
		AshfallArms.LOGGER.info("Ending chosen: {} by {} player(s) on day {}", ending.id(), who.size(), state.endingDay());
		CHOSEN.invoker().onChosen(server, ending, who);
	}

	/** The replay reset. @return whether the world had an ending */
	public static boolean reset(MinecraftServer server) {
		EndingVote.cancel();
		StoryState state = StoryState.get(server);
		EndingProfile old = EndingProfiles.get(state.ending());
		boolean had = state.reset();
		StoryFlags flags = StoryFlags.get(server);
		flags.set(InfectionRules.SEVER_FLAG, false);
		flags.set(BIND_FLAG, false);
		old.flags().forEach(f -> flags.set(f, false));
		GridState.get(server).setBlackStartBonus(0);
		Havens.rebuild(server);
		RESET.invoker().onReset(server);
		AshfallArms.LOGGER.info("Story reset (had an ending: {})", had);
		return had;
	}

	/**
	 * One reward stack: a Plasma Blade comes charged with a fresh crystal at its datapack integrity (the Resonant blade's
	 * 2400), anything else as a single item. Unknown ids are skipped with a warning.
	 */
	static java.util.Optional<ItemStack> reward(Identifier id) {
		java.util.Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
		if (item.isEmpty()) {
			AshfallArms.LOGGER.warn("Ending reward {} is not a registered item", id);
			return java.util.Optional.empty();
		}
		ItemStack stack = ArmsItems.charged(item.get());
		if (item.get() instanceof PlasmaBladeItem) {
			stack.set(ArmsComponents.BLADE_CRYSTAL, BladeCrystal.fresh(BladeStatsManager.get(item.get()).crystalIntegrity()));
			PlasmaBladeItem.refreshLit(stack);
		}
		return java.util.Optional.of(stack);
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.spawnAtLocation(player.level(), stack);
		}
	}

	static void title(ServerPlayer player, Component title, Component subtitle) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 100, 30));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}
}
