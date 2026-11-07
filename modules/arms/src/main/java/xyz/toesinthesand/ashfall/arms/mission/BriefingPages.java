package xyz.toesinthesand.ashfall.arms.mission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The Mission Briefing's pages as lang keys and arguments, kept free of Minecraft types so a unit test can render
 * every page from the shipped {@code en_us.json} and data files and check that it fits a book page.
 * <p>
 * The words live in the lang file; the numbers come from the datapack ({@link Numbers}), so a server that changes the
 * coupler count or the hold time gets a briefing that says so. Each page is a heading (bold) and a body:
 * {@code briefing.ashfall.page.<id>.heading} and {@code .body}.
 */
public final class BriefingPages {
	public static final String PREFIX = "briefing.ashfall.page.";

	private BriefingPages() {
	}

	/**
	 * The datapack numbers the briefing quotes.
	 *
	 * @param couplers         Grid Couplers that repair a core
	 * @param cells            Energy Cells that start the hold
	 * @param holdSeconds      length of the hold
	 * @param holdRadius       how close a player must stay during the hold
	 * @param humRadius        how far the core's hum carries
	 * @param sirenSeconds     how long before nightfall the siege siren sounds
	 * @param districtGrid     the Grid % with every district reclaimed (when the hull opens)
	 * @param darknessCut      percent less darkness spawning in a reclaimed district
	 * @param crystals         Resonance Crystals the Uplink Frame takes
	 * @param cellSeconds      Surge seconds one Energy Cell buys
	 * @param breakerSeconds   seconds each Relay Breaker must be held
	 */
	public record Numbers(int couplers, int cells, int holdSeconds, int holdRadius, int humRadius, int sirenSeconds, int districtGrid,
			int darknessCut, int crystals, int cellSeconds, int breakerSeconds) {
		/** The hold in minutes for the text: "5", or "2.5" when it is not whole minutes. */
		public String holdMinutes() {
			return new BigDecimal(holdSeconds).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
		}
	}

	/** One page: its id (the lang key part) and the body's arguments. */
	public record Page(String id, List<Object> args) {
		public Page {
			args = List.copyOf(args);
		}

		public String headingKey() {
			return PREFIX + id + ".heading";
		}

		public String bodyKey() {
			return PREFIX + id + ".body";
		}
	}

	/** Every page in reading order: situation, mission, how, getting stronger, staying alive, the finale. */
	public static List<Page> pages(Numbers n) {
		return List.of(
				new Page("cover", List.of()),
				new Page("situation", List.of()),
				new Page("the_dead", List.of()),
				new Page("mission", List.of(n.districtGrid())),
				new Page("the_heart", List.of()),
				new Page("repair", List.of(n.couplers())),
				new Page("energize", List.of(n.cells(), n.holdMinutes(), n.holdRadius(), n.humRadius())),
				new Page("link", List.of()),
				new Page("siege", List.of(n.sirenSeconds())),
				new Page("reclaimed", List.of(n.darknessCut())),
				new Page("salvage", List.of()),
				new Page("bench", List.of(n.crystals())),
				new Page("bosses", List.of(n.crystals())),
				new Page("water", List.of()),
				new Page("infection", List.of()),
				new Page("havens", List.of()),
				new Page("hordes", List.of()),
				new Page("uplink", List.of(n.districtGrid(), n.crystals())),
				new Page("black_start", List.of(n.cellSeconds(), n.breakerSeconds())),
				new Page("choice", List.of()),
				new Page("lost", List.of()));
	}
}
