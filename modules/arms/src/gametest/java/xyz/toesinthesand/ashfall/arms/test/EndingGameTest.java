package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import team.reborn.energy.api.base.SimpleEnergyItem;
import xyz.toesinthesand.ashfall.arms.blade.BladeStatsManager;
import xyz.toesinthesand.ashfall.arms.blade.PlasmaBladeItem;
import xyz.toesinthesand.ashfall.arms.charger.ArmsBlocks;
import xyz.toesinthesand.ashfall.arms.grid.GridPower;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.story.Ending;
import xyz.toesinthesand.ashfall.arms.story.EndingProfile;
import xyz.toesinthesand.ashfall.arms.story.EndingProfiles;
import xyz.toesinthesand.ashfall.arms.story.EndingVote;
import xyz.toesinthesand.ashfall.arms.story.Epilogue;
import xyz.toesinthesand.ashfall.arms.story.FieldReport;
import xyz.toesinthesand.ashfall.arms.story.RelayLog;
import xyz.toesinthesand.ashfall.arms.story.RelayLogs;
import xyz.toesinthesand.ashfall.arms.story.StoryEffects;
import xyz.toesinthesand.ashfall.arms.story.StoryEnding;
import xyz.toesinthesand.ashfall.arms.story.StoryRules;
import xyz.toesinthesand.ashfall.arms.story.StoryState;
import xyz.toesinthesand.ashfall.arms.story.SurvivorStats;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.director.boss.BossLair;
import xyz.toesinthesand.ashfall.director.boss.BossLairs;
import xyz.toesinthesand.ashfall.director.horde.HordeHooks;
import xyz.toesinthesand.ashfall.director.horde.HordeNights;
import xyz.toesinthesand.ashfall.director.horde.HordeSchedule;
import xyz.toesinthesand.ashfall.director.infection.Infection;
import xyz.toesinthesand.ashfall.director.infection.InfectionRules;
import xyz.toesinthesand.ashfall.director.spawn.SpawnDirector;
import xyz.toesinthesand.ashfall.director.story.StoryCommand;
import xyz.toesinthesand.ashfall.director.story.StoryFlags;
import xyz.toesinthesand.ashfall.director.story.StoryHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The ending (M9.5) on a real server with the shipped profiles: the profiles load, Sever and Bind change what their
 * files say (spawning, free relays, the Antibiotics cure, horde growth up to the hard cap, the Resonant blade,
 * attachment unlocks), the vote reaches an ending for a party and for a solo player, the books assemble, and
 * {@code /ashfall story reset} undoes it all.
 * <p>
 * The ending is world-wide state, so every test that sets one does all of its work in its first tick and resets in a
 * {@code finally}: no other test can run in between and see a world with an ending.
 */
public class EndingGameTest {
	private static final Identifier RELIT = Identifier.fromNamespaceAndPath("ashfall", "relight/asheville_relit");

	@GameTest
	public void profilesAndContentLoad(GameTestHelper helper) {
		helper.assertTrue(EndingProfiles.loaded(StoryEnding.SEVER) && EndingProfiles.loaded(StoryEnding.BIND), "ending profiles did not load");
		EndingProfile sever = EndingProfiles.get(StoryEnding.SEVER);
		helper.assertTrue(sever.antibioticsCure() && sever.freeRelays() && Math.abs(sever.spawnMultiplier() - 0.4f) < 1e-4
				&& sever.hordeInterval() > 1, "sever.json does not say what the design says: " + sever);
		EndingProfile bind = EndingProfiles.get(StoryEnding.BIND);
		helper.assertTrue(bind.hordeGrowth().isPresent() && bind.unlockAttachments() && bind.bunkerRadiusBonus() > 0
				&& bind.rewards().contains(Identifier.fromNamespaceAndPath("ashfall", "resonant_plasma_blade")), "bind.json: " + bind);
		helper.assertTrue(!sever.closing().pages().isEmpty() && !bind.closing().pages().isEmpty(), "an ending has no closing pages");
		helper.assertTrue(BladeStatsManager.loaded(ArmsItems.RESONANT_PLASMA_BLADE), "resonant blade stats did not load");
		helper.assertValueEqual(BladeStatsManager.get(ArmsItems.RESONANT_PLASMA_BLADE).crystalIntegrity(), PlasmaBladeItem.RESONANT_CRYSTAL_INTEGRITY,
				"resonant crystal integrity in JSON and code");
		BossLair ascendant = BossLairs.get(Identifier.fromNamespaceAndPath("ashfall", "ascendant"));
		helper.assertTrue(ascendant != null && ascendant.requiresFlag().orElse("").equals(Ending.BIND_FLAG)
				&& ascendant.respawn().map(r -> r.afterEndingDays() == 10).orElse(false), "Ascendant lair missing or not Bind-gated");
		helper.assertTrue(helper.getLevel().getServer().getAdvancements().get(RELIT) != null, "asheville_relit did not load");
		helper.succeed();
	}

	@GameTest
	public void severProfileAppliesAndResetUndoesIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		int bonusBefore = GridState.get(server).blackStartBonus();
		BlockPos relay = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos plain = helper.absolutePos(new BlockPos(3, 1, 1));
		helper.setBlock(new BlockPos(1, 1, 1), ArmsBlocks.POWER_RELAY);
		helper.setBlock(new BlockPos(3, 1, 1), ArmsBlocks.FIELD_CHARGER);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().clearContent();
		try {
			fresh(helper, server);
			int capBefore = SpawnDirector.ambientCap(level);
			int intervalBefore = HordeSchedule.get(server).everyNights();
			helper.assertFalse(GridPower.powered(level, relay), "a relay outside any district has no free power before the ending");

			Ending.apply(server, StoryEnding.SEVER, List.of(player));

			helper.assertTrue(StoryState.get(server).ending() == StoryEnding.SEVER, "ending not recorded");
			helper.assertTrue(Math.abs(StoryHooks.spawnMultiplier(server) - 0.4) < 1e-4, "spawn multiplier " + StoryHooks.spawnMultiplier(server));
			helper.assertValueEqual(SpawnDirector.ambientCap(level), xyz.toesinthesand.ashfall.director.spawn.SpawnRules.scaleBySpawnMultiplier(capBefore, 0.4), "ambient cap after Sever");
			helper.assertValueEqual(HordeSchedule.get(server).everyNights(), intervalBefore * EndingProfiles.get(StoryEnding.SEVER).hordeInterval(),
					"horde nights become rare Aftershocks");
			helper.assertTrue(GridPower.powered(level, relay), "Sever: every Power Relay charges for free");
			helper.assertFalse(GridPower.powered(level, plain), "Sever frees relays, not Field Chargers outside a district");
			helper.assertTrue(InfectionRules.severEnding(server), "Sever sets the Antibiotics-cure flag");
			Infection.setStage(player, 4);
			Infection.treatWithAntibiotics(player);
			helper.assertFalse(Infection.isInfected(player), "after Sever, Antibiotics cure stage 4");
			helper.assertValueEqual(GridState.get(server).blackStartBonus(), Ending.BLACK_START_BONUS, "the ending leaves the grid at 100 %");
			helper.assertTrue(books(player).contains(FieldReport.TITLE) && books(player).contains(Epilogue.TITLE),
					"participant books: " + books(player));
			AdvancementHolder relit = server.getAdvancements().get(RELIT);
			helper.assertTrue(relit != null && player.getAdvancements().getOrStartProgress(relit).isDone(), "Asheville Relit not granted");
			helper.assertFalse(StoryEffects.unlockAttachments(server), "Sever does not unlock attachments");
			helper.assertValueEqual(HordeHooks.storyTier(server), 0, "Sever has no horde growth");

			StoryCommand.reset(server);

			helper.assertTrue(StoryState.get(server).ending() == StoryEnding.NONE, "reset keeps the ending");
			helper.assertFalse(InfectionRules.severEnding(server), "reset keeps the Sever flag");
			helper.assertFalse(GridPower.powered(level, relay), "reset keeps free relays");
			helper.assertValueEqual(GridState.get(server).blackStartBonus(), 0, "reset keeps the Black Start bonus");
			helper.assertValueEqual(SpawnDirector.ambientCap(level), capBefore, "reset keeps the spawn multiplier");
			helper.assertValueEqual(HordeSchedule.get(server).everyNights(), intervalBefore, "reset keeps the horde interval");
		} finally {
			Ending.reset(server);
			GridState.get(server).setBlackStartBonus(bonusBefore);
			server.getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void bindProfileGrowsHordesUpToTheCap(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		int bonusBefore = GridState.get(server).blackStartBonus();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().clearContent();
		try {
			fresh(helper, server);
			Ending.apply(server, StoryEnding.BIND, List.of(player));
			EndingProfile.HordeGrowth growth = EndingProfiles.get(StoryEnding.BIND).hordeGrowth().orElseThrow();

			helper.assertTrue(StoryFlags.get(server).has(Ending.BIND_FLAG), "Bind sets its flag (the Ascendant's gate)");
			helper.assertFalse(InfectionRules.severEnding(server), "Bind does not cure");
			helper.assertValueEqual(HordeHooks.storyTier(server), growth.tierStart(), "Bind's tier floor on the ending day");
			helper.assertTrue(StoryEffects.unlockAttachments(server), "Bind unlocks the Overcharge mods");
			helper.assertValueEqual(StoryHooks.bunkerRadiusBonus(server), EndingProfiles.get(StoryEnding.BIND).bunkerRadiusBonus(),
					"Bind powers Bunker Cores");
			helper.assertFalse(StoryEffects.freeRelays(server), "Bind does not free the relays");

			ItemStack blade = find(player, ArmsItems.RESONANT_PLASMA_BLADE);
			helper.assertTrue(!blade.isEmpty(), "no Resonant Plasma Blade for the participant");
			helper.assertValueEqual(blade.get(ArmsComponents.BLADE_CRYSTAL).max(), PlasmaBladeItem.RESONANT_CRYSTAL_INTEGRITY, "resonant crystal");
			helper.assertValueEqual(SimpleEnergyItem.getStoredEnergyUnchecked(blade), PlasmaBladeItem.CAPACITY, "the blade comes charged");

			// A year later the escalation must sit exactly at the hard cap: tier VI and the size cap, nothing more.
			StoryState state = StoryState.get(server);
			state.choose(StoryEnding.BIND, StoryEffects.today(server) - 365, state.endingTick(), state.participants(), state.report());
			helper.assertValueEqual(HordeHooks.storyTier(server), HordeHooks.STORY_TIER_HARD_CAP, "Bind tier after a year");
			helper.assertTrue(HordeHooks.waveSizeMultiplier(server) <= growth.sizeCap() + 1e-9
					&& HordeHooks.waveSizeMultiplier(server) <= HordeHooks.WAVE_SIZE_HARD_CAP, "wave size above the cap: " + HordeHooks.waveSizeMultiplier(server));
			helper.assertTrue(HordeNights.tier(server) <= HordeHooks.STORY_TIER_HARD_CAP || HordeNights.tier(server)
					== HordeSchedule.get(server).dayTier(HordeSchedule.day(server.overworld().getOverworldClockTime())), "horde tier above VI");
			for (HordeSchedule.Tier row : HordeSchedule.get(server).tiers()) {
				for (HordeSchedule.Wave wave : row.waves()) {
					helper.assertTrue(wave.size() <= 100, "a grown wave broke the 100 limit");
				}
			}
		} finally {
			Ending.reset(server);
			GridState.get(server).setBlackStartBonus(bonusBefore);
			server.getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void voteReachesAnEndingForAPartyAndASoloPlayer(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		int bonusBefore = GridState.get(server).blackStartBonus();
		ServerPlayer a = helper.makeMockServerPlayerInLevel();
		ServerPlayer b = helper.makeMockServerPlayerInLevel();
		boolean objective = server.overworld().getGameRules().get(StoryRules.OBJECTIVE);
		try {
			fresh(helper, server);
			// Pure sandbox: the Heart falling prompts nobody.
			server.overworld().getGameRules().set(StoryRules.OBJECTIVE, false, server);
			Ending.beginChoice(helper.getLevel(), List.of(a, b));
			helper.assertFalse(EndingVote.active(), "a vote opened with ashfall:objective off");
			server.overworld().getGameRules().set(StoryRules.OBJECTIVE, true, server);

			Ending.beginChoice(helper.getLevel(), List.of(a, b));
			helper.assertTrue(EndingVote.active(), "beginChoice opened no vote");
			helper.assertTrue(EndingVote.vote(a, StoryEnding.BIND) == EndingVote.Result.RECORDED, "first vote");
			helper.assertTrue(EndingVote.active() && !StoryState.get(server).ending().chosen(), "one of two votes decided the ending");
			helper.assertTrue(EndingVote.vote(b, StoryEnding.BIND) == EndingVote.Result.RECORDED, "second vote");
			helper.assertTrue(StoryState.get(server).ending() == StoryEnding.BIND, "two Bind votes of two did not bind");
			helper.assertFalse(EndingVote.active(), "the vote stayed open");
			Ending.beginChoice(helper.getLevel(), List.of(a, b));
			helper.assertFalse(EndingVote.active(), "a second vote opened in a world with an ending");
			Ending.reset(server);

			// Solo: a click asks for confirmation, and only the confirmation counts.
			server.getPlayerList().remove(b);
			EndingVote.start(server, Set.of(a.getUUID()));
			helper.assertTrue(EndingVote.vote(a, StoryEnding.SEVER) == EndingVote.Result.CONFIRM_NEEDED, "solo click should ask to confirm");
			helper.assertFalse(StoryState.get(server).ending().chosen(), "an unconfirmed solo click decided the ending");
			helper.assertTrue(EndingVote.vote(b, StoryEnding.BIND) == EndingVote.Result.NOT_PARTICIPANT, "an outsider voted");
			helper.assertTrue(EndingVote.confirm(a) == EndingVote.Result.RECORDED, "confirm");
			helper.assertTrue(StoryState.get(server).ending() == StoryEnding.SEVER, "solo confirm did not sever");
		} finally {
			EndingVote.cancel();
			Ending.reset(server);
			server.overworld().getGameRules().set(StoryRules.OBJECTIVE, objective, server);
			GridState.get(server).setBlackStartBonus(bonusBefore);
			server.getPlayerList().remove(a);
			server.getPlayerList().remove(b);
		}
		helper.succeed();
	}

	/**
	 * A vote cut short by a quit (it is cancelled on server stop and never saved) reopens when a fighter of the finale
	 * joins again, and only then: not for an outsider, not over a running vote, not once the world has an ending.
	 */
	@GameTest
	public void interruptedVoteReopensForAReturningFighter(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		int bonusBefore = GridState.get(server).blackStartBonus();
		ServerPlayer fighter = helper.makeMockServerPlayerInLevel();
		ServerPlayer outsider = helper.makeMockServerPlayerInLevel();
		boolean objective = server.overworld().getGameRules().get(StoryRules.OBJECTIVE);
		try {
			fresh(helper, server);
			server.overworld().getGameRules().set(StoryRules.OBJECTIVE, true, server);
			List<java.util.UUID> fought = List.of(fighter.getUUID());

			helper.assertFalse(Ending.resumeChoice(server, fought, outsider), "an outsider's join reopened the vote");
			helper.assertFalse(EndingVote.active(), "vote active after an outsider joined");
			helper.assertTrue(Ending.resumeChoice(server, fought, fighter), "a returning fighter did not reopen the vote");
			helper.assertTrue(EndingVote.active(), "no vote after the fighter joined");
			helper.assertFalse(Ending.resumeChoice(server, fought, fighter), "a second join opened a second vote");
			helper.assertTrue(EndingVote.vote(outsider, StoryEnding.BIND) == EndingVote.Result.NOT_PARTICIPANT,
					"an outsider of the finale (no party) got a vote");

			EndingVote.cancel();
			Ending.apply(server, StoryEnding.SEVER, List.of(fighter));
			helper.assertFalse(Ending.resumeChoice(server, fought, fighter), "the vote reopened in a world with an ending");
		} finally {
			EndingVote.cancel();
			Ending.reset(server);
			server.overworld().getGameRules().set(StoryRules.OBJECTIVE, objective, server);
			GridState.get(server).setBlackStartBonus(bonusBefore);
			server.getPlayerList().remove(fighter);
			server.getPlayerList().remove(outsider);
		}
		helper.succeed();
	}

	@GameTest
	public void epilogueAssemblesFromHeardLogs(GameTestHelper helper) {
		List<RelayLog> pool = new ArrayList<>();
		for (int n = 1; n <= 5; n++) {
			pool.add(new RelayLog(n, "Log " + n, "Author " + n, List.of("Text of log " + n + ".")));
		}
		pool.add(new RelayLog(6, "Long one", "", List.of("word ".repeat(80))));
		EndingProfile.Closing closing = new EndingProfile.Closing("The end", List.of("Closing one.", "Closing two."));
		List<Component> pages = Epilogue.pages(pool, Set.of(1, 3, 6), StoryEnding.SEVER, closing);
		// cover + 3 heard logs + the "still out there" note + 2 closing pages
		helper.assertValueEqual(pages.size(), 7, "epilogue page count");
		helper.assertTrue(pages.get(1).getString().contains("#1 Log 1") && pages.get(2).getString().contains("#3 Log 3"),
				"logs out of order: " + pages.get(1).getString() + " / " + pages.get(2).getString());
		helper.assertTrue(pages.get(3).getString().endsWith("…") && pages.get(3).getString().length() < 300, "long log not cut to an excerpt");
		helper.assertTrue(pages.get(5).getString().startsWith("The end") && pages.get(6).getString().equals("Closing two."), "closing pages");

		// Every shipped log heard: one page each, then the closing, inside the 100-page book limit.
		List<Integer> all = RelayLogs.pool().stream().map(RelayLog::number).toList();
		EndingProfile.Closing bind = EndingProfiles.get(StoryEnding.BIND).closing();
		List<Component> full = Epilogue.pages(RelayLogs.pool(), all, StoryEnding.BIND, bind);
		helper.assertValueEqual(full.size(), 1 + RelayLogs.pool().size() + 1 + bind.pages().size(), "full epilogue page count");
		helper.assertTrue(full.size() <= Epilogue.MAX_PAGES, "epilogue over the book limit");

		List<Component> report = FieldReport.pages(new FieldReport.Stats(42, 4, 4, 5, 18, 30, 2, 9), StoryEnding.BIND, "Tester", 11,
				new SurvivorStats(3, 1));
		helper.assertValueEqual(report.size(), 3, "field report page count");
		helper.assertTrue(report.get(2).getString().startsWith("Tester"), "the report's last page is the reader's");
		helper.succeed();
	}

	/** Every test that sets an ending starts from none (a previous run's leftovers are reset, not trusted). */
	private static void fresh(GameTestHelper helper, MinecraftServer server) {
		EndingVote.cancel();
		if (StoryState.get(server).ending().chosen()) {
			Ending.reset(server);
		}
		helper.assertTrue(StoryState.get(server).ending() == StoryEnding.NONE, "could not start without an ending");
	}

	private static List<String> books(ServerPlayer player) {
		List<String> out = new ArrayList<>();
		for (ItemStack stack : player.getInventory()) {
			WrittenBookContent content = stack.is(Items.WRITTEN_BOOK) ? stack.get(DataComponents.WRITTEN_BOOK_CONTENT) : null;
			if (content != null) {
				out.add(content.title().raw());
			}
		}
		return out;
	}

	private static ItemStack find(ServerPlayer player, net.minecraft.world.item.Item item) {
		for (ItemStack stack : player.getInventory()) {
			if (stack.is(item)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}
}
