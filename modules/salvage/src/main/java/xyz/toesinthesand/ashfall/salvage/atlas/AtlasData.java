package xyz.toesinthesand.ashfall.salvage.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * World-level findings of the atlas pass that later milestones act on: where Power Relays go
 * and which buildings admins may turn into safe zones. Kept in the overworld's saved data
 * ({@code data/ashfall/atlas.dat}) because chunks discover them one at a time, possibly across
 * many sessions. Keyed by id (building id, or {@link RelayPlan#relayKey} for Lost Cities street relays),
 * so reprocessing a chunk or replanning the key relays never duplicates an entry.
 */
public final class AtlasData extends SavedData {
	/**
	 * A Power Relay to place. {@code building} is its id: {@link RelayPlan#relayKey} for a Lost Cities street
	 * relay (district key or ambient), the building id for an Arnis building that rolled one. {@code pos} is
	 * the cell it should stand on (a street surface cell, or an Arnis ground-floor unit cell).
	 */
	public record RelayCandidate(ResourceKey<Level> dimension, BlockPos pos, long building) {
		public static final Codec<RelayCandidate> CODEC = RecordCodecBuilder.create(i -> i.group(
				Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(RelayCandidate::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(RelayCandidate::pos),
				Codec.LONG.fieldOf("building").forGetter(RelayCandidate::building)
		).apply(i, RelayCandidate::new));
	}

	/** A building whose profile allows it to become a safe zone. */
	public record SafeCandidate(long building, BlockPos centre) {
		public static final Codec<SafeCandidate> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("building").forGetter(SafeCandidate::building),
				BlockPos.CODEC.fieldOf("centre").forGetter(SafeCandidate::centre)
		).apply(i, SafeCandidate::new));
	}

	public static final Codec<AtlasData> CODEC = RecordCodecBuilder.create(i -> i.group(
			RelayCandidate.CODEC.listOf().optionalFieldOf("relays", List.of()).forGetter(d -> List.copyOf(d.relays.values())),
			SafeCandidate.CODEC.listOf().optionalFieldOf("safe", List.of()).forGetter(d -> List.copyOf(d.safe.values())),
			Codec.LONG.listOf().optionalFieldOf("placed_relays", List.of()).forGetter(d -> List.copyOf(d.placedRelays))
	).apply(i, AtlasData::new));

	public static final SavedDataType<AtlasData> TYPE = new SavedDataType<>(AshfallSalvage.id("atlas"), AtlasData::new, CODEC, null);

	private final Map<Long, RelayCandidate> relays = new LinkedHashMap<>();
	private final Map<Long, SafeCandidate> safe = new LinkedHashMap<>();
	/**
	 * Buildings whose relay candidate has been dealt with by {@link RelayPlacer} (placed, or found no room).
	 * Remembered so a relay an admin removed is not put back, and so a chunk is never searched twice.
	 */
	private final Set<Long> placedRelays = new LinkedHashSet<>();

	public AtlasData() {
	}

	private AtlasData(List<RelayCandidate> relays, List<SafeCandidate> safe, List<Long> placedRelays) {
		relays.forEach(r -> this.relays.put(r.building(), r));
		safe.forEach(s -> this.safe.put(s.building(), s));
		this.placedRelays.addAll(placedRelays);
	}

	public static AtlasData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public void addRelay(RelayCandidate candidate) {
		if (!candidate.equals(relays.put(candidate.building(), candidate))) {
			setDirty();
		}
	}

	public void addSafe(SafeCandidate candidate) {
		if (!candidate.equals(safe.put(candidate.building(), candidate))) {
			setDirty();
		}
	}

	public Collection<RelayCandidate> relays() {
		return Collections.unmodifiableCollection(relays.values());
	}

	/** Whether the relay of {@code building} has already been placed (or given up on). */
	public boolean relayPlaced(long building) {
		return placedRelays.contains(building);
	}

	public void markRelayPlaced(long building) {
		if (placedRelays.add(building)) {
			setDirty();
		}
	}

	/** Relay candidates not yet placed. */
	public List<RelayCandidate> pendingRelays() {
		return relays.values().stream().filter(r -> !placedRelays.contains(r.building())).toList();
	}

	public Collection<SafeCandidate> safeCandidates() {
		return Collections.unmodifiableCollection(safe.values());
	}
}
