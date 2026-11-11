package xyz.toesinthesand.ashfall.salvage.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/**
 * Lazy refill on open, plus the container operations behind {@code /lootzone}.
 * <p>
 * Refill happens when a player opens a container rather than on a timer sweep: nothing needs to
 * scan 4,000+ containers, unloaded chunks cost nothing, and the reroll is unpacked by vanilla's
 * own open path, so loot functions see the real opening player.
 */
public final class LootRefill {
	/**
	 * Chance (0 to 1) that a player's first open of fresh loot rolls the table a second time. Set by the Director's
	 * Pack Rat skill; salvage itself never gives a bonus.
	 */
	private static volatile ToDoubleFunction<ServerPlayer> extraRollChance = player -> 0.0;

	private LootRefill() {
	}

	/** This player's chance of an extra roll (0 without the Pack Rat skill). */
	public static double extraRollChance(ServerPlayer player) {
		return extraRollChance.applyAsDouble(player);
	}

	/** Replaces the extra-roll chance (see {@link #extraRollChance}); null restores "never". */
	public static void setExtraRollChance(ToDoubleFunction<ServerPlayer> chance) {
		extraRollChance = chance == null ? player -> 0.0 : chance;
	}

	/** {@code UseBlockCallback}: refill if due, stamp the open, and let vanilla open the container. */
	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer) || player.isSpectator()
				|| (player.isSecondaryUseActive() && !(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()))) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		handleOpen(server, pos, true);
		maybeExtraRoll(server, pos, (ServerPlayer) player, player.getRandom().nextDouble());
		// A double chest opens as one inventory: handle the other half too, even when only that
		// half is tracked (e.g. a zone radius that covers just one half).
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			handleOpen(server, ChestBlock.getConnectedBlockPos(pos, state), true);
		}
		return InteractionResult.PASS;
	}

	/**
	 * Applies the refill rule to the container at {@code pos} and records the open.
	 *
	 * @param byPlayer whether a player is standing there right now (marks the area as seen first,
	 *                 so a returning player's very first open counts the quiet gap they ended)
	 * @return false if the block is not an Ashfall-tracked loot container
	 */
	public static boolean handleOpen(ServerLevel level, BlockPos pos, boolean byPlayer) {
		return handleOpen(level, pos, byPlayer, level.getServer().overworld().getGameTime());
	}

	/** As {@link #handleOpen(ServerLevel, BlockPos, boolean)} at an explicit game time (tests). */
	public static boolean handleOpen(ServerLevel level, BlockPos pos, boolean byPlayer, long now) {
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof RandomizableContainer container)) {
			return false;
		}
		LootState state = be.getAttached(AshfallSalvage.LOOT_STATE);
		if (state == null) {
			return false;
		}
		ZoneManager manager = ZoneManager.get(level.getServer());
		if (state.zone().isPresent() && manager.zone(state.zone().get()) == null) {
			// Its zone was deleted while this chunk was unloaded: fall back to the profile now.
			state = released(state);
		}
		Area area = areaOf(level, pos, state, manager);
		if (byPlayer && area.key != null) {
			manager.markSeen(area.key, now, area.quietTicks);
		}
		long gapEnd = area.key != null ? manager.presence(area.key).lastQuietGapEnd() : -1;
		if (RefillRule.shouldRefill(area.rules, state.lastOpened(), now, gapEnd)) {
			state = refill(container, state);
		}
		be.setAttached(AshfallSalvage.LOOT_STATE, state.opened(now));
		be.setChanged();
		return true;
	}

	/**
	 * The Pack Rat bonus: if the tracked container at {@code pos} still holds an unrolled table and {@code roll} is
	 * under the player's extra-roll chance, unpack it now (as vanilla's open would) and roll the same table once more
	 * into the slots left empty.
	 *
	 * @param roll uniform in [0, 1), passed in so tests can pin both outcomes
	 * @return whether an extra roll happened
	 */
	public static boolean maybeExtraRoll(ServerLevel level, BlockPos pos, ServerPlayer player, double roll) {
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof RandomizableContainer container) || be.getAttached(AshfallSalvage.LOOT_STATE) == null) {
			return false;
		}
		ResourceKey<LootTable> key = container.getLootTable();
		if (key == null || roll >= extraRollChance.applyAsDouble(player)) {
			return false;
		}
		container.unpackLootTable(player);
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
				.withLuck(player.getLuck())
				.withParameter(LootContextParams.THIS_ENTITY, player)
				.create(LootContextParamSets.CHEST);
		table.fill(container, params, level.getRandom().nextLong());
		container.setChanged();
		return true;
	}

	/** Clears the container and arms its table with the next deterministic seed. */
	static LootState refill(RandomizableContainer container, LootState state) {
		container.clearContent();
		container.setLootTable(tableKey(state.table()), RefillRule.seed(container.getBlockPos().asLong(), state.refills() + 1));
		container.setChanged();
		return state.refilled();
	}

	/** Refill rules and presence key that govern one container. */
	private record Area(String key, BuildingProfile.Refill rules, long quietTicks) {
	}

	private static Area areaOf(ServerLevel level, BlockPos pos, LootState state, ZoneManager manager) {
		LootZone zone = state.zone().map(manager::zone).orElse(null);
		if (zone != null) {
			return new Area(zone.areaKey(), zone.refill(), zone.refill().quietTicks());
		}
		BuildingProfile profile = state.profile().map(BuildingProfiles::get).orElse(null);
		BuildingProfile.Refill rules = profile != null ? profile.refill() : BuildingProfile.Refill.DEFAULT;
		Building building = level.dimension() == Level.OVERWORLD ? AshfallSalvage.buildings().at(pos) : null;
		if (building == null) {
			// Outside every known building nobody tracks presence, so it never refills.
			return new Area(null, rules, rules.quietTicks());
		}
		return new Area(PresenceTracker.buildingKey(building), rules, PresenceTracker.rulesFor(building).quietTicks());
	}

	public static ResourceKey<net.minecraft.world.level.storage.loot.LootTable> tableKey(Identifier table) {
		return ResourceKey.create(Registries.LOOT_TABLE, table);
	}

	// ---- zone operations ------------------------------------------------------------------------

	/** Loaded loot containers whose column lies within {@code radius} of the zone's center. */
	static void forEachLoadedContainer(ServerLevel level, LootZone zone, int radius, Consumer<BlockEntity> action) {
		BlockPos c = zone.center();
		long r2 = (long) radius * radius;
		List<BlockEntity> found = new ArrayList<>();
		for (int cx = (c.getX() - radius) >> 4; cx <= (c.getX() + radius) >> 4; cx++) {
			for (int cz = (c.getZ() - radius) >> 4; cz <= (c.getZ() + radius) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be instanceof RandomizableContainer && zone.horizontalDistSq(be.getBlockPos()) <= r2) {
						found.add(be);
					}
				}
			}
		}
		found.forEach(action);
	}

	/**
	 * An explicit block id or block tag matches any randomizable container of that block (a tag is the admin's
	 * explicit choice, so it may include hoppers). {@code "any"} only
	 * means loot-style storage (chests of every kind, barrels): hoppers, dispensers, droppers,
	 * crafters, shulker boxes and pots are randomizable too, and wiping a player's redstone or
	 * shulker stash because it sat inside a zone radius would be destructive.
	 */
	static boolean typeMatches(LootZone zone, BlockEntity be) {
		if (zone.containerType().isAny()) {
			return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
		}
		return zone.containerType().matches(be.getBlockState());
	}

	/**
	 * (Re)claims every loaded matching container in the zone and releases containers that it owned
	 * but no longer matches (after a radius or type edit). A container already owned with the same
	 * table keeps its contents, so re-scanning never hands out free loot; anything else is wiped
	 * (Arnis' fixed vanilla items included) and armed with a fresh roll of the zone's table.
	 *
	 * @param oldRadius the zone's previous radius, so a shrink still finds the containers to release
	 * @return number of containers the zone now owns in loaded chunks
	 */
	public static int apply(ServerLevel level, LootZone zone, int oldRadius) {
		int[] count = {0};
		forEachLoadedContainer(level, zone, Math.max(zone.radius(), oldRadius), be -> {
			LootState old = be.getAttached(AshfallSalvage.LOOT_STATE);
			boolean owned = old != null && old.zone().filter(zone.id()::equals).isPresent();
			if (!zone.contains(be.getBlockPos()) || !typeMatches(zone, be)) {
				if (owned) {
					release(be, old);
				}
				return;
			}
			count[0]++;
			if (owned && old.table().equals(zone.table())) {
				return;
			}
			RandomizableContainer container = (RandomizableContainer) be;
			container.clearContent();
			container.setLootTable(tableKey(zone.table()), level.getRandom().nextLong());
			Optional<Identifier> profile = old != null ? old.profile() : Optional.empty();
			int refills = old != null ? old.refills() : 0;
			be.setAttached(AshfallSalvage.LOOT_STATE, new LootState(zone.table(), profile, Optional.of(zone.id()), -1, refills));
			be.setChanged();
		});
		return count[0];
	}

	/** Every loaded container the zone owns gets refilled now, regardless of timers. */
	public static int forceRefill(ServerLevel level, LootZone zone) {
		int[] count = {0};
		forEachLoadedContainer(level, zone, zone.radius(), be -> {
			LootState state = be.getAttached(AshfallSalvage.LOOT_STATE);
			if (state != null && state.zone().filter(zone.id()::equals).isPresent()) {
				be.setAttached(AshfallSalvage.LOOT_STATE, refill((RandomizableContainer) be, state));
				be.setChanged();
				count[0]++;
			}
		});
		return count[0];
	}

	/** Detaches every loaded container from the zone so it falls back to its building profile. */
	public static int releaseAll(ServerLevel level, LootZone zone) {
		int[] count = {0};
		forEachLoadedContainer(level, zone, zone.radius(), be -> {
			LootState state = be.getAttached(AshfallSalvage.LOOT_STATE);
			if (state != null && state.zone().filter(zone.id()::equals).isPresent()) {
				release(be, state);
				count[0]++;
			}
		});
		return count[0];
	}

	private static void release(BlockEntity be, LootState state) {
		be.setAttached(AshfallSalvage.LOOT_STATE, released(state));
		be.setChanged();
	}

	/** The state without its zone; future rolls use the building profile's table if it has one. */
	static LootState released(LootState state) {
		Identifier table = state.profile().map(BuildingProfiles::get)
				.flatMap(BuildingProfile::lootTable).orElse(state.table());
		return new LootState(table, state.profile(), Optional.empty(), state.lastOpened(), state.refills());
	}
}
