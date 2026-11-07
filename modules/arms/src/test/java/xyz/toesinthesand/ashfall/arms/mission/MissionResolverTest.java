package xyz.toesinthesand.ashfall.arms.mission;

import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The objective chain over hand-made states: every step from the radio to the sandbox, each district sub-step, the
 * district focus rules, world events outranking personal steps, and the checklist marks.
 */
class MissionResolverTest {
	private static final List<String> ORDER = List.of("river_arts", "south_slope", "downtown", "mission");
	private static final MissionPos SAFEHOUSE = new MissionPos(-152, -792);
	private static final MissionPos HULL = new MissionPos(2300, -100);

	/** A fresh world: nothing found, every district dark, no ending. */
	private static Builder fresh() {
		return new Builder();
	}

	/** Mutable test builder for {@link MissionFacts}. */
	private static final class Builder {
		boolean radio, armed;
		Optional<MissionPos> bunker = Optional.empty();
		final List<MissionFacts.District> districts = new ArrayList<>();
		int crystals, bosses, banks, pylons;
		boolean frame, uplink, voteOpen = true;
		MissionFacts.Finale finale = MissionFacts.Finale.IDLE;
		String ending = MissionFacts.NO_ENDING;
		Optional<MissionPos> boss = Optional.of(new MissionPos(-952, 120));

		Builder() {
			for (int i = 0; i < ORDER.size(); i++) {
				districts.add(dark(ORDER.get(i)));
			}
		}

		Builder ready() {
			radio = true;
			armed = true;
			bunker = Optional.of(new MissionPos(10, 10));
			return this;
		}

		Builder set(MissionFacts.District d) {
			for (int i = 0; i < districts.size(); i++) {
				if (districts.get(i).id().equals(d.id())) {
					districts.set(i, d);
				}
			}
			return this;
		}

		Builder allReclaimed() {
			for (String id : ORDER) {
				set(state(id, DistrictState.RECLAIMED));
			}
			return this;
		}

		MissionFacts build() {
			return new MissionFacts(radio, armed, bunker, districts, crystals, 3, bosses, 3, boss, frame, banks, 2, uplink, finale, voteOpen, pylons, 4,
					districts.stream().filter(MissionFacts.District::reclaimed).mapToInt(d -> 20).sum(), ending, Optional.of(SAFEHOUSE),
					Optional.of(HULL));
		}
	}

	private static MissionFacts.District dark(String id) {
		return state(id, DistrictState.DARK);
	}

	private static MissionFacts.District state(String id, DistrictState state) {
		return district(id, state, 0, 0, 0, 0);
	}

	private static MissionFacts.District district(String id, DistrictState state, int couplers, int cells, int hold, int linked) {
		return new MissionFacts.District(id, state, couplers, state == DistrictState.BROKEN ? 2 : 4, cells, 4, hold, 32, linked, 6,
				Optional.of(new MissionPos(ORDER.indexOf(id) * 100, 0)), Optional.of(new MissionPos(5, 5)));
	}

	private static Objective now(Builder b) {
		return MissionResolver.current(b.build());
	}

	@Test
	void newPlayerIsSentToTheRadioAtTheSafehouse() {
		Objective o = now(fresh());
		assertEquals(MissionStep.RADIO, o.step());
		assertEquals("radio", o.key());
		assertEquals(Optional.of(SAFEHOUSE), o.target());
	}

	@Test
	void withTheRadioTheNextStepIsArming() {
		Builder b = fresh();
		b.radio = true;
		assertEquals("arm", now(b).key());
	}

	@Test
	void theOptionalHavenNeverHidesTheWayToRiverArts() {
		Builder b = fresh();
		b.radio = true;
		b.armed = true;
		Objective o = now(b);
		assertEquals("district.couplers", o.key(), "no Bunker Core must not hold the player on the optional step");
		assertEquals(Optional.of(new MissionPos(0, 0)), o.target(), "points at the River Arts core straight away");
		MissionResolver.Entry haven = MissionResolver.checklist(b.build()).get(2);
		assertEquals(MissionStep.HAVEN, haven.step());
		assertTrue(haven.optional() && haven.mark() == MissionResolver.Mark.TODO, "the haven stays an optional row");
	}

	@Test
	void withEveryBossDownTheCrystalStepPointsAtCrashSites() {
		Builder b = fresh().ready().allReclaimed();
		b.crystals = 2;
		b.bosses = 2;
		assertEquals("crystals", now(b).key());
		b.bosses = 3;
		Objective o = now(b);
		assertEquals("crystals.crash", o.key(), "the dead bosses can drop no more");
		assertEquals(Optional.empty(), o.target(), "no boss lair or hull to point at");
	}

	@Test
	void aFallenHeartWithNoVoteDoesNotAskForAClick() {
		Builder b = fresh().ready().allReclaimed();
		b.finale = MissionFacts.Finale.COMPLETE;
		assertEquals("choose", now(b).key());
		b.voteOpen = false;
		assertEquals("choose.waiting", now(b).key(), "rule off or vote cut short: no [Sever]/[Bind] to click");
	}

	@Test
	void riverArtsIsRecommendedFirstWithCounts() {
		Objective o = now(fresh().ready());
		assertEquals(MissionStep.DISTRICT, o.step());
		assertEquals("district.couplers", o.key());
		assertEquals(List.of(ObjectiveArg.district("river_arts"), ObjectiveArg.of(0), ObjectiveArg.of(4)), o.args());
		assertEquals(Optional.of(new MissionPos(0, 0)), o.target(), "points at the River Arts core");
	}

	@Test
	void everyDistrictSubStep() {
		Builder b = fresh().ready();
		b.set(district("river_arts", DistrictState.DARK, 2, 0, 0, 0));
		Objective couplers = now(b);
		assertEquals("district.couplers", couplers.key());
		assertEquals(ObjectiveArg.of(2), couplers.args().get(1));

		b.set(district("river_arts", DistrictState.REPAIRED, 4, 1, 0, 0));
		Objective cells = now(b);
		assertEquals("district.cells", cells.key());
		assertEquals(List.of(ObjectiveArg.district("river_arts"), ObjectiveArg.of(1), ObjectiveArg.of(4)), cells.args());

		b.set(district("river_arts", DistrictState.REPAIRED, 4, 4, 40, 2));
		Objective hold = now(b);
		assertEquals("district.hold", hold.key());
		assertEquals(List.of(ObjectiveArg.district("river_arts"), ObjectiveArg.of(40), ObjectiveArg.of(32), ObjectiveArg.of(2), ObjectiveArg.of(6)),
				hold.args());

		b.set(district("river_arts", DistrictState.REPAIRED, 4, 4, 100, 3));
		Objective link = now(b);
		assertEquals("district.link", link.key());
		assertEquals(Optional.of(new MissionPos(5, 5)), link.target(), "the link step points at the next unlinked relay");
		assertEquals(ObjectiveArg.of(3), link.args().get(1));

		b.set(state("river_arts", DistrictState.ENERGIZED));
		assertEquals("district.siege_soon", now(b).key());
		b.set(state("river_arts", DistrictState.SIEGE));
		assertEquals("district.siege", now(b).key());

		b.set(state("river_arts", DistrictState.RECLAIMED));
		Objective next = now(b);
		assertEquals("district.couplers", next.key());
		assertEquals(ObjectiveArg.district("south_slope"), next.args().getFirst(), "after River Arts comes South Slope");
	}

	@Test
	void theDistrictBeingWorkedOnBeatsTheRecommendedOrder() {
		Builder b = fresh().ready();
		b.set(district("downtown", DistrictState.REPAIRED, 4, 2, 0, 0));
		assertEquals(ObjectiveArg.district("downtown"), now(b).args().getFirst());
		// Two started: the one further along the loop wins.
		b.set(state("mission", DistrictState.SIEGE));
		assertEquals(ObjectiveArg.district("mission"), now(b).args().getFirst());
		// Equal progress: the recommended order breaks the tie.
		b.set(state("south_slope", DistrictState.SIEGE));
		assertEquals(ObjectiveArg.district("south_slope"), now(b).args().getFirst());
	}

	@Test
	void brokenCoreBeforeTheEndingAsksForTheHalfCostRepair() {
		Builder b = fresh().ready().allReclaimed();
		b.set(district("downtown", DistrictState.BROKEN, 1, 0, 0, 6));
		Objective o = now(b);
		assertEquals("district.rerepair", o.key());
		assertEquals(List.of(ObjectiveArg.district("downtown"), ObjectiveArg.of(1), ObjectiveArg.of(2)), o.args());
	}

	@Test
	void afterTheDistrictsComeCrystalsUplinkAndTheHull() {
		Builder b = fresh().ready().allReclaimed();
		Objective crystals = now(b);
		assertEquals(MissionStep.CRYSTALS, crystals.step());
		assertEquals(List.of(ObjectiveArg.of(0), ObjectiveArg.of(3), ObjectiveArg.of(0), ObjectiveArg.of(3)), crystals.args());
		assertEquals(b.boss, crystals.target(), "points at the nearest boss still standing");

		b.crystals = 2;
		b.bosses = 2;
		b.boss = Optional.empty();
		assertEquals(Optional.of(HULL), now(b).target(), "with no lair known the Wreck is the last boss's home");

		b.crystals = 3;
		Objective uplink = now(b);
		assertEquals("uplink", uplink.key());
		assertEquals(List.of(ObjectiveArg.of(0), ObjectiveArg.of(0), ObjectiveArg.of(2)), uplink.args());

		b.crystals = 0;
		b.frame = true;
		b.banks = 1;
		Objective partial = now(b);
		assertEquals("uplink", partial.key(), "crystals spent on the frame still count as collected");
		assertEquals(List.of(ObjectiveArg.of(1), ObjectiveArg.of(1), ObjectiveArg.of(2)), partial.args());

		b.uplink = true;
		Objective go = now(b);
		assertEquals("black_start.go", go.key());
		assertEquals(Optional.of(HULL), go.target());
		assertEquals(List.of(ObjectiveArg.of(80)), go.args());
	}

	@Test
	void blackStartPhasesAndPause() {
		Builder b = fresh().ready().allReclaimed();
		b.finale = MissionFacts.Finale.SURGE;
		assertEquals("black_start.surge", now(b).key());
		b.finale = MissionFacts.Finale.FEEDBACK;
		assertEquals("black_start.feedback", now(b).key());
		b.finale = MissionFacts.Finale.HEART;
		b.pylons = 3;
		Objective heart = now(b);
		assertEquals("black_start.heart", heart.key());
		assertEquals(List.of(ObjectiveArg.of(3), ObjectiveArg.of(4)), heart.args());
		b.finale = MissionFacts.Finale.PAUSED;
		assertEquals("black_start.paused", now(b).key());
		b.finale = MissionFacts.Finale.STANDBY;
		assertEquals("black_start.standby", now(b).key());
	}

	@Test
	void aRunningFinaleOutranksANewcomersMissingRadio() {
		Builder b = fresh().allReclaimed();
		b.finale = MissionFacts.Finale.SURGE;
		assertEquals(MissionStep.BLACK_START, now(b).step());
		b.finale = MissionFacts.Finale.COMPLETE;
		assertEquals("choose", now(b).key());
	}

	@Test
	void afterTheEndingTheSandboxGoals() {
		Builder b = fresh().ready().allReclaimed();
		b.finale = MissionFacts.Finale.COMPLETE;
		b.ending = "sever";
		Objective after = now(b);
		assertEquals(MissionStep.AFTER, after.step());
		assertEquals("after", after.key());
		assertEquals(List.of(ObjectiveArg.key("ending.ashfall.sever")), after.args());

		b.set(state("south_slope", DistrictState.SIEGE));
		assertEquals("after.breach", now(b).key());
		b.set(district("south_slope", DistrictState.BROKEN, 0, 0, 0, 6));
		Objective broken = now(b);
		assertEquals("district.rerepair", broken.key());
		assertEquals(MissionStep.AFTER, broken.step());
	}

	@Test
	void checklistMarksDoneCurrentAndTodo() {
		Builder b = fresh().ready();
		b.bunker = Optional.empty();
		b.set(state("river_arts", DistrictState.RECLAIMED));
		b.set(district("south_slope", DistrictState.REPAIRED, 4, 1, 0, 0));
		List<MissionResolver.Entry> rows = MissionResolver.checklist(b.build());
		assertEquals(MissionResolver.Mark.DONE, rows.get(0).mark(), "radio");
		assertEquals(MissionResolver.Mark.DONE, rows.get(1).mark(), "armed");
		assertEquals(MissionResolver.Mark.TODO, rows.get(2).mark(), "haven skipped");
		assertTrue(rows.get(2).optional());
		assertEquals(MissionResolver.Mark.DONE, rows.get(3).mark(), "River Arts");
		assertEquals(MissionResolver.Mark.CURRENT, rows.get(4).mark(), "South Slope is being worked on");
		assertEquals(MissionResolver.Mark.TODO, rows.get(5).mark(), "Downtown");
		assertEquals(MissionResolver.Mark.TODO, rows.get(6).mark(), "Mission");
		assertEquals(ObjectiveArg.key("district_state.ashfall.repaired"), rows.get(4).label().args().get(1));
		assertEquals(11, rows.size(), "no sandbox row before the ending");
		assertTrue(rows.stream().filter(r -> r.mark() == MissionResolver.Mark.CURRENT).count() == 1, "exactly one current row");

		b.allReclaimed();
		b.finale = MissionFacts.Finale.COMPLETE;
		b.ending = "bind";
		List<MissionResolver.Entry> after = MissionResolver.checklist(b.build());
		assertEquals(12, after.size());
		assertEquals(MissionResolver.Mark.CURRENT, after.getLast().mark());
		assertTrue(after.subList(3, after.size() - 1).stream().allMatch(r -> r.mark() == MissionResolver.Mark.DONE),
				"every district and endgame row is done after the ending");
	}

	/** Every objective key the resolver can produce, so the lang test can check each has its text and how-to. */
	static List<String> allKeys() {
		List<String> keys = new ArrayList<>(List.of("radio", "arm", "district.couplers", "district.rerepair", "district.cells",
				"district.hold", "district.link", "district.siege_soon", "district.siege", "district.reclaimed", "crystals", "crystals.crash", "uplink",
				"black_start.go", "black_start.standby", "black_start.surge", "black_start.feedback", "black_start.heart",
				"black_start.paused", "choose", "choose.waiting", "after", "after.breach"));
		return keys;
	}
}
