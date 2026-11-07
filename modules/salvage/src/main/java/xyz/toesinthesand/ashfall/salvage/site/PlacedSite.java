package xyz.toesinthesand.ashfall.salvage.site;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A site that has been placed in the world, as remembered in {@link SiteState}.
 *
 * @param key      {@code fixed:<site id>} or the planner's {@code crash:<rule>:<cx>,<cz>}
 * @param template template id that was placed
 * @param origin   world position of the template's (0, 0, 0) cell
 * @param center   the centre column at the surface: what "near the site" is measured from
 * @param markers  marker name -> world position of the first such cell (the hull's {@code boss} spot)
 * @param chests   loot containers placed from the template
 * @param guards   zombies still to rise around the site when a player comes near; 0 once they have
 * @param guardRadius how far from {@code center} the guards spread
 */
public record PlacedSite(String key, String template, BlockPos origin, BlockPos center, Map<String, BlockPos> markers,
		List<BlockPos> chests, int guards, int guardRadius) {
	public static final Codec<PlacedSite> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("key").forGetter(PlacedSite::key),
			Codec.STRING.fieldOf("template").forGetter(PlacedSite::template),
			BlockPos.CODEC.fieldOf("origin").forGetter(PlacedSite::origin),
			BlockPos.CODEC.fieldOf("center").forGetter(PlacedSite::center),
			Codec.unboundedMap(Codec.STRING, BlockPos.CODEC).optionalFieldOf("markers", Map.of()).forGetter(PlacedSite::markers),
			BlockPos.CODEC.listOf().optionalFieldOf("chests", List.of()).forGetter(PlacedSite::chests),
			Codec.INT.optionalFieldOf("guards", 0).forGetter(PlacedSite::guards),
			Codec.INT.optionalFieldOf("guard_radius", 0).forGetter(PlacedSite::guardRadius)
	).apply(i, PlacedSite::new));

	public Optional<BlockPos> marker(String name) {
		return Optional.ofNullable(markers.get(name));
	}

	public PlacedSite withGuards(int remaining) {
		return new PlacedSite(key, template, origin, center, markers, chests, remaining, guardRadius);
	}

	public static String fixedKey(String siteId) {
		return "fixed:" + siteId;
	}
}
