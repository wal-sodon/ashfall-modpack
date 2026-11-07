package xyz.toesinthesand.ashfall.salvage.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every light the atlas turned off, per zone ({@code data/ashfall/relight.dat}).
 * <p>
 * Restoring a district's power (M4.5) is meant to bring its lights back, and endgame-design section 6 notes
 * we had no record of where they were. The Lights Off assets and {@code generateLighting: false} mean few lights
 * are generated at all, but whatever {@link LightStripper} still removes, and every dead lamp it finds (an unlit
 * redstone lamp, the {@code L} palette char of our landmark assets), is written here with the block it was, so
 * the relight can put back exactly what was there. Keyed by position, so processing a chunk twice never
 * duplicates an entry.
 */
public final class RelightLog extends SavedData {
	/** One light: where it was and what block it was. */
	public record Entry(BlockPos pos, Identifier block) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
				Identifier.CODEC.fieldOf("block").forGetter(Entry::block)
		).apply(i, Entry::new));
	}

	public static final Codec<RelightLog> CODEC = Codec.unboundedMap(Codec.STRING, Entry.CODEC.listOf())
			.xmap(RelightLog::new, RelightLog::snapshot);

	public static final SavedDataType<RelightLog> TYPE = new SavedDataType<>(AshfallSalvage.id("relight"), RelightLog::new, CODEC, null);

	/** Zone id -> packed position -> block. */
	private final Map<String, Map<Long, Identifier>> byZone = new LinkedHashMap<>();

	public RelightLog() {
	}

	private RelightLog(Map<String, List<Entry>> loaded) {
		loaded.forEach((zone, entries) -> entries.forEach(e -> byZone.computeIfAbsent(zone, k -> new LinkedHashMap<>())
				.put(e.pos().asLong(), e.block())));
	}

	public static RelightLog get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public void record(String zone, BlockPos pos, Identifier block) {
		if (!block.equals(byZone.computeIfAbsent(zone, k -> new LinkedHashMap<>()).put(pos.asLong(), block))) {
			setDirty();
		}
	}

	/** Lights logged in one zone, in the order they were found. */
	public List<Entry> entries(String zone) {
		Map<Long, Identifier> map = byZone.get(zone);
		if (map == null) {
			return List.of();
		}
		List<Entry> out = new ArrayList<>(map.size());
		map.forEach((pos, block) -> out.add(new Entry(BlockPos.of(pos), block)));
		return out;
	}

	public Map<String, Integer> countsByZone() {
		Map<String, Integer> out = new LinkedHashMap<>();
		byZone.forEach((zone, map) -> out.put(zone, map.size()));
		return Collections.unmodifiableMap(out);
	}

	public int total() {
		return byZone.values().stream().mapToInt(Map::size).sum();
	}

	private Map<String, List<Entry>> snapshot() {
		Map<String, List<Entry>> out = new LinkedHashMap<>();
		byZone.keySet().forEach(zone -> out.put(zone, entries(zone)));
		return out;
	}
}
