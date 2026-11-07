package xyz.toesinthesand.ashfall.arms.radio;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code /radiojob accept <n>|status} for everyone (the Shortwave Set's [Accept] buttons run the first), and
 * {@code /ashfall radiojob offer|here <type>|cancel} for admins testing jobs by hand.
 */
public final class RadioJobCommand {
	private RadioJobCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("radiojob")
				.then(Commands.literal("accept").then(Commands.argument("n", IntegerArgumentType.integer(1, RadioJobs.OFFERS)).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Component msg = RadioJobs.accept(ctx.getSource().getServer().overworld(), player, IntegerArgumentType.getInteger(ctx, "n") - 1);
					if (!msg.getString().isEmpty()) {
						ctx.getSource().sendSuccess(() -> msg, false);
					}
					return 1;
				})))
				.then(Commands.literal("status").executes(ctx -> {
					RadioJobs.listen(ctx.getSource().getPlayerOrException());
					return 1;
				})));

		var admin = Commands.literal("radiojob").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("offer").executes(ctx -> {
					ServerLevel level = ctx.getSource().getServer().overworld();
					List<RadioJob> offers = RadioJobs.makeOffers(level, BlockPos.containing(ctx.getSource().getPosition()), level.getRandom());
					RadioJobState state = RadioJobState.get(ctx.getSource().getServer());
					state.setOffers(offers, state.offerDay());
					ctx.getSource().sendSuccess(() -> Component.literal(offers.size() + " radio call(s) around you; use a Shortwave Set or /radiojob status"), true);
					return offers.size();
				}))
				.then(Commands.literal("cancel").executes(ctx -> {
					RadioJobs.cancel(ctx.getSource().getServer().overworld());
					ctx.getSource().sendSuccess(() -> Component.literal("Radio job cancelled"), true);
					return 1;
				}));
		for (RadioJob.Type type : RadioJob.Type.values()) {
			// A job of this type on the spot 20 blocks east, already accepted: for trying each one out on any world.
			admin.then(Commands.literal("here").then(Commands.literal(type.id()).executes(ctx -> {
				ServerLevel level = ctx.getSource().getServer().overworld();
				BlockPos pos = BlockPos.containing(ctx.getSource().getPosition()).east(20);
				RadioJob job = RadioJob.offer(type, 0L, type.profiles().iterator().next(), "downtown", 3, pos.getX(), pos.getZ())
						.accepted(level.getGameTime() + RadioJobs.DEADLINE_TICKS);
				RadioJobs.start(level, job.at(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos)));
				ctx.getSource().sendSuccess(() -> Component.literal("Radio job (" + type.id() + ") 20 blocks east"), true);
				return 1;
			})));
		}
		dispatcher.register(Commands.literal("ashfall").then(admin));
	}
}
