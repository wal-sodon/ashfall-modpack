package xyz.toesinthesand.ashfall.director.haven;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * World-wide haven saved data ({@code data/ashfall_havens.dat} in the overworld's data folder):
 * <ul>
 *   <li>every Bunker Core, keyed by owner key (party or player);</li>
 *   <li>safe zones added in game, and datapack safe zones an admin removed (a datapack file is never edited
 *   or deleted by a command; "remove" disables it for this world, which is reversible);</li>
 *   <li>which chunks each safe zone server-claimed through OPAC, so removal releases exactly those.</li>
 * </ul>
 */
public final class HavenState extends SavedData {
	public static final Codec<HavenState> CODEC = RecordCodecBuilder.create(i -> i.group(
			BunkerCoreRecord.CODEC.listOf().optionalFieldOf("bunker_cores", List.of()).forGetter(s -> List.copyOf(s.cores.values())),
			Codec.unboundedMap(Codec.STRING, SafeZone.CODEC).optionalFieldOf("custom_safe_zones", Map.of())
					.forGetter(s -> s.customZones),
			Codec.STRING.listOf().optionalFieldOf("disabled_safe_zones", List.of()).forGetter(s -> List.copyOf(s.disabledZones)),
			Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf()).optionalFieldOf("server_claims", Map.of())
					.forGetter(s -> s.serverClaims)
	).apply(i, HavenState::new));

	/** Same fixer choice as the Director's other saved data: a non-null, schema-less type so no vanilla fix runs. */
	public static final SavedDataType<HavenState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_havens"), HavenState::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, BunkerCoreRecord> cores = new LinkedHashMap<>();
	private final Map<String, SafeZone> customZones = new TreeMap<>();
	private final Set<String> disabledZones = new TreeSet<>();
	private final Map<String, List<Long>> serverClaims = new HashMap<>();

	public HavenState() {
	}

	private HavenState(List<BunkerCoreRecord> cores, Map<String, SafeZone> custom, List<String> disabled,
					   Map<String, List<Long>> claims) {
		cores.forEach(c -> this.cores.put(c.ownerKey(), c));
		customZones.putAll(custom);
		disabledZones.addAll(disabled);
		claims.forEach((k, v) -> serverClaims.put(k, new ArrayList<>(v)));
	}

	public static HavenState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public Collection<BunkerCoreRecord> cores() {
		return cores.values();
	}

	public BunkerCoreRecord core(UUID ownerKey) {
		return cores.get(ownerKey);
	}

	public BunkerCoreRecord coreAt(ResourceKey<Level> dimension, BlockPos pos) {
		for (BunkerCoreRecord core : cores.values()) {
			if (core.dimension().equals(dimension) && core.pos().equals(pos)) {
				return core;
			}
		}
		return null;
	}

	public void putCore(BunkerCoreRecord core) {
		cores.put(core.ownerKey(), core);
		setDirty();
	}

	public void removeCore(UUID ownerKey) {
		if (cores.remove(ownerKey) != null) {
			setDirty();
		}
	}

	public Map<String, SafeZone> customZones() {
		return customZones;
	}

	public void putCustomZone(String id, SafeZone zone) {
		customZones.put(id, zone);
		disabledZones.remove(id);
		setDirty();
	}

	public boolean removeCustomZone(String id) {
		boolean removed = customZones.remove(id) != null;
		if (removed) {
			setDirty();
		}
		return removed;
	}

	public Set<String> disabledZones() {
		return disabledZones;
	}

	public void disableZone(String id) {
		if (disabledZones.add(id)) {
			setDirty();
		}
	}

	/** Zones that hold OPAC server claims, including ones whose datapack file has since gone. */
	public Set<String> serverClaimZoneIds() {
		return serverClaims.keySet();
	}

	public List<Long> serverClaims(String zoneId) {
		return serverClaims.getOrDefault(zoneId, List.of());
	}

	public void addServerClaims(String zoneId, List<Long> chunks) {
		if (!chunks.isEmpty()) {
			serverClaims.computeIfAbsent(zoneId, k -> new ArrayList<>()).addAll(chunks);
			setDirty();
		}
	}

	public List<Long> takeServerClaims(String zoneId) {
		List<Long> taken = serverClaims.remove(zoneId);
		if (taken != null) {
			setDirty();
		}
		return taken == null ? List.of() : taken;
	}
}
