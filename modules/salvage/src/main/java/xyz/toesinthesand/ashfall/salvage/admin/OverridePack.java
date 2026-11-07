package xyz.toesinthesand.ashfall.salvage.admin;

import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The world datapack {@code world/datapacks/ashfall_overrides} that the admin GUIs write into.
 * <p>
 * Admin edits that are data (mob drop rules) go into a real datapack instead of a private config file so they
 * follow every datapack rule an admin already knows: they travel with the world, they can be read and hand-edited,
 * they win over the shipped files of the same id because a world pack is selected after the mod packs, and
 * {@code /datapack disable "file/ashfall_overrides"} turns every GUI edit off at once.
 * <p>
 * Nothing here deletes a file: "reset" renames an override to {@code *.disabled} so an admin's hand edits survive.
 */
public final class OverridePack {
	public static final String NAME = "ashfall_overrides";
	public static final String PACK_ID = "file/" + NAME;

	private OverridePack() {
	}

	public static Path root(MinecraftServer server) {
		return server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(NAME);
	}

	/** {@code data/<path>} inside the pack; rejects paths that would escape it. */
	public static Path dataFile(MinecraftServer server, String path) {
		Path data = root(server).resolve("data").normalize();
		Path file = data.resolve(path).normalize();
		if (!file.startsWith(data)) {
			throw new IllegalArgumentException("path escapes the override pack: " + path);
		}
		return file;
	}

	/** Creates the pack folder and its {@code pack.mcmeta} (for this game's data format) if missing. */
	public static void ensurePack(MinecraftServer server) throws IOException {
		Path root = root(server);
		Files.createDirectories(root.resolve("data"));
		Path meta = root.resolve("pack.mcmeta");
		if (!Files.exists(meta)) {
			int format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).major();
			String json = "{\n\t\"pack\": {\n\t\t\"description\": \"Ashfall admin GUI overrides (mob drops)\",\n"
					+ "\t\t\"min_format\": " + format + ",\n\t\t\"max_format\": " + format + "\n\t}\n}\n";
			Files.writeString(meta, json, StandardCharsets.UTF_8);
		}
	}

	/** Writes {@code data/<path>} atomically (a temp file moved over the old one), creating the pack as needed. */
	public static Path write(MinecraftServer server, String path, String content) throws IOException {
		ensurePack(server);
		Path file = dataFile(server, path);
		Files.createDirectories(file.getParent());
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(temp, content, StandardCharsets.UTF_8);
		Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		return file;
	}

	/**
	 * Moves {@code data/<path>} aside as {@code <name>.disabled} (or {@code <name>.disabled-<millis>} if that is
	 * taken) so the datapack stops loading it without losing it.
	 *
	 * @return where the file went, or empty if there was no override
	 */
	public static Optional<Path> disable(MinecraftServer server, String path) throws IOException {
		Path file = dataFile(server, path);
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		Path target = file.resolveSibling(file.getFileName() + ".disabled");
		if (Files.exists(target)) {
			target = file.resolveSibling(file.getFileName() + ".disabled-" + System.currentTimeMillis());
		}
		Files.move(file, target);
		return Optional.of(target);
	}

	/**
	 * Reloads datapacks like {@code /reload}, but also selects the override pack when it was just created (vanilla
	 * only auto-selects new packs from {@code /reload}). The pack goes last, i.e. on top of everything else.
	 */
	public static CompletableFuture<Void> reload(MinecraftServer server) {
		PackRepository packs = server.getPackRepository();
		packs.reload();
		List<String> selected = new ArrayList<>(packs.getSelectedIds());
		if (packs.isAvailable(PACK_ID) && !selected.contains(PACK_ID)) {
			selected.add(PACK_ID);
			AshfallSalvage.LOGGER.info("Enabling the admin override datapack {}", PACK_ID);
		}
		return server.reloadResources(selected);
	}
}
