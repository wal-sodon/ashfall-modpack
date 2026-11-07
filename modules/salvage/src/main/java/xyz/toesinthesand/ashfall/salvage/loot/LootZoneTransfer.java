package xyz.toesinthesand.ashfall.salvage.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Loot zone export and import as JSON, so an admin can move a hand-curated set of zones between worlds or keep
 * it in version control. The format is {@code {"zones": [<zone>, ...]}} with the same fields as the saved data.
 * <p>
 * Files go to {@code <world>/ashfall/loot_zones.json}: outside {@code datapacks/} because zones are world state,
 * not datapack data, and one fixed name so {@code /lootzone import} needs no path argument a player could abuse.
 */
public final class LootZoneTransfer {
	public static final String FILE = "ashfall/loot_zones.json";

	private static final Codec<List<LootZone>> FILE_CODEC = LootZone.CODEC.listOf().fieldOf("zones").codec();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private LootZoneTransfer() {
	}

	public static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(FILE);
	}

	public static String toJson(Collection<LootZone> zones) {
		JsonElement json = FILE_CODEC.encodeStart(JsonOps.INSTANCE, List.copyOf(zones)).getOrThrow();
		return GSON.toJson(json) + "\n";
	}

	/** Parses an export; duplicate ids keep the last one, as a later import of the same zone would. */
	public static DataResult<List<LootZone>> fromJson(String text) {
		JsonElement json;
		try {
			json = JsonParser.parseString(text);
		} catch (JsonParseException e) {
			return DataResult.error(() -> "not valid JSON: " + e.getMessage());
		}
		return FILE_CODEC.parse(JsonOps.INSTANCE, json);
	}
}
