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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.survivor.Survivor;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRole;
import xyz.toesinthesand.ashfall.arms.survivor.Survivors;
import xyz.toesinthesand.ashfall.director.senses.HuntSurvivorsGoal;
import xyz.toesinthesand.ashfall.director.senses.Stun;
import xyz.toesinthesand.ashfall.director.special.Brute;
import xyz.toesinthesand.ashfall.director.special.SpecialEntities;

import java.util.UUID;

/**
 * Zombies go for the nearest of players and survivors ({@link HuntSurvivorsGoal}): a near survivor is bitten while a
 * player stands farther off, a clearly nearer player wins, a zombie on a survivor ignores a player only slightly
 * nearer, specials still ignore survivors and a stunned zombie takes no target. Survivors here have no AI so they
 * stand still instead of fleeing. Padded wide: the mock players are real players every nearby zombie can see.
 */
public class SurvivorTargetingGameTest {
	private static final int PADDING = 40;

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static Survivor survivor(GameTestHelper helper, Vec3 rel) {
		Survivor survivor = Survivors.spawn(helper.getLevel(), helper.absolutePos(BlockPos.containing(rel)), SurvivorRole.SETTLER, null);
		helper.assertTrue(survivor != null, "survivor spawned");
		Vec3 abs = helper.absoluteVec(rel);
		survivor.snapTo(abs.x, abs.y, abs.z);
		survivor.setNoAi(true);
		return survivor;
	}

	/** A survival player in the player list (vanilla's mock is always creative, which zombies ignore). */
	private static ServerPlayer player(GameTestHelper helper, Vec3 rel) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "prey"), false);
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
		Vec3 abs = helper.absoluteVec(rel);
		player.snapTo(abs.x, abs.y, abs.z);
		return player;
	}

	private static <T extends Mob> T helmeted(T mob) {
		mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the test's daylight
		return mob;
	}

	private static void remove(ServerPlayer player) {
		player.level().getServer().getPlayerList().remove(player);
	}

	@GameTest(maxTicks = 300, padding = PADDING)
	public void nearSurvivorBeatsFarPlayer(GameTestHelper helper) {
		floor(helper);
		Zombie zombie = helmeted(helper.spawn(EntityTypes.ZOMBIE, new Vec3(0.5, 1, 0.5)));
		Survivor survivor = survivor(helper, new Vec3(3.5, 1, 0.5));
		ServerPlayer player = player(helper, new Vec3(7.5, 1, 7.5)); // about 10 blocks off
		float full = survivor.getHealth();
		helper.onEachTick(() -> helper.assertFalse(zombie.getTarget() == player, "the zombie went for the farther player"));
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie.getTarget() == survivor || !survivor.isAlive(), "zombie target is " + zombie.getTarget());
			helper.assertTrue(survivor.getHealth() < full, "the survivor was never bitten");
			survivor.discard();
			remove(player);
		});
	}

	@GameTest(maxTicks = 200, padding = PADDING)
	public void clearlyNearerPlayerWins(GameTestHelper helper) {
		floor(helper);
		Zombie zombie = helmeted(helper.spawn(EntityTypes.ZOMBIE, new Vec3(0.5, 1, 0.5)));
		Survivor survivor = survivor(helper, new Vec3(7.5, 1, 7.5));
		ServerPlayer player = player(helper, new Vec3(2.5, 1, 0.5));
		helper.assertTrue(HuntSurvivorsGoal.choose(zombie, null, player, survivor) == player, "nearest pick");
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie.getTarget() == player, "zombie target is " + zombie.getTarget());
			survivor.discard();
			remove(player);
		});
	}

	@GameTest(maxTicks = 200, padding = PADDING)
	public void chasedSurvivorKeptOverSlightlyNearerPlayer(GameTestHelper helper) {
		floor(helper);
		Zombie zombie = helmeted(helper.spawn(EntityTypes.ZOMBIE, new Vec3(0.5, 1, 0.5)));
		Survivor survivor = survivor(helper, new Vec3(5.5, 1, 0.5));
		ServerPlayer player = player(helper, new Vec3(0.5, 1, 4.0)); // 1.5 blocks nearer than the survivor
		helper.assertTrue(HuntSurvivorsGoal.choose(zombie, survivor, player, survivor) == survivor,
				"a player only slightly nearer took the zombie off its survivor");
		helper.assertTrue(HuntSurvivorsGoal.choose(zombie, player, player, survivor) == player,
				"a survivor only slightly farther took the zombie off its player");
		zombie.setTarget(survivor);
		boolean[] done = {false};
		helper.onEachTick(() -> helper.assertTrue(done[0] || zombie.getTarget() == survivor || !survivor.isAlive(),
				"the zombie switched from its survivor to " + zombie.getTarget()));
		helper.runAfterDelay(100, () -> {
			done[0] = true;
			survivor.discard();
			remove(player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100, padding = PADDING)
	public void specialIgnoresSurvivors(GameTestHelper helper) {
		floor(helper);
		Brute brute = helmeted(helper.spawn(SpecialEntities.BRUTE, new Vec3(0.5, 1, 0.5)));
		Survivor survivor = survivor(helper, new Vec3(3.5, 1, 0.5));
		helper.assertFalse(HuntSurvivorsGoal.huntsSurvivors(brute), "a special counts as a survivor hunter");
		boolean[] done = {false};
		helper.onEachTick(() -> helper.assertFalse(!done[0] && brute.getTarget() == survivor, "the Brute went for a survivor"));
		helper.runAfterDelay(60, () -> {
			done[0] = true;
			survivor.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100, padding = PADDING)
	public void stunnedZombieTakesNoTarget(GameTestHelper helper) {
		floor(helper);
		Zombie zombie = helmeted(helper.spawn(EntityTypes.ZOMBIE, new Vec3(0.5, 1, 0.5)));
		Survivor survivor = survivor(helper, new Vec3(3.5, 1, 0.5));
		ServerPlayer player = player(helper, new Vec3(2.5, 1, 2.5));
		helper.assertTrue(Stun.tryStun(zombie, null) > 0, "the zombie could not be stunned");
		zombie.setTarget(survivor);
		helper.assertTrue(zombie.getTarget() == null, "a stunned zombie took a target");
		boolean[] done = {false};
		helper.onEachTick(() -> helper.assertTrue(done[0] || zombie.getTarget() == null || !Stun.isStunned(zombie),
				"a stunned zombie targets " + zombie.getTarget()));
		helper.runAfterDelay(40, () -> {
			done[0] = true;
			survivor.discard();
			remove(player);
			helper.succeed();
		});
	}
}
