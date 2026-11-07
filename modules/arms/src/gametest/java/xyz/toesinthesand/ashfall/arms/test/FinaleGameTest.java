package xyz.toesinthesand.ashfall.arms.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import xyz.toesinthesand.ashfall.arms.blade.BladeStatsManager;
import xyz.toesinthesand.ashfall.arms.blade.PlasmaBladeItem;
import xyz.toesinthesand.ashfall.arms.finale.ChoirEntity;
import xyz.toesinthesand.ashfall.arms.finale.FinaleActions;
import xyz.toesinthesand.ashfall.arms.finale.FinaleBlocks;
import xyz.toesinthesand.ashfall.arms.finale.FinaleController;
import xyz.toesinthesand.ashfall.arms.finale.FinaleInstance;
import xyz.toesinthesand.ashfall.arms.finale.FinaleLogic;
import xyz.toesinthesand.ashfall.arms.finale.FinaleRun;
import xyz.toesinthesand.ashfall.arms.finale.FinaleSettings;
import xyz.toesinthesand.ashfall.arms.finale.FinaleState;
import xyz.toesinthesand.ashfall.arms.finale.HullSite;
import xyz.toesinthesand.ashfall.arms.finale.RelayBreakerBlock;
import xyz.toesinthesand.ashfall.arms.finale.UplinkSocketBlock;
import xyz.toesinthesand.ashfall.arms.grid.GridBlocks;
import xyz.toesinthesand.ashfall.arms.finale.FinaleEffects;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.story.Ending;
import xyz.toesinthesand.ashfall.arms.story.EndingVote;
import xyz.toesinthesand.ashfall.arms.story.Epilogue;
import xyz.toesinthesand.ashfall.arms.story.FieldReport;
import xyz.toesinthesand.ashfall.arms.story.StoryEnding;
import xyz.toesinthesand.ashfall.arms.story.StoryRules;
import xyz.toesinthesand.ashfall.arms.story.StoryState;
import xyz.toesinthesand.ashfall.director.infection.InfectionRules;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import net.minecraft.resources.Identifier;
import xyz.toesinthesand.ashfall.salvage.site.CrashSiteRule;
import xyz.toesinthesand.ashfall.salvage.site.PlacedSite;
import xyz.toesinthesand.ashfall.salvage.site.SiteState;
import xyz.toesinthesand.ashfall.salvage.site.SiteTemplate;
import xyz.toesinthesand.ashfall.salvage.site.Sites;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The Black Start on a real server. Each test owns a socket in its own structure with its own run, tiny numbers (a
 * 2-second Surge, 1-second breaker holds, a 100-hp Uplink, no waves, no vents, no stimuli) and recording effects, so
 * nothing touches the world's run, the real Grid % or a parallel test. The test drives the controller with explicit
 * steps instead of waiting.
 */
public class FinaleGameTest {
	private static final BlockPos SOCKET = new BlockPos(4, 1, 4);

	/** Records what the real effects would have done. */
	private static final class Recorder implements FinaleInstance.Effects {
		final List<Integer> bands = new ArrayList<>();
		/** Bands an admin jump relit without a phase end. */
		final List<Integer> relit = new ArrayList<>();
		int bonus;
		int completed = -1;

		@Override
		public void phaseEnded(ServerLevel level, FinaleInstance instance, int band, int bonus) {
			bands.add(band);
			this.bonus = bonus;
		}

		@Override
		public void completed(ServerLevel level, FinaleInstance instance, Collection<ServerPlayer> participants) {
			completed = participants.size();
		}

		@Override
		public void bonusChanged(ServerLevel level, int bonus) {
			this.bonus = bonus;
		}

		@Override
		public void bandRelit(ServerLevel level, FinaleInstance instance, int band) {
			relit.add(band);
		}
	}

	private static FinaleSettings settings(boolean choir) {
		FinaleSettings d = FinaleSettings.DEFAULT;
		return new FinaleSettings(2,
				new FinaleSettings.Buffer(1, 60, 600, 240L),
				new FinaleSettings.Stimulus(600, 1, 1),
				new FinaleSettings.Waves(0, 0, 0, 0, 120, 32),
				new FinaleSettings.Breakers(1, 3.0, List.of(new Vec3i(-3, 0, 0), new Vec3i(3, 0, 0)), 0, 1.0),
				new FinaleSettings.Vents(0, 0, List.of(), List.of()),
				new FinaleSettings.Uplink(100, 40, 0.5, 1.5, 50, 0, 0),
				32, 1, 1,
				new FinaleSettings.Heart(List.of(new Vec3i(-2, 0, -2), new Vec3i(2, 0, -2), new Vec3i(-2, 0, 2), new Vec3i(2, 0, 2)),
						choir, new Vec3i(0, 0, -3), 14, 32, d.heart().attributes()),
				d.bands(), d.scale());
	}

	private record Setup(FinaleInstance instance, Recorder effects, BlockPos socket) {
		FinaleRun run() {
			return instance.run();
		}
	}

	private static Setup setup(GameTestHelper helper, boolean choir) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		helper.setBlock(SOCKET, FinaleBlocks.UPLINK_SOCKET);
		Recorder effects = new Recorder();
		FinaleSettings s = settings(choir);
		FinaleInstance instance = new FinaleInstance(new FinaleRun(), () -> s, () -> {
		}, effects);
		BlockPos abs = helper.absolutePos(SOCKET);
		FinaleController.registerTest(abs, instance);
		return new Setup(instance, effects, abs);
	}

	private static void cleanup(GameTestHelper helper, Setup setup) {
		FinaleController.abort(helper.getLevel(), setup.instance());
		FinaleController.unregisterTest(setup.socket());
	}

	/** A survival mock player (vanilla's helper is creative, which never uses up items). */
	private static ServerPlayer survivalPlayer(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.getAbilities().instabuild = false;
		player.getAbilities().invulnerable = false;
		return player;
	}

	private static void remove(ServerPlayer player) {
		player.level().getServer().getPlayerList().remove(player);
	}

	private static void standAt(ServerPlayer player, BlockPos abs, double dx, double dz) {
		player.snapTo(abs.getX() + 0.5 + dx, abs.getY(), abs.getZ() + 0.5 + dz, 0f, 0f);
	}

	private static void seat(GameTestHelper helper, Setup setup, ServerPlayer player) {
		ItemStack uplink = new ItemStack(FinaleBlocks.GRID_UPLINK);
		helper.assertValueEqual(FinaleActions.seat(helper.getLevel(), setup.socket(), player, uplink), FinaleLogic.Result.ACCEPTED,
				"seating the Uplink");
		helper.assertTrue(uplink.isEmpty(), "the Uplink is used up when seated");
	}

	private static void tick(GameTestHelper helper, Setup setup, long step) {
		FinaleController.tick(helper.getLevel(), setup.instance(), step);
	}

	@GameTest(maxTicks = 100)
	public void finaleRunsThroughAllThreePhases(GameTestHelper helper) {
		Setup setup = setup(helper, false);
		ServerPlayer player = survivalPlayer(helper, "finale-run");
		try {
			FinaleRun run = setup.run();
			standAt(player, setup.socket(), 1, 0);
			seat(helper, setup, player);
			helper.assertValueEqual(run.status, FinaleRun.Status.RUNNING, "running once seated");
			helper.assertBlockProperty(SOCKET, UplinkSocketBlock.POWER, 2);

			// Surge: 20 ticks of start charge, then a cell for the rest
			tick(helper, setup, 20);
			helper.assertValueEqual(run.phaseTicks, 20L, "the start charge powers the first second");
			ItemStack cell = ArmsItems.charged(ArmsItems.ENERGY_CELL);
			helper.assertValueEqual(FinaleActions.feedCell(helper.getLevel(), setup.socket(), player, cell), FinaleLogic.Result.ACCEPTED, "a cell");
			tick(helper, setup, 20);
			helper.assertValueEqual(run.phase, FinaleRun.Phase.FEEDBACK, "a 2-second Surge is over");
			helper.assertValueEqual(setup.effects().bands, List.of(0), "the first band lit");
			helper.assertValueEqual(setup.effects().bonus, 7, "80 -> 87");

			// Feedback: two breakers raised on the floor, each closed by standing at it
			helper.assertValueEqual(run.breakers.size(), 2, "two Relay Breakers");
			BlockPos a = BlockPos.of(run.breakers.get(0).pos), b = BlockPos.of(run.breakers.get(1).pos);
			helper.assertTrue(helper.getLevel().getBlockState(a).is(FinaleBlocks.RELAY_BREAKER), "breaker A placed at " + a);
			helper.assertTrue(helper.getLevel().getBlockState(a).getValue(RelayBreakerBlock.TRIPPED), "breaker A tripped");
			standAt(player, a, 1, 0);
			tick(helper, setup, 20);
			helper.assertTrue(run.breakers.get(0).closed, "a 1-second hold closes breaker A");
			helper.assertFalse(helper.getLevel().getBlockState(a).getValue(RelayBreakerBlock.TRIPPED), "breaker A shows closed");
			standAt(player, b, 0, 1);
			tick(helper, setup, 20);
			helper.assertValueEqual(run.phase, FinaleRun.Phase.HEART, "both breakers closed ends the Feedback");
			helper.assertValueEqual(setup.effects().bonus, 14, "87 -> 94");

			// Heart: four pylons raised; breaking them all ends the run
			helper.assertValueEqual(run.pylons.size(), 4, "four Heart pylons");
			for (long packed : run.pylons) {
				helper.assertTrue(helper.getLevel().getBlockState(BlockPos.of(packed)).is(FinaleBlocks.HEART_PYLON), "pylon placed");
				helper.getLevel().destroyBlock(BlockPos.of(packed), false);
			}
			standAt(player, setup.socket(), 1, 0);
			tick(helper, setup, 20);
			helper.assertValueEqual(run.status, FinaleRun.Status.COMPLETE, "every pylon broken ends the Black Start");
			helper.assertValueEqual(setup.effects().bands, List.of(0, 1, 2), "three bands lit, one per phase");
			helper.assertValueEqual(setup.effects().bonus, 20, "94 -> 100");
			helper.assertValueEqual(setup.effects().completed, 1, "the ending choice opens for the participant");
			helper.assertTrue(run.participants.contains(player.getUUID()), "the player is a participant");
			helper.succeed();
		} finally {
			remove(player);
			cleanup(helper, setup);
		}
	}

	/**
	 * The replay hand-back (black-start spec section 9, phase 7): the story reset's rearm leaves the spent Uplink in its
	 * socket in standby, the socket glows, and an empty hand powers it up into a fresh Surge without a second Uplink. A new
	 * Uplink brought to a socket in standby is kept in hand.
	 */
	@GameTest(maxTicks = 100)
	public void theReplayResetLeavesTheUplinkInItsSocket(GameTestHelper helper) {
		Setup setup = setup(helper, false);
		ServerPlayer player = survivalPlayer(helper, "finale-replay");
		try {
			FinaleRun run = setup.run();
			standAt(player, setup.socket(), 1, 0);
			seat(helper, setup, player);
			FinaleController.jumpTo(helper.getLevel(), setup.instance(), FinaleRun.Phase.HEART);
			run.status = FinaleRun.Status.COMPLETE;
			List<Long> pylons = List.copyOf(run.pylons);
			helper.assertValueEqual(pylons.size(), 4, "the Heart's pylons stood");
			helper.assertTrue(FinaleController.rearm(helper.getLevel(), setup.instance()), "the Uplink is kept");
			helper.assertValueEqual(run.status, FinaleRun.Status.IDLE, "rearmed to IDLE");
			helper.assertValueEqual(run.standby, setup.socket().asLong(), "the Uplink waits in this socket");
			helper.assertValueEqual(setup.effects().bonus, 0, "the Black Start bonus is gone");
			for (long packed : pylons) {
				helper.assertFalse(helper.getLevel().getBlockState(BlockPos.of(packed)).is(FinaleBlocks.HEART_PYLON),
						"the reset removes the pylons");
			}
			helper.assertBlockProperty(SOCKET, UplinkSocketBlock.POWER, 1);
			helper.assertTrue(FinaleActions.statusLine(setup.instance()).getContents()
					instanceof net.minecraft.network.chat.contents.TranslatableContents t
					&& t.getKey().equals("status.ashfall.finale.standby"), "the socket reads as standby");

			ItemStack spare = new ItemStack(FinaleBlocks.GRID_UPLINK);
			helper.assertValueEqual(FinaleActions.seat(helper.getLevel(), setup.socket(), player, spare), FinaleLogic.Result.ACCEPTED,
					"a socket in standby powers up its own Uplink");
			helper.assertFalse(spare.isEmpty(), "and leaves the new one in hand");
			helper.assertValueEqual(run.status, FinaleRun.Status.RUNNING, "the replay runs");
			helper.assertValueEqual(run.phase, FinaleRun.Phase.SURGE, "from the Surge");
			helper.assertBlockProperty(SOCKET, UplinkSocketBlock.POWER, 2);

			run.status = FinaleRun.Status.COMPLETE;
			FinaleController.rearm(helper.getLevel(), setup.instance());
			helper.assertValueEqual(FinaleActions.restart(helper.getLevel(), setup.socket(), player), FinaleLogic.Result.ACCEPTED,
					"empty-handed restart");
			helper.assertTrue(run.participants.contains(player.getUUID()), "the restarter is a participant");
			helper.succeed();
		} finally {
			remove(player);
			cleanup(helper, setup);
		}
	}

	/** An admin phase jump relights the bands of the phases it skipped (phase 6 known issue), once each. */
	@GameTest(maxTicks = 100)
	public void aPhaseJumpRelightsTheSkippedBands(GameTestHelper helper) {
		Setup setup = setup(helper, false);
		ServerPlayer player = survivalPlayer(helper, "finale-jump");
		try {
			standAt(player, setup.socket(), 1, 0);
			seat(helper, setup, player);
			FinaleController.jumpTo(helper.getLevel(), setup.instance(), FinaleRun.Phase.FEEDBACK);
			helper.assertValueEqual(setup.effects().relit, List.of(0), "Surge skipped: band 1 relit");
			helper.assertValueEqual(setup.effects().bonus, 7, "and its bonus");
			FinaleController.jumpTo(helper.getLevel(), setup.instance(), FinaleRun.Phase.HEART);
			helper.assertValueEqual(setup.effects().relit, List.of(0, 1), "Feedback skipped: band 2, band 1 not again");
			helper.assertValueEqual(setup.effects().bonus, 14, "Grid 94");
			helper.assertValueEqual(setup.run().pylons.size(), 4, "the Heart's pylons are up");
			FinaleController.jumpTo(helper.getLevel(), setup.instance(), FinaleRun.Phase.SURGE);
			helper.assertValueEqual(setup.effects().relit, List.of(0, 1), "jumping back relights nothing");
			helper.assertValueEqual(setup.effects().bands, List.of(), "a jump is not a phase end");
			helper.succeed();
		} finally {
			remove(player);
			cleanup(helper, setup);
		}
	}

	@GameTest(maxTicks = 100)
	public void anEmptyBufferPausesTheSurge(GameTestHelper helper) {
		Setup setup = setup(helper, false);
		ServerPlayer player = survivalPlayer(helper, "finale-buffer");
		try {
			FinaleRun run = setup.run();
			standAt(player, setup.socket(), 1, 0);
			seat(helper, setup, player);
			tick(helper, setup, 20);
			tick(helper, setup, 20);
			tick(helper, setup, 20);
			helper.assertValueEqual(run.phaseTicks, 20L, "only the buffered second counted");
			helper.assertValueEqual(run.bufferTicks, 0L, "buffer empty");
			helper.assertValueEqual(run.status, FinaleRun.Status.RUNNING, "starved is not failed");
			helper.assertTrue(FinaleLogic.starved(run), "the Surge is starved");
			ItemStack flat = new ItemStack(ArmsItems.ENERGY_CELL);
			helper.assertValueEqual(FinaleActions.feedCell(helper.getLevel(), setup.socket(), player, flat), FinaleLogic.Result.WRONG_STATE,
					"a flat cell is refused");
			ItemStack cell = ArmsItems.charged(ArmsItems.ENERGY_CELL);
			FinaleActions.feedCell(helper.getLevel(), setup.socket(), player, cell);
			helper.assertTrue(cell.isEmpty(), "the fed cell is used up");
			helper.assertValueEqual(run.bufferTicks, 1200L, "a cell buys 60 seconds");
			tick(helper, setup, 10);
			helper.assertValueEqual(run.phaseTicks, 30L, "progress resumes with charge");
			helper.succeed();
		} finally {
			remove(player);
			cleanup(helper, setup);
		}
	}

	@GameTest(maxTicks = 100)
	public void aBreakerNeedsAPlayerWithinThreeBlocks(GameTestHelper helper) {
		Setup setup = setup(helper, false);
		ServerPlayer player = survivalPlayer(helper, "finale-breaker");
		try {
			FinaleRun run = setup.run();
			standAt(player, setup.socket(), 1, 0);
			seat(helper, setup, player);
			run.bufferTicks = 6000;
			int bonus = FinaleLogic.jump(run, setup.instance().s(), FinaleRun.Phase.FEEDBACK);
			helper.assertValueEqual(bonus, 7, "the Surge counts as won");
			FinaleController.enterPhase(helper.getLevel(), setup.instance());
			BlockPos a = BlockPos.of(run.breakers.getFirst().pos);
			standAt(player, a, 4.5, 0);
			tick(helper, setup, 10);
			helper.assertValueEqual(run.breakers.getFirst().holdTicks, 0L, "4.5 blocks away is not holding it");
			standAt(player, a, 2.0, 0);
			tick(helper, setup, 10);
			helper.assertValueEqual(run.breakers.getFirst().holdTicks, 10L, "2 blocks away holds it");
			helper.assertValueEqual(run.breakers.get(1).holdTicks, 0L, "the other breaker is not held");
			tick(helper, setup, 10);
			helper.assertTrue(run.breakers.getFirst().closed, "held for its second, it closes");
			helper.assertValueEqual(run.phase, FinaleRun.Phase.FEEDBACK, "one of two is not enough");
			helper.succeed();
		} finally {
			remove(player);
			cleanup(helper, setup);
		}
	}

	@GameTest(maxTicks = 100)
	public void brokenPylonsAreCounted(GameTestHelper helper) {
		Setup setup = setup(helper, false);
		ServerPlayer player = survivalPlayer(helper, "finale-pylons");
		try {
			FinaleRun run = setup.run();
			standAt(player, setup.socket(), 1, 0);
			seat(helper, setup, player);
			FinaleLogic.jump(run, setup.instance().s(), FinaleRun.Phase.HEART);
			FinaleController.enterPhase(helper.getLevel(), setup.instance());
			helper.assertValueEqual(FinaleController.standingPylons(helper.getLevel(), run), 4, "four standing");
			helper.getLevel().destroyBlock(BlockPos.of(run.pylons.get(0)), false);
			tick(helper, setup, 20);
			helper.assertValueEqual(run.pylonsBroken, 1, "one broken");
			helper.getLevel().destroyBlock(BlockPos.of(run.pylons.get(1)), false);
			helper.getLevel().destroyBlock(BlockPos.of(run.pylons.get(2)), false);
			tick(helper, setup, 20);
			helper.assertValueEqual(run.pylonsBroken, 3, "three broken");
			helper.assertValueEqual(run.status, FinaleRun.Status.RUNNING, "not over with one standing");
			// the Plasma Blade's bonus reaches the real pylon block through #ashfall:crystal_pylons
			BlockPos last = BlockPos.of(run.pylons.get(3));
			helper.assertTrue(helper.getLevel().getBlockState(last).is(PlasmaBladeItem.CRYSTAL_PYLON_BLOCKS), "the pylon is a crystal pylon");
			ItemStack blade = ArmsItems.charged(ArmsItems.PLASMA_BLADE);
			helper.assertValueEqual(blade.getDestroySpeed(helper.getLevel().getBlockState(last)), BladeStatsManager.get(ArmsItems.PLASMA_BLADE).pylonMiningSpeed(),
					"a lit blade mines pylons at the stats file's speed");
			helper.assertTrue(new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE).getDestroySpeed(helper.getLevel().getBlockState(last)) <= 1f,
					"a pickaxe is no better than a fist");
			helper.getLevel().destroyBlock(last, false);
			tick(helper, setup, 20);
			helper.assertValueEqual(run.status, FinaleRun.Status.COMPLETE, "all four broken");
			helper.succeed();
		} finally {
			remove(player);
			cleanup(helper, setup);
		}
	}

	@GameTest(maxTicks = 100)
	public void aBatteredUplinkPausesAndCouplersResumeIt(GameTestHelper helper) {
		Setup setup = setup(helper, false);
		ServerPlayer player = survivalPlayer(helper, "finale-fail");
		try {
			FinaleRun run = setup.run();
			standAt(player, setup.socket(), 3, 3);
			seat(helper, setup, player);
			run.bufferTicks = 6000;
			run.phaseTicks = 10; // 10 + 20 powered ticks stays under the 2-second Surge
			Zombie attacker = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, SOCKET.east());
			tick(helper, setup, 20);
			helper.assertValueEqual(run.uplinkHp, 50, "one zombie at the socket: 50 hp a second in this test");
			tick(helper, setup, 20);
			helper.assertValueEqual(run.status, FinaleRun.Status.PAUSED, "the Uplink at 0 pauses the run");
			helper.assertValueEqual(run.pause, FinaleRun.PauseReason.UPLINK_DOWN, "because the Uplink is down");
			helper.assertValueEqual(run.phase, FinaleRun.Phase.SURGE, "still in the Surge");
			helper.assertValueEqual(run.phaseTicks, 10L, "30 powered ticks minus the 1-second setback");
			helper.assertValueEqual(run.socket, setup.socket().asLong(), "the Uplink stays seated");
			helper.assertBlockProperty(SOCKET, UplinkSocketBlock.POWER, 1);
			attacker.discard();
			tick(helper, setup, 20);
			helper.assertValueEqual(run.status, FinaleRun.Status.PAUSED, "a paused run waits for repairs");
			ItemStack couplers = new ItemStack(GridBlocks.GRID_COUPLER, 4);
			helper.assertValueEqual(FinaleActions.patch(helper.getLevel(), setup.socket(), player, couplers), FinaleLogic.Result.ACCEPTED, "40 hp");
			helper.assertValueEqual(FinaleActions.patch(helper.getLevel(), setup.socket(), player, couplers), FinaleLogic.Result.RESUMED,
					"80 of 100 resumes");
			helper.assertValueEqual(couplers.getCount(), 2, "patches use couplers up");
			helper.assertValueEqual(run.status, FinaleRun.Status.RUNNING, "running again");
			helper.assertBlockProperty(SOCKET, UplinkSocketBlock.POWER, 2);
			helper.succeed();
		} finally {
			remove(player);
			cleanup(helper, setup);
		}
	}

	@GameTest(maxTicks = 100)
	public void everyoneLeavingPausesTheRun(GameTestHelper helper) {
		Setup setup = setup(helper, false);
		ServerPlayer player = survivalPlayer(helper, "finale-leave");
		try {
			FinaleRun run = setup.run();
			standAt(player, setup.socket(), 1, 0);
			seat(helper, setup, player);
			run.bufferTicks = 6000;
			// the player logs out (no teleport: a mock player parked 200 blocks away could land in another test)
			remove(player);
			tick(helper, setup, 20);
			helper.assertValueEqual(run.status, FinaleRun.Status.PAUSED, "nobody near for the abandon time");
			helper.assertValueEqual(run.pause, FinaleRun.PauseReason.ABANDONED, "abandoned");
			player = survivalPlayer(helper, "finale-back");
			standAt(player, setup.socket(), 1, 0);
			ItemStack cell = ArmsItems.charged(ArmsItems.ENERGY_CELL);
			helper.assertValueEqual(FinaleActions.feedCell(helper.getLevel(), setup.socket(), player, cell), FinaleLogic.Result.RESUMED,
					"a cell brings it back");
			helper.succeed();
		} finally {
			remove(player);
			cleanup(helper, setup);
		}
	}

	@GameTest(maxTicks = 100)
	public void theChoirRisesAtTheHeartAndShattersWithIt(GameTestHelper helper) {
		Setup setup = setup(helper, true);
		ServerPlayer player = survivalPlayer(helper, "finale-choir");
		try {
			FinaleRun run = setup.run();
			standAt(player, setup.socket(), 1, 0);
			seat(helper, setup, player);
			FinaleLogic.jump(run, setup.instance().s(), FinaleRun.Phase.HEART);
			FinaleController.enterPhase(helper.getLevel(), setup.instance());
			LivingEntity choir = FinaleController.choir(helper.getLevel(), run);
			helper.assertTrue(choir instanceof ChoirEntity, "the Choir rose");
			helper.assertValueEqual(choir.getMaxHealth(), 420f, "with the settings' health");
			helper.assertTrue(choir.entityTags().contains(ChoirEntity.TAG) && choir.entityTags().contains(FinaleController.FINALE_TAG),
					"tagged as the Choir and a finale mob");
			helper.assertTrue(choir.typeHolder().is(net.minecraft.tags.EntityTypeTags.ZOMBIES), "the Choir is a zombie for every zombie rule");
			((ChoirEntity) choir).setTarget(null);
			FinaleController.enterPhase(helper.getLevel(), setup.instance());
			helper.assertValueEqual(helper.getLevel().getEntitiesOfClass(ChoirEntity.class, choir.getBoundingBox().inflate(16)).size(), 1,
					"one Choir per run");
			for (long packed : run.pylons) {
				helper.getLevel().destroyBlock(BlockPos.of(packed), false);
			}
			tick(helper, setup, 20);
			helper.assertValueEqual(run.status, FinaleRun.Status.COMPLETE, "the Heart is dark");
			helper.assertTrue(choir.isRemoved(), "the Choir comes apart with the Heart");
			helper.succeed();
		} finally {
			remove(player);
			cleanup(helper, setup);
		}
	}

	@GameTest
	public void theHullSealHoldsTheHatchUntilItOpens(GameTestHelper helper) {
		BlockPos lower = new BlockPos(3, 1, 3), upper = lower.above();
		helper.setBlock(lower, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(upper, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		List<BlockPos> hatch = List.of(helper.absolutePos(lower), helper.absolutePos(upper));
		helper.assertValueEqual(HullSite.seal(helper.getLevel(), hatch), 2, "both door halves sealed");
		helper.assertBlockPresent(FinaleBlocks.HULL_SEAL, lower);
		helper.assertBlockPresent(FinaleBlocks.HULL_SEAL, upper);
		helper.assertTrue(FinaleBlocks.HULL_SEAL.defaultBlockState().getDestroySpeed(helper.getLevel(), helper.absolutePos(lower)) < 0,
				"the seal is unbreakable");
		helper.assertFalse(HullSite.shouldOpen(79), "sealed below Grid 80%");
		helper.assertTrue(HullSite.shouldOpen(80), "open at Grid 80%");
		helper.assertValueEqual(HullSite.unseal(helper.getLevel(), hatch), 2, "both cells opened");
		helper.assertBlockPresent(Blocks.AIR, lower);
		helper.assertBlockPresent(Blocks.AIR, upper);
		helper.succeed();
	}

	/**
	 * The shipped hull template carries the socket placeholder and the iron-door hatch, and arms turns them into the
	 * Uplink Socket and the Hull Seal. Placed under a test key, so the world's own hull record is never touched.
	 */
	@GameTest(maxTicks = 60, padding = 48)
	public void theRealHullTemplateGetsASocketAndASeal(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Identifier id = Identifier.fromNamespaceAndPath("ashfall", "wreck_hull");
		SiteTemplate template = Sites.templates().get(id);
		helper.assertTrue(template != null, "the hull template is loaded");
		BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
		String key = "test:finale_hull:" + UUID.randomUUID();
		PlacedSite site = Sites.placeAt(level, key, id, template, origin, origin.offset(16, template.sink(), 8), 1L,
				CrashSiteRule.Guards.NONE, 0);
		try {
			helper.assertTrue(site != null, "hull placed");
			BlockPos socket = HullSite.socketOf(site).orElse(null);
			helper.assertTrue(socket != null && site.marker(HullSite.SOCKET_MARKER).isPresent(), "the socket marker is recorded");
			helper.assertTrue(level.getBlockState(socket).is(Blocks.LODESTONE), "salvage places the vanilla placeholder");
			List<BlockPos> hatch = HullSite.hatchOf(site);
			helper.assertValueEqual(hatch.size(), 4, "a two-door hatch");
			helper.assertTrue(HullSite.prepareSite(level, site, true), "prepared");
			helper.assertTrue(level.getBlockState(socket).is(FinaleBlocks.UPLINK_SOCKET), "the real socket is in place");
			helper.assertTrue(level.getBlockState(socket.above()).isAir() && level.getBlockState(socket.above(2)).isAir(),
					"room to stand at the socket");
			for (BlockPos p : hatch) {
				helper.assertTrue(level.getBlockState(p).is(FinaleBlocks.HULL_SEAL), "hatch cell sealed at " + p);
			}
			FinaleSettings s = FinaleSettings.DEFAULT;
			for (Vec3i offset : s.heart().pylons()) {
				helper.assertTrue(level.getBlockState(socket.offset(offset)).canBeReplaced() || level.getBlockState(socket.offset(offset)).is(Blocks.COBWEB),
						"room inside the hull for a Heart pylon at offset " + offset);
			}
			List<Vec3i> standing = new ArrayList<>(s.vents().offsets());
			standing.add(s.heart().choirOffset());
			for (Vec3i offset : standing) {
				BlockPos p = socket.offset(offset);
				helper.assertTrue(level.getBlockState(p).getCollisionShape(level, p).isEmpty()
						&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty(),
						"room inside the hull to spawn at offset " + offset);
			}
			helper.assertValueEqual(HullSite.unseal(level, hatch), 4, "the hatch opens");
			helper.succeed();
		} finally {
			SiteState.get(level.getServer()).forget(key);
		}
	}

	@GameTest
	public void theUplinkCraftsInThreeSteps(GameTestHelper helper) {
		RecipeManager recipes = helper.getLevel().getServer().getRecipeManager();
		ItemStack r = new ItemStack(ArmsItems.RESONANCE_CRYSTAL), l = new ItemStack(ArmsItems.FOCUSING_LENS);
		ItemStack c = new ItemStack(GridBlocks.GRID_COUPLER), e = new ItemStack(ArmsItems.ENERGY_CELL);
		CraftingInput frameIn = CraftingInput.of(3, 3, List.of(l, r, l, c, r, c, l, r, l));
		helper.assertTrue(craft(helper, recipes, frameIn).is(FinaleBlocks.UPLINK_FRAME), "3 crystals, 4 lenses, 2 couplers: the frame");
		CraftingInput bankIn = CraftingInput.of(3, 3, List.of(e, e, e, e, c, e, e, e, e));
		helper.assertTrue(craft(helper, recipes, bankIn).is(FinaleBlocks.CAPACITOR_BANK), "8 cells and a coupler: a bank");
		ItemStack f = new ItemStack(FinaleBlocks.UPLINK_FRAME), b = new ItemStack(FinaleBlocks.CAPACITOR_BANK);
		CraftingInput uplinkIn = CraftingInput.of(3, 3, List.of(c, b, c, ItemStack.EMPTY, f, ItemStack.EMPTY, c, b, c));
		helper.assertTrue(craft(helper, recipes, uplinkIn).is(FinaleBlocks.GRID_UPLINK), "frame, two banks, 4 couplers: the Uplink");
		helper.succeed();
	}

	private static ItemStack craft(GameTestHelper helper, RecipeManager recipes, CraftingInput input) {
		return recipes.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).map(holder -> holder.value().assemble(input))
				.orElse(ItemStack.EMPTY);
	}

	/** The shipped settings reach the server through the datapack. */
	@GameTest
	public void finaleSettingsLoad(GameTestHelper helper) {
		FinaleSettings s = FinaleSettings.get();
		helper.assertValueEqual(s.waves().maxMobs(), 120, "the finale's mob cap");
		helper.assertValueEqual(s.heart().pylons().size(), 4, "four pylons");
		helper.assertValueEqual(s.bonus(3), 20, "the Black Start ends at Grid 100%");
		helper.succeed();
	}

	/**
	 * Phase 6 end to end: the Heart falls in a real (test-owned) run whose completion goes through the world's effects,
	 * so "Asheville Relit" is awarded and {@link Ending#beginChoice} opens the vote; the solo participant votes Sever and
	 * confirms; the Sever profile applies (Antibiotics cure, Grid 100 %), both books arrive; then the replay reset
	 * forgets the ending and rearms a COMPLETE world finale back to IDLE.
	 * <p>
	 * The ending is world-wide state, so all of it happens in the first tick and is undone in the {@code finally}.
	 */
	@GameTest(maxTicks = 100)
	public void theHeartFallingOpensTheVoteAndTheEndingApplies(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		int bonusBefore = GridState.get(server).blackStartBonus();
		boolean objective = server.overworld().getGameRules().get(StoryRules.OBJECTIVE);
		Setup setup = setup(helper, false);
		// the world's completion (relit, the vote), but not its band lighting or Grid bonus: those are the real city's
		FinaleInstance instance = new FinaleInstance(setup.run(), setup.instance()::s, () -> {
		}, new FinaleInstance.Effects() {
			@Override
			public void phaseEnded(ServerLevel level, FinaleInstance in, int band, int bonus) {
				setup.effects().phaseEnded(level, in, band, bonus);
			}

			@Override
			public void completed(ServerLevel level, FinaleInstance in, Collection<ServerPlayer> participants) {
				setup.effects().completed(level, in, participants);
				FinaleEffects.WORLD.completed(level, in, participants);
			}

			@Override
			public void bonusChanged(ServerLevel level, int bonus) {
				setup.effects().bonusChanged(level, bonus);
			}
		});
		FinaleController.registerTest(setup.socket(), instance);
		Setup wired = new Setup(instance, setup.effects(), setup.socket());
		ServerPlayer player = survivalPlayer(helper, "finale-ending");
		player.getInventory().clearContent();
		FinaleRun mainRun = FinaleState.get(server).run();
		try {
			EndingVote.cancel();
			if (StoryState.get(server).ending().chosen()) {
				Ending.reset(server);
			}
			server.overworld().getGameRules().set(StoryRules.OBJECTIVE, true, server);
			FinaleRun run = wired.run();
			standAt(player, wired.socket(), 1, 0);
			seat(helper, wired, player);
			tick(helper, wired, 20);
			FinaleActions.feedCell(helper.getLevel(), wired.socket(), player, ArmsItems.charged(ArmsItems.ENERGY_CELL));
			tick(helper, wired, 20);
			helper.assertValueEqual(run.phase, FinaleRun.Phase.FEEDBACK, "Surge over");
			for (FinaleRun.Breaker breaker : List.copyOf(run.breakers)) {
				standAt(player, BlockPos.of(breaker.pos), 1, 0);
				tick(helper, wired, 20);
			}
			helper.assertValueEqual(run.phase, FinaleRun.Phase.HEART, "breakers closed");
			for (long packed : run.pylons) {
				helper.getLevel().destroyBlock(BlockPos.of(packed), false);
			}
			standAt(player, wired.socket(), 1, 0);
			tick(helper, wired, 20);
			helper.assertValueEqual(run.status, FinaleRun.Status.COMPLETE, "the Heart is dark");

			AdvancementHolder relit = server.getAdvancements().get(FinaleController.RELIT_ADVANCEMENT);
			helper.assertTrue(relit != null && player.getAdvancements().getOrStartProgress(relit).isDone(), "Asheville Relit at the Heart's fall");
			helper.assertTrue(EndingVote.active(), "the Heart falling opened no vote");
			helper.assertValueEqual(EndingVote.vote(player, StoryEnding.SEVER), EndingVote.Result.CONFIRM_NEEDED, "solo asks to confirm");
			helper.assertValueEqual(EndingVote.confirm(player), EndingVote.Result.RECORDED, "solo confirm");
			helper.assertTrue(StoryState.get(server).ending() == StoryEnding.SEVER, "the vote did not sever");
			helper.assertTrue(InfectionRules.severEnding(server), "Sever: Antibiotics fully cure");
			helper.assertTrue(GridState.get(server).blackStartBonus() >= Ending.BLACK_START_BONUS, "the ending leaves the Grid at 100 %");
			List<String> books = new ArrayList<>();
			for (ItemStack stack : player.getInventory()) {
				WrittenBookContent content = stack.is(Items.WRITTEN_BOOK) ? stack.get(DataComponents.WRITTEN_BOOK_CONTENT) : null;
				if (content != null) {
					books.add(content.title().raw());
				}
			}
			helper.assertTrue(books.contains(FieldReport.TITLE) && books.contains(Epilogue.TITLE), "books: " + books);

			// the replay: a COMPLETE world finale refuses a new start, so the reset puts it back to IDLE
			mainRun.status = FinaleRun.Status.COMPLETE;
			Ending.reset(server);
			helper.assertTrue(StoryState.get(server).ending() == StoryEnding.NONE, "reset kept the ending");
			helper.assertValueEqual(FinaleState.get(server).run().status, FinaleRun.Status.IDLE, "reset did not rearm the finale");
			helper.succeed();
		} finally {
			EndingVote.cancel();
			if (StoryState.get(server).ending().chosen()) {
				Ending.reset(server);
			}
			if (mainRun.status == FinaleRun.Status.COMPLETE) {
				mainRun.reset();
			}
			server.overworld().getGameRules().set(StoryRules.OBJECTIVE, objective, server);
			GridState.get(server).setBlackStartBonus(bonusBefore);
			remove(player);
			cleanup(helper, wired);
		}
	}
}
