package xyz.toesinthesand.ashfall.director.haven;

/**
 * The places a player can pick after dying. The order of preference for the default lives in
 * {@link #pickDefault}; pure so it is unit-tested.
 */
public enum RespawnOption {
	/** The player's (or their party's) Bunker Core. */
	BUNKER("bunker"),
	/** The safe zone nearest to where they died. */
	SAFE_ZONE("safezone"),
	/** Wherever vanilla put them: their bed or anchor if they have one, else the world spawn. */
	SPAWN("spawn");

	private final String arg;

	RespawnOption(String arg) {
		this.arg = arg;
	}

	/** The word used in {@code /ashfall respawn <arg>}. */
	public String arg() {
		return arg;
	}

	/**
	 * The default place: the Bunker Core first (the player built it to come back to); then, if they set a bed
	 * or anchor themselves, that (vanilla already put them there, and it was their own choice); then the
	 * nearest safe zone; finally the world spawn.
	 */
	public static RespawnOption pickDefault(boolean hasBunker, boolean hasPersonalSpawn, boolean hasSafeZone) {
		if (hasBunker) {
			return BUNKER;
		}
		if (hasPersonalSpawn || !hasSafeZone) {
			return SPAWN;
		}
		return SAFE_ZONE;
	}
}
