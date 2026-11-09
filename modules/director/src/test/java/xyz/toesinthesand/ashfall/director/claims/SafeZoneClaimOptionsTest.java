package xyz.toesinthesand.ashfall.director.claims;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The playtest bug: every player was refused the safehouse's spruce door, barrels and beds because safe zones used
 * OPAC's locked main server claim config. These pin what the safe-zone sub-config opens and what it keeps closed.
 */
class SafeZoneClaimOptionsTest {
	private static final String INTERACT = "playerConfig.claims.protection.exceptions.groups.block.interact.";

	@Test
	void everyoneMayUseTheSafehouseDoorsContainersAndBeds() {
		Map<String, String> options = SafeZoneClaimOptions.options();
		for (String group : new String[]{"Doors", "Chests", "Barrels", "Beds", "Furnaces", "Anvils", "Controls"}) {
			assertEquals("E", options.get(INTERACT + group), group);
		}
	}

	@Test
	void itemUseStaysProtectedSoNobodyCanWallUpTheDoor() {
		assertFalse(SafeZoneClaimOptions.options().containsKey("playerConfig.claims.protection.exceptions.itemUse"));
	}

	@Test
	void everyoneMayReadBooksInside() {
		assertEquals("E", SafeZoneClaimOptions.options().get("playerConfig.claims.protection.exceptions.groups.item.interact.Books"));
	}

	@Test
	void breakingAndPlacingStayProtected() {
		Map<String, String> options = SafeZoneClaimOptions.options();
		// Not set means inherited from the main server claim config, which protects them from everybody.
		assertFalse(options.containsKey("playerConfig.claims.protection.exceptions.blocksByPlayers"));
		assertFalse(options.containsKey("playerConfig.claims.protection.exceptions.fullAccess"));
		assertFalse(options.containsKey("playerConfig.claims.protection.exceptions.entitiesByPlayers"));
	}

	@Test
	void subConfigIdIsOneOpacAccepts() {
		for (String id : new String[]{SafeZoneClaimOptions.SUB_ID, SafeZoneClaimOptions.OPEN_SUB_ID}) {
			assertTrue(id.matches("[a-zA-Z0-9_-]+"), id);
			assertTrue(id.length() <= 32, id);
		}
		assertFalse(SafeZoneClaimOptions.SUB_ID.equals(SafeZoneClaimOptions.OPEN_SUB_ID));
	}

	/** Reclaimed compounds: everyone gets the full access a Bunker Core's party has, on top of the shelter options. */
	@Test
	void openZonesGiveEveryoneFullAccess() {
		Map<String, String> open = SafeZoneClaimOptions.options(true);
		assertEquals("E", open.get("playerConfig.claims.protection.exceptions.fullAccess"));
		assertEquals(SafeZoneClaimOptions.OPEN_NAME, open.get("playerConfig.claims.name"));
		for (Map.Entry<String, String> e : SafeZoneClaimOptions.options().entrySet()) {
			if (!e.getKey().equals("playerConfig.claims.name")) {
				assertEquals(e.getValue(), open.get(e.getKey()), e.getKey());
			}
		}
		assertEquals(SafeZoneClaimOptions.options(), SafeZoneClaimOptions.options(false));
		assertEquals(SafeZoneClaimOptions.OPEN_SUB_ID, SafeZoneClaimOptions.subId(true));
		assertEquals(SafeZoneClaimOptions.SUB_ID, SafeZoneClaimOptions.subId(false));
	}

	@Test
	void onlyOurOwnLockedServerClaimsAreMoved() {
		int safe = 0;
		assertTrue(SafeZoneClaimOptions.shouldAdopt(true, true, -1, safe), "our safe zone on the main server sub-config");
		assertFalse(SafeZoneClaimOptions.shouldAdopt(true, true, safe, safe), "already moved");
		assertFalse(SafeZoneClaimOptions.shouldAdopt(false, true, -1, safe), "an admin's server claim");
		assertFalse(SafeZoneClaimOptions.shouldAdopt(true, false, -1, safe), "a player took the chunk since");
		assertFalse(SafeZoneClaimOptions.shouldAdopt(true, true, -1, -1), "sub-config could not be created");
	}
}
