package xyz.toesinthesand.ashfall.arms.survivor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.spawn.DirectorDay;

/**
 * Food upkeep (survivors plan, build order step 2): every survivor with a leader eats once a day. Living at home
 * ({@link SurvivorWork#atHome}) it eats from the Supply Lockers of its home claim only; anywhere else (following, or
 * told to stay out in the city, home or not) it eats from its leader's own inventory, when the leader is online and
 * within {@link #LEADER_RANGE} blocks. Never both on one day. A ration is counted in hunger points: it eats the least
 * nutritious food first ({@link SurvivorWork#LEAST_NUTRITIOUS}) until it has had enough, so one loaf of bread is a day
 * and berries take several. Rotten flesh, drinks and golden apples don't count ({@link SurvivorWork#REFUSED_FOOD}). At
 * home the first {@link SurvivorBalance.Upkeep#freeSettlers} settlers at a core (by time on the roster) need
 * {@link SurvivorBalance.Upkeep#nutritionPerDay} points each, every one after that
 * {@link SurvivorBalance.Upkeep#extraNutritionPerDay}; away from home it is always
 * {@link SurvivorBalance.Upkeep#nutritionPerDay}. A survivor that cannot have its whole ration eats nothing (leaving
 * the food for the others) and is hungry: half speed at work ({@link SurvivorWork#workSpeed}), "hungry" on its
 * nameplate and in talk. After {@link SurvivorBalance.Upkeep#hungryDaysToLeave} days in a row without food it leaves
 * for good, its leader hears it, and its weapon and haul go into the lockers when it leaves from home, else on the
 * ground at its feet.
 * <p>
 * The meal is taken on the survivor's first home check of each new Director day. Days it spends unloaded (a base
 * nobody is near) are not charged: a survivor that wakes up after a week away eats once, not seven times. Survivors
 * without a leader (holdouts, traders) never eat and are never hungry.
 */
public final class SurvivorUpkeep {
	/** What a meal check did. */
	public enum Meal { NOT_DUE, NOT_SETTLED, FED, HUNGRY, LEFT }

	/** A survivor away from home eats from its leader's inventory only while the leader is this close. */
	public static final double LEADER_RANGE = 32.0;

	private SurvivorUpkeep() {
	}

	/** Today's meal, if it is due (from {@link Survivor}'s home check). */
	static void tick(ServerLevel level, Survivor survivor) {
		dailyMeal(level, survivor, DirectorDay.today(level));
	}

	/**
	 * The meal for Director day {@code day}, passed in so gametests can step through days. A survivor checked for the
	 * first time, or one that just got a leader, starts counting from {@code day} without eating.
	 */
	public static Meal dailyMeal(ServerLevel level, Survivor survivor, int day) {
		boolean atHome = SurvivorWork.atHome(level, survivor);
		if (survivor.isTrader() || (!atHome && survivor.leader() == null)) {
			survivor.setUpkeep(day, 0);
			return Meal.NOT_SETTLED;
		}
		if (survivor.fedDay() <= 0 || day < survivor.fedDay()) {
			survivor.setUpkeep(day, survivor.hungryDays()); // first check, or the clock was turned back
			return Meal.NOT_DUE;
		}
		if (day == survivor.fedDay()) {
			return Meal.NOT_DUE;
		}
		int ration = ration(level, survivor);
		boolean fed = atHome ? eatFromLockers(level, survivor, ration) : eatFromLeader(level, survivor, ration);
		if (fed) {
			survivor.setUpkeep(day, 0);
			return Meal.FED;
		}
		int days = survivor.hungryDays() + 1;
		survivor.setUpkeep(day, days);
		if (days >= SurvivorBalance.get().upkeep().hungryDaysToLeave()) {
			leave(level, survivor, days, atHome);
			return Meal.LEFT;
		}
		return Meal.HUNGRY;
	}

	/** The whole ration from the home lockers, or nothing. */
	private static boolean eatFromLockers(ServerLevel level, Survivor survivor, int ration) {
		if (available(level, survivor) < ration) {
			return false;
		}
		for (int eaten = 0; eaten < ration; ) {
			ItemStack food = SupplyLockers.takeOne(level, survivor, SurvivorWork.FOOD, SurvivorWork.LEAST_NUTRITIOUS);
			if (food.isEmpty()) {
				break;
			}
			eaten += SurvivorWork.nutrition(food);
		}
		return true;
	}

	/** The leader who can feed {@code survivor} now: online, in its level, within {@link #LEADER_RANGE}; else null. */
	static @Nullable ServerPlayer feedingLeader(ServerLevel level, Survivor survivor) {
		ServerPlayer leader = survivor.leader() == null ? null : level.getServer().getPlayerList().getPlayer(survivor.leader());
		if (leader == null || leader.level() != level || !leader.isAlive() || leader.isSpectator()
				|| leader.distanceToSqr(survivor) > LEADER_RANGE * LEADER_RANGE) {
			return null;
		}
		return leader;
	}

	/** The whole ration from the leader's inventory (least nutritious first), or nothing. */
	private static boolean eatFromLeader(ServerLevel level, Survivor survivor, int ration) {
		ServerPlayer leader = feedingLeader(level, survivor);
		if (leader == null) {
			return false;
		}
		Inventory inventory = leader.getInventory();
		int points = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack in = inventory.getItem(i);
			if (!in.isEmpty() && SurvivorWork.FOOD.test(in)) {
				points += SurvivorWork.nutrition(in) * in.getCount();
			}
		}
		if (points < ration) {
			return false;
		}
		for (int eaten = 0; eaten < ration; ) {
			int best = -1;
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack in = inventory.getItem(i);
				if (!in.isEmpty() && SurvivorWork.FOOD.test(in)
						&& (best < 0 || SurvivorWork.LEAST_NUTRITIOUS.compare(in, inventory.getItem(best)) < 0)) {
					best = i;
				}
			}
			if (best < 0) {
				break;
			}
			eaten += SurvivorWork.nutrition(inventory.getItem(best));
			inventory.removeItem(best, 1);
		}
		return true;
	}

	/** Hunger points of edible food in {@code survivor}'s lockers. */
	static int available(ServerLevel level, Survivor survivor) {
		int points = 0;
		for (SupplyLockerBlockEntity locker : SupplyLockers.lockers(level, survivor)) {
			for (int i = 0; i < locker.getContainerSize(); i++) {
				ItemStack in = locker.getItem(i);
				if (!in.isEmpty() && SurvivorWork.FOOD.test(in)) {
					points += SurvivorWork.nutrition(in) * in.getCount();
				}
			}
		}
		return points;
	}

	/**
	 * Hunger points a day for {@code survivor}: at home more for every settler past the first few at its core, away from
	 * home always {@link SurvivorBalance.Upkeep#nutritionPerDay}.
	 */
	public static int ration(ServerLevel level, Survivor survivor) {
		SurvivorBalance.Upkeep rules = SurvivorBalance.get().upkeep();
		BlockPos home = survivor.home();
		if (home == null || !SurvivorWork.atHome(level, survivor)) {
			return rules.nutritionPerDay();
		}
		int rank = SurvivorRoster.get(level.getServer()).homedRank(level.dimension(), home, survivor.getUUID());
		return rank >= rules.freeSettlers() ? rules.extraNutritionPerDay() : rules.nutritionPerDay();
	}

	/**
	 * Gone for good: the leader told, the entity removed. Leaving from home its weapon and haul go into the lockers (or
	 * at its feet when they are full); leaving from anywhere else they go on the ground at its feet.
	 */
	static void leave(ServerLevel level, Survivor survivor, int days, boolean fromHome) {
		ItemStack weapon = survivor.getMainHandItem();
		if (!weapon.isEmpty()) {
			survivor.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			if (fromHome) {
				SupplyLockers.store(level, survivor, weapon);
			} else {
				survivor.spawnAtLocation(level, weapon);
			}
		}
		if (fromHome) {
			SurvivorWork.deposit(level, survivor);
		}
		SurvivorWork.dropPack(level, survivor);
		ServerPlayer leader = survivor.leader() == null ? null : level.getServer().getPlayerList().getPlayer(survivor.leader());
		if (leader != null) {
			leader.sendSystemMessage(Component.translatable(fromHome ? "message.ashfall.survivor.left_hungry"
					: "message.ashfall.survivor.left_hungry_away", survivor.survivorName(), days).withStyle(ChatFormatting.RED));
		}
		survivor.discard();
	}
}
