package xyz.toesinthesand.ashfall.salvage.atlas;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.loot.LootState;

import java.util.Map;
import java.util.TreeMap;

/**
 * {@code /ashfall atlas ...}: admin visibility into, and control over, the lazy map conversion.
 * Needed because conversion happens silently as chunks load; without it an admin cannot tell
 * which building source is active, how far conversion got, or whether loot ended up where expected.
 */
public final class AtlasCommands {
	private AtlasCommands() {
	}

	/** Default {@code processall} radius: the dense city, out to where the wasteland ring starts. */
	public static final int DEFAULT_PROCESSALL_RADIUS = 1600;

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("atlas")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("status").executes(AtlasCommands::status))
						.then(Commands.literal("process")
								.then(Commands.argument("radius", IntegerArgumentType.integer(0, 32))
										.executes(AtlasCommands::process)))
						.then(processAllNode())
						.then(Commands.literal("audit").executes(AtlasCommands::audit)))
				// The plan's name for the same job, next to /ashfall city zone|info.
				.then(Commands.literal("city")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(processAllNode())));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> processAllNode() {
		return Commands.literal("processall")
				.executes(ctx -> processAll(ctx, DEFAULT_PROCESSALL_RADIUS))
				.then(Commands.argument("radius", IntegerArgumentType.integer(16, 4000))
						.executes(ctx -> processAll(ctx, IntegerArgumentType.getInteger(ctx, "radius"))));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		AtlasData data = AtlasData.get(source.getServer());
		ProcessAllJob job = AtlasProcessor.job();
		int size = AtlasProcessor.buildings().size();
		String msg = "Atlas v" + AtlasProcessor.CURRENT_VERSION + ": buildings from " + AtlasProcessor.buildings().name()
				+ (size >= 0 ? " (" + size + ")" : "")
				+ (AtlasProcessor.active() ? "" : " (inactive)")
				+ ", " + AtlasProcessor.processedThisSession() + " chunks processed this session"
				+ ", queue " + AtlasProcessor.queueSize()
				+ (AtlasProcessor.waitingSize() > 0 ? " (+" + AtlasProcessor.waitingSize() + " waiting for neighbours)" : "")
				+ ", " + data.relays().size() + " relay candidates"
				+ ", " + data.safeCandidates().size() + " safe candidates"
				+ ", " + RelightLog.get(source.getServer()).total() + " lights logged for the relight"
				+ (job != null ? ", processall " + job.done() + "/" + job.total() : "");
		source.sendSuccess(() -> Component.literal(msg), false);
		return AtlasProcessor.processedThisSession();
	}

	private static int process(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getServer().overworld();
		if (source.getLevel().dimension() != Level.OVERWORLD) {
			source.sendFailure(Component.literal("The atlas only processes the overworld"));
			return 0;
		}
		int radius = IntegerArgumentType.getInteger(ctx, "radius");
		ChunkPos centre = ChunkPos.containing(BlockPos.containing(source.getPosition()));
		int queued = 0;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(centre.x() + dx, centre.z() + dz);
				if (chunk != null && AtlasProcessor.needsProcessing(chunk) && AtlasProcessor.enqueue(chunk.getPos().pack())) {
					queued++;
				}
			}
		}
		int n = queued;
		source.sendSuccess(() -> Component.literal("Queued " + n + " loaded chunks for atlas processing"
				+ (AtlasProcessor.active() ? "" : " (atlas inactive: no building source or no building profiles)")), true);
		return n;
	}

	private static int processAll(CommandContext<CommandSourceStack> ctx, int radius) {
		CommandSourceStack source = ctx.getSource();
		if (!AtlasProcessor.active()) {
			source.sendFailure(Component.literal("Atlas inactive: no building source (Lost Cities or Arnis) or no building profiles loaded"));
			return 0;
		}
		ProcessAllJob running = AtlasProcessor.job();
		if (running != null) {
			running.cancel(source.getServer().overworld());
		}
		LongArrayList chunks = new LongArrayList(AtlasProcessor.buildings().chunks(radius));
		AtlasProcessor.startJob(new ProcessAllJob(chunks, source));
		source.sendSuccess(() -> Component.literal("City processall started: " + chunks.size() + " chunks"
				+ (AtlasProcessor.buildings().size() < 0 ? " within " + radius + " blocks of Downtown" : "")), true);
		return chunks.size();
	}

	/** What {@code /ashfall atlas audit} found; a record so the gametest can check the numbers directly. */
	public record Audit(int chunks, int unprocessed, int lights, Map<String, Integer> containersByProfile) {
		public int containers() {
			return containersByProfile.values().stream().mapToInt(Integer::intValue).sum();
		}
	}

	/** Audits every chunk the overworld really has loaded, including force-loaded ones (see {@link LoadedChunks}). */
	public static Audit audit(ServerLevel level) {
		Map<String, Integer> byProfile = new TreeMap<>();
		int lights = 0, chunks = 0, unprocessed = 0;
		for (LevelChunk chunk : AtlasProcessor.loadedChunks(level)) {
			chunks++;
			if (AtlasProcessor.needsProcessing(chunk)) {
				unprocessed++;
			}
			lights += LightStripper.count(chunk);
			for (BlockEntity be : chunk.getBlockEntities().values()) {
				LootState state = be.getAttached(AshfallSalvage.LOOT_STATE);
				if (state != null) {
					byProfile.merge(state.profile().map(Identifier::toString).orElse("(none)"), 1, Integer::sum);
				}
			}
		}
		return new Audit(chunks, unprocessed, lights, byProfile);
	}

	private static int audit(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Audit audit = audit(source.getServer().overworld());
		String msg = "Atlas audit of " + audit.chunks() + " loaded chunks (" + audit.unprocessed() + " unprocessed): "
				+ audit.containers() + " loot containers, " + audit.lights() + " light blocks left";
		source.sendSuccess(() -> Component.literal(msg), false);
		audit.containersByProfile().forEach((profile, count) -> source.sendSuccess(() -> Component.literal("  " + profile + ": " + count), false));
		return audit.containers();
	}
}
