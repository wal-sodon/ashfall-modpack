package xyz.toesinthesand.ashfall.director.test;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import xyz.toesinthesand.ashfall.director.claims.Claims;
import xyz.toesinthesand.ashfall.director.fortify.Fortified;
import xyz.toesinthesand.ashfall.director.haven.BunkerBeds;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreRecord;
import xyz.toesinthesand.ashfall.director.haven.BunkerCores;
import xyz.toesinthesand.ashfall.director.haven.ChunkArea;
import xyz.toesinthesand.ashfall.director.haven.HavenBlocks;
import xyz.toesinthesand.ashfall.director.haven.HavenSettings;
import xyz.toesinthesand.ashfall.director.haven.HavenState;
import xyz.toesinthesand.ashfall.director.haven.Havens;
import xyz.toesinthesand.ashfall.director.haven.HordeSiren;
import xyz.toesinthesand.ashfall.director.haven.HordeSnapshot;
import xyz.toesinthesand.ashfall.director.haven.RespawnChoice;
import xyz.toesinthesand.ashfall.director.haven.SafeZone;
import xyz.toesinthesand.ashfall.director.haven.SafeZones;
import xyz.toesinthesand.ashfall.director.haven.SirenLogic;
import xyz.toesinthesand.ashfall.director.spawn.SpawnDirector;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Havens on a real server (OPAC is not on the gametest classpath, so these exercise the fallback exclusion
 * regions, which are what protects a world without OPAC and back up OPAC when it refuses a claim).
 * <p>
 * Every test that changes global haven state (a temporary safe zone, a Bunker Core, a fake horde schedule)
 * sets it up, checks and tears it down inside one synchronous block, so tests ticking alongside never see it.
 */
public class HavenGameTest {
	private static final String TEMP_ZONE = "gametest_zone";

	/** A closed 3x3 stone room (inner cell at relative 2,1,2, two blocks tall) with no light inside. */
	private static BlockPos darkRoom(GameTestHelper helper) {
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				for (int y = 0; y <= 3; y++) {
					boolean inner = x == 2 && z == 2 && (y == 1 || y == 2);
					helper.setBlock(x, y, z, inner ? Blocks.AIR : Blocks.STONE);
				}
			}
		}
		return helper.absolutePos(new BlockPos(2, 1, 2));
	}

	@GameTest
	public void montfordSafehouseIsASafeZone(GameTestHelper helper) {
		SafeZone zone = SafeZones.datapack().get("ashfall:montford_safehouse");
		helper.assertTrue(zone != null, "montford_safehouse.json not loaded");
		helper.assertValueEqual(zone.area(), new ChunkArea(-10, -50, 1), "safehouse area");
		helper.assertTrue(zone.useWorldSpawn(), "safehouse respawns at the world spawn (its front hall)");
		ServerLevel level = helper.getLevel();
		BlockPos hall = new BlockPos(-154, 70, -788);
		helper.assertTrue(Havens.inSafeZone(level, hall), "safehouse hall is not in a safe zone");
		helper.assertTrue(Claims.isClaimed(level, hall), "safehouse hall is not excluded from spawns");
		helper.assertFalse(Havens.inSafeZone(level, new BlockPos(-120, 70, -790)), "the pharmacy must stay outside");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void noDirectorSpawnsOrBreakingInASafeZone(GameTestHelper helper) {
		BlockPos pos = darkRoom(helper);
		helper.runAfterDelay(10, () -> {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(SpawnDirector.spotWeight(level, pos) > 0, "dark room is not dark");
			helper.assertTrue(SpawnDirector.check(level, pos) != SpawnDirector.Verdict.CLAIM, "claimed before the zone exists");
			HavenState state = HavenState.get(level.getServer());
			state.putCustomZone(TEMP_ZONE, new SafeZone("Gametest", Level.OVERWORLD, ChunkArea.around(pos.getX(), pos.getZ(), 0),
					Optional.of(pos), false, Optional.empty()));
			Havens.rebuild(level.getServer());
			try {
				helper.assertValueEqual(SpawnDirector.check(level, pos), SpawnDirector.Verdict.CLAIM, "verdict in a safe zone");
				for (int i = 0; i < 100; i++) {
					helper.assertTrue(SpawnDirector.tryAmbientSpawn(level, pos, 30, level.getRandom()) == null, "spawned in a safe zone");
				}
				helper.assertFalse(Fortified.mayBreak(level, pos, Blocks.OAK_PLANKS.defaultBlockState(), EntityTypes.ZOMBIE),
						"zombies may not break anything in a safe zone");
				helper.assertFalse(Fortified.mayBuild(level, pos), "zombies may not build in a safe zone");
				helper.assertValueEqual(Havens.respawnPoint(level, SafeZones.active(level.getServer()).get(TEMP_ZONE)), pos,
						"explicit respawn point");
			} finally {
				state.removeCustomZone(TEMP_ZONE);
				Havens.rebuild(level.getServer());
			}
			helper.assertTrue(SpawnDirector.check(level, pos) != SpawnDirector.Verdict.CLAIM, "zone removal left an exclusion");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void bunkerCorePlacementCreatesTheExclusion(GameTestHelper helper) {
		BlockPos room = darkRoom(helper);
		BlockPos corePos = helper.absolutePos(new BlockPos(0, 1, 0));
		helper.runAfterDelay(10, () -> {
			ServerLevel level = helper.getLevel();
			Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
			UUID id = placer.getUUID();
			helper.assertTrue(BunkerCores.mayPlace(level.getServer(), id), "fresh player may place a core");
			helper.setBlock(new BlockPos(0, 1, 0), HavenBlocks.BUNKER_CORE);
			try {
				HavenBlocks.BUNKER_CORE.setPlacedBy(level, corePos, HavenBlocks.BUNKER_CORE.defaultBlockState(), placer,
						new ItemStack(HavenBlocks.BUNKER_CORE_ITEM));
				BunkerCoreRecord core = HavenState.get(level.getServer()).coreAt(level.dimension(), corePos);
				helper.assertTrue(core != null, "placing the core did not register it");
				helper.assertValueEqual(core.radius(), HavenSettings.get().bunkerRadius(), "claim radius from settings");
				helper.assertValueEqual(SpawnDirector.check(level, room), SpawnDirector.Verdict.CLAIM, "spawn verdict next to the core");
				BlockPos edge = corePos.offset(16 * core.radius(), 0, 0);
				helper.assertTrue(Claims.isClaimed(level, edge), "the far chunk of the radius is not covered");
				helper.assertFalse(Claims.isClaimed(level, corePos.offset(16 * (core.radius() + 1) + 16, 0, 0)), "covered past the radius");
				helper.assertFalse(Havens.inSafeZone(level, corePos), "a bunker is a haven, not a safe zone");
				helper.assertTrue(Fortified.mayBreak(level, room, Blocks.OAK_PLANKS.defaultBlockState(), EntityTypes.ZOMBIE),
						"partial protection: hordes still break plain blocks at a bunker");
				helper.assertFalse(Fortified.mayBreak(level, corePos, HavenBlocks.BUNKER_CORE.defaultBlockState(), EntityTypes.ZOMBIE),
						"the core itself is fortified");
				helper.assertFalse(BunkerCores.mayPlace(level.getServer(), id), "a second core for the same player");
				helper.assertTrue(BunkerCores.forPlayer(level.getServer(), id).isPresent(), "core is not a respawn option");
			} finally {
				helper.setBlock(new BlockPos(0, 1, 0), Blocks.AIR);
			}
			helper.assertTrue(HavenState.get(level.getServer()).coreAt(level.dimension(), corePos) == null, "breaking kept the record");
			helper.assertTrue(SpawnDirector.check(level, room) != SpawnDirector.Verdict.CLAIM, "breaking kept the exclusion");
			helper.assertTrue(BunkerCores.mayPlace(level.getServer(), id), "may place again after breaking");
			helper.succeed();
		});
	}

	@GameTest
	public void respawnProtectionTimerAndCancelOnAttack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = mockPlayer(level);
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		try {
			int ticks = HavenSettings.get().respawnProtectionTicks();
			helper.assertValueEqual(ticks, 200, "default protection is 10 s");
			// the mock joins at the world spawn; the drop-target sweep only reaches zombies near the player
			player.teleportTo(level, zombie.getX() + 2, zombie.getY(), zombie.getZ(), java.util.Set.of(), 0, 0, true);
			zombie.setTarget(player);
			helper.assertTrue(zombie.getTarget() == player, "setup: zombie should target the unprotected player");
			RespawnChoice.protect(player, ticks);
			helper.assertTrue(RespawnChoice.isProtected(player), "not protected after protect()");
			helper.assertValueEqual(RespawnChoice.remaining(player), ticks, "full duration");
			MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
			helper.assertTrue(resistance != null && resistance.getAmplifier() == 4, "no Resistance V");
			helper.assertTrue(zombie.getTarget() == null, "a zombie already chasing kept its target");
			zombie.setTarget(player);
			helper.assertTrue(zombie.getTarget() == null, "a zombie could target a protected player");
			zombie.hurtServer(level, level.damageSources().playerAttack(player), 1.0f);
			helper.assertFalse(RespawnChoice.isProtected(player), "attacking did not cancel protection");
			helper.assertFalse(player.hasEffect(MobEffects.RESISTANCE), "cancel left the Resistance on");
			zombie.setTarget(player);
			helper.assertTrue(zombie.getTarget() == player, "zombies still ignore an unprotected player");
		} finally {
			zombie.discard();
			player.level().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void sirenFollowsAFakeHordeSchedule(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		AtomicReference<HordeSnapshot> horde = new AtomicReference<>(new HordeSnapshot(false, 1, 0));
		HordeSiren.setSourceForTesting(s -> horde.get());
		try {
			SirenLogic.Step quiet = HordeSiren.update(server, 6000);
			helper.assertValueEqual(quiet.phase(), SirenLogic.Phase.IDLE, "midday is before the warning window");
			SirenLogic.Step warning = HordeSiren.update(server, 24000L * 3 + 11000);
			helper.assertValueEqual(warning.phase(), SirenLogic.Phase.WARNING, "an hour before nightfall");
			helper.assertTrue(warning.siren(), "no siren when the warning starts");
			helper.assertValueEqual(warning.ticksToNightfall(), 1000L, "countdown");
			helper.assertTrue(HordeSiren.barForTesting() != null, "no countdown bar");
			horde.set(new HordeSnapshot(true, 0, 0));
			SirenLogic.Step night = HordeSiren.update(server, 24000L * 3 + 12000);
			helper.assertValueEqual(night.phase(), SirenLogic.Phase.HORDE, "the horde flag starts the horde phase");
			helper.assertTrue(night.siren(), "no siren when the horde night starts");
			horde.set(new HordeSnapshot(false, 7, 0));
			helper.assertValueEqual(HordeSiren.update(server, 24000L * 4 + 1000).phase(), SirenLogic.Phase.IDLE, "morning");
			helper.assertTrue(HordeSiren.barForTesting().getPlayers().isEmpty(), "bar still shown after the horde");
		} finally {
			HordeSiren.setSourceForTesting(null);
		}
		helper.succeed();
	}

	/** A survival mock player in the player list (removed by the caller), as in {@code HydrationGameTest}. */
	/** Beds work only inside the player's own Bunker Core claim (10.3.1); the game rule turns that off. */
	@GameTest
	public void bedsOnlyWorkInsideYourBunkerClaim(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = mockPlayer(level);
		BlockPos corePos = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.setBlock(new BlockPos(1, 1, 1), HavenBlocks.BUNKER_CORE);
		UUID owner = player.getUUID();
		try {
			BlockPos nearBed = corePos.offset(2, 0, 0);
			BlockPos farBed = corePos.offset(400, 0, 0);
			helper.assertTrue(BunkerBeds.problem(player, nearBed) != null, "no core yet: every bed refuses");
			HavenState.get(level.getServer()).putCore(new BunkerCoreRecord(owner, owner, Level.OVERWORLD, corePos, 1, List.of()));
			helper.assertTrue(BunkerBeds.problem(player, nearBed) == null, "a bed inside the core's claim works");
			helper.assertTrue(BunkerBeds.problem(player, farBed) != null, "a bed outside the claim refuses");
			level.getGameRules().set(BunkerBeds.ENABLED, false, level.getServer());
			try {
				helper.assertTrue(BunkerBeds.problem(player, farBed) == null, "with the rule off any bed works");
			} finally {
				level.getGameRules().set(BunkerBeds.ENABLED, true, level.getServer());
			}
		} finally {
			HavenState.get(level.getServer()).removeCore(owner);
			level.getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	private static ServerPlayer mockPlayer(ServerLevel level) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "haven-mock"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.getAbilities().invulnerable = false;
		return player;
	}
}
