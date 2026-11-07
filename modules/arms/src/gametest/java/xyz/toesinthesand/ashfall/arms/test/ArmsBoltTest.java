package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.entity.ArmsEntities;
import xyz.toesinthesand.ashfall.arms.entity.EnergyBolt;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.gun.BoltStyle;
import xyz.toesinthesand.ashfall.arms.gun.EnergyGunItem;
import xyz.toesinthesand.ashfall.arms.gun.EnergyGunItem.FireSound;
import xyz.toesinthesand.ashfall.arms.gun.GunLogic;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.item.EnergyItems;

import java.util.List;
import java.util.Map;

/**
 * Bolt presentation on a real server: each gun's shot sound and bolt style, the style travelling on the
 * fired bolt, the particle types the impact emitters need, the impact geometry, and that impacts never
 * break or ignite blocks.
 */
public class ArmsBoltTest {
	private static Identifier ashfall(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static ItemStack loadedGun(Item gun, Item pack, long energy) {
		ItemStack stack = new ItemStack(gun);
		stack.set(ArmsComponents.LOADED_PACK, ItemStackTemplate.fromNonEmptyStack(EnergyItems.withEnergy(new ItemStack(pack), energy)));
		return stack;
	}

	private static void assertNear(GameTestHelper helper, Vec3 actual, Vec3 expected, String what) {
		helper.assertTrue(actual.distanceTo(expected) < 1e-6, what + ": expected " + expected + ", got " + actual);
	}

	@GameTest
	public void everyGunHasItsSoundAndBoltStyle(GameTestHelper helper) {
		record Look(FireSound sound, BoltStyle style) {
		}
		Map<Item, Look> expected = Map.of(
				ArmsItems.SPARK_PISTOL, new Look(FireSound.PISTOL, BoltStyle.PISTOL),
				ArmsItems.VOLT_SIDEARM, new Look(FireSound.PISTOL, BoltStyle.PISTOL),
				ArmsItems.ARC_MAGNUM, new Look(FireSound.MAGNUM, BoltStyle.MAGNUM),
				ArmsItems.PULSE_CARBINE, new Look(FireSound.CARBINE, BoltStyle.CARBINE),
				ArmsItems.ION_RIFLE, new Look(FireSound.RIFLE, BoltStyle.RIFLE),
				ArmsItems.LANCER_LONGRIFLE, new Look(FireSound.LANCER, BoltStyle.LANCER),
				ArmsItems.STORM_REPEATER, new Look(FireSound.STORM, BoltStyle.STORM));
		for (Map.Entry<Item, Look> e : expected.entrySet()) {
			EnergyGunItem gun = (EnergyGunItem) e.getKey();
			helper.assertValueEqual(gun.fireSound(), e.getValue().sound(), gun + " fire sound");
			helper.assertValueEqual(gun.boltStyle(), e.getValue().style(), gun + " bolt style");
		}
		// Every shot sound is a registered event; pistols are the quietest, the Lancer carries furthest.
		for (FireSound sound : FireSound.values()) {
			helper.assertTrue(BuiltInRegistries.SOUND_EVENT.getKey(sound.sound()) != null, sound + " is not registered");
			helper.assertTrue(FireSound.PISTOL.loudness() <= sound.loudness(), sound + " is quieter than a pistol");
			helper.assertTrue(sound.loudness() <= FireSound.LANCER.loudness(), sound + " is louder than the Lancer");
		}
		for (String id : List.of("gun.fire.light", "gun.fire.heavy", "gun.fire.auto", "gun.fire.pistol", "gun.fire.magnum",
				"gun.fire.carbine", "gun.fire.rifle", "gun.fire.lancer", "gun.fire.storm", "gun.overheat", "gun.empty",
				"gun.reload", "bolt.impact", "bolt.hit", "charger.hum", "relay.hum")) {
			helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(ashfall(id)), "sound ashfall:" + id + " missing");
		}
		for (String id : List.of("bolt_spark", "bolt_flash", "bolt_impact", "bolt_hit", "scorch")) {
			helper.assertTrue(BuiltInRegistries.PARTICLE_TYPE.containsKey(ashfall(id)), "particle ashfall:" + id + " missing");
		}
		// Styles: the Lancer is the longest and thinnest, the Storm Repeater short and thick, pistols short.
		helper.assertTrue(BoltStyle.LANCER.length() >= 2f, "lancer beam should be long");
		for (BoltStyle style : BoltStyle.values()) {
			helper.assertTrue(style.length() >= 1.2f && style.length() <= 2f, style + " length out of range");
			helper.assertTrue(style.coreRadius() < style.glowRadius(), style + " core should sit inside its glow");
			helper.assertTrue(BoltStyle.LANCER.coreRadius() <= style.coreRadius(), style + " is thinner than the Lancer");
			helper.assertTrue(style.coreRadius() <= BoltStyle.STORM.coreRadius(), style + " is thicker than the Storm Repeater");
			helper.assertValueEqual(BoltStyle.byId(style.ordinal()), style, "style id round trip");
		}
		helper.assertTrue(BoltStyle.PISTOL.length() < BoltStyle.RIFLE.length(), "pistol bolts should be shorter than rifle bolts");
		helper.assertValueEqual(BoltStyle.byId(99), BoltStyle.CARBINE, "unknown style id falls back");
		helper.succeed();
	}

	@GameTest
	public void firedBoltsCarryTheGunsStyle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		player.setPos(pos.x, pos.y, pos.z);

		ItemStack lancer = loadedGun(ArmsItems.LANCER_LONGRIFLE, ArmsItems.CELL_PACK_STANDARD, 60);
		helper.assertValueEqual(GunLogic.tryFire(level, player, lancer), GunLogic.FireResult.FIRED, "lancer shot");
		List<EnergyBolt> bolts = helper.getEntities(ArmsEntities.ENERGY_BOLT);
		helper.assertValueEqual(bolts.size(), 1, "bolts after the lancer shot");
		helper.assertValueEqual(bolts.getFirst().getStyle(), BoltStyle.LANCER, "lancer bolt style");
		helper.assertValueEqual(bolts.getFirst().getColor(), 0xb45aff, "lancer bolt colour");

		ItemStack storm = loadedGun(ArmsItems.STORM_REPEATER, ArmsItems.CELL_PACK_HEAVY, 120);
		helper.assertValueEqual(GunLogic.tryFire(level, player, storm), GunLogic.FireResult.FIRED, "storm shot");
		boolean stormBolt = helper.getEntities(ArmsEntities.ENERGY_BOLT).stream().anyMatch(b -> b.getStyle() == BoltStyle.STORM);
		helper.assertTrue(stormBolt, "storm repeater bolt should carry the STORM style");

		// A bolt made without a gun (turret, test) still has a valid style.
		EnergyBolt bare = EnergyBolt.create(level, null, pos, 1f, 1f, 10f, 0xffffff);
		helper.assertValueEqual(bare.getStyle(), BoltStyle.CARBINE, "default bolt style");
		helper.succeed();
	}

	@GameTest
	public void impactPointsSitOnTheBeamAndTheSurface(GameTestHelper helper) {
		double lift = EnergyBolt.VISUAL_CENTRE_LIFT;
		Vec3 hit = new Vec3(10, 64, -3);
		// A wall (normal east): lifted up the wall to beam height.
		assertNear(helper, EnergyBolt.visualImpactPoint(hit, new Vec3(1, 0, 0)), hit.add(0, lift, 0), "wall impact");
		// A floor (normal up): stays on the floor.
		assertNear(helper, EnergyBolt.visualImpactPoint(hit, new Vec3(0, 1, 0)), hit, "floor impact");
		// A ceiling (normal down): stays on the ceiling.
		assertNear(helper, EnergyBolt.visualImpactPoint(hit, new Vec3(0, -1, 0)), hit, "ceiling impact");
		// Entity hits face back along the flight; a stopped bolt points up.
		assertNear(helper, EnergyBolt.impactNormal(new Vec3(0, 0, 5)), new Vec3(0, 0, -1), "entity hit normal");
		assertNear(helper, EnergyBolt.impactNormal(Vec3.ZERO), new Vec3(0, 1, 0), "stopped bolt normal");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void boltsNeverBreakOrIgniteBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos glass = new BlockPos(1, 2, 5);
		BlockPos tnt = new BlockPos(3, 2, 5);
		helper.setBlock(glass, Blocks.GLASS);
		helper.setBlock(tnt, Blocks.TNT);
		EnergyBolt atGlass = fire(helper, level, new Vec3(1.5, 2.4, 1.5));
		EnergyBolt atTnt = fire(helper, level, new Vec3(3.5, 2.4, 1.5));
		helper.succeedWhen(() -> {
			helper.assertTrue(atGlass.isRemoved() && atTnt.isRemoved(), "bolts should stop at the blocks");
			helper.assertBlockPresent(Blocks.GLASS, glass);
			helper.assertBlockPresent(Blocks.TNT, tnt);
			helper.assertBlockNotPresent(Blocks.FIRE, glass.north());
			helper.assertBlockNotPresent(Blocks.FIRE, tnt.north());
		});
	}

	/**
	 * Automatic blasters fire faster than vanilla's damage cooldown (20 ticks, of which the first 10 swallow any
	 * hit that does not beat the last one). Four equal bolts two ticks apart must all land in full; before the
	 * fix only the first one did, so a Storm Repeater dealt a quarter of its stated DPS.
	 */
	@GameTest(maxTicks = 80)
	public void rapidBoltsAllDealFullDamage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		IronGolem golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(1.5, 0, 4.5)); // standing on the floor, no fall
		float start = golem.getHealth();
		float perBolt = 4f;
		int shots = 4;
		List<EnergyBolt> fired = new java.util.ArrayList<>();
		for (int i = 0; i < shots; i++) {
			helper.runAfterDelay(1 + i * 2, () -> {
				// Chest height on the 2.7-block golem; headshot multiplier 1, so every bolt is worth exactly perBolt.
				Vec3 from = helper.absoluteVec(new Vec3(1.5, 1.2, 2.0));
				EnergyBolt bolt = EnergyBolt.create(level, null, from, perBolt, 1f, 32f, 0x3cc8ff);
				bolt.shoot(0, 0, 1, 1.0f, 0f);
				level.addFreshEntity(bolt);
				fired.add(bolt);
			});
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(fired.size() == shots && fired.stream().allMatch(EnergyBolt::isRemoved), "all bolts should have hit");
			float dealt = start - golem.getHealth();
			helper.assertTrue(Math.abs(dealt - perBolt * shots) < 0.01f,
					"expected " + (perBolt * shots) + " damage from " + shots + " bolts, got " + dealt);
		});
	}

	private static EnergyBolt fire(GameTestHelper helper, ServerLevel level, Vec3 relative) {
		Vec3 from = helper.absoluteVec(relative);
		EnergyBolt bolt = EnergyBolt.create(level, null, from, 4f, 1.75f, 96f, 0x3cc8ff);
		bolt.setStyle(BoltStyle.LANCER);
		Vec3 target = helper.absoluteVec(relative.add(0, 0, 3.5));
		bolt.shoot(target.x - from.x, 0, target.z - from.z, 1.0f, 0f);
		level.addFreshEntity(bolt);
		return bolt;
	}
}
