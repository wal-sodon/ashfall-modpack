package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import xyz.toesinthesand.ashfall.arms.grid.DistrictRecord;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;
import xyz.toesinthesand.ashfall.arms.grid.GridDistricts;
import xyz.toesinthesand.ashfall.arms.grid.GridSettings;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.grid.SiegeSettings;
import xyz.toesinthesand.ashfall.arms.mission.MissionBriefing;
import xyz.toesinthesand.ashfall.arms.mission.MissionCommand;
import xyz.toesinthesand.ashfall.arms.mission.MissionFacts;
import xyz.toesinthesand.ashfall.arms.mission.MissionPos;
import xyz.toesinthesand.ashfall.arms.mission.MissionProgress;
import xyz.toesinthesand.ashfall.arms.mission.MissionRadio;
import xyz.toesinthesand.ashfall.arms.mission.MissionResolver;
import xyz.toesinthesand.ashfall.arms.mission.MissionSyncPayload;
import xyz.toesinthesand.ashfall.arms.mission.MissionTracker;
import xyz.toesinthesand.ashfall.arms.mission.MissionWorld;
import xyz.toesinthesand.ashfall.arms.mission.Objective;
import xyz.toesinthesand.ashfall.arms.mission.ObjectiveArg;
import xyz.toesinthesand.ashfall.arms.story.RadioProgress;
import xyz.toesinthesand.ashfall.arms.story.StoryRules;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreRecord;
import xyz.toesinthesand.ashfall.director.haven.HavenBlocks;
import xyz.toesinthesand.ashfall.director.haven.HavenState;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The mission layer on a real server: the first-join briefing (once, never on a rejoin), the objective following a
 * real {@link GridState} change, {@code ashfall:objective} silencing the prompts but not {@code /ashfall mission}, and
 * the Bunker Core reaching the client payload and the radio's way home.
 * <p>
 * Tests that flip the shared game rule set and restore it within one synchronous block, so the other tests running on
 * the server never see it off. The grid test uses its own test district, since the real districts are shared.
 */
public class MissionGameTest {
	private static boolean objectiveRule(MinecraftServer server) {
		return server.overworld().getGameRules().get(StoryRules.OBJECTIVE);
	}

	private static void setObjectiveRule(MinecraftServer server, boolean on) {
		server.overworld().getGameRules().set(StoryRules.OBJECTIVE, on, server);
	}

	private static int briefingBooks(ServerPlayer player) {
		int n = 0;
		for (ItemStack stack : player.getInventory()) {
			if (MissionBriefing.isBriefing(stack)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static ServerPlayer freshPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().clearContent();
		MissionProgress.set(player, MissionProgress.EMPTY);
		return player;
	}

	@GameTest
	public void firstJoinGivesTheBriefingBookOnce(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		boolean before = objectiveRule(server);
		try {
			setObjectiveRule(server, true);
			ServerPlayer player = freshPlayer(helper);
			helper.assertTrue(MissionBriefing.maybeBrief(player), "a new player was not briefed");
			helper.assertTrue(MissionProgress.get(player).briefed(), "the briefing was not recorded");
			helper.assertValueEqual(briefingBooks(player), 1, "briefing books after the first join");
			ItemStack book = findBriefing(player);
			WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
			helper.assertTrue(content != null && content.pages().size() >= 6, "the briefing book has too few pages");

			// A rejoin: the attachment persists, so the next join's delivery is a no-op.
			helper.assertFalse(MissionBriefing.maybeBrief(player), "the briefing was delivered twice");
			helper.assertValueEqual(briefingBooks(player), 1, "briefing books after a rejoin");

			// The progress survives death like the radio's: the attachment copies to the respawned player.
			helper.assertTrue(MissionProgress.TYPE.copyOnDeath(), "mission progress must copy on death");
		} finally {
			setObjectiveRule(server, before);
		}
		helper.succeed();
	}

	private static ItemStack findBriefing(ServerPlayer player) {
		for (ItemStack stack : player.getInventory()) {
			if (MissionBriefing.isBriefing(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	@GameTest
	public void objectiveFollowsTheGrid(GameTestHelper helper) {
		String id = "mission_test_district";
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(7, 7, 7)));
		GridSettings d = GridSettings.DEFAULT;
		GridSettings settings = new GridSettings(2, 2, 240L, 10, 4, 1, 32, d.pressure(), d.core(), 150, 23_000, 0, 6, d.reclaimed(), Map.of());
		SiegeSettings siege = new SiegeSettings(List.of(new SiegeSettings.DayTier(1, 1)), 1, 2, 3, 90, 0,
				List.of(new SiegeSettings.Tier(1, 1, 1, Map.of(Identifier.fromNamespaceAndPath("ashfall", "walker"), 1))));
		GridDistricts.registerTest(new GridDistricts.TestDistrict(id, box, Set.of(), settings, siege, 1, true));
		GridState grid = GridState.get(helper.getLevel().getServer());
		try {
			grid.update(helper.getLevel(), id, r -> {
				r.reset();
				r.corePos = helper.absolutePos(new BlockPos(3, 1, 3)).asLong();
			});
			ServerPlayer player = freshPlayer(helper);
			RadioProgress.set(player, RadioProgress.get(player).withRadio());
			MissionProgress.set(player, MissionProgress.get(player).withArmed());

			helper.assertValueEqual(current(player, id).key(), "district.couplers", "the optional haven never replaces the district");

			grid.update(helper.getLevel(), id, r -> r.couplers = 1);
			Objective couplers = current(player, id);
			helper.assertValueEqual(couplers.key(), "district.couplers", "one coupler in");
			helper.assertValueEqual(couplers.args(), List.of(ObjectiveArg.district(id), ObjectiveArg.of(1), ObjectiveArg.of(2)), "coupler counts");
			BlockPos core = helper.absolutePos(new BlockPos(3, 1, 3));
			helper.assertValueEqual(couplers.target(), Optional.of(new MissionPos(core.getX(), core.getZ())), "points at the registered core");

			grid.update(helper.getLevel(), id, r -> {
				r.couplers = 2;
				r.state = DistrictState.REPAIRED;
			});
			helper.assertValueEqual(current(player, id).key(), "district.cells", "repaired: load cells");

			grid.update(helper.getLevel(), id, r -> {
				r.cells = 2;
				r.holdTicks = 100;
			});
			Objective hold = current(player, id);
			helper.assertValueEqual(hold.key(), "district.hold", "cells in: hold");
			helper.assertValueEqual(hold.args().get(1), ObjectiveArg.of(50), "hold percent from the real hold ticks");

			grid.update(helper.getLevel(), id, r -> r.state = DistrictState.SIEGE);
			helper.assertValueEqual(current(player, id).key(), "district.siege", "siege");

			// The synced payload carries the same objective the resolver picks.
			MissionSyncPayload payload = MissionTracker.payloadFor(player);
			helper.assertTrue(payload.key().length() > 0 && payload.step().length() > 0, "empty payload");
		} finally {
			grid.update(helper.getLevel(), id, r -> {
				r.reset();
				r.corePos = DistrictRecord.NO_CORE;
			});
			GridDistricts.unregisterTest(id);
		}
		helper.succeed();
	}

	/**
	 * The resolver's pick over just the test district, with the world-wide finale and ending set aside: other
	 * gametests run the Black Start and the ending on the same server at the same time.
	 */
	private static Objective current(ServerPlayer player, String district) {
		MissionFacts f = MissionWorld.collect(player, List.of(district));
		MissionFacts calm = new MissionFacts(f.radio(), f.armed(), Optional.empty(), f.districts(), f.crystals(), f.crystalsNeeded(), f.bosses(),
				f.bossesTotal(), f.nearestBoss(), f.frame(), f.banks(), f.banksNeeded(), f.uplink(), MissionFacts.Finale.IDLE, false, 0, f.pylonsTotal(),
				f.gridPercent(), MissionFacts.NO_ENDING, f.safehouse(), f.hull());
		return MissionResolver.current(calm);
	}

	@GameTest
	public void objectiveRuleOffSilencesPromptsButNotTheCommand(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		boolean before = objectiveRule(server);
		try {
			setObjectiveRule(server, false);
			ServerPlayer player = freshPlayer(helper);
			helper.assertFalse(MissionBriefing.maybeBrief(player), "briefed with ashfall:objective off");
			helper.assertValueEqual(briefingBooks(player), 0, "a book was given with ashfall:objective off");
			helper.assertFalse(MissionProgress.get(player).briefed(), "the flag must stay unset so turning the rule on still briefs");
			helper.assertFalse(MissionTracker.payloadFor(player).prompts(), "the HUD line would still show");
			List<Component> lines = MissionCommand.checklistLines(player);
			helper.assertTrue(lines.size() >= 10, "/ashfall mission stopped working with the rule off: " + lines.size() + " lines");

			setObjectiveRule(server, true);
			helper.assertTrue(MissionTracker.payloadFor(player).prompts(), "prompts did not come back with the rule on");
		} finally {
			setObjectiveRule(server, before);
		}
		helper.succeed();
	}

	@GameTest
	public void bunkerCoreReachesThePayloadAndTheRadio(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = freshPlayer(helper);
		BlockPos rel = new BlockPos(2, 1, 2);
		BlockPos pos = helper.absolutePos(rel);
		helper.assertTrue(MissionTracker.payloadFor(player).bunker().isEmpty(), "a bunker before one was placed");
		helper.assertTrue(radioHomeKey(player).equals("radio.ashfall.home.safehouse"), "without a core home is the safehouse");

		// The record without its OPAC claim: a claim would reach into the neighbouring tests' structures.
		helper.setBlock(rel, HavenBlocks.BUNKER_CORE);
		HavenState.get(server).putCore(new BunkerCoreRecord(player.getUUID(), player.getUUID(), Level.OVERWORLD, pos, 0, List.of()));
		try {
			Optional<MissionPos> synced = MissionTracker.payloadFor(player).bunker();
			helper.assertValueEqual(synced, Optional.of(new MissionPos(pos.getX(), pos.getZ())), "bunker position in the payload");
			helper.assertValueEqual(radioHomeKey(player), "radio.ashfall.home.bunker", "the radio's way home");
		} finally {
			HavenState.get(server).removeCore(player.getUUID());
			helper.setBlock(rel, Blocks.AIR);
		}
		helper.succeed();
	}

	private static String radioHomeKey(ServerPlayer player) {
		Component line = MissionRadio.homeLine(player).orElseThrow();
		return line.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}
}
