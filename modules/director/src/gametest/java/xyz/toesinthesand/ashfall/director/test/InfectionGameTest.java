package xyz.toesinthesand.ashfall.director.test;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.director.claims.Claims;
import xyz.toesinthesand.ashfall.director.haven.ChunkArea;
import xyz.toesinthesand.ashfall.director.haven.HavenState;
import xyz.toesinthesand.ashfall.director.haven.Havens;
import xyz.toesinthesand.ashfall.director.haven.SafeZone;
import xyz.toesinthesand.ashfall.director.infection.Infection;
import xyz.toesinthesand.ashfall.director.infection.InfectionData;
import xyz.toesinthesand.ashfall.director.infection.InfectionRules;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.story.StoryFlags;

import java.util.Optional;
import java.util.UUID;

/**
 * Staged infection on a real server, without Contagion (Ashfall's timeline must stand on its own): stage
 * progression over simulated time, symptoms and the stage-4 death, Bandage and Antibiotics through the real
 * consume path, the Sever-ending cure, zombie-hit chances by variant, an infected death dropping items like any
 * other death, and the testing commands.
 */
public class InfectionGameTest {
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
		// a real client reports "loaded" after joining; until then players can't be hurt at all
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		// the test server's default game mode may be creative; abilities come from it, not from gameMode()
		player.getAbilities().invulnerable = false;
		player.getAbilities().mayfly = false;
		player.getAbilities().flying = false;
		player.getAbilities().instabuild = false;
		return player;
	}

	private static void remove(ServerPlayer player) {
		player.level().getServer().getPlayerList().remove(player);
	}

	private static InfectionRules rules() {
		return Infection.rules();
	}

	private static long seconds(int s) {
		return s * 20L;
	}

	@GameTest
	public void stagesProgressOverSimulatedTime(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "infected-mock");
		try {
			helper.assertValueEqual(Infection.stage(player), 0, "healthy stage");
			helper.assertTrue(Infection.infect(player), "a healthy player gets infected");
			helper.assertTrue(!Infection.infect(player), "a second infection does not restart the clock");
			helper.assertValueEqual(Infection.stage(player), 1, "fresh infection");
			helper.assertValueEqual(player.getAttachedOrElse(Infection.STAGE, -1), 1, "synced HUD stage");

			long s2 = rules().stage(2).startsAtTicks();
			Infection.advance(player, s2 - 1);
			helper.assertValueEqual(Infection.stage(player), 1, "one tick before stage 2");
			Infection.advance(player, 1);
			helper.assertValueEqual(Infection.stage(player), 2, "stage 2 on time");
			Infection.advance(player, rules().stage(3).startsAtTicks() - s2);
			helper.assertValueEqual(Infection.stage(player), 3, "stage 3 on time");
			Infection.advance(player, rules().stage(4).startsAtTicks() - rules().stage(3).startsAtTicks());
			helper.assertValueEqual(Infection.stage(player), 4, "stage 4 on time");
			helper.assertValueEqual(player.getAttachedOrElse(Infection.STAGE, -1), 4, "synced HUD stage at 4");
			helper.assertTrue(player.isAlive() && !rules().isFatal(Infection.get(player)), "stage 4 is not yet death");

			// the tick loop is what moves time for real
			long before = Infection.get(player).progress();
			Infection.tick(player, 1);
			helper.assertValueEqual(Infection.get(player).progress(), before + 1, "one tick of progress");
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void symptomsFollowTheStage(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "symptom-mock");
		try {
			RandomSource sure = RandomSource.create(1);
			Infection.applySymptoms(player, rules().stage(2), sure);
			helper.assertTrue(player.getEffect(MobEffects.SLOWNESS) == null, "stage 2 does not slow");
			Infection.applySymptoms(player, rules().stage(3), sure);
			MobEffectInstance slow = player.getEffect(MobEffects.SLOWNESS);
			helper.assertTrue(slow != null && slow.getAmplifier() == 0 && slow.isAmbient(), "stage 3 slowness I, ambient: " + slow);
			helper.assertTrue(player.getEffect(MobEffects.WEAKNESS) != null, "stage 3 weakness");
			player.removeAllEffects();
			Infection.applySymptoms(player, rules().stage(4), sure);
			MobEffectInstance slow4 = player.getEffect(MobEffects.SLOWNESS);
			helper.assertTrue(slow4 != null && slow4.getAmplifier() == 1, "stage 4 slowness II: " + slow4);
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void untreatedStageFourKills(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "fatal-mock");
		try {
			Infection.setStage(player, 4);
			Infection.advance(player, rules().deathAt() - Infection.get(player).progress() - 1);
			Infection.tick(player, 1);
			helper.assertTrue(!player.isAlive(), "the infection kills when the stage 4 timeout runs out");
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void antibioticsKnockBackAStageAndCureStageOne(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "antibiotics-mock");
		try {
			Infection.setStage(player, 3);
			ItemStack pills = new ItemStack(DirectorItems.ANTIBIOTICS, 3);
			player.setItemInHand(InteractionHand.MAIN_HAND, pills);
			pills.finishUsingItem(helper.getLevel(), player);
			helper.assertValueEqual(pills.getCount(), 2, "one dose used");
			InfectionData after = Infection.get(player);
			helper.assertValueEqual(rules().stageAt(after), 2, "stage 3 knocked back to 2");
			helper.assertValueEqual(after.progress(), (long) rules().stage(2).startsAtTicks(), "at the start of stage 2");
			helper.assertValueEqual(after.pauseTicks(), rules().treatments().antibioticPauseTicks(), "and paused");

			Infection.advance(player, after.pauseTicks());
			helper.assertValueEqual(Infection.get(player).progress(), after.progress(), "no progress while paused");

			pills.finishUsingItem(helper.getLevel(), player);
			helper.assertValueEqual(Infection.stage(player), 1, "stage 2 knocked back to 1, still infected");
			pills.finishUsingItem(helper.getLevel(), player);
			helper.assertTrue(!Infection.isInfected(player), "antibiotics cure stage 1");
			helper.assertValueEqual(player.getAttachedOrElse(Infection.STAGE, -1), 0, "HUD stage cleared");
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	/** The real right-click path: start using, hold for the use time, and one Bandage from the stack is spent. */
	@GameTest
	public void bandageIsUsedUpByHoldingUse(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "bandage-use-mock");
		try {
			player.setHealth(10f);
			ItemStack stack = new ItemStack(DirectorItems.BANDAGE, 3);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			var result = player.gameMode.useItem(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND);
			helper.assertTrue(result.consumesAction(), "right-click should start using the bandage, got " + result);
			helper.assertTrue(player.isUsingItem(), "the player should be holding the bandage up");
			for (int t = 0; t < 60 && player.isUsingItem(); t++) {
				player.doTick();
			}
			helper.assertFalse(player.isUsingItem(), "using should finish after the use time");
			helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "one bandage used up");
			// natural regeneration also ticks while the bandage is held, so the heal is a floor
			helper.assertTrue(player.getHealth() >= 10f + rules().treatments().bandageHeal(), "bandage heals, health " + player.getHealth());
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	/**
	 * The same, standing in a safe zone (the safehouse is one). With OPAC loaded (-PashfallPackTest) the zone is a real
	 * server claim with item use protected, so this is where a claim rule that blocks medical items would show.
	 */
	@GameTest
	public void bandageWorksInASafeZone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "bandage-zone-mock");
		BlockPos pos = player.blockPosition();
		HavenState state = HavenState.get(level.getServer());
		state.putCustomZone("gametest_bandage", new SafeZone("Gametest", Level.OVERWORLD, ChunkArea.around(pos.getX(), pos.getZ(), 0),
				Optional.of(pos), false, Optional.empty()));
		Havens.rebuild(level.getServer());
		try {
			helper.assertTrue(Claims.isClaimed(level, pos), "the test spot should be claimed");
			player.setHealth(10f);
			ItemStack stack = new ItemStack(DirectorItems.BANDAGE, 3);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			var result = player.gameMode.useItem(player, level, stack, InteractionHand.MAIN_HAND);
			helper.assertTrue(result.consumesAction(), "right-click in a safe zone should start the bandage, got " + result);
			for (int t = 0; t < 60 && player.isUsingItem(); t++) {
				player.doTick();
			}
			helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "one bandage used up in a safe zone");
			helper.assertTrue(player.getHealth() >= 10f + rules().treatments().bandageHeal(), "bandage heals, health " + player.getHealth());
		} finally {
			remove(player);
			state.removeCustomZone("gametest_bandage");
			Havens.rebuild(level.getServer());
		}
		helper.succeed();
	}

	@GameTest
	public void bandageHealsAndPausesProgress(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "bandage-mock");
		try {
			player.setHealth(10f);
			Infection.infect(player);
			Infection.advance(player, 100);
			ItemStack bandage = new ItemStack(DirectorItems.BANDAGE);
			player.setItemInHand(InteractionHand.MAIN_HAND, bandage);
			bandage.finishUsingItem(helper.getLevel(), player);
			helper.assertValueEqual(player.getHealth(), 10f + rules().treatments().bandageHeal(), "bandage heals");
			int pause = rules().treatments().bandagePauseTicks();
			helper.assertValueEqual(Infection.get(player).pauseTicks(), pause, "bandage pause");
			Infection.advance(player, pause);
			helper.assertValueEqual(Infection.get(player).progress(), 100L, "progress held during the pause");
			Infection.advance(player, 20);
			helper.assertValueEqual(Infection.get(player).progress(), 120L, "progress resumes after it");
			helper.assertValueEqual(Infection.stage(player), 1, "a bandage never cures");
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void severEndingLetsAntibioticsCureAnyStage(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		StoryFlags flags = StoryFlags.get(server);
		boolean wasSet = flags.has(InfectionRules.SEVER_FLAG);
		ServerPlayer player = survivalPlayer(helper, "sever-mock");
		try {
			CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
			CommandSourceStack op = server.createCommandSourceStack().withSuppressedOutput();
			dispatcher.execute("ashfall story reset", op);
			helper.assertTrue(!InfectionRules.severEnding(server), "no Sever in a fresh run");

			Infection.setStage(player, 4);
			Infection.treatWithAntibiotics(player);
			helper.assertValueEqual(Infection.stage(player), 3, "before Sever, stage 4 only drops to 3");

			dispatcher.execute("ashfall story sever", op);
			helper.assertTrue(InfectionRules.severEnding(server), "the command sets the Sever flag");
			Infection.setStage(player, 4);
			Infection.treatWithAntibiotics(player);
			helper.assertTrue(!Infection.isInfected(player), "after Sever, antibiotics cure stage 4");

			dispatcher.execute("ashfall story reset", op);
			helper.assertTrue(!InfectionRules.severEnding(server), "reset clears the flag");
		} catch (CommandSyntaxException e) {
			throw helper.assertionException("story command failed: " + e.getMessage());
		} finally {
			flags.set(InfectionRules.SEVER_FLAG, wasSet);
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void zombieHitChanceFollowsVariant(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "bitten-mock");
		Zombie patient = helper.spawn(EntityTypes.ZOMBIE, new Vec3(1.5, 2, 1.5));
		Zombie stray = helper.spawn(EntityTypes.ZOMBIE, new Vec3(3.5, 2, 1.5));
		try {
			patient.setAttached(DirectorSpawns.TAG, new DirectorZombie(DirectorZombie.Source.AMBIENT, "patient", DirectorZombie.NO_BUILDING));
			String zone = xyz.toesinthesand.ashfall.director.spawn.DirectorContexts.zoneAt(player.blockPosition()).id();
			float patientChance = rules().sources().chance("patient", zone, player.getArmorValue());
			float strayChance = rules().sources().chance(null, zone, player.getArmorValue());
			helper.assertValueEqual(Infection.hitChance(player, patient), patientChance, "patient chance");
			helper.assertValueEqual(Infection.hitChance(player, stray), strayChance, "untagged zombie chance");
			helper.assertTrue(patientChance > strayChance, "patients infect more often");

			helper.assertTrue(!Infection.onZombieHit(player, stray, strayChance), "a roll of exactly the chance misses");
			helper.assertTrue(!Infection.isInfected(player), "still healthy");
			helper.assertTrue(Infection.onZombieHit(player, patient, patientChance - 0.001f), "a roll under the chance infects");
			helper.assertValueEqual(Infection.stage(player), 1, "a bite starts at stage 1");
		} finally {
			remove(player);
			patient.discard();
			stray.discard();
		}
		helper.succeed();
	}

	/**
	 * An infected death is an ordinary death (10.3.0 dropped the rising): the player dies, and what they carried
	 * drops where they fell like any other death, with nothing kept back for a zombie to wear.
	 */
	@GameTest
	public void infectedDeathDropsItems(GameTestHelper helper) {
		ServerPlayer infected = survivalPlayer(helper, "infected-death");
		try {
			Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 2)));
			infected.teleportTo(at.x, at.y, at.z);
			infected.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 3));
			Infection.setStage(infected, 4);
			Infection.succumb(infected);
			helper.assertTrue(!infected.isAlive(), "the infected player did not die");
			helper.assertTrue(infected.getInventory().isEmpty(), "the infected player kept their items");
			int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(4.0),
					item -> item.getItem().is(Items.IRON_INGOT)).stream().mapToInt(item -> item.getItem().getCount()).sum();
			helper.assertValueEqual(dropped, 3, "iron dropped where the infected player died");
		} finally {
			remove(infected);
		}
		helper.succeed();
	}

	@GameTest
	public void infectionCommandGetSetClear(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String name = "infcmdmock";
		ServerPlayer player = survivalPlayer(helper, name);
		try {
			CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
			CommandSourceStack op = server.createCommandSourceStack().withSuppressedOutput();
			helper.assertValueEqual(dispatcher.execute("ashfall infection get " + name, op), 0, "healthy");
			helper.assertValueEqual(dispatcher.execute("ashfall infection set " + name + " 3", op), 3, "set result");
			helper.assertValueEqual(Infection.stage(player), 3, "stage after set");
			helper.assertValueEqual(dispatcher.execute("ashfall infection get " + name, op), 3, "get result");
			dispatcher.execute("ashfall infection clear " + name, op);
			helper.assertTrue(!Infection.isInfected(player), "clear cures");

			CommandSourceStack plain = player.createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS).withSuppressedOutput();
			boolean refused = false;
			try {
				dispatcher.execute("ashfall infection set " + name + " 4", plain);
			} catch (CommandSyntaxException expected) {
				refused = true;
			}
			helper.assertTrue(refused && !Infection.isInfected(player), "non-ops can't infect");
		} catch (CommandSyntaxException e) {
			throw helper.assertionException("infection command failed: " + e.getMessage());
		} finally {
			remove(player);
		}
		helper.succeed();
	}
}
