package xyz.toesinthesand.ashfall.arms.client.grid;

import xyz.toesinthesand.ashfall.arms.grid.DistrictState;

/**
 * One palette for the HUD pips, the map fills and the map legend, so a colour means the same state everywhere:
 * grey dark, dim amber repaired, bright amber energized, red siege, pale cyan lit (reclaimed).
 */
public final class GridColours {
	private GridColours() {
	}

	/** Bright pip / marker colour. */
	public static int pip(DistrictState state) {
		return switch (state) {
			case DARK -> 0xFF4A4A52;
			case REPAIRED -> 0xFFB0782A;
			case ENERGIZED -> 0xFFFFC940;
			case SIEGE -> 0xFFE53935;
			case RECLAIMED -> 0xFF9FF0FF;
			case BROKEN -> 0xFF7A1010;
			case BLACKOUT -> 0xFF6A4A8A;
		};
	}

	/** Muted area fill for the map, dark enough that labels on top stay readable. */
	public static int fill(DistrictState state) {
		return switch (state) {
			case DARK -> 0xFF2A2A31;
			case REPAIRED -> 0xFF4A3A1E;
			case ENERGIZED -> 0xFF5E5016;
			case SIEGE -> 0xFF5A1C1C;
			case RECLAIMED -> 0xFF1E4E5A;
			case BROKEN -> 0xFF3A1414;
			case BLACKOUT -> 0xFF2E2440;
		};
	}

	/** Siege flashes (on the HUD and the map) so it reads as urgent, every other half second. */
	public static int pip(DistrictState state, long gameTime) {
		return state == DistrictState.SIEGE && (gameTime / 10) % 2 == 0 ? 0xFF7A1A1A : pip(state);
	}
}
