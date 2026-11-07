package xyz.toesinthesand.ashfall.arms.gun;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code data/<ns>/ashfall/gun_attachment/*.json} into {@link AttachmentStats}, keyed by attachment
 * id (the file's path), and keeps the client's copy. Two maps for the same reason as
 * {@link GunStatsManager}: in single player the client's copy must still come only from the sync packet.
 */
public final class AttachmentStatsManager extends SimpleJsonResourceReloadListener<AttachmentStats> {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "gun_attachments");
	public static final String DIRECTORY = "ashfall/gun_attachment";

	private static volatile Map<String, AttachmentStats> server = Map.of();
	private static volatile Map<String, AttachmentStats> client = Map.of();

	public AttachmentStatsManager() {
		super(AttachmentStats.CODEC, FileToIdConverter.json(DIRECTORY));
	}

	@Override
	protected void apply(Map<Identifier, AttachmentStats> loaded, ResourceManager manager, ProfilerFiller profiler) {
		Map<String, AttachmentStats> byPath = new HashMap<>();
		loaded.forEach((id, stats) -> byPath.put(id.getPath(), stats));
		server = Map.copyOf(byPath);
		AshfallArms.LOGGER.info("Loaded {} gun attachment files", byPath.size());
	}

	public static Map<String, AttachmentStats> serverStats() {
		return server;
	}

	public static void setClientStats(Map<String, AttachmentStats> stats) {
		client = Map.copyOf(stats);
	}

	/**
	 * Stats of attachment {@code id} on one side, or null for an unknown id (it then does nothing). The
	 * Dampener falls back to its built-in numbers so guns dampened before attachments were data-driven
	 * keep working even without the file.
	 */
	public static AttachmentStats get(boolean clientSide, String id) {
		AttachmentStats stats = (clientSide ? client : server).get(id);
		if (stats == null && GunAttachments.DAMPENER.equals(id)) {
			return AttachmentStats.DAMPENER_FALLBACK;
		}
		return stats;
	}

	public static AttachmentStats get(Level level, String id) {
		return get(level.isClientSide(), id);
	}

	/** The stats of every attachment fitted to a gun, unknown ids skipped. */
	public static List<AttachmentStats> fitted(boolean clientSide, GunAttachments attachments) {
		return attachments.ids().stream().map(id -> get(clientSide, id)).filter(s -> s != null).toList();
	}
}
