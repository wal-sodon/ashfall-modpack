package xyz.toesinthesand.ashfall.arms.survivor;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.arms.grid.DistrictRecord;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;
import xyz.toesinthesand.ashfall.arms.grid.GridDistricts;
import xyz.toesinthesand.ashfall.arms.grid.GridEvents;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.story.MapMarkers;

import java.util.Optional;
import java.util.UUID;

/**
 * District traders (survivors plan, build order step 3). When a district is reclaimed, a Trader survivor comes to its
 * substation compound, the district's safehouse once it is lit (it becomes a safe zone), and stands near the core with
 * the district's own stock ({@link TraderStock}). One trader per district with a stock file, saved in the
 * {@link TraderRoster}; it keeps its post through a blackout or a Breach. A trader that is killed or turns is replaced
 * {@link SurvivorBalance.Trader#replaceTicks} of the day clock later, once the district is reclaimed and its compound is loaded.
 * <p>
 * Traders are the one exception to "everything comes from the bunker": players pay from their own inventory, in the
 * vanilla trading screen ({@link SurvivorTrade}). They take no orders and no roles, have no leader or home, never eat,
 * and are not on the {@link SurvivorRoster}, so they count toward no cap. Zombies hunt them like any survivor, and
 * players can't hurt them (the survivor tag covers both). They stay within {@link SurvivorBalance.Trader#radius} of their
 * post.
 */
public final class Traders {
	/** How often (ticks) the server looks for districts owed a trader. */
	static final int CHECK_TICKS = 100;

	private Traders() {
	}

	static void init() {
		GridEvents.DISTRICT_CHANGED.register((level, id, from, to) -> {
			if (to == DistrictState.RECLAIMED) {
				check(level, id, level.getOverworldClockTime());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 41) {
				ServerLevel level = server.overworld();
				for (String id : TraderStock.districts()) {
					if (!GridDistricts.isManual(id)) {
						check(level, id, level.getOverworldClockTime());
					}
				}
			}
		});
	}

	/**
	 * Brings {@code district} its trader if it is owed one at day-clock time {@code now}: the district has a stock file and
	 * is reclaimed, it has no living trader, any replacement delay is over (a due time more than the whole delay ahead,
	 * from a clock turned back, counts as over), and its compound is loaded.
	 *
	 * @return the new trader, or null when none came
	 */
	public static @Nullable Survivor check(ServerLevel level, String district, long now) {
		if (level.dimension() != Level.OVERWORLD || TraderStock.of(district).isEmpty()
				|| !GridState.get(level.getServer()).isReclaimed(district)) {
			return null;
		}
		TraderRoster roster = TraderRoster.get(level.getServer());
		long due = roster.dueAt(district);
		if (roster.trader(district).isPresent() || now < due && due - now <= SurvivorBalance.get().trader().replaceTicks()) {
			return null;
		}
		BlockPos anchor = anchor(level, district);
		if (anchor == null) {
			return null;
		}
		Survivor trader = spawn(level, district, standSpot(level, anchor));
		if (trader != null) {
			AshfallArms.LOGGER.info("Survivors: trader {} arrived at {} ({})", trader.survivorName(), district, trader.blockPosition());
		}
		return trader;
	}

	/**
	 * Puts a trader for {@code district} at {@code pos} (its post) and makes it the district's trader, removing the one
	 * before it if that one is loaded (one in an unloaded chunk removes itself when it next loads, {@link #checkPost}).
	 *
	 * @return the trader, or null if the district has no stock or the entity could not be created
	 */
	public static @Nullable Survivor spawn(ServerLevel level, String district, BlockPos pos) {
		if (TraderStock.of(district).isEmpty()) {
			return null;
		}
		Survivor trader = Survivors.spawn(level, pos, SurvivorRole.TRADER, null);
		if (trader == null) {
			return null;
		}
		trader.becomeTrader(district, level.getOverworldClockTime());
		TraderRoster roster = TraderRoster.get(level.getServer());
		Optional<UUID> old = roster.trader(district);
		roster.set(district, trader.getUUID());
		old.map(level::getEntity).filter(e -> e instanceof Survivor && e != trader).ifPresent(e -> e.discard());
		return trader;
	}

	/**
	 * The trader's periodic check: one that is no longer its district's trader (replaced while it was unloaded) leaves,
	 * and one its district has no record of (an older save) is adopted.
	 */
	static void checkPost(ServerLevel level, Survivor trader) {
		SurvivorTrade trade = trader.trade();
		if (trade == null) {
			return;
		}
		TraderRoster roster = TraderRoster.get(level.getServer());
		Optional<UUID> current = roster.trader(trade.district());
		if (current.isEmpty()) {
			roster.set(trade.district(), trader.getUUID());
		} else if (!current.get().equals(trader.getUUID())) {
			trader.discard();
		}
	}

	/** A trader died, turned or was removed: its district is due a new one after the replacement delay (day clock). */
	static void gone(ServerLevel level, Survivor trader) {
		SurvivorTrade trade = trader.trade();
		if (trade != null) {
			TraderRoster.get(level.getServer()).lost(trade.district(), trader.getUUID(),
					level.getOverworldClockTime() + SurvivorBalance.get().trader().replaceTicks());
		}
	}

	/**
	 * Right-click on a trader: refused while another player is trading; otherwise restocks if due and opens the vanilla
	 * trading screen, or, when every offer is used up until the next restock, says how many days that is instead.
	 */
	public static void trade(ServerPlayer player, Survivor trader) {
		SurvivorTrade trade = trader.trade();
		if (trade == null) {
			return;
		}
		if (trade.busyWith(player)) {
			player.sendSystemMessage(SurvivorTalk.line(trader, "trader.busy", trade.getTradingPlayer().getDisplayName()));
			return;
		}
		// only once nobody else has the screen open: a restock swaps the offer list out from under an open menu
		long now = player.level().getOverworldClockTime();
		trade.restockIfDue(now);
		if (trade.soldOut()) {
			player.sendSystemMessage(SurvivorTalk.line(trader, "trader.sold_out", trade.daysToRestock(now)).withStyle(ChatFormatting.GRAY));
			return;
		}
		trader.setTradingPlayer(player);
		trader.openTradingScreen(player, trader.getDisplayName(), 1);
	}

	/**
	 * Where a district's trader stands guard: its Substation Core, or failing that the substation marker on the Grid Map,
	 * as long as that spot is loaded.
	 */
	static @Nullable BlockPos anchor(ServerLevel level, String district) {
		DistrictRecord r = GridState.get(level.getServer()).record(district);
		if (r != null && r.hasCore()) {
			BlockPos core = BlockPos.of(r.corePos);
			return level.hasChunkAt(core) ? core : null;
		}
		return MapMarkers.substation(district)
				.map(m -> new BlockPos(m.x(), 0, m.z()))
				.filter(level::hasChunkAt)
				.map(p -> level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p))
				.orElse(null);
	}

	/**
	 * A spot near {@code anchor} a survivor can stand on: two blocks of air over a sturdy floor, in rings out from two to
	 * five blocks away, first one level down (the control-room floor beside a core on its plinth), then level, then lower
	 * and higher. On top of the anchor when nothing fits.
	 */
	static BlockPos standSpot(ServerLevel level, BlockPos anchor) {
		for (int dy : new int[]{-1, 0, -2, 1}) {
			for (int ring = 2; ring <= 5; ring++) {
				for (int dx = -ring; dx <= ring; dx++) {
					for (int dz = -ring; dz <= ring; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
							continue;
						}
						BlockPos pos = anchor.offset(dx, dy, dz);
						if (level.hasChunkAt(pos) && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
								&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
							return pos;
						}
					}
				}
			}
		}
		return anchor.above();
	}
}
