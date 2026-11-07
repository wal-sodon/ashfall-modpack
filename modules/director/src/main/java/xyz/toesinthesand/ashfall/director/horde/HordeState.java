package xyz.toesinthesand.ashfall.director.horde;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The little the horde scheduler must remember across restarts ({@code data/ashfall_hordes.dat}): which day's Breach
 * already ran, and the substation picked when its siren sounded, so the warning and the attack name the same place
 * even if the server restarts in between. The horde-night schedule itself needs no saved state: it is a pure
 * function of the world seed and the day ({@link HordeSchedule}).
 */
public final class HordeState extends SavedData {
	public static final Codec<HordeState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("last_breach_day", 0).forGetter(s -> s.lastBreachDay),
			Codec.INT.optionalFieldOf("breach_warned_day", 0).forGetter(s -> s.breachWarnedDay),
			Codec.STRING.optionalFieldOf("breach_target", "").forGetter(s -> s.breachTarget)
	).apply(i, HordeState::new));

	/** Same schema-less fixer choice as the other Ashfall saved data, so no vanilla fix runs. */
	public static final SavedDataType<HordeState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_hordes"), HordeState::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private int lastBreachDay;
	private int breachWarnedDay;
	private String breachTarget = "";

	public HordeState() {
	}

	private HordeState(int lastBreachDay, int breachWarnedDay, String breachTarget) {
		this.lastBreachDay = lastBreachDay;
		this.breachWarnedDay = breachWarnedDay;
		this.breachTarget = breachTarget;
	}

	public static HordeState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** World day whose Breach has run (or was skipped for want of a target). */
	public int lastBreachDay() {
		return lastBreachDay;
	}

	public void setLastBreachDay(int day) {
		lastBreachDay = day;
		setDirty();
	}

	/** World day whose Breach warning has sounded. */
	public int breachWarnedDay() {
		return breachWarnedDay;
	}

	/** The substation the warning named ("" when none). */
	public String breachTarget() {
		return breachTarget;
	}

	public void warnBreach(int day, String target) {
		breachWarnedDay = day;
		breachTarget = target == null ? "" : target;
		setDirty();
	}
}
