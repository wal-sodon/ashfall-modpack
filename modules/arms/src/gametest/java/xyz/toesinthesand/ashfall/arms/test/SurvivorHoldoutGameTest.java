package xyz.toesinthesand.ashfall.arms.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import xyz.toesinthesand.ashfall.arms.survivor.Survivor;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorBalance;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorHoldouts;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorMode;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRescue;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRole;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRoster;
import xyz.toesinthesand.ashfall.arms.survivor.Survivors;
import xyz.toesinthesand.ashfall.director.spawn.BuildingPopulations;
import xyz.toesinthesand.ashfall.director.spawn.DirectorState;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariant;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.Footprint;
import xyz.toesinthesand.ashfall.salvage.building.LostCitiesBuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.Unit;
import xyz.toesinthesand.ashfall.salvage.lock.Strongboxes;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Building holdouts and the world cap (survivors plan, phase 4): with the chance forced to 1, a hotel's first population
 * leaves a holdout hiding on its upper storey; a holdout calls out once to a player who comes near and the first player
 * to order it becomes its leader, unless that player's group is full; a distress rescue passes a full player over for
 * the nearest one with room; and at the 30-survivor world cap (hiding holdouts not counted) a distress rescue, a new
 * holdout, a hiding holdout joining and the admin spawn are all refused, while a hiding holdout never blocks a rescue.
 * Each runs in a test environment of its own kind: environments run as separate batches, so the balance overrides and
 * the filled world cap never reach another test. Rescue beacons sit high above their test so that only the test's own
 * players are in range.
 */
public class SurvivorHoldoutGameTest {
	private static final String HOLDOUTS = "ashfall_arms_test:survivor_holdouts";
	private static final String WORLD_CAP = "ashfall_arms_test:survivor_world_cap";
	/** Well past day 7, when holdouts and rescues begin. */
	private static final int LATE_DAY = 100;

	private static long randomId() {
		return 8_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
	}

	/**
	 * A two-storey Downtown hotel over rel x/z 1..4: stone ground at rel y 0, a stone upper floor at rel y 4. Downtown, so
	 * its population does not depend on the day.
	 */
	private static Building hotel(GameTestHelper helper) {
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 6; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				if (x >= 1 && x <= 4 && z >= 1 && z <= 4) {
					helper.setBlock(x, 4, z, Blocks.STONE);
				}
			}
		}
		BlockPos a = helper.absolutePos(new BlockPos(1, 0, 1));
		BlockPos b = helper.absolutePos(new BlockPos(4, 0, 4));
		int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
		int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
		List<int[]> rows = new ArrayList<>();
		for (int z = z0; z <= z1; z++) {
			rows.add(new int[]{z, x0, x1});
		}
		Footprint footprint = Footprint.of(rows);
		int y = a.getY();
		List<Unit> units = List.of(new Unit(0, y, y + 3, "Ground", footprint), new Unit(1, y + 4, y + 7, "Upper", footprint));
		return new Building(randomId(), Map.of("building", "hotel", LostCitiesBuildingSource.TAG_ZONE, "downtown"),
				new int[]{y, y + 4}, y, y + 8, footprint, units);
	}

	/** A survival player that keeps every chat line it is sent. */
	private static final class Listener {
		final ServerPlayer player;
		final List<Component> heard = new ArrayList<>();

		Listener(GameTestHelper helper, String name) {
			ServerLevel level = helper.getLevel();
			CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
			player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
				@Override
				public GameType gameMode() {
					return GameType.SURVIVAL;
				}

				@Override
				public void sendSystemMessage(Component message, boolean overlay) {
					heard.add(message);
					super.sendSystemMessage(message, overlay);
				}
			};
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
			player.getAbilities().instabuild = false;
		}

		int count(String key) {
			return (int) heard.stream().filter(c -> key.equals(lineKey(c)) || key.equals(ownKey(c))).count();
		}

		void at(GameTestHelper helper, BlockPos rel) {
			BlockPos abs = helper.absolutePos(rel);
			player.snapTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		}

		void order(Survivor survivor, String order) {
			MinecraftServer server = player.level().getServer();
			server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "survivor " + survivor.getUUID() + " " + order);
		}

		void remove() {
			player.level().getServer().getPlayerList().remove(player);
		}
	}

	/** The line key inside a {@code Name: "line"} reply. */
	private static String lineKey(Component reply) {
		if (reply.getContents() instanceof TranslatableContents says && says.getArgs().length > 1
				&& says.getArgs()[1] instanceof Component line && line.getContents() instanceof TranslatableContents inner) {
			return inner.getKey();
		}
		return "";
	}

	private static String ownKey(Component message) {
		return message.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	@GameTest(environment = HOLDOUTS, maxTicks = 60)
	public void holdoutSpawnsWithAFirstPopulation(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Building building = hotel(helper);
		SurvivorBalance before = SurvivorBalance.get();
		SurvivorBalance.setForTesting(before.withRescueFirstDay(0).withHoldout(new SurvivorBalance.Holdout(1.0f, before.holdout().callRange())));
		BuildingProfile.Population population = new BuildingProfile.Population(20f, Map.of(ZombieVariant.ZOMBIE, 1));
		BuildingPopulations.setOverridesForTesting(l -> l == building.id() ? building : AshfallSalvage.buildings().get(l), (b, u) -> population);
		Strongboxes.setChanceForTesting(0f);
		List<Entity> spawned = new ArrayList<>();
		try {
			helper.assertTrue(SurvivorHoldouts.suits(building), "a hotel suits a holdout");
			helper.assertTrue(BuildingPopulations.populate(level, building) > 0, "the hotel gets a population");
			for (UUID id : DirectorState.get(level.getServer()).building(building.id()).members()) {
				Entity e = level.getEntity(id);
				if (e != null) {
					spawned.add(e);
				}
			}
			Footprint f = building.footprint();
			List<Survivor> found = level.getEntitiesOfClass(Survivor.class,
					new AABB(f.minX(), building.baseY(), f.minZ(), f.maxX() + 1, building.topY(), f.maxZ() + 1));
			spawned.addAll(found);
			helper.assertValueEqual(found.size(), 1, "holdouts in the hotel");
			Survivor holdout = found.getFirst();
			helper.assertTrue(holdout.holdout(), "the survivor in the hotel is not hiding");
			helper.assertTrue(holdout.leader() == null, "a holdout has a leader");
			helper.assertValueEqual(holdout.mode(), SurvivorMode.STAY, "a holdout's mode");
			helper.assertValueEqual(holdout.getBlockY(), building.floors()[1] + 1, "a holdout's storey (feet on the upper floor)");
			helper.assertTrue(SurvivorRoster.get(level.getServer()).contains(holdout.getUUID()), "a spawned holdout is not on the roster");
			helper.assertTrue(!holdout.canBeSeenAsEnemy(), "zombies can find a holdout that has not called out");
		} finally {
			SurvivorBalance.setForTesting(before);
			BuildingPopulations.setOverridesForTesting(null, null);
			Strongboxes.setChanceForTesting(null);
			spawned.forEach(Entity::discard);
		}
		helper.succeed();
	}

	@GameTest(environment = HOLDOUTS, maxTicks = 200)
	public void holdoutCallsOutOnceAndFollowsItsFinder(GameTestHelper helper) {
		Building building = hotel(helper);
		Survivor holdout = SurvivorHoldouts.tryHoldout(helper.getLevel(), building, LATE_DAY, 0f);
		helper.assertTrue(holdout != null, "no holdout with the roll under the chance");
		Listener finder = new Listener(helper, "holdout-finder");
		finder.at(helper, new BlockPos(2, 1, 30)); // well out of call range
		helper.startSequence()
				.thenExecuteAfter(40, () -> {
					helper.assertTrue(!holdout.calledOut(), "the holdout called out to a player 25+ blocks off");
					finder.at(helper, new BlockPos(2, 1, 9));
				})
				.thenWaitUntil(() -> helper.assertTrue(holdout.calledOut(), "the holdout did not call out to a player in range"))
				.thenExecuteAfter(60, () -> {
					helper.assertValueEqual(finder.count("message.ashfall.survivor.holdout.call"), 1, "call-outs heard");
					helper.assertTrue(holdout.canBeSeenAsEnemy(), "zombies still can't find a holdout that called out");
					helper.assertTrue(holdout.leader() == null, "calling out gave it a leader");
					finder.at(helper, new BlockPos(2, 1, 6));
					finder.order(holdout, "follow");
					helper.assertTrue(finder.player.getUUID().equals(holdout.leader()), "the first player to order the holdout is not its leader");
					helper.assertValueEqual(holdout.mode(), SurvivorMode.FOLLOW, "the holdout's mode after [Follow]");
					helper.assertTrue(!holdout.holdout(), "a led holdout is still hiding");
				})
				.thenExecute(() -> {
					holdout.discard();
					finder.remove();
				})
				.thenSucceed();
	}

	@GameTest(environment = HOLDOUTS)
	public void holdoutRefusesAFullGroup(GameTestHelper helper) {
		Building building = hotel(helper);
		Listener finder = new Listener(helper, "holdout-full");
		List<Survivor> keep = new ArrayList<>();
		try {
			int group = SurvivorBalance.get().caps().group();
			for (int i = 0; i < group; i++) {
				Survivor s = Survivors.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, i % 6)), SurvivorRole.SETTLER, finder.player.getUUID());
				helper.assertTrue(s != null, "group survivor spawned");
				keep.add(s);
			}
			Survivor holdout = SurvivorHoldouts.tryHoldout(helper.getLevel(), building, LATE_DAY, 0f);
			helper.assertTrue(holdout != null, "no holdout with the roll under the chance");
			keep.add(holdout);
			finder.at(helper, new BlockPos(2, 1, 6));
			finder.order(holdout, "follow");
			helper.assertTrue(holdout.leader() == null, "a holdout joined a full group");
			helper.assertTrue(holdout.holdout(), "a refused holdout stopped hiding");
			helper.assertValueEqual(finder.count("message.ashfall.survivor.holdout.full"), 1, "refusals heard");
		} finally {
			keep.forEach(Survivor::discard);
			finder.remove();
		}
		helper.succeed();
	}

	@GameTest(environment = HOLDOUTS)
	public void rescueGoesToTheNearestPlayerWithRoom(GameTestHelper helper) {
		Listener full = new Listener(helper, "rescue-full");
		Listener roomy = new Listener(helper, "rescue-roomy");
		List<Survivor> keep = new ArrayList<>();
		try {
			for (int i = 0; i < SurvivorBalance.get().caps().group(); i++) {
				Survivor s = Survivors.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(i % 6, 1, 5)), SurvivorRole.SETTLER, full.player.getUUID());
				helper.assertTrue(s != null, "group survivor spawned");
				s.setNoAi(true);
				keep.add(s);
			}
			// high above the test, so a neighbouring test's player can't be nearer
			BlockPos beacon = helper.absolutePos(new BlockPos(2, 80, 2));
			full.at(helper, new BlockPos(2, 80, 4));
			roomy.at(helper, new BlockPos(2, 80, 12));
			Survivor rescued = SurvivorRescue.atBeacon(helper.getLevel(), beacon, LATE_DAY);
			helper.assertTrue(rescued != null, "no rescue with a player with room in range");
			keep.add(rescued);
			helper.assertTrue(roomy.player.getUUID().equals(rescued.leader()), "the rescue did not pass over the full player to the one with room");
			helper.assertValueEqual(roomy.count("message.ashfall.survivor.rescued"), 1, "rescue lines heard by the player with room");
			helper.assertValueEqual(full.count("message.ashfall.survivor.rescue_full"), 0, "refusals heard by the full player");
		} finally {
			keep.forEach(Survivor::discard);
			full.remove();
			roomy.remove();
		}
		helper.succeed();
	}

	@GameTest(environment = WORLD_CAP, maxTicks = 60)
	public void worldCapRefusesRescueHoldoutAndSpawn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		SurvivorRoster roster = SurvivorRoster.get(server);
		int cap = SurvivorBalance.get().caps().world();
		helper.assertValueEqual(cap, 30, "the shipped world cap");
		Building building = hotel(helper);
		Listener rescuer = new Listener(helper, "world-cap");
		List<Survivor> fill = new ArrayList<>();
		try {
			// a holdout still hiding doesn't count toward the cap
			Survivor hiding = SurvivorHoldouts.tryHoldout(level, building, LATE_DAY, 0f);
			helper.assertTrue(hiding != null, "no holdout under the world cap");
			fill.add(hiding);
			while (roster.counted() < cap) {
				Survivor s = Survivors.spawn(level, helper.absolutePos(new BlockPos(fill.size() % 6, 1, 5 + fill.size() / 6)), SurvivorRole.SETTLER, null);
				helper.assertTrue(s != null, "filler survivor spawned");
				s.setNoAi(true);
				fill.add(s);
			}
			helper.assertValueEqual(roster.counted(), cap, "survivors counted toward the world cap");
			helper.assertTrue(roster.total() > cap, "the hiding holdout is not on the roster");

			// high above the test, so only the rescuer is in range
			BlockPos beacon = helper.absolutePos(new BlockPos(5, 80, 0));
			rescuer.at(helper, new BlockPos(5, 80, 2));
			helper.assertTrue(SurvivorRescue.atBeacon(level, beacon, LATE_DAY) == null, "a rescue at the world cap");
			helper.assertValueEqual(rescuer.count("message.ashfall.survivor.rescue_world_full"), 1, "world-cap refusals heard");
			helper.assertValueEqual(rescuer.count("message.ashfall.survivor.rescue_full"), 0, "group-cap refusals heard");
			helper.assertTrue(SurvivorHoldouts.tryHoldout(level, building, LATE_DAY, 0f) == null, "a holdout at the world cap");
			BlockPos at = helper.absolutePos(new BlockPos(0, 1, 0));
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(net.minecraft.world.phys.Vec3.atBottomCenterOf(at))
					.withLevel(level).withSuppressedOutput(), "ashfall survivor spawn");
			helper.assertValueEqual(roster.counted(), cap, "survivors after an admin spawn at the world cap");

			// the hiding holdout won't join anyone while the world is full
			rescuer.at(helper, new BlockPos(2, 1, 6));
			rescuer.order(hiding, "follow");
			helper.assertTrue(hiding.leader() == null, "a holdout joined at the world cap");
			helper.assertValueEqual(rescuer.count("message.ashfall.survivor.holdout.world_full"), 1, "holdout world-cap refusals heard");

			// one place free: the rescue goes ahead, though the hiding holdout keeps the roster at the cap
			fill.removeLast().discard();
			helper.assertValueEqual(roster.counted(), cap - 1, "survivors counted with one place free");
			helper.assertTrue(roster.total() >= cap, "the hiding holdout left the roster");
			rescuer.at(helper, new BlockPos(5, 80, 2));
			Survivor rescued = SurvivorRescue.atBeacon(level, beacon, LATE_DAY);
			helper.assertTrue(rescued != null, "a hiding holdout blocked a rescue");
			fill.add(rescued);
			helper.assertTrue(rescuer.player.getUUID().equals(rescued.leader()), "the rescued survivor's leader");
		} finally {
			fill.forEach(Survivor::discard);
			rescuer.remove();
		}
		helper.succeed();
	}
}
