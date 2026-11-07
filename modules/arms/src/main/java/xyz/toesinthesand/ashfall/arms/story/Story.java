package xyz.toesinthesand.ashfall.arms.story;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import xyz.toesinthesand.ashfall.arms.charger.ArmsBlocks;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;
import xyz.toesinthesand.ashfall.arms.grid.GridEvents;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.director.boss.BossLairs;
import xyz.toesinthesand.ashfall.director.infection.Infection;
import xyz.toesinthesand.ashfall.director.story.StoryHooks;

/**
 * Wires the story layer together: the Field Radio, Relay Logs on Power Relays, the "Relight" advancement
 * triggers, the Grid Map data sync and the radio flag that turns on the Grid HUD.
 * <p>
 * Grid progress is world-wide, so district and grid-percent criteria are fired for every online player when a
 * district changes and again for each player on join: whoever was offline when River Arts was reclaimed still
 * gets the advancement the next time they log in.
 */
public final class Story {
	/** The "Relight" tab's root: picking up the Field Radio. Having it turns on the Grid HUD. */
	public static final Identifier ROOT_ADVANCEMENT = Identifier.fromNamespaceAndPath("ashfall", "relight/root");
	/** Players within this many blocks of a dying story boss share the kill. */
	public static final int BOSS_CREDIT_RADIUS = 64;
	/** Ticks between checks of the root advancement for players whose HUD is still off. */
	private static final int RADIO_POLL_TICKS = 40;

	private Story() {
	}

	public static void init() {
		StoryItems.init();
		RelightTriggers.init();
		RelayDocumentFunction.register();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(RelayLogs.ID, new RelayLogs.Loader());
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(MapMarkers.ID, new MapMarkers.Loader());

		PayloadTypeRegistry.clientboundPlay().register(GridMapPayload.TYPE, GridMapPayload.CODEC);
		// Fires for each player on join and for everyone after /reload, so map edits in a datapack reach clients.
		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
			if (ServerPlayNetworking.canSend(player, GridMapPayload.TYPE)) {
				ServerPlayNetworking.send(player, GridMapPayload.current());
			}
		});

		UseBlockCallback.EVENT.register(Story::onUseBlock);
		GridEvents.DISTRICT_CHANGED.register(Story::onDistrictChanged);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> fireGrid(handler.player, server));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> onBossDeath(entity, source.getEntity()));
		ServerTickEvents.END_SERVER_TICK.register(Story::pollRadio);

		// The ending (M9.5): profiles, the vote, its effects on other systems, the stats it reports and the replay reset.
		StoryRules.init();
		SurvivorStats.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(EndingProfiles.ID, new EndingProfiles());
		StoryEffects.init();
		StoryHooks.onReset(Ending::reset);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> EndingCommand.register(dispatcher));
		ServerTickEvents.END_SERVER_TICK.register(EndingVote::tick);
		ServerTickEvents.END_SERVER_TICK.register(Story::announceAftershocks);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> EndingVote.cancel());
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				countDeath(player);
			}
		});
	}

	/**
	 * Counts a player death for the field report. Read after death but before respawn, while the infection attachment
	 * is still on the body, so a death while infected counts as one lost to the infection.
	 */
	public static void countDeath(ServerPlayer player) {
		boolean infected = Infection.isInfected(player);
		StoryStats.get(player.level().getServer()).countDeath(infected);
		SurvivorStats.set(player, SurvivorStats.get(player).withDeath(infected));
	}

	/** Ticks between checks for a new Aftershock pass: cheap enough to be prompt, rare enough to cost nothing. */
	private static final int AFTERSHOCK_POLL_TICKS = 400;

	/**
	 * Announces each new Aftershock pass once (endgame-design section 5): new crash sites fell on the ridge and in the
	 * wasteland. The sites themselves are placed lazily by salvage's site placer as players come near.
	 */
	private static void announceAftershocks(MinecraftServer server) {
		if (server.getTickCount() % AFTERSHOCK_POLL_TICKS != 0) {
			return;
		}
		StoryState state = StoryState.get(server);
		if (!state.ending().chosen()) {
			return;
		}
		int generations = StoryEffects.aftershocks(server).generations();
		if (generations <= state.aftershocksAnnounced()) {
			return;
		}
		state.setAftershocksAnnounced(generations);
		if (StoryRules.objective(server)) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.sendSystemMessage(Component.translatable("message.ashfall.aftershock").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/**
	 * Holding the Field Radio and using a Power Relay tunes into its log. Handled before the block's own use
	 * (which opens the charger menu) and on both sides, so the client does not open the menu either.
	 */
	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (player.isSpectator() || !player.getItemInHand(hand).is(StoryItems.FIELD_RADIO)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!level.getBlockState(pos).is(ArmsBlocks.POWER_RELAY)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			tuneRelay(serverPlayer, pos);
		}
		return InteractionResult.SUCCESS;
	}

	/** The relay half of the radio, public for gametests: tune in, tell the player, fire the log criteria. */
	public static RelayLogs.Tuned tuneRelay(ServerPlayer player, BlockPos pos) {
		RadioProgress.set(player, RadioProgress.get(player).withRadio());
		RelayLogs.Tuned tuned = RelayLogs.tune(player, pos);
		switch (tuned.result()) {
			case NEW -> {
				RelayLog log = tuned.log().orElseThrow();
				player.sendSystemMessage(Component.translatable("radio.ashfall.tuned_new", log.number(), log.title()).withStyle(ChatFormatting.AQUA));
				player.level().playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 0.8f);
			}
			case ALREADY_HEARD -> player.sendOverlayMessage(Component.translatable("radio.ashfall.tuned_old",
					tuned.log().orElseThrow().number()).withStyle(ChatFormatting.GRAY));
			case EMPTY -> player.sendOverlayMessage(Component.translatable("radio.ashfall.tuned_static").withStyle(ChatFormatting.DARK_GRAY));
		}
		RelightTriggers.RELAY_LOGS.trigger(player, RelayLogs.collected(RadioProgress.get(player)), RelayLogs.pool().size());
		return tuned;
	}

	/**
	 * A district changed: every online player gets the district criterion with the event's new state (the
	 * event is the authority on what just happened) and the grid-percent criterion from the grid state.
	 */
	public static void onDistrictChanged(ServerLevel level, String district, DistrictState from, DistrictState to) {
		int percent = GridState.get(level.getServer()).gridPercent();
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			RelightTriggers.DISTRICT.trigger(player, district, to);
			RelightTriggers.GRID_PERCENT.trigger(player, percent);
		}
	}

	/** Fires the district and grid-percent criteria for one player from the current grid state (on join). */
	public static void fireGrid(ServerPlayer player, MinecraftServer server) {
		GridState grid = GridState.get(server);
		for (String id : grid.districts()) {
			DistrictState state = grid.state(id);
			RelightTriggers.DISTRICT.trigger(player, id, state);
		}
		RelightTriggers.GRID_PERCENT.trigger(player, grid.gridPercent());
	}

	/** Credits a story boss kill to the killer and everyone nearby, so a party shares the advancement. */
	private static void onBossDeath(LivingEntity entity, net.minecraft.world.entity.Entity killer) {
		String lair = entity.getAttached(BossLairs.BOSS);
		Identifier lairId = lair == null ? null : Identifier.tryParse(lair);
		if (lairId == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (killer instanceof ServerPlayer player) {
			RelightTriggers.BOSS_DEFEATED.trigger(player, lairId);
		}
		double r2 = (double) BOSS_CREDIT_RADIUS * BOSS_CREDIT_RADIUS;
		for (ServerPlayer player : level.players()) {
			if (player != killer && player.distanceToSqr(entity) <= r2) {
				RelightTriggers.BOSS_DEFEATED.trigger(player, lairId);
			}
		}
	}

	/**
	 * Turns on the radio flag (and with it the Grid HUD) for players who earned the root advancement by
	 * picking the radio up. Polling a handful of players every two seconds is cheaper and simpler than hooking
	 * advancement awards, and it also catches {@code /advancement grant}.
	 */
	private static void pollRadio(MinecraftServer server) {
		if (server.getTickCount() % RADIO_POLL_TICKS != 0) {
			return;
		}
		AdvancementHolder root = server.getAdvancements().get(ROOT_ADVANCEMENT);
		if (root == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			RadioProgress progress = RadioProgress.get(player);
			if (!progress.radio() && player.getAdvancements().getOrStartProgress(root).isDone()) {
				RadioProgress.set(player, progress.withRadio());
			}
		}
	}
}
