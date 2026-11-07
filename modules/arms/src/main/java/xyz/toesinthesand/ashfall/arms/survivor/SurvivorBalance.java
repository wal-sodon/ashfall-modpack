package xyz.toesinthesand.ashfall.arms.survivor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.Map;

/**
 * Every survivor number in one place, from {@code data/ashfall/ashfall/survivor/balance.json} (survivors plan, balance
 * controls). Missing fields keep the shipped values below.
 *
 * @param caps             how many survivors a group, a core and the world hold
 * @param rescueFirstDay   distress rescues leave a survivor from this Director day ({@link SurvivorRescue})
 * @param guardDamageShare a guard hits for this share of a player's damage with the same weapon (1.0 since 10.2.2: the
 *                         same per hit, about 60% of a player's damage a second with a sword, from the fixed 1 s swing)
 * @param guardBonusHealth extra max health a survivor has while it is a Guard (10.2.2: 30 instead of 20)
 * @param scavenger        the scavenger's trip ({@link SurvivorWork})
 * @param medic            the medic's turn
 * @param mechanic         the mechanic's turn
 * @param upkeep           food eaten from the lockers ({@link SurvivorUpkeep})
 * @param roles            role-change rules ({@link Survivors#roleChangeRefusal}, {@link SurvivorWork#workSpeed})
 * @param trader           district traders ({@link Traders})
 * @param holdout          survivors hiding in buildings ({@link SurvivorHoldouts}); they come from {@code rescueFirstDay} too
 */
public record SurvivorBalance(Caps caps, int rescueFirstDay, double guardDamageShare, double guardBonusHealth,
		Scavenger scavenger, Medic medic, Mechanic mechanic, Upkeep upkeep, Roles roles, Trader trader, Holdout holdout) {

	/**
	 * @param group a player's group (their own and their OPAC party's survivors) takes no more rescues past this many
	 * @param base  a Bunker Core takes no more settlers past this many
	 * @param world past this many survivors on the server, distress calls only leave the note
	 */
	public record Caps(int group, int base, int world) {
		public static final Caps DEFAULT = new Caps(6, 6, 30);
		public static final Codec<Caps> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(0, 64).optionalFieldOf("group", DEFAULT.group).forGetter(Caps::group),
				Codec.intRange(0, 64).optionalFieldOf("base", DEFAULT.base).forGetter(Caps::base),
				Codec.intRange(0, 1024).optionalFieldOf("world", DEFAULT.world).forGetter(Caps::world)
		).apply(i, Caps::new));
	}

	/**
	 * @param leaveFrom    day-clock tick (0 = 06:00) from which a scavenger sets out ...
	 * @param leaveUntil   ... and before which
	 * @param tripTicks    how long a trip lasts
	 * @param haulRolls    rolls of one building table per haul: the haul's share of a room of about four containers
	 * @param haulAttempts rerolls when a haul came back empty
	 * @param hurtChance   chance it comes back at half health
	 * @param lostChance   chance it never comes back
	 * @param fitToLeave   it stays home below this share of its health
	 * @param antibioticsKeep each Antibiotics stack a haul rolls comes home only with this chance (10.2.2: Mission
	 *                     scavengers were an uncapped antibiotic supply)
	 */
	public record Scavenger(long leaveFrom, long leaveUntil, long tripTicks, int haulRolls, int haulAttempts, float hurtChance,
			float lostChance, float fitToLeave, float antibioticsKeep) {
		public static final Scavenger DEFAULT = new Scavenger(0L, 4_000L, 12_000L, 2, 4, 0.08f, 0.03f, 0.5f, 0.25f);
		public static final Codec<Scavenger> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("leave_from", DEFAULT.leaveFrom).forGetter(Scavenger::leaveFrom),
				Codec.LONG.optionalFieldOf("leave_until", DEFAULT.leaveUntil).forGetter(Scavenger::leaveUntil),
				Codec.LONG.optionalFieldOf("trip_ticks", DEFAULT.tripTicks).forGetter(Scavenger::tripTicks),
				Codec.intRange(0, 16).optionalFieldOf("haul_rolls", DEFAULT.haulRolls).forGetter(Scavenger::haulRolls),
				Codec.intRange(1, 16).optionalFieldOf("haul_attempts", DEFAULT.haulAttempts).forGetter(Scavenger::haulAttempts),
				Codec.floatRange(0f, 1f).optionalFieldOf("hurt_chance", DEFAULT.hurtChance).forGetter(Scavenger::hurtChance),
				Codec.floatRange(0f, 1f).optionalFieldOf("lost_chance", DEFAULT.lostChance).forGetter(Scavenger::lostChance),
				Codec.floatRange(0f, 1f).optionalFieldOf("fit_to_leave", DEFAULT.fitToLeave).forGetter(Scavenger::fitToLeave),
				Codec.floatRange(0f, 1f).optionalFieldOf("antibiotics_keep", DEFAULT.antibioticsKeep).forGetter(Scavenger::antibioticsKeep)
		).apply(i, Scavenger::new));
	}

	/**
	 * @param intervalTicks  ticks between a medic's turns at full speed
	 * @param range          it treats people within this many blocks of home
	 * @param worthABandage  health a patient must be missing before a bandage is spent on them
	 */
	public record Medic(int intervalTicks, double range, float worthABandage) {
		public static final Medic DEFAULT = new Medic(100, 16.0, 2.0f);
		public static final Codec<Medic> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 72_000).optionalFieldOf("interval_ticks", DEFAULT.intervalTicks).forGetter(Medic::intervalTicks),
				Codec.doubleRange(1.0, 64.0).optionalFieldOf("range", DEFAULT.range).forGetter(Medic::range),
				Codec.floatRange(0f, 20f).optionalFieldOf("worth_a_bandage", DEFAULT.worthABandage).forGetter(Medic::worthABandage)
		).apply(i, Medic::new));
	}

	/**
	 * @param intervalTicks ticks between a mechanic's turns at full speed
	 * @param setsPerTurn   most sets of repair materials a mechanic spends on a worn wall block in one turn (10.2.2: one,
	 *                      so a wall under attack still falls; before, it restored the block whole every turn)
	 */
	public record Mechanic(int intervalTicks, int setsPerTurn) {
		public static final Mechanic DEFAULT = new Mechanic(600, 1);
		public static final Codec<Mechanic> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 72_000).optionalFieldOf("interval_ticks", DEFAULT.intervalTicks).forGetter(Mechanic::intervalTicks),
				Codec.intRange(1, 64).optionalFieldOf("sets_per_turn", DEFAULT.setsPerTurn).forGetter(Mechanic::setsPerTurn)
		).apply(i, Mechanic::new));
	}

	/**
	 * @param nutritionPerDay   hunger points of food a settler eats a day (5, a loaf of bread; a cooked steak is 8)
	 * @param extraNutritionPerDay hunger points a day for each settler past {@code freeSettlers} at the same core
	 * @param freeSettlers      settlers per core that eat only {@code nutritionPerDay} (upkeep rises after this many)
	 * @param hungryDaysToLeave a settler that goes this many days in a row without food leaves for good
	 * @param hungrySpeed       work speed while hungry
	 */
	public record Upkeep(int nutritionPerDay, int extraNutritionPerDay, int freeSettlers, int hungryDaysToLeave, double hungrySpeed) {
		public static final Upkeep DEFAULT = new Upkeep(5, 10, 3, 3, 0.5);
		public static final Codec<Upkeep> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(0, 200).optionalFieldOf("nutrition_per_day", DEFAULT.nutritionPerDay).forGetter(Upkeep::nutritionPerDay),
				Codec.intRange(0, 200).optionalFieldOf("extra_nutrition_per_day", DEFAULT.extraNutritionPerDay).forGetter(Upkeep::extraNutritionPerDay),
				Codec.intRange(0, 64).optionalFieldOf("free_settlers", DEFAULT.freeSettlers).forGetter(Upkeep::freeSettlers),
				Codec.intRange(1, 365).optionalFieldOf("hungry_days_to_leave", DEFAULT.hungryDaysToLeave).forGetter(Upkeep::hungryDaysToLeave),
				Codec.doubleRange(0.05, 1.0).optionalFieldOf("hungry_speed", DEFAULT.hungrySpeed).forGetter(Upkeep::hungrySpeed)
		).apply(i, Upkeep::new));
	}

	/**
	 * @param firstDaySpeed work speed for the first {@code firstDayTicks} in a new role
	 * @param firstDayTicks how long a new role is new: one in-game day
	 * @param minSpeed      the slowest a survivor ever works, however many slowdowns stack (hungry and new: a quarter)
	 */
	public record Roles(double firstDaySpeed, long firstDayTicks, double minSpeed) {
		public static final Roles DEFAULT = new Roles(0.5, 24_000L, 0.25);
		public static final Codec<Roles> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.doubleRange(0.05, 1.0).optionalFieldOf("first_day_speed", DEFAULT.firstDaySpeed).forGetter(Roles::firstDaySpeed),
				Codec.LONG.optionalFieldOf("first_day_ticks", DEFAULT.firstDayTicks).forGetter(Roles::firstDayTicks),
				Codec.doubleRange(0.05, 1.0).optionalFieldOf("min_speed", DEFAULT.minSpeed).forGetter(Roles::minSpeed)
		).apply(i, Roles::new));
	}

	/**
	 * @param restockTicks a trader's offers are full again this long after the last restock (three in-game days)
	 * @param replaceTicks a new trader arrives this long after the last one was killed or turned (two in-game days)
	 * @param radius       a trader never strays further than this from its post
	 */
	public record Trader(long restockTicks, long replaceTicks, int radius) {
		public static final Trader DEFAULT = new Trader(72_000L, 48_000L, 8);
		public static final Codec<Trader> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("restock_ticks", DEFAULT.restockTicks).forGetter(Trader::restockTicks),
				Codec.LONG.optionalFieldOf("replace_ticks", DEFAULT.replaceTicks).forGetter(Trader::replaceTicks),
				Codec.intRange(2, 32).optionalFieldOf("radius", DEFAULT.radius).forGetter(Trader::radius)
		).apply(i, Trader::new));
	}

	/**
	 * @param chance    chance a fitting building's first population also holds a holdout
	 * @param callRange a holdout calls out once to the first player within this many blocks
	 */
	public record Holdout(float chance, double callRange) {
		public static final Holdout DEFAULT = new Holdout(0.06f, 12.0);
		public static final Codec<Holdout> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.floatRange(0f, 1f).optionalFieldOf("chance", DEFAULT.chance).forGetter(Holdout::chance),
				Codec.doubleRange(1.0, 64.0).optionalFieldOf("call_range", DEFAULT.callRange).forGetter(Holdout::callRange)
		).apply(i, Holdout::new));
	}

	public static final SurvivorBalance DEFAULT = new SurvivorBalance(Caps.DEFAULT, 7, 1.0, 10.0, Scavenger.DEFAULT, Medic.DEFAULT,
			Mechanic.DEFAULT, Upkeep.DEFAULT, Roles.DEFAULT, Trader.DEFAULT, Holdout.DEFAULT);

	public static final Codec<SurvivorBalance> CODEC = RecordCodecBuilder.create(i -> i.group(
			Caps.CODEC.optionalFieldOf("caps", Caps.DEFAULT).forGetter(SurvivorBalance::caps),
			Codec.intRange(0, 10_000).optionalFieldOf("rescue_first_day", DEFAULT.rescueFirstDay).forGetter(SurvivorBalance::rescueFirstDay),
			Codec.doubleRange(0.0, 4.0).optionalFieldOf("guard_damage_share", DEFAULT.guardDamageShare).forGetter(SurvivorBalance::guardDamageShare),
			Codec.doubleRange(0.0, 100.0).optionalFieldOf("guard_bonus_health", DEFAULT.guardBonusHealth).forGetter(SurvivorBalance::guardBonusHealth),
			Scavenger.CODEC.optionalFieldOf("scavenger", Scavenger.DEFAULT).forGetter(SurvivorBalance::scavenger),
			Medic.CODEC.optionalFieldOf("medic", Medic.DEFAULT).forGetter(SurvivorBalance::medic),
			Mechanic.CODEC.optionalFieldOf("mechanic", Mechanic.DEFAULT).forGetter(SurvivorBalance::mechanic),
			Upkeep.CODEC.optionalFieldOf("upkeep", Upkeep.DEFAULT).forGetter(SurvivorBalance::upkeep),
			Roles.CODEC.optionalFieldOf("roles", Roles.DEFAULT).forGetter(SurvivorBalance::roles),
			Trader.CODEC.optionalFieldOf("trader", Trader.DEFAULT).forGetter(SurvivorBalance::trader),
			Holdout.CODEC.optionalFieldOf("holdout", Holdout.DEFAULT).forGetter(SurvivorBalance::holdout)
	).apply(i, SurvivorBalance::new));

	private static volatile SurvivorBalance current = DEFAULT;

	public static SurvivorBalance get() {
		return current;
	}

	/** These numbers with other holdout rules (gametests). */
	public SurvivorBalance withHoldout(Holdout next) {
		return new SurvivorBalance(caps, rescueFirstDay, guardDamageShare, guardBonusHealth, scavenger, medic, mechanic, upkeep, roles,
				trader, next);
	}

	/** These numbers with holdouts and rescues from {@code day} (gametests). */
	public SurvivorBalance withRescueFirstDay(int day) {
		return new SurvivorBalance(caps, day, guardDamageShare, guardBonusHealth, scavenger, medic, mechanic, upkeep, roles, trader,
				holdout);
	}

	/** These numbers with other mechanic rules (gametests). */
	public SurvivorBalance withMechanic(Mechanic next) {
		return new SurvivorBalance(caps, rescueFirstDay, guardDamageShare, guardBonusHealth, scavenger, medic, next, upkeep, roles,
				trader, holdout);
	}

	/** These numbers with other scavenger rules (gametests). */
	public SurvivorBalance withScavenger(Scavenger next) {
		return new SurvivorBalance(caps, rescueFirstDay, guardDamageShare, guardBonusHealth, next, medic, mechanic, upkeep, roles,
				trader, holdout);
	}

	/** For gametests: replace the loaded numbers until the next reload (null goes back to the defaults). */
	public static void setForTesting(SurvivorBalance balance) {
		current = balance == null ? DEFAULT : balance;
	}

	/** Reload listener for {@code data/<ns>/ashfall/survivor/balance.json}; the {@code ashfall} file wins. */
	public static final class Loader extends SimpleJsonResourceReloadListener<SurvivorBalance> {
		public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "survivor_balance");
		private static final Identifier FILE = Identifier.fromNamespaceAndPath("ashfall", "balance");

		public Loader() {
			super(CODEC, FileToIdConverter.json("ashfall/survivor"));
		}

		@Override
		protected void apply(Map<Identifier, SurvivorBalance> loaded, ResourceManager manager, ProfilerFiller profiler) {
			current = loaded.getOrDefault(FILE, DEFAULT);
			AshfallArms.LOGGER.info("Survivor balance loaded (caps {}/{}/{}, {} food points a day)", current.caps.group(), current.caps.base(),
					current.caps.world(), current.upkeep.nutritionPerDay());
		}
	}
}
