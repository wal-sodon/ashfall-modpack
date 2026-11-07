package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import xyz.toesinthesand.ashfall.arms.bench.ArmsBench;
import xyz.toesinthesand.ashfall.arms.bench.BenchRules;
import xyz.toesinthesand.ashfall.arms.bench.WeaponsmithBenchMenu;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentStats;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentStatsManager;
import xyz.toesinthesand.ashfall.arms.gun.GunAttachments;
import xyz.toesinthesand.ashfall.arms.gun.GunLogic;
import xyz.toesinthesand.ashfall.arms.gun.GunStats;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;

import java.util.function.Function;

/**
 * The Weaponsmith Bench: attachment data, stat math through the real firing path, slot exclusivity,
 * district gating, and the menu fitting an attachment.
 */
public class WeaponsmithBenchGameTest {
	private static final Function<String, AttachmentStats> SERVER = id -> AttachmentStatsManager.get(false, id);

	private static ItemStack withAttachments(Item gun, String... ids) {
		ItemStack stack = new ItemStack(gun);
		GunAttachments attachments = GunAttachments.NONE;
		for (String id : ids) {
			attachments = attachments.with(id);
		}
		stack.set(ArmsComponents.ATTACHMENTS, attachments);
		return stack;
	}

	@GameTest
	public void attachmentDataLoads(GameTestHelper helper) {
		for (String id : GunAttachments.ALL) {
			helper.assertTrue(AttachmentStatsManager.serverStats().containsKey(id), "no gun_attachment/" + id + ".json loaded");
		}
		helper.assertValueEqual(SERVER.apply(GunAttachments.CELL_REGULATOR).slot(), SERVER.apply(GunAttachments.OVERCHARGER).slot(),
				"regulator and overcharger share a slot");
		helper.assertValueEqual(SERVER.apply(GunAttachments.HEAT_SINK).requiresDistrict().orElse(""), "south_slope", "heat sink gate");
		helper.assertValueEqual(SERVER.apply(GunAttachments.CELL_REGULATOR).requiresDistrict().orElse(""), "south_slope", "regulator gate");
		helper.assertTrue(SERVER.apply(GunAttachments.OPTIC).requiresDistrict().isEmpty(), "the optic is never gated");
		helper.succeed();
	}

	@GameTest
	public void attachmentsChangeTheFiringStats(GameTestHelper helper) {
		GunStats base = GunLogic.effectiveStats(false, new ItemStack(ArmsItems.ION_RIFLE), false);
		GunStats sink = GunLogic.effectiveStats(false, withAttachments(ArmsItems.ION_RIFLE, GunAttachments.HEAT_SINK), false);
		helper.assertTrue(Math.abs(sink.heatCapacity() - base.heatCapacity() * 1.35f) < 1e-3, "heat sink capacity " + sink.heatCapacity());
		helper.assertTrue(sink.coolPerSecond() > base.coolPerSecond(), "heat sink cools faster");
		GunStats regulated = GunLogic.effectiveStats(false, withAttachments(ArmsItems.ION_RIFLE, GunAttachments.CELL_REGULATOR), false);
		helper.assertTrue(Math.abs(regulated.energyPerShot() - base.energyPerShot() * 0.7f) < 1e-3, "regulator energy " + regulated.energyPerShot());
		GunStats over = GunLogic.effectiveStats(false, withAttachments(ArmsItems.ION_RIFLE, GunAttachments.OVERCHARGER), false);
		helper.assertTrue(over.damage() > base.damage() && over.heatPerShot() > base.heatPerShot(), "overcharger: more damage, more heat");
		ItemStack optic = withAttachments(ArmsItems.ION_RIFLE, GunAttachments.OPTIC);
		helper.assertTrue(GunLogic.effectiveStats(false, optic, true).spread() < base.spread(), "optic tightens spread while aiming");
		helper.assertValueEqual(GunLogic.effectiveStats(false, optic, false).spread(), base.spread(), "optic spread unchanged from the hip");
		helper.succeed();
	}

	@GameTest
	public void benchRulesGateAndExclusiveSlots(GameTestHelper helper) {
		ItemStack gun = new ItemStack(ArmsItems.PULSE_CARBINE);
		ItemStack sink = new ItemStack(ArmsItems.HEAT_SINK);
		helper.assertValueEqual(BenchRules.check(gun, sink, SERVER, district -> false), BenchRules.Status.LOCKED,
				"heat sink before South Slope is reclaimed");
		helper.assertValueEqual(BenchRules.check(gun, sink, SERVER, "south_slope"::equals), BenchRules.Status.READY,
				"heat sink after South Slope is reclaimed");
		helper.assertValueEqual(BenchRules.check(gun, new ItemStack(ArmsItems.CELL_REGULATOR), SERVER, district -> false),
				BenchRules.Status.LOCKED, "regulator before South Slope is reclaimed");
		helper.assertValueEqual(BenchRules.check(gun, new ItemStack(ArmsItems.OPTIC), SERVER, district -> false),
				BenchRules.Status.READY, "optic needs no district");
		helper.assertValueEqual(BenchRules.check(new ItemStack(ArmsItems.BATTERY), sink, SERVER, d -> true),
				BenchRules.Status.NOT_A_GUN, "only blasters take attachments");
		ItemStack dampened = withAttachments(ArmsItems.PULSE_CARBINE, GunAttachments.DAMPENER);
		helper.assertValueEqual(BenchRules.check(dampened, new ItemStack(ArmsItems.DAMPENER), SERVER, d -> true),
				BenchRules.Status.ALREADY_FITTED, "no second dampener");

		BenchRules.Fit regulated = BenchRules.fit(dampened, GunAttachments.CELL_REGULATOR, SERVER);
		helper.assertTrue(regulated.replaced().isEmpty(), "regulator replaces nothing");
		BenchRules.Fit over = BenchRules.fit(regulated.gun(), GunAttachments.OVERCHARGER, SERVER);
		GunAttachments fitted = over.gun().getOrDefault(ArmsComponents.ATTACHMENTS, GunAttachments.NONE);
		helper.assertTrue(fitted.has(GunAttachments.OVERCHARGER) && !fitted.has(GunAttachments.CELL_REGULATOR),
				"the overcharger takes the core slot from the regulator: " + fitted);
		helper.assertTrue(fitted.has(GunAttachments.DAMPENER), "other slots are kept");
		helper.assertTrue(over.replaced().contains(GunAttachments.CELL_REGULATOR), "the regulator is handed back");
		helper.succeed();
	}

	@GameTest
	public void benchMenuFitsAndRefuses(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ArmsBench.WEAPONSMITH_BENCH);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		WeaponsmithBenchMenu menu = new WeaponsmithBenchMenu(1, player.getInventory(),
				ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)));
		Slot gunSlot = menu.getSlot(WeaponsmithBenchMenu.GUN_SLOT);
		Slot attachmentSlot = menu.getSlot(WeaponsmithBenchMenu.ATTACHMENT_SLOT);
		Slot result = menu.getSlot(menu.getResultSlot());

		gunSlot.set(new ItemStack(ArmsItems.ION_RIFLE));
		attachmentSlot.set(new ItemStack(ArmsItems.OVERCHARGER, 2));
		// A fresh gametest world has reclaimed nothing, so the Downtown-gated Overcharger is refused.
		helper.assertValueEqual(menu.status(), BenchRules.Status.LOCKED, "overcharger status");
		helper.assertTrue(result.getItem().isEmpty(), "a locked attachment offers no result");

		attachmentSlot.set(new ItemStack(ArmsItems.OPTIC, 2));
		helper.assertValueEqual(menu.status(), BenchRules.Status.READY, "optic status");
		ItemStack fitted = result.getItem().copy();
		helper.assertTrue(fitted.is(ArmsItems.ION_RIFLE)
				&& fitted.getOrDefault(ArmsComponents.ATTACHMENTS, GunAttachments.NONE).has(GunAttachments.OPTIC), "result " + fitted);
		ItemStack taken = result.remove(1);
		result.onTake(player, taken);
		helper.assertTrue(gunSlot.getItem().isEmpty(), "the input gun is used up");
		helper.assertValueEqual(attachmentSlot.getItem().getCount(), 1, "one optic is used up");
		menu.removed(player);
		helper.succeed();
	}
}
