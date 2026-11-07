package xyz.toesinthesand.ashfall.arms.finale;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import team.reborn.energy.api.base.SimpleEnergyItem;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.arms.finale.FinaleLogic.Result;
import xyz.toesinthesand.ashfall.arms.finale.FinaleRun.Status;
import xyz.toesinthesand.ashfall.arms.grid.GridState;

/**
 * What a player does at the Uplink Socket with their hands: seat the Uplink, feed a cell, patch with a coupler, read
 * the run. Every action applies {@link FinaleLogic} to the socket's instance and answers on the action bar.
 */
public final class FinaleActions {
	private FinaleActions() {
	}

	/**
	 * Seats the Grid Uplink and starts the Black Start. The world's run needs the city at Grid 80% (the hull is only
	 * open from then on anyway, so this guards admin-placed sockets) and only one socket at a time.
	 */
	public static Result seat(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
		FinaleInstance instance = FinaleController.forSocket(level, pos);
		FinaleRun run = instance.run();
		boolean test = FinaleController.isTest(pos);
		if (!test && !HullSite.shouldOpen(GridState.get(level.getServer()).gridPercent())) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.finale.needs_grid", GridState.get(level.getServer()).gridPercent())
					.withStyle(ChatFormatting.GRAY));
			return Result.WRONG_STATE;
		}
		if (run.status != Status.IDLE) {
			player.sendOverlayMessage(Component.translatable(run.socket == pos.asLong() ? "message.ashfall.finale.already_seated"
					: "message.ashfall.finale.elsewhere").withStyle(ChatFormatting.GRAY));
			return Result.WRONG_STATE;
		}
		if (run.standby == pos.asLong()) {
			// the replay's Uplink is still in this socket: power it up and keep the new one in hand
			return restart(level, pos, player);
		}
		Result result = FinaleLogic.seat(run, instance.s(), pos.asLong());
		if (result == Result.ACCEPTED) {
			stack.consume(1, player);
			run.participants.add(player.getUUID());
			level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0f, 0.5f);
			FinaleController.enterPhase(level, instance);
			FinaleController.showSocket(level, pos, run);
			instance.save().run();
			AshfallArms.LOGGER.info("Finale: {} seated the Grid Uplink at {}; the Black Start begins", player.getName().getString(),
					pos.toShortString());
		}
		return result;
	}

	/**
	 * Powers up the Uplink the replay reset left in this socket ({@link FinaleRun#standby}) and starts the Black Start
	 * again, with the same Grid gate as seating a new one. Empty-handed use of a socket in standby lands here.
	 */
	public static Result restart(ServerLevel level, BlockPos pos, ServerPlayer player) {
		FinaleInstance instance = FinaleController.forSocket(level, pos);
		FinaleRun run = instance.run();
		if (!FinaleController.isTest(pos) && !HullSite.shouldOpen(GridState.get(level.getServer()).gridPercent())) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.finale.needs_grid", GridState.get(level.getServer()).gridPercent())
					.withStyle(ChatFormatting.GRAY));
			return Result.WRONG_STATE;
		}
		Result result = FinaleLogic.restart(run, instance.s(), pos.asLong());
		if (result == Result.ACCEPTED) {
			run.participants.add(player.getUUID());
			level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0f, 0.5f);
			FinaleController.enterPhase(level, instance);
			FinaleController.showSocket(level, pos, run);
			instance.save().run();
			AshfallArms.LOGGER.info("Finale: {} powered up the waiting Grid Uplink at {}; the Black Start begins again",
					player.getName().getString(), pos.toShortString());
		}
		return result;
	}

	/** One full Energy Cell into the Uplink's buffer (consumed unless creative). */
	public static Result feedCell(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
		FinaleInstance instance = FinaleController.forSocket(level, pos);
		FinaleRun run = instance.run();
		FinaleSettings s = instance.s();
		if (!run.active() || run.socket != pos.asLong()) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.finale.no_uplink").withStyle(ChatFormatting.GRAY));
			return Result.WRONG_STATE;
		}
		if (SimpleEnergyItem.getStoredEnergyUnchecked(stack) < s.buffer().cellMinEnergy()) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.grid.cell_flat", s.buffer().cellMinEnergy()));
			return Result.WRONG_STATE;
		}
		Result result = FinaleLogic.feedCell(run, s);
		switch (result) {
			case ACCEPTED, RESUMED -> {
				stack.consume(1, player);
				run.participants.add(player.getUUID());
				level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 0.8f);
				player.sendOverlayMessage(Component.translatable("message.ashfall.finale.cell", run.bufferTicks / 20));
				if (result == Result.RESUMED) {
					resumed(level, instance, pos);
				}
			}
			case FULL -> player.sendOverlayMessage(Component.translatable("message.ashfall.finale.buffer_full", s.buffer().maxSeconds()));
			default -> {
			}
		}
		instance.save().run();
		return result;
	}

	/** One Grid Coupler patched into the Uplink (consumed unless creative). */
	public static Result patch(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
		FinaleInstance instance = FinaleController.forSocket(level, pos);
		FinaleRun run = instance.run();
		FinaleSettings s = instance.s();
		if (!run.active() || run.socket != pos.asLong()) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.finale.no_uplink").withStyle(ChatFormatting.GRAY));
			return Result.WRONG_STATE;
		}
		int before = run.uplinkHp;
		Result result = FinaleLogic.patch(run, s);
		if (run.uplinkHp > before) {
			stack.consume(1, player);
			run.participants.add(player.getUUID());
			level.playSound(null, pos, SoundEvents.HEAVY_CORE_PLACE, SoundSource.BLOCKS, 1.0f, 1.2f);
		}
		switch (result) {
			case ACCEPTED, RESUMED -> player.sendOverlayMessage(Component.translatable("message.ashfall.finale.patched", run.uplinkHp,
					s.uplink().hp()));
			case FULL -> player.sendOverlayMessage(Component.translatable("message.ashfall.finale.uplink_full"));
			default -> {
			}
		}
		if (result == Result.RESUMED) {
			resumed(level, instance, pos);
		}
		instance.save().run();
		return result;
	}

	/** A paused run came back: sound, socket light, message and log. Package-private for the console's feed and patch. */
	static void resumed(ServerLevel level, FinaleInstance instance, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0f, 0.6f);
		FinaleController.showSocket(level, pos, instance.run());
		FinaleController.broadcastNear(level, pos, instance.s().presenceRadius() * 3,
				Component.translatable("message.ashfall.finale.resumed").withStyle(ChatFormatting.GREEN));
		AshfallArms.LOGGER.info("Finale: resumed in the {} phase", instance.run().phase.getSerializedName());
	}

	/** Empty-handed use: the run's status in chat. */
	public static void status(ServerLevel level, BlockPos pos, ServerPlayer player) {
		FinaleInstance instance = FinaleController.forSocket(level, pos);
		player.sendSystemMessage(statusLine(instance));
	}

	/** One line describing the run (also the first line of {@code /ashfall finale status}). */
	public static Component statusLine(FinaleInstance instance) {
		FinaleRun run = instance.run();
		FinaleSettings s = instance.s();
		return switch (run.status) {
			case IDLE -> Component.translatable(run.standby != FinaleRun.NONE ? "status.ashfall.finale.standby" : "status.ashfall.finale.idle");
			case COMPLETE -> Component.translatable("status.ashfall.finale.complete");
			case PAUSED -> Component.translatable("status.ashfall.finale.paused", Component.translatable(
					"phase.ashfall.finale." + run.phase.getSerializedName()), Component.translatable(
					"pause.ashfall.finale." + run.pause.getSerializedName()), run.uplinkHp, s.uplink().hp(), FinaleLogic.resumeHp(s),
					run.bufferTicks / 20);
			case RUNNING -> switch (run.phase) {
				case SURGE -> Component.translatable("status.ashfall.finale.surge", run.phaseTicks * 100 / Math.max(1, s.surgeTicks()),
						run.bufferTicks / 20, run.uplinkHp, s.uplink().hp());
				case FEEDBACK -> Component.translatable("status.ashfall.finale.feedback",
						run.breakers.stream().filter(b -> b.closed).count(), run.breakers.size(), run.uplinkHp, s.uplink().hp());
				case HEART -> Component.translatable("status.ashfall.finale.heart", run.pylonsBroken, run.pylons.size(), run.uplinkHp,
						s.uplink().hp());
			};
		};
	}
}
