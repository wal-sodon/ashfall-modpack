package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.entity.EnergyBolt;
import xyz.toesinthesand.ashfall.arms.entity.Nail;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.survivor.Survivor;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorBalance;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorInfection;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRescue;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRole;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRoster;
import xyz.toesinthesand.ashfall.arms.survivor.Survivors;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreRecord;
import xyz.toesinthesand.ashfall.director.haven.HavenState;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;
import xyz.toesinthesand.ashfall.director.senses.HuntSurvivorsGoal;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.traps.SpikeStripBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Survivors in the world (phase 1 hooks): zombies go for a survivor they can see; a bite starts the clock and an untreated
 * survivor turns into a Director zombie, while Antibiotics cure one; players, their shots, their pets and spike strips
 * never hurt a survivor but a zombie does; and the caps (6 per group for rescues, 6 settlers per bunker) hold.
 */
public class SurvivorHooksGameTest {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static Survivor survivor(GameTestHelper helper, BlockPos rel, UUID leader) {
		Survivor survivor = Survivors.spawn(helper.getLevel(), helper.absolutePos(rel), SurvivorRole.SETTLER, leader);
		helper.assertTrue(survivor != null, "survivor spawned");
		return survivor;
	}

	private static void remove(ServerPlayer player) {
		player.level().getServer().getPlayerList().remove(player);
	}

	private static String nameplateKey(Survivor survivor) {
		return survivor.getCustomName() != null && survivor.getCustomName().getContents() instanceof TranslatableContents t
				? t.getKey() : "";
	}

	@GameTest(maxTicks = 200)
	public void zombieTargetsSurvivor(GameTestHelper helper) {
		floor(helper);
		Survivor survivor = survivor(helper, new BlockPos(1, 1, 1), null);
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new Vec3(5.5, 1, 5.5));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the test's daylight
		helper.assertTrue(HuntSurvivorsGoal.nearestSurvivor(zombie, 16) == survivor, "the zombie does not see the survivor");
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie.getTarget() == survivor, "zombie target is " + zombie.getTarget());
			survivor.discard();
		});
	}

	@GameTest
	public void untreatedBittenSurvivorTurns(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Survivor survivor = survivor(helper, new BlockPos(2, 1, 2), null);
		UUID id = survivor.getUUID();
		Zombie biter = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(5.5, 1, 5.5));
		helper.assertFalse(SurvivorInfection.onZombieHit(survivor, biter, 0.999f), "a high roll must not infect");
		helper.assertTrue(SurvivorInfection.onZombieHit(survivor, biter, 0f), "a zero roll must infect");
		helper.assertTrue(survivor.infected(), "the survivor is bitten");
		helper.assertValueEqual(nameplateKey(survivor), "entity.ashfall.survivor.nameplate_bitten", "nameplate");
		helper.assertTrue(SurvivorRoster.get(level.getServer()).contains(id), "a living survivor is on the roster");
		biter.discard();

		SurvivorInfection.advance(level, survivor, SurvivorInfection.TURN_TICKS - 1);
		helper.assertTrue(survivor.isAlive(), "turned a tick early");
		SurvivorInfection.advance(level, survivor, 1);
		helper.assertTrue(survivor.isRemoved(), "the untreated survivor did not turn");
		helper.assertFalse(SurvivorRoster.get(level.getServer()).contains(id), "a turned survivor stays on the roster");
		List<Zombie> turned = level.getEntitiesOfClass(Zombie.class, new AABB(helper.absolutePos(new BlockPos(2, 1, 2))).inflate(1.5),
				z -> DirectorSpawns.tagOf(z) != null);
		helper.assertTrue(turned.size() == 1, "expected one Director zombie where the survivor stood, found " + turned.size());
		helper.assertValueEqual(DirectorSpawns.tagOf(turned.get(0)).variant(), SurvivorInfection.TURNED_VARIANT, "turned variant");
		turned.forEach(Zombie::discard);
		helper.succeed();
	}

	@GameTest
	public void antibioticsCureBittenSurvivor(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.setGameMode(GameType.SURVIVAL);
			Survivor survivor = survivor(helper, new BlockPos(2, 1, 2), null);
			SurvivorInfection.infect(survivor);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DirectorItems.ANTIBIOTICS, 2));
			survivor.interact(player, InteractionHand.MAIN_HAND, survivor.position());
			helper.assertFalse(survivor.infected(), "Antibiotics did not cure the survivor");
			helper.assertValueEqual(nameplateKey(survivor), "entity.ashfall.survivor.nameplate", "nameplate after the cure");
			helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Antibiotics used");
			survivor.interact(player, InteractionHand.MAIN_HAND, survivor.position());
			helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Antibiotics wasted on a healthy survivor");
			survivor.discard();
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void playersNeverHurtSurvivors(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Player leader = helper.makeMockPlayer(GameType.SURVIVAL);
		Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
		Survivor survivor = survivor(helper, new BlockPos(1, 1, 1), leader.getUUID());
		float full = survivor.getHealth();
		Wolf dog = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new Vec3(3.5, 1, 3.5));
		dog.tame(stranger);

		EnergyBolt bolt = EnergyBolt.create(level, stranger, 4f, 1f, 32f, 0x3cc8ff);
		Nail nail = new Nail(level, leader, new ItemStack(ArmsItems.NAILS));
		DamageSource[] friendly = {
				level.damageSources().playerAttack(leader),
				level.damageSources().playerAttack(stranger),
				new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(EnergyBolt.DAMAGE_TYPE),
						bolt, stranger),
				level.damageSources().thrown(nail, leader),
				level.damageSources().mobAttack(dog),
				level.damageSources().explosion(null, stranger),
				level.damageSources().source(SpikeStripBlock.DAMAGE),
		};
		for (DamageSource source : friendly) {
			helper.assertFalse(survivor.hurtServer(level, source, 4f), "a survivor was hurt by " + source.getMsgId());
			helper.assertTrue(survivor.getHealth() == full, "survivor health fell to " + survivor.getHealth() + " from " + source.getMsgId());
		}
		BlockPos strip = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.setBlock(new BlockPos(1, 1, 1), DirectorBlocks.SPIKE_STRIP);
		SpikeStripBlock.stepOn(level.getBlockState(strip), level, strip, survivor);
		helper.assertTrue(survivor.getHealth() == full, "the spike strip hurt a survivor");

		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 1, 2.5));
		helper.assertTrue(survivor.hurtServer(level, level.damageSources().mobAttack(zombie), 2f), "a zombie's hit must land");
		helper.assertTrue(survivor.getHealth() < full, "a zombie's hit did no damage");
		bolt.discard();
		nail.discard();
		survivor.discard();
		helper.succeed();
	}

	@GameTest
	public void survivorCapsHold(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		UUID owner = player.getUUID();
		BlockPos core = helper.absolutePos(new BlockPos(0, 1, 0));
		// No OPAC in tests: the player's own id is their owner key. A 1-chunk radius covers the whole test.
		HavenState.get(server).putCore(new BunkerCoreRecord(owner, owner, Level.OVERWORLD, core, 1, List.of()));
		List<Survivor> group = new ArrayList<>();
		try {
			for (int i = 0; i < SurvivorBalance.get().caps().base(); i++) {
				Survivor s = survivor(helper, new BlockPos(1 + i, 1, 3), owner);
				helper.assertValueEqual(s.settle(level), Survivor.Settle.SETTLED, "settling survivor " + (i + 1));
				group.add(s);
			}
			helper.assertValueEqual(SurvivorRoster.get(server).homedAt(level.dimension(), core), SurvivorBalance.get().caps().base(), "settlers at the core");
			helper.assertValueEqual(SurvivorRoster.get(server).groupOf(server, owner), SurvivorBalance.get().caps().base(), "survivors in the group");

			Survivor extra = survivor(helper, new BlockPos(2, 1, 5), null);
			extra.setLeader(owner);
			group.add(extra);
			helper.assertValueEqual(extra.settle(level), Survivor.Settle.FULL, "a seventh settler");
			helper.assertTrue(extra.home() == null, "the seventh settler got a home anyway");

			// A rescue goes to the nearest player with room, so the beacon sits high above the test, out of reach of
			// a neighbouring test's player.
			BlockPos beacon = helper.absolutePos(new BlockPos(5, 80, 6));
			player.snapTo(beacon.getX() + 0.5, beacon.getY(), beacon.getZ() + 1.5);
			helper.assertTrue(SurvivorRescue.atBeacon(level, beacon, SurvivorBalance.get().rescueFirstDay()) == null,
					"a full group still got a rescued survivor");
		} finally {
			group.forEach(Survivor::discard);
			HavenState.get(server).removeCore(owner);
			remove(player);
		}
		helper.assertFalse(SurvivorRoster.get(server).contains(group.get(0).getUUID()), "a discarded survivor stays on the roster");
		helper.succeed();
	}
}
