package xyz.toesinthesand.ashfall.salvage.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * World-wide saved data ({@code data/ashfall/loot_zones.dat}) holding admin loot zones and the
 * presence history of every tracked area ({@code "b:<osmId>"} buildings, {@code "z:<id>"} zones).
 * <p>
 * Presence has to persist: refill eligibility depends on a quiet period that may span a restart,
 * and losing it would either refill everything at once or nothing for hours.
 */
public final class ZoneManager extends SavedData {
	/**
	 * Last time a player was near an area, and when the last quiet period (no player for at least
	 * the area's quiet ticks) ended, i.e. when someone came back after leaving it alone. -1 = never.
	 */
	public record Presence(long lastSeen, long lastQuietGapEnd) {
		public static final Presence NONE = new Presence(-1, -1);
		public static final Codec<Presence> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("last_seen").forGetter(Presence::lastSeen),
				Codec.LONG.fieldOf("last_quiet_gap_end").forGetter(Presence::lastQuietGapEnd)
		).apply(i, Presence::new));
	}

	public static final Codec<ZoneManager> CODEC = RecordCodecBuilder.create(i -> i.group(
			LootZone.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(m -> List.copyOf(m.zones.values())),
			Codec.unboundedMap(Codec.STRING, Presence.CODEC).optionalFieldOf("presence", Map.of()).forGetter(m -> m.presence)
	).apply(i, ZoneManager::new));

	/**
	 * Saved data needs a non-null fixer type; our tags carry the current DataVersion on save so no
	 * vanilla fix ever runs on them. Command storage is the most neutral (schema-less) choice.
	 */
	public static final SavedDataType<ZoneManager> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(AshfallSalvage.NAMESPACE, "loot_zones"), ZoneManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<String, LootZone> zones = new TreeMap<>();
	private final Map<String, Presence> presence = new HashMap<>();

	public ZoneManager() {
	}

	private ZoneManager(List<LootZone> zones, Map<String, Presence> presence) {
		zones.forEach(z -> this.zones.put(z.id(), z));
		this.presence.putAll(presence);
	}

	public static ZoneManager get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public Collection<LootZone> zones() {
		return zones.values();
	}

	public LootZone zone(String id) {
		return zones.get(id);
	}

	public void putZone(LootZone zone) {
		zones.put(zone.id(), zone);
		setDirty();
	}

	public LootZone removeZone(String id) {
		LootZone removed = zones.remove(id);
		if (removed != null) {
			presence.remove(removed.areaKey());
			setDirty();
		}
		return removed;
	}

	public Presence presence(String areaKey) {
		return presence.getOrDefault(areaKey, Presence.NONE);
	}

	public void setPresence(String areaKey, Presence value) {
		presence.put(areaKey, value);
		setDirty();
	}

	/**
	 * Records a player near {@code areaKey} at {@code now}. If nobody had been there for at least
	 * {@code quietTicks} (or ever), a quiet period just ended and {@code lastQuietGapEnd} moves to now.
	 */
	public void markSeen(String areaKey, long now, long quietTicks) {
		Presence old = presence(areaKey);
		long gapEnd = old.lastSeen() < 0 || now - old.lastSeen() >= quietTicks ? now : old.lastQuietGapEnd();
		presence.put(areaKey, new Presence(now, gapEnd));
		setDirty();
	}
}
