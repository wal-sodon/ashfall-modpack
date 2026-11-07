package xyz.toesinthesand.ashfall.arms.finale;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.salvage.site.PlacedSite;
import xyz.toesinthesand.ashfall.salvage.site.SiteState;
import xyz.toesinthesand.ashfall.salvage.site.SiteTemplate;
import xyz.toesinthesand.ashfall.salvage.site.Sites;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Wreck hull as the finale sees it: where its Uplink Socket and hatch are, sealing the hatch until the city
 * reaches Grid 80% ("the hull opens at Grid 80%", endgame-design section 3), and opening it then.
 * <p>
 * Salvage places the hull and cannot name arms blocks, so its template carries vanilla placeholders: a lodestone
 * marked {@code uplink_socket} and two iron doors. When the hull is placed (or, for a hull placed before this
 * existed, the first time its chunks are loaded) arms swaps them for the {@link FinaleBlocks#UPLINK_SOCKET} and the
 * unbreakable {@link FinaleBlocks#HULL_SEAL}. Positions are worked out from the placed origin and the current
 * template, so an older placement finds its socket spot the same way.
 * <p>
 * While sealed, survival players cannot break any block of the hull's footprint either: the hull is deepslate, and a
 * seal you can tunnel around is no seal.
 */
public final class HullSite {
	public static final String SITE_ID = "ashfall:wreck_hull";
	public static final String SOCKET_MARKER = "uplink_socket";
	private static final int CHECK_TICKS = 40;

	private HullSite() {
	}

	static void init() {
		Sites.PLACED.register((level, site) -> {
			if (site.key().equals(PlacedSite.fixedKey(SITE_ID))) {
				prepare(level);
			}
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !sealHolds(level, player, pos));
	}

	/** The placed hull, if it is in the world yet. */
	public static Optional<PlacedSite> placed(MinecraftServer server) {
		return Optional.ofNullable(SiteState.get(server).site(PlacedSite.fixedKey(SITE_ID)));
	}

	private static Optional<SiteTemplate> template(PlacedSite site) {
		return Optional.ofNullable(Sites.template(site.template()));
	}

	/** The hull's Uplink Socket position (from its marker), once the hull is placed. */
	public static Optional<BlockPos> socket(MinecraftServer server) {
		return placed(server).flatMap(HullSite::socketOf);
	}

	/** The hatch cells (the template's iron doors), once the hull is placed. */
	public static List<BlockPos> hatch(MinecraftServer server) {
		return placed(server).map(HullSite::hatchOf).orElse(List.of());
	}

	/**
	 * A placed hull's socket: the recorded marker, or (for a hull placed before the template had one) the current
	 * template's marker cell from the placed origin.
	 */
	public static Optional<BlockPos> socketOf(PlacedSite site) {
		return site.marker(SOCKET_MARKER).or(() -> template(site).flatMap(t -> t.markerCells().stream()
				.filter(m -> m.getKey().equals(SOCKET_MARKER)).findFirst()
				.map(m -> site.origin().offset(m.getValue().x(), m.getValue().y(), m.getValue().z()))));
	}

	/** A placed hull's hatch cells: wherever its template has iron doors. */
	public static List<BlockPos> hatchOf(PlacedSite site) {
		List<BlockPos> out = new ArrayList<>();
		template(site).ifPresent(t -> {
			for (SiteTemplate.Cell cell : t.cells()) {
				String state = t.palette().get(String.valueOf(cell.symbol()));
				if (state != null && state.startsWith("minecraft:iron_door")) {
					out.add(site.origin().offset(cell.x(), cell.y(), cell.z()));
				}
			}
		});
		return out;
	}

	/** The hull's footprint, the protected box while it is sealed. */
	public static Optional<BoundingBox> box(MinecraftServer server) {
		return placed(server).flatMap(site -> template(site).map(t -> BoundingBox.fromCorners(site.origin(),
				site.origin().offset(t.width() - 1, t.height() - 1, t.depth() - 1))));
	}

	/** Once every 2 seconds: prepare a placed hull, ask for the hatch to open at Grid 80%, open it when loaded. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 7) {
			return;
		}
		ServerLevel level = server.overworld();
		FinaleState state = FinaleState.get(server);
		if (placed(server).isEmpty()) {
			return;
		}
		if (!state.hullPrepared()) {
			prepare(level);
		}
		if (!state.openRequested() && shouldOpen(GridState.get(server).gridPercent())) {
			state.requestOpen(true);
			FinaleController.broadcast(server, Component.translatable("message.ashfall.finale.hull_unsealed")
					.withStyle(ChatFormatting.LIGHT_PURPLE));
			AshfallArms.LOGGER.info("Finale: the Grid reached 80%, the Wreck hull unseals");
		}
		if (state.openRequested() && !state.hullOpen()) {
			open(level);
		}
	}

	/** The hull opens at Grid 80%: all four districts reclaimed. */
	public static boolean shouldOpen(int gridPercent) {
		return gridPercent >= 80;
	}

	/** Swaps the placeholders for the socket and the seal, if the hull is placed and its cells are loaded. */
	public static boolean prepare(ServerLevel level) {
		MinecraftServer server = level.getServer();
		FinaleState state = FinaleState.get(server);
		Optional<PlacedSite> site = placed(server);
		if (site.isEmpty() || !prepareSite(level, site.get(), !state.hullOpen())) {
			return false;
		}
		state.setHullPrepared(true);
		return true;
	}

	/**
	 * Swaps one placed hull's socket placeholder for the Uplink Socket and, if {@code seal}, its doors for the Hull
	 * Seal. Public for gametests, which place the real template under their own key. @return false while any of its
	 * cells is unloaded (nothing is changed then)
	 */
	public static boolean prepareSite(ServerLevel level, PlacedSite site, boolean seal) {
		Optional<BlockPos> socket = socketOf(site);
		List<BlockPos> hatch = hatchOf(site);
		if (socket.isEmpty() || !level.hasChunkAt(socket.get()) || hatch.stream().anyMatch(p -> !level.hasChunkAt(p))) {
			return false;
		}
		if (!level.getBlockState(socket.get()).is(FinaleBlocks.UPLINK_SOCKET)) {
			level.setBlock(socket.get(), FinaleBlocks.UPLINK_SOCKET.defaultBlockState(), Block.UPDATE_ALL);
		}
		if (seal) {
			seal(level, hatch);
		}
		AshfallArms.LOGGER.info("Finale: Wreck hull {} prepared (socket at {}, {} hatch cells {})", site.key(), socket.get().toShortString(),
				hatch.size(), seal ? "sealed" : "open");
		return true;
	}

	/** Opens the hatch now if its chunks are loaded. @return true if it is open */
	public static boolean open(ServerLevel level) {
		FinaleState state = FinaleState.get(level.getServer());
		List<BlockPos> hatch = hatch(level.getServer());
		if (hatch.isEmpty() || hatch.stream().anyMatch(p -> !level.hasChunkAt(p))) {
			return false;
		}
		int opened = unseal(level, hatch);
		state.requestOpen(true);
		state.setHullOpen(true);
		if (opened > 0) {
			level.playSound(null, hatch.getFirst(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2.0f, 0.5f);
		}
		AshfallArms.LOGGER.info("Finale: Wreck hull hatch opened ({} seal blocks)", opened);
		return true;
	}

	/**
	 * Puts the Hull Seal into {@code cells}. Without shape updates, so the second half of an iron door does not pop
	 * off as an item when the first is replaced. Public for gametests. @return cells sealed
	 */
	public static int seal(ServerLevel level, List<BlockPos> cells) {
		int n = 0;
		for (BlockPos pos : cells) {
			BlockState current = level.getBlockState(pos);
			if (!current.is(FinaleBlocks.HULL_SEAL)) {
				level.setBlock(pos, FinaleBlocks.HULL_SEAL.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				n++;
			}
		}
		return n;
	}

	/** Clears the Hull Seal from {@code cells} (only seal blocks; anything else there stays). @return cells opened */
	public static int unseal(ServerLevel level, List<BlockPos> cells) {
		int n = 0;
		for (BlockPos pos : cells) {
			if (level.getBlockState(pos).is(FinaleBlocks.HULL_SEAL)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				n++;
			}
		}
		return n;
	}

	/** Whether the seal stops this block break: a survival player inside the sealed hull's footprint. */
	static boolean sealHolds(Level level, Player player, BlockPos pos) {
		if (!(level instanceof ServerLevel server) || level.dimension() != Level.OVERWORLD || player.isCreative() || player.isSpectator()) {
			return false;
		}
		FinaleState state = FinaleState.get(server.getServer());
		if (state.hullOpen()) {
			return false;
		}
		Optional<BoundingBox> box = box(server.getServer());
		if (box.isEmpty() || !box.get().isInside(pos)) {
			return false;
		}
		if (player instanceof ServerPlayer sp) {
			sp.sendOverlayMessage(Component.translatable("message.ashfall.finale.hull_sealed", GridState.get(server.getServer()).gridPercent())
					.withStyle(ChatFormatting.GRAY));
		}
		return true;
	}
}
