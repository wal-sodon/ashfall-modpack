package xyz.toesinthesand.ashfall.arms.grid;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import team.reborn.energy.api.base.SimpleEnergyItem;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.Optional;
import java.util.Set;

/**
 * What a player does to the loop with their hands: socket a coupler (also how a BROKEN core is re-repaired, at half
 * the couplers), load a cell, link a relay, read a core.
 * Every action resolves the district, applies {@link DistrictLoop} through {@link GridState#update} (which fires
 * the change event) and answers on the action bar.
 */
public final class GridActions {
	private GridActions() {
	}

	/** The district a core at {@code pos} serves, resolving it on first use. */
	static Optional<String> districtOfCore(ServerLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof SubstationCoreBlockEntity core) {
			Optional<String> id = core.resolveDistrict(level);
			id.ifPresent(d -> {
				GridState grid = GridState.get(level.getServer());
				DistrictRecord r = grid.record(d);
				if (r == null || !r.hasCore()) {
					grid.update(level, d, rec -> rec.corePos = pos.asLong());
				}
			});
			return id;
		}
		return Optional.empty();
	}

	/** Repair: socket one Grid Coupler (consumed unless creative). @return the result, or null off-grid */
	public static DistrictLoop.Result socket(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
		Optional<String> id = districtOfCore(level, pos);
		if (id.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.grid.off_grid").withStyle(ChatFormatting.GRAY));
			return null;
		}
		String district = id.get();
		LoopRules rules = GridDistricts.settings(district).rules();
		DistrictLoop.Result[] result = new DistrictLoop.Result[1];
		GridState grid = GridState.get(level.getServer());
		boolean broken = grid.state(district) == DistrictState.BROKEN;
		DistrictRecord r = grid.update(level, district, rec -> result[0] = DistrictLoop.socket(rec, rules));
		switch (result[0]) {
			case ACCEPTED, COMPLETED -> {
				stack.consume(1, player);
				level.playSound(null, pos, SoundEvents.HEAVY_CORE_PLACE, SoundSource.BLOCKS, 1.0f, 1.2f);
				if (result[0] == DistrictLoop.Result.COMPLETED && broken) {
					broadcast(level, Component.translatable("message.ashfall.grid.rerepaired", GridDistricts.name(district),
							grid.gridPercent()).withStyle(ChatFormatting.GREEN));
				} else if (result[0] == DistrictLoop.Result.COMPLETED) {
					broadcast(level, Component.translatable("message.ashfall.grid.repaired", GridDistricts.name(district),
							rules.relaysRequired(GridDistricts.keyRelayChunks(district)), rules.cells()).withStyle(ChatFormatting.GOLD));
				} else {
					int needed = broken ? DistrictLoop.reRepairCouplers(rules) : rules.couplers();
					player.sendOverlayMessage(Component.translatable("message.ashfall.grid.coupler", r.couplers, needed));
				}
			}
			case FULL -> player.sendOverlayMessage(Component.translatable("message.ashfall.grid.couplers_full"));
			default -> player.sendOverlayMessage(Component.translatable("message.ashfall.grid.not_now", stateName(r.state)));
		}
		return result[0];
	}

	/** Energize: load one Energy Cell (consumed unless creative); the last one starts the hold. */
	public static DistrictLoop.Result loadCell(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
		Optional<String> id = districtOfCore(level, pos);
		if (id.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.grid.off_grid").withStyle(ChatFormatting.GRAY));
			return null;
		}
		String district = id.get();
		GridSettings settings = GridDistricts.settings(district);
		if (SimpleEnergyItem.getStoredEnergyUnchecked(stack) < settings.cellMinEnergy()) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.grid.cell_flat", settings.cellMinEnergy()));
			return DistrictLoop.Result.WRONG_STATE;
		}
		LoopRules rules = settings.rules();
		DistrictLoop.Result[] result = new DistrictLoop.Result[1];
		DistrictRecord r = GridState.get(level.getServer()).update(level, district, rec -> result[0] = DistrictLoop.loadCell(rec, rules));
		switch (result[0]) {
			case ACCEPTED, COMPLETED -> {
				stack.consume(1, player);
				level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 1.0f);
				if (result[0] == DistrictLoop.Result.COMPLETED) {
					int relays = rules.relaysRequired(GridDistricts.keyRelayChunks(district));
					broadcast(level, Component.translatable("message.ashfall.grid.energizing", GridDistricts.name(district),
							settings.energizeSeconds() / 60, settings.holdRadius(), relays).withStyle(ChatFormatting.GOLD));
					AshfallArms.LOGGER.info("Grid: {} energizing (hold {} s, {} key relays)", district, settings.energizeSeconds(), relays);
				} else {
					player.sendOverlayMessage(Component.translatable("message.ashfall.grid.cell", r.cells, rules.cells()));
				}
			}
			case FULL -> player.sendOverlayMessage(Component.translatable("message.ashfall.grid.cells_full"));
			default -> player.sendOverlayMessage(Component.translatable("message.ashfall.grid.not_now", stateName(r.state)));
		}
		return result[0];
	}

	/**
	 * Link: touch a Power Relay with a coupler. Nothing is consumed (see {@link GridCouplerItem}). A relay that is
	 * no district's key relay says so, so players learn which relays matter.
	 */
	public static DistrictLoop.Result link(ServerLevel level, BlockPos relayPos, ServerPlayer player) {
		ChunkPos chunk = ChunkPos.containing(relayPos);
		Optional<String> id = GridDistricts.keyRelayDistrict(chunk.x(), chunk.z());
		if (id.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.grid.not_key_relay").withStyle(ChatFormatting.GRAY));
			return DistrictLoop.Result.NOT_KEY_RELAY;
		}
		String district = id.get();
		Set<Long> keys = GridDistricts.keyRelayChunks(district);
		LoopRules rules = GridDistricts.settings(district).rules();
		DistrictLoop.Result[] result = new DistrictLoop.Result[1];
		DistrictRecord r = GridState.get(level.getServer()).update(level, district,
				rec -> result[0] = DistrictLoop.link(rec, rules, chunk.pack(), keys));
		Component name = GridDistricts.name(district);
		boolean won = r.state == DistrictState.RECLAIMED || r.state == DistrictState.BLACKOUT;
		if (won && (result[0] == DistrictLoop.Result.ACCEPTED || result[0] == DistrictLoop.Result.ALREADY_LINKED)) {
			// On a won district a coupler reads the relay, and a new link adds a spare.
			if (result[0] == DistrictLoop.Result.ACCEPTED) {
				level.playSound(null, relayPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.4f);
				GridDecay.refresh(level, district);
			}
			player.sendOverlayMessage(relayCondition(level, district, chunk.pack()));
			return result[0];
		}
		switch (result[0]) {
			case ACCEPTED -> {
				level.playSound(null, relayPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.4f);
				player.sendOverlayMessage(Component.translatable("message.ashfall.grid.linked", name,
						LoopRules.linkedCount(r.linked, keys), rules.relaysRequired(keys)));
			}
			case COMPLETED -> {
				level.playSound(null, relayPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
				broadcast(level, Component.translatable("message.ashfall.grid.all_linked", name).withStyle(ChatFormatting.GOLD));
			}
			case ALREADY_LINKED -> player.sendOverlayMessage(Component.translatable("message.ashfall.grid.already_linked", name,
					LoopRules.linkedCount(r.linked, keys), rules.relaysRequired(keys)));
			default -> player.sendOverlayMessage(Component.translatable("message.ashfall.grid.not_now", stateName(r.state)));
		}
		// Linking may be the last missing piece after the hold finished.
		GridController.checkEnergized(level, district, level.getOverworldClockTime());
		return result[0];
	}

	/**
	 * Repair: a Wire Spool on a worn, linked relay of a reclaimed (or blacked-out) district, with Duct Tape in the
	 * inventory, puts it back in full condition; both are consumed unless creative. Enough working relays bring a
	 * blacked-out district straight back.
	 */
	public static DistrictLoop.Result repairRelay(ServerLevel level, BlockPos relayPos, ServerPlayer player, ItemStack wire) {
		ChunkPos chunk = ChunkPos.containing(relayPos);
		Optional<String> id = GridDistricts.keyRelayDistrict(chunk.x(), chunk.z());
		if (id.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.grid.not_key_relay").withStyle(ChatFormatting.GRAY));
			return DistrictLoop.Result.NOT_KEY_RELAY;
		}
		String district = id.get();
		Set<Long> keys = GridDistricts.keyRelayChunks(district);
		GridState grid = GridState.get(level.getServer());
		DistrictRecord current = grid.record(district);
		if (current == null) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.grid.repair_not_won").withStyle(ChatFormatting.GRAY));
			return DistrictLoop.Result.WRONG_STATE;
		}
		ItemStack tape = findTape(player);
		DistrictLoop.Result check = DistrictLoop.repairRelay(copyForCheck(current), chunk.pack(), keys);
		if (check == DistrictLoop.Result.COMPLETED && tape.isEmpty() && !player.getAbilities().instabuild) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.grid.repair_needs_tape").withStyle(ChatFormatting.RED));
			return DistrictLoop.Result.WRONG_STATE;
		}
		DistrictLoop.Result[] result = new DistrictLoop.Result[1];
		grid.update(level, district, rec -> result[0] = DistrictLoop.repairRelay(rec, chunk.pack(), keys));
		switch (result[0]) {
			case COMPLETED -> {
				wire.consume(1, player);
				tape.consume(1, player);
				level.playSound(null, relayPos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.3f);
				GridDecay.refresh(level, district);
				player.sendOverlayMessage(relayCondition(level, district, chunk.pack()));
			}
			case FULL -> player.sendOverlayMessage(relayCondition(level, district, chunk.pack()));
			case NOT_KEY_RELAY -> player.sendOverlayMessage(Component.translatable("message.ashfall.grid.relay_not_linked",
					GridDistricts.name(district)).withStyle(ChatFormatting.GRAY));
			default -> player.sendOverlayMessage(Component.translatable("message.ashfall.grid.repair_not_won").withStyle(ChatFormatting.GRAY));
		}
		return result[0];
	}

	/**
	 * Whether the key relay in {@code relayChunk} of {@code district} is worn and a repair would take: the district is
	 * reclaimed (or blacked out) and the relay is linked and below full condition. Checks a copy, nothing changes.
	 */
	public static boolean relayNeedsRepair(ServerLevel level, String district, long relayChunk) {
		DistrictRecord current = GridState.get(level.getServer()).record(district);
		return current != null && DistrictLoop.repairRelay(copyForCheck(current), relayChunk,
				GridDistricts.keyRelayChunks(district)) == DistrictLoop.Result.COMPLETED;
	}

	/**
	 * The same repair as {@link #repairRelay} without a player: a survivor Mechanic's (it pays the Wire Spool and Duct
	 * Tape from its Supply Lockers itself, and only when this returns COMPLETED).
	 */
	public static DistrictLoop.Result repairRelayChunk(ServerLevel level, String district, long relayChunk) {
		if (GridState.get(level.getServer()).record(district) == null) {
			return DistrictLoop.Result.WRONG_STATE;
		}
		Set<Long> keys = GridDistricts.keyRelayChunks(district);
		DistrictLoop.Result[] result = new DistrictLoop.Result[1];
		GridState.get(level.getServer()).update(level, district, rec -> result[0] = DistrictLoop.repairRelay(rec, relayChunk, keys));
		if (result[0] == DistrictLoop.Result.COMPLETED) {
			GridDecay.refresh(level, district);
		}
		return result[0];
	}

	/** A check-only copy: the decision whether tape is needed must not touch the saved record. */
	private static DistrictRecord copyForCheck(DistrictRecord r) {
		DistrictRecord copy = new DistrictRecord();
		copy.state = r.state;
		copy.linked.addAll(r.linked);
		copy.relayCondition.putAll(r.relayCondition);
		return copy;
	}

	private static ItemStack findTape(ServerPlayer player) {
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(xyz.toesinthesand.ashfall.salvage.item.SalvageItems.DUCT_TAPE)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** "Relay: 64% - Riverside 3/3 relays working", for the action bar. */
	static Component relayCondition(ServerLevel level, String district, long chunk) {
		DistrictRecord r = GridState.get(level.getServer()).record(district);
		Set<Long> keys = GridDistricts.keyRelayChunks(district);
		int condition = r == null ? DistrictRecord.FULL_CONDITION : r.condition(chunk);
		int working = r == null ? 0 : DistrictLoop.workingRelays(r, keys);
		int required = GridDistricts.settings(district).rules().relaysRequired(keys);
		ChatFormatting color = condition <= 0 ? ChatFormatting.RED : condition <= DecaySettings.get().warnBelow()
				? ChatFormatting.YELLOW : ChatFormatting.GREEN;
		return Component.translatable("message.ashfall.grid.relay_condition", condition, GridDistricts.name(district), working, required)
				.withStyle(color);
	}

	/** Empty-handed use: the core's status in chat. */
	public static void status(ServerLevel level, BlockPos pos, ServerPlayer player) {
		Optional<String> id = districtOfCore(level, pos);
		if (id.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.ashfall.grid.off_grid").withStyle(ChatFormatting.GRAY));
			return;
		}
		player.sendSystemMessage(statusLine(level, id.get()));
		if (GridState.get(level.getServer()).isReclaimed(id.get())) {
			xyz.toesinthesand.ashfall.arms.travel.Travel.offer(player);
		}
	}

	/** One line describing where a district stands (also used by {@code /ashfall grid status}). */
	public static Component statusLine(ServerLevel level, String district) {
		GridState grid = GridState.get(level.getServer());
		DistrictRecord r = grid.record(district);
		GridSettings settings = GridDistricts.settings(district);
		LoopRules rules = settings.rules();
		Component name = GridDistricts.name(district);
		DistrictState st = r == null ? DistrictState.DARK : r.state;
		Set<Long> keySet = GridDistricts.keyRelayChunks(district);
		int keys = rules.relaysRequired(keySet);
		int linked = r == null ? 0 : LoopRules.linkedCount(r.linked, keySet);
		return switch (st) {
			case DARK -> Component.translatable("status.ashfall.grid.dark", name, r == null ? 0 : r.couplers, rules.couplers());
			case REPAIRED -> r.energizing(rules)
					? Component.translatable("status.ashfall.grid.energizing", name, r.holdTicks * 100 / Math.max(1, rules.holdTicks()),
					linked, keys)
					: Component.translatable("status.ashfall.grid.repaired", name, r.cells, rules.cells(), linked, keys);
			case ENERGIZED -> Component.translatable("status.ashfall.grid.energized", name);
			case SIEGE -> Component.translatable("status.ashfall.grid.siege", name, r.coreHp,
					Math.max(r.coreHp, GridSiege.coreHp(level, district)), r.wavesSpawned);
			case RECLAIMED -> r.flickerSinceDay != DistrictRecord.UNSET
					? Component.translatable("status.ashfall.grid.flickering", name, DistrictLoop.workingRelays(r, keySet), keys,
					DistrictLoop.flickerDaysLeft(r, DecaySettings.get(), xyz.toesinthesand.ashfall.director.spawn.DirectorDay.today(level)))
					: Component.translatable("status.ashfall.grid.reclaimed", name, r.reclaimedAtDay);
			case BROKEN -> Component.translatable("status.ashfall.grid.broken", name, r.couplers, DistrictLoop.reRepairCouplers(rules));
			case BLACKOUT -> Component.translatable("status.ashfall.grid.blackout", name, DistrictLoop.workingRelays(r, keySet), keys);
		};
	}

	static Component stateName(DistrictState state) {
		return Component.translatable("district_state.ashfall." + state.id());
	}

	/** A chat line to every overworld player: grid progress is shared, so everyone hears about it. */
	static void broadcast(ServerLevel level, Component message) {
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
			p.sendSystemMessage(message);
		}
	}
}
