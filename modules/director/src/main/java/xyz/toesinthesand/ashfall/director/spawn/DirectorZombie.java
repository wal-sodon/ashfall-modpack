package xyz.toesinthesand.ashfall.director.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The tag every Director-spawned zombie carries (a persistent entity attachment): where it came from,
 * which variant it is and, for building populations, which building owns it. Counting, caps,
 * building clears and the despawn rule all key off this, and zombies from other mods (the Mutant
 * Monsters bosses) never have it, which is exactly why they don't count.
 *
 * @param buildingId id of the owning building ({@code Building#id()}: the packed origin chunk of a Lost Cities
 *                   building instance), or {@link #NO_BUILDING}
 */
public record DirectorZombie(Source source, String variant, long buildingId) {
	public static final long NO_BUILDING = 0L;

	public enum Source implements StringRepresentable {
		/** Budget-loop spawn around a player; counts towards the ambient cap. */
		AMBIENT,
		/** One-time building population; counts towards the hard caps only. */
		BUILDING,
		/**
		 * A District Siege wave (arms' grid). Counts towards the hard caps like a building zombie, but not the
		 * ambient cap: a siege around a substation must not starve the ambient budget of players elsewhere.
		 */
		SIEGE,
		/**
		 * A horde-night wave (plan M8, {@code HordeNights}). Counts towards the hard caps but not the ambient cap, for
		 * the same reason as a siege: a horde night adds to the ambient dead, it does not replace them.
		 */
		HORDE;

		public static final Codec<Source> CODEC = StringRepresentable.fromEnum(Source::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final Codec<DirectorZombie> CODEC = RecordCodecBuilder.create(i -> i.group(
			Source.CODEC.fieldOf("source").forGetter(DirectorZombie::source),
			Codec.STRING.fieldOf("variant").forGetter(DirectorZombie::variant),
			Codec.LONG.optionalFieldOf("building", NO_BUILDING).forGetter(DirectorZombie::buildingId)
	).apply(i, DirectorZombie::new));

	public boolean fromBuilding() {
		return source == Source.BUILDING && buildingId != NO_BUILDING;
	}
}
