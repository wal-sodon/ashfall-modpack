package xyz.toesinthesand.ashfall.salvage.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The footprint of a {@link Zone} in block coordinates (x, z). Kept to a few primitive shapes because
 * the same test runs for every chunk Lost Cities plans, on worldgen threads, so it must be cheap and
 * free of level state.
 */
public sealed interface ZoneShape {
	Codec<ZoneShape> CODEC = Codec.STRING.dispatch("type", ZoneShape::type, ZoneShape::codecFor);

	boolean contains(int x, int z);

	String type();

	private static MapCodec<? extends ZoneShape> codecFor(String type) {
		return switch (type) {
			case "disc" -> Disc.CODEC;
			case "ring" -> Ring.CODEC;
			case "box" -> Box.CODEC;
			case "all" -> All.CODEC;
			default -> throw new IllegalArgumentException("Unknown zone shape type: " + type);
		};
	}

	/** Everything within {@code r} blocks of (x, z). */
	record Disc(int x, int z, int r) implements ZoneShape {
		static final MapCodec<Disc> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.INT.fieldOf("x").forGetter(Disc::x),
				Codec.INT.fieldOf("z").forGetter(Disc::z),
				Codec.INT.fieldOf("r").forGetter(Disc::r)
		).apply(i, Disc::new));

		@Override
		public boolean contains(int px, int pz) {
			long dx = px - x, dz = pz - z;
			return dx * dx + dz * dz < (long) r * r;
		}

		@Override
		public String type() {
			return "disc";
		}
	}

	/** The annulus from {@code inner_r} (inclusive) to {@code r} (exclusive) around (x, z). */
	record Ring(int x, int z, int innerR, int r) implements ZoneShape {
		static final MapCodec<Ring> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.INT.fieldOf("x").forGetter(Ring::x),
				Codec.INT.fieldOf("z").forGetter(Ring::z),
				Codec.INT.fieldOf("inner_r").forGetter(Ring::innerR),
				Codec.INT.fieldOf("r").forGetter(Ring::r)
		).apply(i, Ring::new));

		@Override
		public boolean contains(int px, int pz) {
			long dx = px - x, dz = pz - z;
			long d2 = dx * dx + dz * dz;
			return d2 >= (long) innerR * innerR && d2 < (long) r * r;
		}

		@Override
		public String type() {
			return "ring";
		}
	}

	/** An axis-aligned rectangle, min inclusive and max exclusive. */
	record Box(int minX, int minZ, int maxX, int maxZ) implements ZoneShape {
		static final MapCodec<Box> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.INT.fieldOf("min_x").forGetter(Box::minX),
				Codec.INT.fieldOf("min_z").forGetter(Box::minZ),
				Codec.INT.fieldOf("max_x").forGetter(Box::maxX),
				Codec.INT.fieldOf("max_z").forGetter(Box::maxZ)
		).apply(i, Box::new));

		@Override
		public boolean contains(int px, int pz) {
			return px >= minX && px < maxX && pz >= minZ && pz < maxZ;
		}

		@Override
		public String type() {
			return "box";
		}
	}

	/** Matches everywhere: the catch-all zone (Undiscovered) uses it with the lowest priority. */
	record All() implements ZoneShape {
		static final All INSTANCE = new All();
		static final MapCodec<All> CODEC = MapCodec.unit(INSTANCE);

		@Override
		public boolean contains(int px, int pz) {
			return true;
		}

		@Override
		public String type() {
			return "all";
		}
	}
}
