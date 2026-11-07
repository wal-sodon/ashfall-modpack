package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.survivor.SupplyLockerBlockEntity;
import xyz.toesinthesand.ashfall.arms.survivor.Survivor;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorBalance;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorMode;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRole;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorUpkeep;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorWork;
import xyz.toesinthesand.ashfall.arms.survivor.Survivors;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreRecord;
import xyz.toesinthesand.ashfall.director.haven.HavenState;
import xyz.toesinthesand.ashfall.director.haven.SirenLogic;
import xyz.toesinthesand.ashfall.director.horde.HordeSchedule;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Survivors, step 2's rules: food upkeep ({@link SurvivorUpkeep}) stepped through explicit Director days (a settler eats
 * the least nutritious locker food each day and goes hungry without; three hungry days and it leaves; the fourth settler
 * at a core eats double; drinks and golden apples are never eaten; a survivor following its leader eats from the
 * leader's inventory, only with the leader near, and leaves its home lockers alone until it is home again), half speed on the first day in a new role (stacking with hunger to a quarter), and no role
 * changes from the horde siren to dawn, checked at explicit day-clock times. Padded wide like
 * {@link SurvivorWorkGameTest}: lockers are found anywhere in the saved core's area.
 */
public class SurvivorRulesGameTest {
	private static final int PADDING = 32;
	private static final BlockPos CORE = new BlockPos(0, 1, 0);
	private static final BlockPos LOCKER = new BlockPos(5, 1, 5);
	/** Any Director day: meals are counted from whatever day a survivor is first checked on. */
	private static final int DAY = 20;

	/**
	 * A saved Bunker Core at {@link #CORE}, a Supply Locker holding {@code stock} and {@code settlers} Settlers living
	 * there (homed in spawn order); everything is removed after {@code body}.
	 */
	private static void atBase(GameTestHelper helper, int settlers, List<ItemStack> stock, Consumer<Base> body) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		MinecraftServer server = helper.getLevel().getServer();
		UUID owner = UUID.randomUUID();
		BlockPos core = helper.absolutePos(CORE);
		HavenState.get(server).putCore(new BunkerCoreRecord(owner, owner, Level.OVERWORLD, core, 1, List.of()));
		List<Survivor> group = new ArrayList<>();
		try {
			helper.setBlock(LOCKER, Survivors.SUPPLY_LOCKER);
			SupplyLockerBlockEntity locker = helper.getBlockEntity(LOCKER, SupplyLockerBlockEntity.class);
			for (int i = 0; i < stock.size(); i++) {
				locker.setItem(i, stock.get(i).copy());
			}
			for (int i = 0; i < settlers; i++) {
				Survivor s = Survivors.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(1 + i, 1, 2)), SurvivorRole.SETTLER, null);
				helper.assertTrue(s != null, "survivor " + (i + 1) + " spawned");
				s.setHome(core);
				s.setMode(SurvivorMode.HOME);
				group.add(s);
			}
			body.accept(new Base(helper.getLevel(), group, locker));
		} finally {
			group.forEach(Survivor::discard);
			HavenState.get(server).removeCore(owner);
		}
	}

	private record Base(ServerLevel level, List<Survivor> group, SupplyLockerBlockEntity locker) {
		Survivor first() {
			return group.getFirst();
		}

		int count(Item item) {
			int n = 0;
			for (int i = 0; i < locker.getContainerSize(); i++) {
				if (locker.getItem(i).is(item)) {
					n += locker.getItem(i).getCount();
				}
			}
			return n;
		}

		SurvivorUpkeep.Meal meal(Survivor s, int day) {
			return SurvivorUpkeep.dailyMeal(level, s, day);
		}
	}

	/** Guards refuse Plasma Blades (10.2.4); one already held from an older save is put down where it never despawns. */
	@GameTest
	public void guardsRefusePlasmaBlades(GameTestHelper helper) {
		helper.assertFalse(Survivors.isMeleeWeapon(new ItemStack(ArmsItems.PLASMA_BLADE)), "a Plasma Blade is refused");
		helper.assertFalse(Survivors.isMeleeWeapon(new ItemStack(ArmsItems.RESONANT_PLASMA_BLADE)), "a Resonant Plasma Blade is refused");
		helper.assertTrue(Survivors.isMeleeWeapon(new ItemStack(ArmsItems.SCRAP_MACHETE)), "a Scrap Machete is still a weapon");
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 4; x++) {
			for (int z = 0; z < 4; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		Survivor guard = Survivors.spawn(level, helper.absolutePos(new BlockPos(1, 1, 1)), SurvivorRole.GUARD, null);
		helper.assertTrue(guard != null, "no survivor spawned");
		guard.equipWeapon(new ItemStack(ArmsItems.PLASMA_BLADE));
		guard.returnRefusedWeapon(level);
		helper.assertTrue(guard.getMainHandItem().isEmpty(), "the guard put the blade down");
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, guard.getBoundingBox().inflate(3),
				e -> e.getItem().is(ArmsItems.PLASMA_BLADE));
		helper.assertValueEqual(drops.size(), 1, "dropped blades");
		helper.assertTrue(drops.get(0).getAge() == Short.MIN_VALUE, "the dropped blade never despawns");
		drops.forEach(ItemEntity::discard);
		guard.discard();
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void settlerEatsFromLockerEachDayAndGoesHungryWithout(GameTestHelper helper) {
		// 5 points a day: 3 berries (2 each) beat a loaf of bread (5) as the least nutritious; rotten flesh never counts
		atBase(helper, 1, List.of(new ItemStack(Items.BREAD), new ItemStack(Items.SWEET_BERRIES, 3), new ItemStack(Items.ROTTEN_FLESH, 16)), base -> {
			Survivor s = base.first();
			helper.assertValueEqual(base.meal(s, DAY), SurvivorUpkeep.Meal.NOT_DUE, "first check");
			helper.assertValueEqual(base.count(Items.BREAD) + base.count(Items.SWEET_BERRIES), 4, "food eaten on the first check");
			helper.assertValueEqual(base.meal(s, DAY), SurvivorUpkeep.Meal.NOT_DUE, "second check the same day");

			helper.assertValueEqual(base.meal(s, DAY + 1), SurvivorUpkeep.Meal.FED, "day 2");
			helper.assertValueEqual(base.count(Items.SWEET_BERRIES), 0, "berries (the least nutritious) left after day 2");
			helper.assertValueEqual(base.count(Items.BREAD), 1, "bread after day 2");
			helper.assertValueEqual(base.meal(s, DAY + 2), SurvivorUpkeep.Meal.FED, "day 3");
			helper.assertValueEqual(base.count(Items.BREAD), 0, "bread after day 3");
			helper.assertValueEqual(base.count(Items.ROTTEN_FLESH), 16, "rotten flesh is never eaten");
			helper.assertTrue(!s.hungry(), "fed survivor is hungry");
			helper.assertValueEqual(SurvivorWork.workSpeed(s), 1.0, "a fed old hand's work speed");

			helper.assertValueEqual(base.meal(s, DAY + 3), SurvivorUpkeep.Meal.HUNGRY, "day 4 with empty lockers");
			helper.assertTrue(s.hungry(), "unfed survivor not hungry");
			helper.assertValueEqual(SurvivorWork.workSpeed(s), 0.5, "a hungry survivor's work speed");

			base.locker().setItem(0, new ItemStack(Items.BREAD));
			helper.assertValueEqual(base.meal(s, DAY + 4), SurvivorUpkeep.Meal.FED, "day 5 with bread again");
			helper.assertTrue(!s.hungry(), "still hungry after eating");
		});
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void survivorLeavesAfterThreeHungryDays(GameTestHelper helper) {
		int days = SurvivorBalance.get().upkeep().hungryDaysToLeave();
		atBase(helper, 1, List.of(), base -> {
			Survivor s = base.first();
			base.meal(s, DAY);
			for (int d = 1; d < days; d++) {
				helper.assertValueEqual(base.meal(s, DAY + d), SurvivorUpkeep.Meal.HUNGRY, "hungry day " + d);
				helper.assertTrue(!s.isRemoved(), "left after " + d + " hungry days");
			}
			helper.assertValueEqual(base.meal(s, DAY + days), SurvivorUpkeep.Meal.LEFT, "hungry day " + days);
			helper.assertTrue(s.isRemoved(), "still here after " + days + " hungry days");
		});
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void fourthSettlerEatsDouble(GameTestHelper helper) {
		SurvivorBalance.Upkeep rules = SurvivorBalance.get().upkeep();
		atBase(helper, rules.freeSettlers() + 1, List.of(new ItemStack(Items.BREAD, rules.extraNutritionPerDay() / 5)), base -> {
			Survivor first = base.first();
			Survivor fourth = base.group().getLast();
			helper.assertValueEqual(SurvivorUpkeep.ration(base.level(), first), rules.nutritionPerDay(), "the first settler's ration");
			helper.assertValueEqual(SurvivorUpkeep.ration(base.level(), fourth), rules.extraNutritionPerDay(), "the fourth settler's ration");
			base.meal(fourth, DAY);
			helper.assertValueEqual(base.meal(fourth, DAY + 1), SurvivorUpkeep.Meal.FED, "the fourth settler's meal");
			helper.assertValueEqual(base.count(Items.BREAD), 0, "bread left after the fourth settler ate");
		});
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void survivorsRefuseDrinksAndGoldenApples(GameTestHelper helper) {
		// Bottled Water (2) and golden apples (4) would go before bread (5) if they counted
		atBase(helper, 1, List.of(new ItemStack(SalvageItems.BOTTLED_WATER, 4), new ItemStack(Items.GOLDEN_APPLE),
				new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), new ItemStack(Items.BREAD)), base -> {
			Survivor s = base.first();
			base.meal(s, DAY);
			helper.assertValueEqual(base.meal(s, DAY + 1), SurvivorUpkeep.Meal.FED, "day 2");
			helper.assertValueEqual(base.count(Items.BREAD), 0, "bread after day 2");
			helper.assertValueEqual(base.count(SalvageItems.BOTTLED_WATER), 4, "bottled water eaten");
			helper.assertValueEqual(base.count(Items.GOLDEN_APPLE), 1, "golden apples eaten");
			helper.assertValueEqual(base.count(Items.ENCHANTED_GOLDEN_APPLE), 1, "enchanted golden apples eaten");
			helper.assertValueEqual(base.meal(s, DAY + 2), SurvivorUpkeep.Meal.HUNGRY, "day 3 with only refused food");
		});
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void followerEatsFromItsLeadersInventory(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer leader = helper.makeMockServerPlayerInLevel();
		Survivor s = Survivors.spawn(level, helper.absolutePos(new BlockPos(2, 1, 2)), SurvivorRole.SETTLER, leader.getUUID());
		try {
			helper.assertTrue(s != null, "survivor spawned");
			s.setMode(SurvivorMode.FOLLOW);
			BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
			leader.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			leader.getInventory().clearContent();
			leader.getInventory().add(new ItemStack(Items.BREAD, 2));
			leader.getInventory().add(new ItemStack(Items.SWEET_BERRIES, 3));
			leader.getInventory().add(new ItemStack(SalvageItems.BOTTLED_WATER, 2));
			leader.getInventory().add(new ItemStack(Items.GOLDEN_APPLE));
			helper.assertValueEqual(SurvivorUpkeep.dailyMeal(level, s, DAY), SurvivorUpkeep.Meal.NOT_DUE, "first check");
			helper.assertValueEqual(SurvivorUpkeep.dailyMeal(level, s, DAY + 1), SurvivorUpkeep.Meal.FED, "day 2 beside its leader");
			helper.assertValueEqual(count(leader, Items.SWEET_BERRIES), 0, "berries (the least nutritious) left after day 2");
			helper.assertValueEqual(count(leader, Items.BREAD), 2, "bread after day 2");
			helper.assertValueEqual(count(leader, SalvageItems.BOTTLED_WATER), 2, "the leader's bottled water was eaten");
			helper.assertValueEqual(count(leader, Items.GOLDEN_APPLE), 1, "the leader's golden apple was eaten");

			// the leader more than 32 blocks off: no meal, though the food is there
			leader.snapTo(at.getX() + 60.5, at.getY(), at.getZ() + 0.5);
			helper.assertValueEqual(SurvivorUpkeep.dailyMeal(level, s, DAY + 2), SurvivorUpkeep.Meal.HUNGRY, "day 3 with the leader far off");
			helper.assertValueEqual(count(leader, Items.BREAD), 2, "bread eaten from a leader far off");
		} finally {
			if (s != null) {
				s.discard();
			}
			leader.level().getServer().getPlayerList().remove(leader);
		}
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void settlerAwayFromHomeLeavesTheLockersAlone(GameTestHelper helper) {
		atBase(helper, 1, List.of(new ItemStack(Items.BREAD, 3)), base -> {
			ServerPlayer leader = helper.makeMockServerPlayerInLevel();
			try {
				Survivor s = base.first();
				s.setLeader(leader.getUUID());
				s.setMode(SurvivorMode.FOLLOW);
				BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
				leader.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				leader.getInventory().clearContent();
				base.meal(s, DAY);
				helper.assertValueEqual(base.meal(s, DAY + 1), SurvivorUpkeep.Meal.HUNGRY, "day 2 following a leader with no food");
				helper.assertValueEqual(base.count(Items.BREAD), 3, "bread taken from the home lockers while following");
				leader.getInventory().add(new ItemStack(Items.BREAD));
				helper.assertValueEqual(base.meal(s, DAY + 2), SurvivorUpkeep.Meal.FED, "day 3 with bread on the leader");
				helper.assertValueEqual(count(leader, Items.BREAD), 0, "the leader's bread after day 3");
				helper.assertValueEqual(base.count(Items.BREAD), 3, "the home lockers' bread after day 3");

				s.setMode(SurvivorMode.HOME);
				helper.assertValueEqual(base.meal(s, DAY + 3), SurvivorUpkeep.Meal.FED, "day 4 back home");
				helper.assertValueEqual(base.count(Items.BREAD), 2, "the home lockers' bread once home again");
			} finally {
				leader.level().getServer().getPlayerList().remove(leader);
			}
		});
		helper.succeed();
	}

	private static int count(ServerPlayer player, Item item) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(item)) {
				n += player.getInventory().getItem(i).getCount();
			}
		}
		return n;
	}

	@GameTest(padding = PADDING, maxTicks = 200)
	public void newRoleWorksAtHalfSpeedForADay(GameTestHelper helper) {
		SurvivorBalance before = SurvivorBalance.get();
		// a one-second "day", so the test can see it end
		SurvivorBalance.setForTesting(new SurvivorBalance(before.caps(), before.rescueFirstDay(), before.guardDamageShare(),
				before.guardBonusHealth(), before.scavenger(), before.medic(), before.mechanic(), before.upkeep(),
				new SurvivorBalance.Roles(before.roles().firstDaySpeed(), 20L, before.roles().minSpeed()), before.trader(),
				before.holdout()));
		List<Survivor> keep = new ArrayList<>();
		try {
			atBase(helper, 1, List.of(), base -> {
				Survivor s = base.first();
				helper.assertValueEqual(SurvivorWork.workSpeed(s), 1.0, "a spawned settler's work speed");
				s.changeRole(SurvivorRole.MEDIC);
				helper.assertTrue(SurvivorWork.newInRole(s), "a new medic is not new in the role");
				helper.assertValueEqual(SurvivorWork.workSpeed(s), 0.5, "a new medic's work speed");
				base.meal(s, DAY);
				base.meal(s, DAY + 1); // no food: hungry
				helper.assertValueEqual(SurvivorWork.workSpeed(s), 0.25, "a new, hungry medic's work speed");
				base.locker().setItem(0, new ItemStack(Items.BREAD));
				base.meal(s, DAY + 2);
				helper.assertValueEqual(SurvivorWork.workSpeed(s), 0.5, "a new, fed medic's work speed");
			});
		} catch (RuntimeException e) {
			SurvivorBalance.setForTesting(before);
			throw e;
		}
		// the same role a "day" later, on a survivor of its own
		Survivor later = Survivors.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)), SurvivorRole.SETTLER, null);
		helper.assertTrue(later != null, "survivor spawned");
		keep.add(later);
		later.changeRole(SurvivorRole.GUARD);
		helper.runAfterDelay(30, () -> {
			try {
				helper.assertTrue(!SurvivorWork.newInRole(later), "still new in the role after its first day");
				helper.assertValueEqual(SurvivorWork.workSpeed(later), 1.0, "work speed after the first day");
			} finally {
				SurvivorBalance.setForTesting(before);
				keep.forEach(Survivor::discard);
			}
			helper.succeed();
		});
	}

	@GameTest(padding = PADDING)
	public void roleChangeRefusedFromSirenUntilDawn(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HordeSchedule schedule = HordeSchedule.get(server);
		helper.assertTrue(schedule.enabled(), "horde nights are off");
		int hordeDay = schedule.nextHordeDay(2, server.overworld().getSeed());
		long start = (hordeDay - 1) * HordeSchedule.DAY_TICKS;
		long sirenFrom = Math.min(schedule.nightStart(), SirenLogic.NIGHTFALL
				- xyz.toesinthesand.ashfall.director.haven.HavenSettings.get().sirenWarningTicks());
		atBase(helper, 1, List.of(), base -> {
			Survivor s = base.first();
			helper.assertTrue(Survivors.roleChangeRefusal(s, SurvivorRole.GUARD, start + 6_000L).isEmpty(),
					"refused at noon on a horde day");
			helper.assertTrue(Survivors.roleChangeRefusal(s, SurvivorRole.GUARD, start + sirenFrom - 20L).isEmpty(),
					"refused just before the siren");
			Optional<Component> atSiren = Survivors.roleChangeRefusal(s, SurvivorRole.GUARD, start + sirenFrom);
			helper.assertTrue(atSiren.isPresent(), "allowed once the siren went");
			helper.assertValueEqual(lineKey(atSiren.get()), "message.ashfall.survivor.role.horde", "the refusal line");
			helper.assertTrue(Survivors.roleChangeRefusal(s, SurvivorRole.GUARD, start + 18_000L).isPresent(),
					"allowed at midnight of a horde night");
			helper.assertTrue(Survivors.roleChangeRefusal(s, SurvivorRole.GUARD, start + schedule.dawn()).isEmpty(),
					"refused at dawn");
			if (!schedule.isHordeDay(hordeDay - 1, server.overworld().getSeed())) {
				helper.assertTrue(Survivors.roleChangeRefusal(s, SurvivorRole.GUARD, start - HordeSchedule.DAY_TICKS + 18_000L).isEmpty(),
						"refused at midnight of an ordinary night");
			}
			// orders other than a role change still work
			s.setMode(SurvivorMode.STAY);
			helper.assertValueEqual(s.mode(), SurvivorMode.STAY, "mode after a Stay order");
		});
		helper.succeed();
	}

	/** The line key inside a {@code Name: "line"} reply. */
	private static String lineKey(Component reply) {
		if (reply.getContents() instanceof TranslatableContents says && says.getArgs().length > 1
				&& says.getArgs()[1] instanceof Component line && line.getContents() instanceof TranslatableContents inner) {
			return inner.getKey();
		}
		return reply.getString();
	}

	@GameTest(padding = PADDING)
	public void guardRoleAddsTenHealthAndLeavingTakesItBack(GameTestHelper helper) {
		helper.setBlock(2, 0, 2, Blocks.STONE);
		Survivor s = Survivors.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), SurvivorRole.SETTLER, null);
		helper.assertTrue(s != null, "survivor spawned");
		try {
			double bonus = SurvivorBalance.get().guardBonusHealth();
			helper.assertValueEqual(bonus, 10.0, "shipped guard_bonus_health");
			helper.assertValueEqual(s.getMaxHealth(), (float) Survivor.MAX_HEALTH, "a settler's max health");
			s.changeRole(SurvivorRole.GUARD);
			helper.assertValueEqual(s.getMaxHealth(), (float) (Survivor.MAX_HEALTH + bonus), "a guard's max health");
			helper.assertValueEqual(s.getHealth(), s.getMaxHealth(), "a whole settler is a whole guard");
			s.changeRole(SurvivorRole.SETTLER);
			helper.assertValueEqual(s.getMaxHealth(), (float) Survivor.MAX_HEALTH, "max health after leaving the guard role");
			helper.assertTrue(s.getHealth() <= s.getMaxHealth(), "health " + s.getHealth() + " above max after leaving the role");
			helper.assertValueEqual(s.getHealth(), (float) Survivor.MAX_HEALTH, "health after leaving the role");
			// a hurt guard leaving the job comes back no healthier than it was (same share), and back again heals nothing
			s.changeRole(SurvivorRole.GUARD);
			s.setHealth(15.0f);
			s.changeRole(SurvivorRole.SETTLER);
			helper.assertValueEqual(s.getHealth(), 10.0f, "half-health guard back as a settler");
			s.changeRole(SurvivorRole.GUARD);
			helper.assertValueEqual(s.getHealth(), 15.0f, "half-health settler as a guard");
		} finally {
			s.discard();
		}
		helper.succeed();
	}
}
