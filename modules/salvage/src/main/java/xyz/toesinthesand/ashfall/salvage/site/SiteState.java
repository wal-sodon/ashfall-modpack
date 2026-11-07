package xyz.toesinthesand.ashfall.salvage.site;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Which sites are already in the world ({@code data/ashfall/ashfall_sites.dat}). Placement is lazy, so this is
 * what makes it happen exactly once per site: a site is placed the first time a player comes near and never
 * again, even if players later dig it up or build over it.
 */
public final class SiteState extends SavedData {
	public static final Codec<SiteState> CODEC = RecordCodecBuilder.create(i -> i.group(
			PlacedSite.CODEC.listOf().optionalFieldOf("sites", List.of()).forGetter(s -> List.copyOf(s.sites.values()))
	).apply(i, SiteState::new));

	/** Same fixer choice as the other Ashfall saved data: a schema-less type so no vanilla fix runs. */
	public static final SavedDataType<SiteState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_sites"), SiteState::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<String, PlacedSite> sites = new TreeMap<>();

	public SiteState() {
	}

	private SiteState(List<PlacedSite> list) {
		list.forEach(s -> sites.put(s.key(), s));
	}

	public static SiteState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public PlacedSite site(String key) {
		return sites.get(key);
	}

	public boolean placed(String key) {
		return sites.containsKey(key);
	}

	public Collection<PlacedSite> all() {
		return sites.values();
	}

	public void put(PlacedSite site) {
		sites.put(site.key(), site);
		setDirty();
	}

	/**
	 * Test and admin hook: forgets a site so it can be placed again. Nothing in the world is removed; the
	 * blocks stay where they are.
	 */
	public void forget(String key) {
		if (sites.remove(key) != null) {
			setDirty();
		}
	}
}
