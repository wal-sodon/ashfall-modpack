package xyz.toesinthesand.ashfall.arms.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import xyz.toesinthesand.ashfall.arms.grid.DecaySettings;
import xyz.toesinthesand.ashfall.arms.grid.DistrictLoop;
import xyz.toesinthesand.ashfall.arms.grid.DistrictRecord;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;
import xyz.toesinthesand.ashfall.arms.grid.GridActions;
import xyz.toesinthesand.ashfall.arms.grid.GridCommand;
import xyz.toesinthesand.ashfall.arms.grid.GridDecay;
import xyz.toesinthesand.ashfall.arms.grid.GridDistricts;
import xyz.toesinthesand.ashfall.arms.grid.GridSettings;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.grid.SiegeSettings;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.director.spawn.DirectorDay;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * District decay in a real world: a worn-out relay flickers a reclaimed district, it blacks out (no longer reclaimed),
 * and a Wire Spool with Duct Tape brings it straight back. A one-relay test district, so one relay is all it needs.
 */
public class DecayGameTest {
	private static final BlockPos RELAY = new BlockPos(2, 1, 2);
	private static final int PADDING = 24;

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
		return player;
	}

	@GameTest(maxTicks = 100, padding = PADDING)
	public void wornRelaysBlackOutAndRepairBringsTheDistrictBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos relay = helper.absolutePos(RELAY);
		long relayChunk = ChunkPos.containing(relay).pack();
		String id = "test_decay";
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(7, 7, 7)));
		GridDistricts.registerTest(new GridDistricts.TestDistrict(id, box, Set.of(relayChunk), GridSettings.DEFAULT,
				SiegeSettings.DEFAULT, 1, true));
		GridState grid = GridState.get(level.getServer());
		grid.update(level, id, r -> {
			r.reset();
			r.corePos = DistrictRecord.NO_CORE;
		});
		ServerPlayer player = survivalPlayer(helper, "decay-repair");
		try {
			GridCommand.force(level, id, DistrictState.RECLAIMED);
			helper.assertTrue(grid.record(id).linked.contains(relayChunk), "a forced reclaim links the relay");
			long day = DirectorDay.today(level);

			helper.assertValueEqual(GridDecay.damage(level, id, DecaySettings.DEFAULT, new Random(1)), 1, "the horde hits the one relay");
			helper.assertValueEqual(grid.record(id).condition(relayChunk), 100 - DecaySettings.DEFAULT.hordeDamage(), "horde damage");

			grid.update(level, id, r -> r.setCondition(relayChunk, 0));
			helper.assertValueEqual(GridDecay.refresh(level, id), DistrictLoop.DecayChange.FLICKER_STARTED, "too few relays: flicker");
			helper.assertTrue(grid.isReclaimed(id), "still lit while it flickers");
			helper.assertValueEqual(GridDecay.daily(level, id, DecaySettings.DEFAULT, day + DecaySettings.DEFAULT.flickerDays()),
					DistrictLoop.DecayChange.BLACKOUT, "the flicker runs out");
			helper.assertValueEqual(grid.state(id), DistrictState.BLACKOUT, "blacked out");
			helper.assertFalse(grid.isReclaimed(id), "a blacked-out district stops counting as reclaimed");

			ItemStack wire = new ItemStack(ArmsItems.WIRE_SPOOL, 2);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wire);
			GridActions.repairRelay(level, relay, player, wire);
			helper.assertValueEqual(wire.getCount(), 2, "no tape, no repair");
			helper.assertValueEqual(grid.state(id), DistrictState.BLACKOUT, "still dark");

			player.getInventory().add(new ItemStack(SalvageItems.DUCT_TAPE, 1));
			helper.assertValueEqual(GridActions.repairRelay(level, relay, player, wire), DistrictLoop.Result.COMPLETED, "repaired");
			helper.assertValueEqual(wire.getCount(), 1, "one wire used");
			helper.assertFalse(player.getInventory().hasAnyMatching(s -> s.is(SalvageItems.DUCT_TAPE)), "the tape was used");
			helper.assertValueEqual(grid.state(id), DistrictState.RECLAIMED, "back on the grid without a siege");
			helper.assertTrue(grid.isReclaimed(id), "it counts as reclaimed again");
			helper.succeed();
		} finally {
			level.getServer().getPlayerList().remove(player);
			GridDistricts.unregisterTest(id);
		}
	}
}
