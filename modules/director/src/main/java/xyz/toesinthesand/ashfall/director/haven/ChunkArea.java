package xyz.toesinthesand.ashfall.director.haven;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * A square of whole chunks: the centre chunk plus {@code radius} chunks on every side. Havens are measured
 * in chunks rather than blocks because OPAC claims whole chunks; the fallback exclusion region therefore
 * covers exactly what the OPAC claim would, and a haven behaves the same with or without OPAC.
 * <p>
 * Pure value type with no world access, so the containment maths is unit-tested.
 *
 * @param chunkX centre chunk x
 * @param chunkZ centre chunk z
 * @param radius chunks on each side of the centre (0 = the centre chunk only)
 */
public record ChunkArea(int chunkX, int chunkZ, int radius) {
	public static final Codec<ChunkArea> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("chunk_x").forGetter(ChunkArea::chunkX),
			Codec.INT.fieldOf("chunk_z").forGetter(ChunkArea::chunkZ),
			Codec.intRange(0, 16).optionalFieldOf("radius", 1).forGetter(ChunkArea::radius)
	).apply(i, ChunkArea::new));

	public ChunkArea {
		if (radius < 0) {
			throw new IllegalArgumentException("radius < 0: " + radius);
		}
	}

	/** The area centred on the chunk holding block column ({@code blockX}, {@code blockZ}). */
	public static ChunkArea around(int blockX, int blockZ, int radius) {
		return new ChunkArea(blockX >> 4, blockZ >> 4, radius);
	}

	public boolean containsChunk(int cx, int cz) {
		return Math.abs(cx - chunkX) <= radius && Math.abs(cz - chunkZ) <= radius;
	}

	/** Whether the block column ({@code blockX}, {@code blockZ}) lies in the area (all heights count). */
	public boolean contains(int blockX, int blockZ) {
		return containsChunk(blockX >> 4, blockZ >> 4);
	}

	/** Whether this area and {@code other} share at least one chunk. */
	public boolean overlaps(ChunkArea other) {
		return Math.abs(chunkX - other.chunkX) <= radius + other.radius
				&& Math.abs(chunkZ - other.chunkZ) <= radius + other.radius;
	}

	/** Centre of the centre chunk, in block coordinates. */
	public int centerBlockX() {
		return (chunkX << 4) + 8;
	}

	public int centerBlockZ() {
		return (chunkZ << 4) + 8;
	}

	/** Squared horizontal distance from a block column to the area's centre, for "nearest safe zone". */
	public double distanceSqTo(double blockX, double blockZ) {
		double dx = blockX - centerBlockX(), dz = blockZ - centerBlockZ();
		return dx * dx + dz * dz;
	}

	/** Every chunk in the area as packed positions ({@code ChunkPos.pack} layout), row by row. */
	public List<Long> chunks() {
		List<Long> out = new ArrayList<>((2 * radius + 1) * (2 * radius + 1));
		for (int dz = -radius; dz <= radius; dz++) {
			for (int dx = -radius; dx <= radius; dx++) {
				out.add(pack(chunkX + dx, chunkZ + dz));
			}
		}
		return out;
	}

	/** Same layout as {@code ChunkPos.pack}, kept here so the class needs no Minecraft bootstrap in tests. */
	public static long pack(int cx, int cz) {
		return (cx & 0xFFFFFFFFL) | ((cz & 0xFFFFFFFFL) << 32);
	}
}
