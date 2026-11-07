package xyz.toesinthesand.ashfall.arms.survivor;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /survivor <uuid> follow|stay|home|settle|role <role>} for everyone: the buttons {@link SurvivorTalk} sends run
 * these. [Stay] given inside the leader's Bunker Core area to a survivor with no home there settles it, like [Settle here].
 * Each checks the player is within {@link Survivors#ORDER_RANGE} and may give orders ({@link Survivors#mayOrder}); the
 * first order to a survivor with no leader makes the player its leader (a holdout refuses a player whose group is full,
 * {@link SurvivorHoldouts#joinRefusal}).
 * <p>
 * Traders refuse every one of them.
 * <p>
 * {@code /ashfall survivor spawn [role]} for admins: a survivor at your feet, following you (refused at the world cap).
 * {@code /ashfall survivor trader <district>}: that district's trader at your feet (its post), replacing the one before.
 */
public final class SurvivorCommand {
	private static final SuggestionProvider<CommandSourceStack> ROLES = (ctx, builder) -> SharedSuggestionProvider.suggest(
			Arrays.stream(SurvivorRole.values()).filter(SurvivorRole::assignable).map(SurvivorRole::id), builder);
	private static final SuggestionProvider<CommandSourceStack> TRADER_DISTRICTS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(TraderStock.districts(), builder);

	private SurvivorCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("survivor")
				.then(Commands.argument("id", UuidArgument.uuid())
						.then(Commands.literal("follow").executes(ctx -> order(ctx, SurvivorMode.FOLLOW)))
						.then(Commands.literal("stay").executes(ctx -> order(ctx, SurvivorMode.STAY)))
						.then(Commands.literal("home").executes(ctx -> order(ctx, SurvivorMode.HOME)))
						.then(Commands.literal("settle").executes(SurvivorCommand::settle))
						.then(Commands.literal("role").then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES)
								.executes(SurvivorCommand::role)))));

		dispatcher.register(Commands.literal("ashfall").then(Commands.literal("survivor")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("spawn")
						.executes(ctx -> spawn(ctx.getSource(), SurvivorRole.SETTLER))
						.then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES).executes(ctx -> {
							SurvivorRole role = SurvivorRole.parse(StringArgumentType.getString(ctx, "role"));
							if (role == null || !role.assignable()) {
								ctx.getSource().sendFailure(Component.literal("Unknown or not yet assignable role"));
								return 0;
							}
							return spawn(ctx.getSource(), role);
						})))
				.then(Commands.literal("trader")
						.then(Commands.argument("district", StringArgumentType.word()).suggests(TRADER_DISTRICTS)
								.executes(ctx -> trader(ctx.getSource(), StringArgumentType.getString(ctx, "district")))))));
	}

	/** The survivor the command names, if the player is close enough and may give it orders (else says why). */
	private static Optional<Survivor> target(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		UUID id = UuidArgument.getUuid(ctx, "id");
		Entity entity = player.level().getEntity(id);
		if (!(entity instanceof Survivor survivor) || !survivor.isAlive()) {
			player.sendSystemMessage(Component.translatable("message.ashfall.survivor.gone"));
			return Optional.empty();
		}
		if (survivor.away()) {
			player.sendSystemMessage(SurvivorTalk.line(survivor, "role.away"));
			return Optional.empty();
		}
		if (survivor.isTrader()) {
			player.sendSystemMessage(SurvivorTalk.line(survivor, "trader.no_orders"));
			return Optional.empty();
		}
		if (player.distanceToSqr(survivor) > Survivors.ORDER_RANGE * Survivors.ORDER_RANGE) {
			player.sendSystemMessage(Component.translatable("message.ashfall.survivor.too_far", survivor.survivorName()));
			return Optional.empty();
		}
		if (!Survivors.mayOrder(player, survivor)) {
			SurvivorTalk.talk(player, survivor);
			return Optional.empty();
		}
		if (survivor.leader() == null) {
			Optional<Component> refusal = SurvivorHoldouts.joinRefusal(player, survivor);
			if (refusal.isPresent()) {
				player.sendSystemMessage(refusal.get());
				return Optional.empty();
			}
			survivor.setLeader(player.getUUID());
		}
		return Optional.of(survivor);
	}

	private static int order(CommandContext<CommandSourceStack> ctx, SurvivorMode mode) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Optional<Survivor> found = target(ctx, player);
		if (found.isEmpty()) {
			return 0;
		}
		Survivor survivor = found.get();
		if (mode == SurvivorMode.STAY && survivor.home() == null && survivor.settleableCore(player.level()).isPresent()) {
			// a full bunker: say so, then just hold the spot
			Survivor.Settle result = survivor.settle(player.level());
			SurvivorTalk.settled(player, survivor, result);
			if (result != Survivor.Settle.FULL) {
				return 1;
			}
		}
		player.sendSystemMessage(SurvivorTalk.ordered(survivor, mode));
		if (mode == SurvivorMode.HOME && survivor.home() == null) {
			return 0;
		}
		survivor.setMode(mode);
		return 1;
	}

	private static int settle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Optional<Survivor> found = target(ctx, player);
		if (found.isEmpty()) {
			return 0;
		}
		Survivor.Settle result = found.get().settle(player.level());
		SurvivorTalk.settled(player, found.get(), result);
		return result == Survivor.Settle.SETTLED || result == Survivor.Settle.ALREADY ? 1 : 0;
	}

	private static int role(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		SurvivorRole role = SurvivorRole.parse(StringArgumentType.getString(ctx, "role"));
		if (role == null) {
			player.sendSystemMessage(Component.translatable("message.ashfall.survivor.no_role"));
			return 0;
		}
		Optional<Survivor> found = target(ctx, player);
		if (found.isEmpty()) {
			return 0;
		}
		Survivor survivor = found.get();
		Optional<Component> refusal = Survivors.roleChangeRefusal(survivor, role);
		if (refusal.isPresent()) {
			player.sendSystemMessage(refusal.get());
			return 0;
		}
		player.sendSystemMessage(SurvivorTalk.roleChanged(survivor, role, survivor.changeRole(role)));
		return 1;
	}

	private static int spawn(CommandSourceStack source, SurvivorRole role) {
		ServerLevel level = source.getLevel();
		ServerPlayer player = source.getPlayer();
		int world = SurvivorBalance.get().caps().world();
		if (!SurvivorRoster.get(level.getServer()).worldHasRoom()) {
			source.sendFailure(Component.literal("The world already has " + world + " survivors (the world cap)"));
			return 0;
		}
		Survivor survivor = Survivors.spawn(level, BlockPos.containing(source.getPosition()), role, player == null ? null : player.getUUID());
		if (survivor == null) {
			source.sendFailure(Component.literal("Could not spawn a survivor"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned " + survivor.survivorName() + " (" + role.id() + ")"), true);
		return 1;
	}

	private static int trader(CommandSourceStack source, String district) {
		if (TraderStock.of(district).isEmpty()) {
			source.sendFailure(Component.literal("No trader stock for " + district));
			return 0;
		}
		Survivor trader = Traders.spawn(source.getLevel(), district, BlockPos.containing(source.getPosition()));
		if (trader == null) {
			source.sendFailure(Component.literal("Could not spawn a trader"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned " + trader.survivorName() + ", trader for " + district), true);
		return 1;
	}
}
