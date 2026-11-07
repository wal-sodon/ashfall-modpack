package xyz.toesinthesand.ashfall.director.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.fortify.Fortified;
import xyz.toesinthesand.ashfall.director.infection.Infection;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.Stimulus;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;
import xyz.toesinthesand.ashfall.director.special.AcidSpit;
import xyz.toesinthesand.ashfall.director.special.Bloated;
import xyz.toesinthesand.ashfall.director.special.Brute;
import xyz.toesinthesand.ashfall.director.special.Crawler;
import xyz.toesinthesand.ashfall.director.special.InfectionClouds;
import xyz.toesinthesand.ashfall.director.special.Screamer;
import xyz.toesinthesand.ashfall.director.special.SpecialEntities;
import xyz.toesinthesand.ashfall.director.special.SpecialInfected;
import xyz.toesinthesand.ashfall.director.special.SpecialInfectedEntity;
import xyz.toesinthesand.ashfall.director.special.SpecialInfectedRules;
import xyz.toesinthesand.ashfall.director.special.SpecialKind;
import xyz.toesinthesand.ashfall.director.special.Spitter;
import xyz.toesinthesand.ashfall.director.special.Stalker;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;

import java.util.List;
import java.util.UUID;

/**
 * The special infected on a real server: each kind spawns through the Director path with its file's stats and tag,
 * and does its one thing (the bloated's cloud, the screamer's shriek, the spitter's acid, the brute's bulk against
 * fortified blocks, the stalker hiding in the dark). The screamer test gets a wide padding because its shriek is a
 * 48-block SOUND that must not reach other tests' zombies.
 */
public class SpecialInfectedGameTest {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	@GameTest
	public void everySpecialSpawnsAsATaggedDirectorZombie(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(SpecialInfectedRules.all().size() == SpecialKind.values().length,
				"every kind has a special_infected file loaded, got " + SpecialInfectedRules.all().keySet());
		int i = 0;
		for (SpecialKind kind : SpecialKind.values()) {
			BlockPos pos = helper.absolutePos(new BlockPos(1 + (i % 3) * 3, 1, 1 + (i / 3) * 4));
			SpecialInfectedEntity special = SpecialInfected.spawn(level, pos, kind, DirectorZombie.Source.AMBIENT);
			helper.assertTrue(special != null, kind + " did not spawn");
			helper.assertTrue(special.getType() == SpecialEntities.type(kind), kind + " has the wrong type " + special.getType());
			helper.assertTrue(special.kind() == kind, kind + " reports kind " + special.kind());
			DirectorZombie tag = DirectorSpawns.tagOf(special);
			helper.assertTrue(tag != null && tag.variant().equals(kind.id()), kind + " Director tag " + tag);
			helper.assertTrue(special.entityTags().contains(DirectorSpawns.VARIANT_TAG_PREFIX + kind.id()), kind + " variant entity tag");
			helper.assertTrue(special instanceof Zombie && special.getType().builtInRegistryHolder().is(EntityTypeTags.ZOMBIES),
					kind + " must be a zombie to ZB&B, senses and other mods");
			helper.assertTrue(!special.isBaby(), kind + " must never be a baby");
			double health = SpecialInfectedRules.get(kind).attributes()
					.getOrDefault(net.minecraft.resources.Identifier.withDefaultNamespace("max_health"), 20.0);
			helper.assertTrue(Math.abs(special.getMaxHealth() - health) < 1e-3, kind + " max health " + special.getMaxHealth() + " != " + health);
			i++;
		}
		helper.succeed();
	}

	@GameTest
	public void everySpecialHasALootTable(GameTestHelper helper) {
		var registries = helper.getLevel().getServer().reloadableRegistries();
		for (SpecialKind kind : SpecialKind.values()) {
			var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
					net.minecraft.resources.Identifier.fromNamespaceAndPath("ashfall", "entities/" + kind.id()));
			helper.assertTrue(registries.getLootTable(key) != net.minecraft.world.level.storage.loot.LootTable.EMPTY,
					"loot table " + key.identifier() + " did not load");
			helper.assertTrue(SpecialEntities.type(kind).getDefaultLootTable().filter(key::equals).isPresent(),
					kind + " uses " + SpecialEntities.type(kind).getDefaultLootTable());
		}
		helper.succeed();
	}

	@GameTest
	public void spawnEggPathAppliesTheFileToo(GameTestHelper helper) {
		floor(helper);
		Crawler crawler = helper.spawn(SpecialEntities.CRAWLER, new Vec3(2.5, 1, 2.5));
		helper.assertTrue(crawler.getBbHeight() < 1.0f, "a crawler fits under a one-block gap, height " + crawler.getBbHeight());
		helper.assertTrue(Math.abs(crawler.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) - 0.32) < 1e-6,
				"crawler speed " + crawler.getAttributeBaseValue(Attributes.MOVEMENT_SPEED));
		helper.succeed();
	}

	@GameTest
	public void bruteIsBigAndToughButFortifiedHolds(GameTestHelper helper) {
		floor(helper);
		Brute brute = (Brute) SpecialInfected.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)), SpecialKind.BRUTE,
				DirectorZombie.Source.SIEGE);
		helper.assertTrue(brute != null, "brute did not spawn");
		helper.assertTrue(brute.getMaxHealth() >= 60f, "brute health " + brute.getMaxHealth());
		helper.assertTrue(brute.getArmorValue() >= 8, "brute armour " + brute.getArmorValue());
		helper.assertTrue(Math.abs(brute.getScale() - 1.6f) < 1e-3, "brute scale " + brute.getScale());
		helper.assertTrue(brute.getAttributeValue(Attributes.ATTACK_KNOCKBACK) >= 2.0, "brute knockback");
		helper.assertTrue(Brute.blockBreakMultiplier(brute) > 1f, "brute breaks blocks faster");
		Zombie walker = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(1.5, 1, 1.5));
		helper.assertTrue(Brute.blockBreakMultiplier(walker) == 1f, "ordinary zombies keep ZB&B's own damage");
		helper.assertTrue(!Fortified.mayBreak(Blocks.IRON_BLOCK.defaultBlockState(), SpecialEntities.BRUTE),
				"a brute is not a boss: fortified blocks hold");
		helper.assertTrue(Fortified.mayBreak(Blocks.OAK_PLANKS.defaultBlockState(), SpecialEntities.BRUTE), "ordinary blocks break");
		// the hitbox follows the scale attribute once the entity has ticked
		helper.succeedWhen(() -> helper.assertTrue(brute.getBbHeight() > 3.0f, "brute is 1.6x tall, height " + brute.getBbHeight()));
	}

	@GameTest
	public void bloatedBurstsIntoAnInfectionCloud(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Bloated bloated = helper.spawnWithNoFreeWill(SpecialEntities.BLOATED, new Vec3(3.5, 1, 3.5));
		Vec3 at = bloated.position();
		bloated.kill(level);
		List<AreaEffectCloud> clouds = level.getEntitiesOfClass(AreaEffectCloud.class, bloated.getBoundingBox().inflate(3),
				c -> c.entityTags().contains(InfectionClouds.TAG));
		helper.assertTrue(clouds.size() == 1, "one infection cloud where the bloated died, got " + clouds.size());
		AreaEffectCloud cloud = clouds.getFirst();
		helper.assertTrue(cloud.position().distanceTo(at) < 0.5, "cloud at " + cloud.position() + ", bloated died at " + at);
		helper.assertTrue(cloud.getRadius() >= 3f, "cloud radius " + cloud.getRadius());
		helper.assertTrue(cloud.getDuration() > 100, "cloud lingers, duration " + cloud.getDuration());
		cloud.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void cloudInfectsAPlayerStandingInIt(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Bloated bloated = helper.spawnWithNoFreeWill(SpecialEntities.BLOATED, new Vec3(3.5, 1, 3.5));
		AreaEffectCloud cloud = bloated.burst(level);
		bloated.discard();
		ServerPlayer player = survivalPlayer(helper, "cloud_test");
		player.snapTo(cloud.getX(), cloud.getY(), cloud.getZ());
		// the cloud waits 10 ticks before it is live
		helper.runAfterDelay(15, () -> {
			try {
				helper.assertTrue(InfectionClouds.inCloud(player), "player at the burst point stands in the cloud");
				// The live cloud rolls on its own every second, so it may already have infected the player (a flaky
				// failure in the phase 6 build); clear that so the explicit rolls below test the chance alone.
				Infection.clear(player);
				helper.assertTrue(!Infection.tryInfect(player, InfectionClouds.chancePerSecond(), 0.999f), "a high roll does not infect");
				helper.assertTrue(Infection.tryInfect(player, InfectionClouds.chancePerSecond(), 0.0f), "a low roll infects");
				helper.assertTrue(Infection.isInfected(player), "player infected");
			} finally {
				Infection.clear(player);
				level.getServer().getPlayerList().remove(player);
				cloud.discard();
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100, padding = 64)
	public void screamerShriekPullsAndSpeedsTheHorde(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Screamer screamer = helper.spawn(SpecialEntities.SCREAMER, new Vec3(2.5, 1, 2.5));
		Zombie packmate = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(5.5, 1, 2.5));
		Villager prey = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new Vec3(2.5, 1, 6.5));
		screamer.setTarget(prey);
		helper.succeedWhen(() -> {
			helper.assertTrue(screamer.screamCooldown() > 0, "screamer has not shrieked at its prey yet");
			helper.assertTrue(packmate.hasEffect(MobEffects.SPEED), "a zombie next to the screamer got the speed buff");
			List<Stimulus> heard = Stimuli.query(level, screamer.getEyePosition().add(40, 0, 0), 1.0);
			helper.assertTrue(heard.stream().anyMatch(s -> s.type() == StimulusType.SOUND && s.sourceId() == screamer.getId()
					&& s.radius() >= 40), "the shriek is a SOUND heard 40 blocks away, got " + heard);
		});
	}

	@GameTest(maxTicks = 200)
	public void spitterAcidHurtsAndPoisons(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Spitter spitter = helper.spawnWithNoFreeWill(SpecialEntities.SPITTER, new Vec3(1.5, 1, 1.5));
		Villager prey = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new Vec3(6.5, 1, 6.5));
		AcidSpit spit = spitter.spitAt(level, prey);
		helper.assertTrue(spit != null && spit.getOwner() == spitter, "spitAt put an owned acid spit in the level");
		helper.assertTrue(spit.getType() == SpecialEntities.ACID_SPIT, "acid spit type");
		helper.assertTrue(spit.damage() > 1f, "acid hits harder than llama spit");
		helper.assertTrue(spitter.spitCooldown() > 0, "spitting starts the cooldown");
		float full = prey.getMaxHealth();
		helper.succeedWhen(() -> {
			helper.assertTrue(prey.getHealth() < full, "the villager has not been hit by acid yet");
			helper.assertTrue(prey.hasEffect(MobEffects.POISON), "acid poisons");
		});
	}

	@GameTest(maxTicks = 100)
	public void spitterFiresOnItsOwn(GameTestHelper helper) {
		floor(helper);
		Spitter spitter = helper.spawn(SpecialEntities.SPITTER, new Vec3(1.5, 1, 1.5));
		Villager prey = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new Vec3(6.5, 1, 6.5));
		spitter.setTarget(prey);
		helper.succeedWhen(() -> helper.assertTrue(spitter.spitCooldown() > 0, "the spitter has not spat at a target 7 blocks away"));
	}

	/** A closed stone box: no sky, no block light, room for a stalker. */
	private static void darkBox(GameTestHelper helper) {
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 6; z++) {
				for (int y = 0; y <= 4; y++) {
					boolean shell = x == 0 || x == 5 || z == 0 || z == 5 || y == 0 || y == 4;
					helper.setBlock(x, y, z, shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	@GameTest(maxTicks = 100)
	public void stalkerVanishesInTheDark(GameTestHelper helper) {
		darkBox(helper);
		ServerLevel level = helper.getLevel();
		Stalker stalker = helper.spawnWithNoFreeWill(SpecialEntities.STALKER, new Vec3(2.5, 1, 2.5));
		helper.succeedWhen(() -> {
			stalker.updateCover(level);
			helper.assertTrue(stalker.isInvisible(), "stalker unseen in the dark is still visible, light "
					+ level.getMaxLocalRawBrightness(stalker.blockPosition()));
		});
	}

	@GameTest(maxTicks = 100)
	public void stalkerCannotHideInTheLight(GameTestHelper helper) {
		darkBox(helper);
		helper.setBlock(3, 3, 3, Blocks.GLOWSTONE);
		ServerLevel level = helper.getLevel();
		Stalker stalker = helper.spawnWithNoFreeWill(SpecialEntities.STALKER, new Vec3(2.5, 1, 2.5));
		helper.runAfterDelay(20, () -> {
			stalker.updateCover(level);
			helper.assertTrue(!stalker.isInvisible(), "a lit stalker must be visible, light "
					+ level.getMaxLocalRawBrightness(stalker.blockPosition()));
			helper.succeed();
		});
	}

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
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		player.getAbilities().invulnerable = false;
		player.getAbilities().mayfly = false;
		player.getAbilities().flying = false;
		player.getAbilities().instabuild = false;
		return player;
	}
}
