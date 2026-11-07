package xyz.toesinthesand.ashfall.director.graffiti;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.claims.Claims;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;
import xyz.toesinthesand.ashfall.director.spawn.BuildingRecord;
import xyz.toesinthesand.ashfall.director.spawn.DirectorState;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.building.Building;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Graffiti marks: co-op notes on walls ("cleared", "danger", "loot", "safehouse", arrows) sprayed with the Spray Can.
 * <p>
 * Cleared marks tell the truth. A mark knows the building it was sprayed on (the block it is on, or the wall behind
 * it) and that building's population generation, from the Director's {@link BuildingRecord}. A Cleared mark on a
 * building that still has a population gets the warn overlay and its sprayer hears movement inside; when the building
 * empties it turns fresh; when the record refills (READY, after the cleared period) it turns stale for good. Marks in
 * loaded chunks change the moment the record does ({@link #buildingChanged}); the rest catch up by random tick.
 * <p>
 * Overworld marks go in {@link GraffitiIndex} (saved with the Director's state). Every {@link #SYNC_TICKS} each player
 * is sent the map marks of everyone in their Open Parties and Claims party, or only their own without a party: each
 * owner's newest {@link #MAP_MARKS}.
 */
public final class Graffiti {
	public static final int MAP_MARKS = 64;
	public static final int SYNC_TICKS = 40;

	/** The last list each player was sent. */
	private static final Map<UUID, List<GraffitiPayload.Mark>> LAST = new HashMap<>();
	private static Function<BlockPos, Long> buildingOverride;

	private Graffiti() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(GraffitiPayload.TYPE, GraffitiPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SYNC_TICKS == 13) {
				sync(server);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> LAST.remove(handler.getPlayer().getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
	}

	/**
	 * Sprays {@code symbol} on the {@code face} side of {@code wall}, over any mark already there. Fails on a floor or
	 * ceiling, where something else is in the way, or where the wall can't hold a mark (not a full face).
	 */
	public static boolean spray(ServerLevel level, BlockPos wall, Direction face, GraffitiSymbol symbol, Player player) {
		BlockState clicked = level.getBlockState(wall);
		if (clicked.is(DirectorBlocks.GRAFFITI)) {
			// Aiming at a wall with paint on it hits the paint: spray over it instead.
			face = clicked.getValue(GraffitiBlock.FACING);
			wall = wall.relative(face.getOpposite());
		}
		if (!face.getAxis().isHorizontal()) {
			return false;
		}
		BlockPos pos = wall.relative(face);
		BlockState there = level.getBlockState(pos);
		if (!there.isAir() && !there.is(DirectorBlocks.GRAFFITI)) {
			return false;
		}
		BlockState state = DirectorBlocks.GRAFFITI.defaultBlockState().setValue(GraffitiBlock.FACING, face).setValue(GraffitiBlock.SYMBOL, symbol);
		if (!state.canSurvive(level, pos)) {
			return false;
		}
		Long building = buildingAt(level, pos, face);
		DirectorState director = DirectorState.get(level.getServer());
		BuildingRecord record = building == null ? null : director.building(building);
		int generation = record == null ? 0 : record.generation();
		MarkState mark = symbol == GraffitiSymbol.CLEARED && building != null ? truth(record, generation) : MarkState.FRESH;
		level.setBlock(pos, state.setValue(GraffitiBlock.STATE, mark), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.35f, 1.8f);
		if (level == level.getServer().overworld()) {
			director.graffiti().put(new GraffitiMark(pos.asLong(), player.getUUID(), symbol, Optional.ofNullable(building), generation,
					level.getGameTime()));
			director.setDirty();
		}
		if (mark == MarkState.WARN) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.graffiti.movement"));
		}
		return true;
	}

	/**
	 * What a Cleared mark should show: stale once the building refilled after it was sprayed, warn while it has (or
	 * is about to get) a population, fresh while it is cleared. No record means the building has no population.
	 */
	public static MarkState truth(@Nullable BuildingRecord record, int markGeneration) {
		if (record == null) {
			return MarkState.FRESH;
		}
		if (record.generation() > markGeneration) {
			return MarkState.STALE;
		}
		return record.status() == BuildingRecord.Status.CLEARED ? MarkState.FRESH : MarkState.WARN;
	}

	/** The building's record changed (it cleared or refilled): updates its loaded Cleared marks now. */
	public static void buildingChanged(MinecraftServer server, long building) {
		ServerLevel level = server.overworld();
		for (GraffitiMark mark : DirectorState.get(server).graffiti().onBuilding(building)) {
			BlockPos pos = BlockPos.of(mark.pos());
			if (mark.symbol() == GraffitiSymbol.CLEARED && level.isLoaded(pos)) {
				refresh(level, pos, level.getBlockState(pos));
			}
		}
	}

	/** Brings one Cleared mark in line with its building's record. */
	public static void refresh(ServerLevel level, BlockPos pos, BlockState state) {
		if (!state.is(DirectorBlocks.GRAFFITI) || state.getValue(GraffitiBlock.SYMBOL) != GraffitiSymbol.CLEARED
				|| level != level.getServer().overworld()) {
			return;
		}
		DirectorState director = DirectorState.get(level.getServer());
		GraffitiMark mark = director.graffiti().get(pos.asLong());
		if (mark == null || mark.building().isEmpty()) {
			return;
		}
		MarkState truth = truth(director.building(mark.building().get()), mark.generation());
		if (state.getValue(GraffitiBlock.STATE) != truth) {
			level.setBlock(pos, state.setValue(GraffitiBlock.STATE, truth), Block.UPDATE_CLIENTS);
		}
	}

	/** The paint at {@code pos} is gone. */
	static void removed(ServerLevel level, BlockPos pos) {
		if (level != level.getServer().overworld()) {
			return;
		}
		DirectorState director = DirectorState.get(level.getServer());
		if (director.graffiti().remove(pos.asLong())) {
			director.setDirty();
		}
	}

	/** The building the mark is on: the one holding the mark's block, else the one holding the wall behind it. */
	private static @Nullable Long buildingAt(ServerLevel level, BlockPos pos, Direction face) {
		Function<BlockPos, Long> test = buildingOverride;
		if (test != null) {
			Long id = test.apply(pos);
			return id != null ? id : test.apply(pos.relative(face.getOpposite()));
		}
		Building building = AshfallSalvage.buildings().at(pos);
		if (building == null) {
			building = AshfallSalvage.buildings().at(pos.relative(face.getOpposite()));
		}
		return building == null ? null : building.id();
	}

	/** What one player may see: their own map marks and those of their party, each owner's newest {@link #MAP_MARKS}. */
	static List<GraffitiPayload.Mark> visible(MinecraftServer server, DirectorState director, UUID player, Map<UUID, Optional<UUID>> parties) {
		GraffitiIndex index = director.graffiti();
		Optional<UUID> party = party(server, player, parties);
		List<GraffitiPayload.Mark> out = new ArrayList<>();
		for (UUID owner : index.owners()) {
			if (!owner.equals(player) && (party.isEmpty() || !party.equals(party(server, owner, parties)))) {
				continue;
			}
			for (GraffitiMark mark : index.newestOnMap(owner, MAP_MARKS)) {
				BlockPos pos = BlockPos.of(mark.pos());
				MarkState state = mark.symbol() == GraffitiSymbol.CLEARED && mark.building().isPresent()
						? truth(director.building(mark.building().get()), mark.generation()) : MarkState.FRESH;
				out.add(new GraffitiPayload.Mark(pos.getX(), pos.getZ(), mark.symbol(), state));
			}
		}
		return out;
	}

	private static Optional<UUID> party(MinecraftServer server, UUID player, Map<UUID, Optional<UUID>> parties) {
		return parties.computeIfAbsent(player, p -> Optional.ofNullable(Claims.partyId(server, p)));
	}

	private static void sync(MinecraftServer server) {
		DirectorState director = DirectorState.get(server);
		Map<UUID, Optional<UUID>> parties = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!ServerPlayNetworking.canSend(player, GraffitiPayload.TYPE)) {
				continue;
			}
			List<GraffitiPayload.Mark> view = visible(server, director, player.getUUID(), parties);
			if (!view.equals(LAST.get(player.getUUID()))) {
				LAST.put(player.getUUID(), view);
				ServerPlayNetworking.send(player, new GraffitiPayload(view));
			}
		}
	}

	/** Test hook: replaces the building lookup with {@code lookup} (block position to building id, or null); null restores it. */
	public static void setBuildingLookupForTesting(@Nullable Function<BlockPos, Long> lookup) {
		buildingOverride = lookup;
	}
}
