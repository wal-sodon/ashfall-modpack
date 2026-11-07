package xyz.toesinthesand.ashfall.arms.mission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The world's endgame milestones for the objective ({@code ashfall_mission_state}): the crystals were gathered, an
 * Uplink Frame, the Capacitor Banks and a Grid Uplink were made.
 * <p>
 * Why this exists: the objective first counted these items in online inventories only. A party that stored the Frame
 * in a chest, or whose carrier logged off, was sent back to "Collect Resonance Crystals" although the story bosses had
 * already died and could not drop more. Each flag is set the first time {@link MissionWorld} sees the item (held, or
 * crafted per the player's stats) and never clears on its own, the same way "armed" is sticky per player. A story
 * reset clears them, because a replayed finale needs a new Uplink.
 */
public final class MissionState extends SavedData {
	public static final Codec<MissionState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("crystals_gathered", false).forGetter(s -> s.crystalsGathered),
			Codec.BOOL.optionalFieldOf("frame_made", false).forGetter(s -> s.frameMade),
			Codec.BOOL.optionalFieldOf("banks_made", false).forGetter(s -> s.banksMade),
			Codec.BOOL.optionalFieldOf("uplink_made", false).forGetter(s -> s.uplinkMade)
	).apply(i, MissionState::new));

	/** Same non-null, schema-less fixer choice as the other Ashfall saved data, so no vanilla fix runs. */
	public static final SavedDataType<MissionState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_mission_state"), MissionState::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private boolean crystalsGathered;
	private boolean frameMade;
	private boolean banksMade;
	private boolean uplinkMade;

	public MissionState() {
	}

	private MissionState(boolean crystalsGathered, boolean frameMade, boolean banksMade, boolean uplinkMade) {
		this.crystalsGathered = crystalsGathered;
		this.frameMade = frameMade;
		this.banksMade = banksMade;
		this.uplinkMade = uplinkMade;
	}

	public static MissionState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean crystalsGathered() {
		return crystalsGathered;
	}

	public boolean frameMade() {
		return frameMade;
	}

	public boolean banksMade() {
		return banksMade;
	}

	public boolean uplinkMade() {
		return uplinkMade;
	}

	/** Raises any milestone seen now; never lowers one. */
	public void see(boolean crystals, boolean frame, boolean banks, boolean uplink) {
		boolean changed = (crystals && !crystalsGathered) || (frame && !frameMade) || (banks && !banksMade) || (uplink && !uplinkMade);
		if (changed) {
			crystalsGathered |= crystals;
			frameMade |= frame;
			banksMade |= banks;
			uplinkMade |= uplink;
			setDirty();
		}
	}

	/** Forgets every milestone (a story reset, so the replay asks for a new Uplink). */
	public void reset() {
		if (crystalsGathered || frameMade || banksMade || uplinkMade) {
			crystalsGathered = frameMade = banksMade = uplinkMade = false;
			setDirty();
		}
	}
}
