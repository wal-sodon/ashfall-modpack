package xyz.toesinthesand.ashfall.arms.mission;

import xyz.toesinthesand.ashfall.arms.grid.DistrictState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks the player's current objective and builds the mission checklist from a {@link MissionFacts} snapshot. Pure
 * rules, no world access.
 * <p>
 * The chain follows endgame-design section 3: radio, arms, an optional haven, the four districts, the crystals, the
 * Uplink, the Black Start, the choice, then the sandbox. A few rules decide what "current" means when the world is
 * further along than the player:
 * <ul>
 * <li>World events come first. A running Black Start, a finished one waiting for its vote, or a chosen ending
 * outranks a newcomer's missing radio: in co-op the person who just joined should be told what the party is doing.</li>
 * <li>The haven is optional, so it is never the current objective: it is only a checklist row. As an objective it
 * had no place to point at and hid the walk to River Arts, sometimes for hours, from a player who skipped it.</li>
 * <li>The choice only asks for a click while a vote is actually open. With {@code ashfall:objective} off, or a vote
 * cut short by a restart, the Heart can fall with no vote, and the text says how an ending still gets picked.</li>
 * <li>With every story boss down the crystal step points at crash sites, the only source left before the ending.</li>
 * <li>Districts may be done in any order. The objective follows the district the party is already working on (the
 * one furthest along the loop), and otherwise suggests the next unlit one in the recommended order, River Arts
 * first, as the zones' tiers rise.</li>
 * </ul>
 */
public final class MissionResolver {
	private MissionResolver() {
	}

	/** How a checklist row is marked. */
	public enum Mark {
		DONE, CURRENT, TODO
	}

	/**
	 * One checklist row.
	 *
	 * @param step     the stage
	 * @param mark     done, current or still to do
	 * @param label    the row's text (an objective key under {@code objective.ashfall.list.})
	 * @param optional the row can be skipped (the haven)
	 */
	public record Entry(MissionStep step, Mark mark, Objective label, boolean optional) {
	}

	/** The objective the player should work on now. */
	public static Objective current(MissionFacts f) {
		if (f.endingChosen()) {
			return afterEnding(f);
		}
		if (f.finale() == MissionFacts.Finale.COMPLETE) {
			return Objective.of(MissionStep.CHOOSE, f.voteOpen() ? "choose" : "choose.waiting", f.hull());
		}
		if (f.finale().running()) {
			return blackStart(f);
		}
		if (!f.radio()) {
			return Objective.of(MissionStep.RADIO, "radio", f.safehouse());
		}
		if (!f.armed()) {
			return Objective.of(MissionStep.ARM, "arm", Optional.empty());
		}
		Optional<MissionFacts.District> focus = focusDistrict(f.districts());
		if (focus.isPresent()) {
			return district(focus.get(), MissionStep.DISTRICT);
		}
		if (f.finale() == MissionFacts.Finale.STANDBY) {
			return Objective.of(MissionStep.BLACK_START, "black_start.standby", f.hull());
		}
		if (!crystalsDone(f)) {
			boolean bossesLeft = f.bosses() < f.bossesTotal();
			return Objective.of(MissionStep.CRYSTALS, bossesLeft ? "crystals" : "crystals.crash",
					bossesLeft ? f.nearestBoss().or(f::hull) : Optional.empty(), ObjectiveArg.of(f.crystals()),
					ObjectiveArg.of(f.crystalsNeeded()), ObjectiveArg.of(f.bosses()), ObjectiveArg.of(f.bossesTotal()));
		}
		if (!uplinkDone(f)) {
			return Objective.of(MissionStep.UPLINK, "uplink", Optional.empty(), ObjectiveArg.of(f.frame() ? 1 : 0),
					ObjectiveArg.of(Math.min(f.banks(), f.banksNeeded())), ObjectiveArg.of(f.banksNeeded()));
		}
		return Objective.of(MissionStep.BLACK_START, "black_start.go", f.hull(), ObjectiveArg.of(f.gridPercent()));
	}

	/**
	 * The district the objective should be about: the unreclaimed one furthest along the loop (a party that has
	 * started South Slope should not be sent to River Arts), else the first unreclaimed one in the recommended order.
	 */
	public static Optional<MissionFacts.District> focusDistrict(List<MissionFacts.District> districts) {
		Optional<MissionFacts.District> started = districts.stream()
				.filter(d -> !d.reclaimed() && d.started())
				.max(Comparator.comparingInt(MissionResolver::progressRank)
						.thenComparing(Comparator.comparingInt((MissionFacts.District d) -> districts.indexOf(d)).reversed()));
		return started.or(() -> districts.stream().filter(d -> !d.reclaimed()).findFirst());
	}

	/** Further along the loop is higher; a BROKEN core and a DARK one with couplers in rank lowest. */
	static int progressRank(MissionFacts.District d) {
		return switch (d.state()) {
			case SIEGE -> 4;
			case ENERGIZED -> 3;
			case REPAIRED -> 2;
			case DARK, BROKEN, BLACKOUT -> 1;
			case RECLAIMED -> 0;
		};
	}

	/** The sub-step of one district's loop. */
	static Objective district(MissionFacts.District d, MissionStep step) {
		ObjectiveArg name = ObjectiveArg.district(d.id());
		return switch (d.state()) {
			case DARK -> Objective.of(step, "district.couplers", d.core(), name, ObjectiveArg.of(d.couplers()), ObjectiveArg.of(d.couplersNeeded()));
			case BLACKOUT -> Objective.of(step, "district.blackout", d.core(), name);
			case BROKEN -> Objective.of(step, "district.rerepair", d.core(), name, ObjectiveArg.of(d.couplers()), ObjectiveArg.of(d.couplersNeeded()));
			case REPAIRED -> {
				if (d.cells() < d.cellsNeeded()) {
					yield Objective.of(step, "district.cells", d.core(), name, ObjectiveArg.of(d.cells()), ObjectiveArg.of(d.cellsNeeded()));
				}
				if (d.holdPercent() < 100) {
					yield Objective.of(step, "district.hold", d.core(), name, ObjectiveArg.of(d.holdPercent()), ObjectiveArg.of(d.holdRadius()),
							ObjectiveArg.of(d.linked()), ObjectiveArg.of(d.keys()));
				}
				if (d.linked() < d.keys()) {
					yield Objective.of(step, "district.link", d.nextRelay().or(d::core), name, ObjectiveArg.of(d.linked()), ObjectiveArg.of(d.keys()));
				}
				// Hold done and every relay linked: the controller energizes on its next pass.
				yield Objective.of(step, "district.hold", d.core(), name, ObjectiveArg.of(100), ObjectiveArg.of(d.holdRadius()),
						ObjectiveArg.of(d.linked()), ObjectiveArg.of(d.keys()));
			}
			case ENERGIZED -> Objective.of(step, "district.siege_soon", d.core(), name);
			case SIEGE -> Objective.of(step, "district.siege", d.core(), name);
			case RECLAIMED -> Objective.of(step, "district.reclaimed", d.core(), name);
		};
	}

	private static Objective blackStart(MissionFacts f) {
		return switch (f.finale()) {
			case SURGE -> Objective.of(MissionStep.BLACK_START, "black_start.surge", f.hull());
			case FEEDBACK -> Objective.of(MissionStep.BLACK_START, "black_start.feedback", f.hull());
			case HEART -> Objective.of(MissionStep.BLACK_START, "black_start.heart", f.hull(), ObjectiveArg.of(f.pylonsBroken()),
					ObjectiveArg.of(f.pylonsTotal()));
			default -> Objective.of(MissionStep.BLACK_START, "black_start.paused", f.hull());
		};
	}

	/** After the ending: a broken substation first (it costs the district its perks), then a running Breach, then the open sandbox. */
	private static Objective afterEnding(MissionFacts f) {
		for (MissionFacts.District d : f.districts()) {
			if (d.state().dark()) {
				return district(d, MissionStep.AFTER);
			}
		}
		for (MissionFacts.District d : f.districts()) {
			if (d.state() == DistrictState.SIEGE) {
				return Objective.of(MissionStep.AFTER, "after.breach", d.core(), ObjectiveArg.district(d.id()));
			}
		}
		return Objective.of(MissionStep.AFTER, "after", Optional.empty(), ObjectiveArg.key("ending.ashfall." + f.ending()));
	}

	/** The crystals are no longer needed once they went into a frame, an Uplink or a Black Start. */
	static boolean crystalsDone(MissionFacts f) {
		return f.crystals() >= f.crystalsNeeded() || f.frame() || uplinkDone(f);
	}

	static boolean uplinkDone(MissionFacts f) {
		return f.uplink() || f.finale() != MissionFacts.Finale.IDLE || f.endingChosen();
	}

	/** The whole mission as checklist rows, marked against the current objective. */
	public static List<Entry> checklist(MissionFacts f) {
		Objective now = current(f);
		List<Entry> out = new ArrayList<>();
		out.add(row(MissionStep.RADIO, f.radio(), now, Objective.of(MissionStep.RADIO, "list.radio", Optional.empty()), false));
		out.add(row(MissionStep.ARM, f.armed(), now, Objective.of(MissionStep.ARM, "list.arm", Optional.empty()), false));
		out.add(row(MissionStep.HAVEN, f.bunker().isPresent(), now, Objective.of(MissionStep.HAVEN, "list.haven", Optional.empty()), true));
		Optional<MissionFacts.District> focus = now.step() == MissionStep.DISTRICT ? focusDistrict(f.districts()) : Optional.empty();
		for (MissionFacts.District d : f.districts()) {
			Mark mark = d.reclaimed() ? Mark.DONE : focus.filter(d::equals).isPresent() ? Mark.CURRENT : Mark.TODO;
			out.add(new Entry(MissionStep.DISTRICT, mark, Objective.of(MissionStep.DISTRICT, "list.district", d.core(),
					ObjectiveArg.district(d.id()), ObjectiveArg.key("district_state.ashfall." + d.state().id())), false));
		}
		out.add(row(MissionStep.CRYSTALS, crystalsDone(f), now, Objective.of(MissionStep.CRYSTALS, "list.crystals", Optional.empty(),
				ObjectiveArg.of(Math.min(f.crystals(), f.crystalsNeeded())), ObjectiveArg.of(f.crystalsNeeded())), false));
		out.add(row(MissionStep.UPLINK, uplinkDone(f), now, Objective.of(MissionStep.UPLINK, "list.uplink", Optional.empty()), false));
		boolean blackStartDone = f.finale() == MissionFacts.Finale.COMPLETE || f.endingChosen();
		out.add(row(MissionStep.BLACK_START, blackStartDone, now, Objective.of(MissionStep.BLACK_START, "list.black_start", Optional.empty()), false));
		out.add(row(MissionStep.CHOOSE, f.endingChosen(), now, Objective.of(MissionStep.CHOOSE, "list.choose", Optional.empty()), false));
		if (f.endingChosen()) {
			out.add(row(MissionStep.AFTER, false, now, Objective.of(MissionStep.AFTER, "list.after", Optional.empty()), false));
		}
		return out;
	}

	private static Entry row(MissionStep step, boolean done, Objective now, Objective label, boolean optional) {
		Mark mark = done ? Mark.DONE : now.step() == step ? Mark.CURRENT : Mark.TODO;
		return new Entry(step, mark, label, optional);
	}
}
