package xyz.toesinthesand.ashfall.director.claims;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The OPAC claim options of Ashfall's safe-zone sub-config: what a survivor may do inside a server-claimed safe
 * zone (the safehouse, Reclaimed substations). Kept free of OPAC types so a unit test can check it.
 * <p>
 * Why a sub-config: safe zones used to be claimed with the server claim config's main sub-config, whose OPAC
 * defaults protect everything from everybody ({@code blocksByPlayers = "N"}, every block interaction group
 * {@code "N"}, {@code itemUse = "N"}, and full access only for the server's party, which does not exist). Every
 * player, the singleplayer host included, was refused the safehouse doors, barrels, beds and the story's field
 * radio. Changing the main server claim config instead would also open every claim an admin made, and the per-world
 * file never falls back to a pack default once it exists, so it would not reach existing worlds anyway.
 * <p>
 * What stays protected on purpose: breaking and placing blocks ({@code blocksByPlayers} is single option for
 * both and is not listed, so it inherits "N"), item use (which on Fabric would also allow placing blocks against
 * doors and chests; only written books are opened), entities, and OPAC's fluid, piston and explosion barriers. So a
 * safe zone works as a shelter anyone can use but nobody can grief. Two bridges live outside this class: the
 * {@code forge:chests/wooden} and {@code forge:barrels} block tags pull Lootr containers into OPAC's Chests and
 * Barrels groups (Lootr only tags {@code c:}), and the pack's OPAC server config exempts the {@code #ashfall:claim_usable}
 * item tag (Field Radio, medical items, blasters) from item-use protection in every claim.
 * <p>
 * Open safe zones (reclaimed substation compounds, {@code SafeZone#open}) use a second sub-config,
 * {@link #OPEN_SUB_ID}: the same options plus {@code fullAccess} for everyone, so every survivor has what a Bunker
 * Core's party has in its claim (building, breaking, item use, entities). OPAC's fluid, piston, fire and explosion
 * barriers stay on there too.
 * Values are OPAC's exception levels:
 * {@code "N"} nobody, {@code "P"} party, {@code "A"} allies, {@code "E"} everyone.
 */
public final class SafeZoneClaimOptions {
	/** Sub-config id in the server claim config (OPAC allows [a-zA-Z0-9_-]). */
	public static final String SUB_ID = "ashfall-safezone";
	/** Shown by OPAC (map, claim messages) for these chunks. */
	public static final String NAME = "Ashfall Safe Zone";
	public static final String EVERYONE = "E";
	/** Sub-config id for open safe zones. */
	public static final String OPEN_SUB_ID = "ashfall-compound";
	/** Shown by OPAC for open safe-zone chunks. */
	public static final String OPEN_NAME = "Ashfall Reclaimed Compound";

	static final String NAME_OPTION = "playerConfig.claims.name";
	static final String EXCEPTIONS = "playerConfig.claims.protection.exceptions.";
	static final String BLOCK_INTERACT = EXCEPTIONS + "groups.block.interact.";
	/** Everyone counts as the claim's owner: what a Bunker Core's party has in its claim. */
	static final String FULL_ACCESS = EXCEPTIONS + "fullAccess";
	/** OPAC's default item group for written and writable books: the Mission Briefing, Relay Logs, survivor notes. */
	static final String BOOKS = EXCEPTIONS + "groups.item.interact.Books";

	/**
	 * Right-click groups opened to everyone. These are the group names of OPAC's default
	 * {@code blockProtectionOptionalExceptionGroups}; a server that removed a group simply has no option for it,
	 * which {@link #options()} callers skip.
	 */
	static final List<String> INTERACT_GROUPS = List.of("Controls", "Doors", "Chests", "Barrels", "Ender_Chests", "Shulker_Boxes",
			"Furnaces", "Hoppers", "Dispenser-like", "Anvils", "Stonecutters", "Grindstones", "Cartography_Tables", "Lecterns",
			"Smithing_Tables", "Looms", "Jukeboxes", "Beds", "Enchanting_Tables");

	private SafeZoneClaimOptions() {
	}

	/** Option id to value, in the order they are applied. Applied every start, so admin edits are repaired. */
	public static Map<String, String> options() {
		Map<String, String> out = new LinkedHashMap<>();
		out.put(NAME_OPTION, NAME);
		for (String group : INTERACT_GROUPS) {
			out.put(BLOCK_INTERACT + group, EVERYONE);
		}
		// itemUse stays inherited ("N") on purpose: on Fabric, OPAC's item-use exception also lets players place
		// blocks against interactable blocks and empty buckets, which would let anyone wall up the safehouse door
		// with blocks nobody else could break.
		// Reading books never places anything, so the mission briefing and logs open inside the claim.
		out.put(BOOKS, EVERYONE);
		// Pressure plates at the safehouse door.
		out.put(EXCEPTIONS + "platesByPlayers", EVERYONE);
		return out;
	}

	/** The open safe-zone sub-config: {@link #options()} with full access for everyone. */
	public static Map<String, String> openOptions() {
		Map<String, String> out = options();
		out.put(NAME_OPTION, OPEN_NAME);
		out.put(FULL_ACCESS, EVERYONE);
		return out;
	}

	/** The sub-config id for a closed or an open safe zone. */
	public static String subId(boolean open) {
		return open ? OPEN_SUB_ID : SUB_ID;
	}

	/** The options of the sub-config for a closed or an open safe zone. */
	public static Map<String, String> options(boolean open) {
		return open ? openOptions() : options();
	}

	/**
	 * Whether an existing chunk claim should be moved to the safe-zone sub-config: only a server claim that Ashfall
	 * itself made for a safe zone (recorded in the haven state) and that still uses another sub-config. A claim an
	 * admin made, or a player's, is never touched.
	 */
	public static boolean shouldAdopt(boolean recordedAsOurs, boolean ownedByServer, int currentSubIndex, int safeZoneSubIndex) {
		return recordedAsOurs && ownedByServer && safeZoneSubIndex >= 0 && currentSubIndex != safeZoneSubIndex;
	}
}
