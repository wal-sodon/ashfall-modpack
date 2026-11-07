package xyz.toesinthesand.ashfall.director.boss;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.Optional;

/**
 * {@code /ashfall boss list|spawn <lair> [here]|unseal <lair>} (permission level 2).
 * <ul>
 * <li>{@code list}: every lair, its status, seal and boss position, so an admin can see the story's progress.</li>
 * <li>{@code spawn}: wakes a lair now, at its spawn point (or the caller's position with {@code here}), ignoring
 * the spawn-once rule and the seal. A boss of that lair that is still alive and loaded is removed first, so
 * there is never more than one.</li>
 * <li>{@code unseal}: opens a sealed lair, as the grid event will ({@link BossLairs#unseal}).</li>
 * </ul>
 */
public final class BossCommand {
	private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
			id -> Component.translatable("commands.ashfall.boss.unknown", String.valueOf(id)));
	private static final DynamicCommandExceptionType NO_SPOT = new DynamicCommandExceptionType(
			id -> Component.translatable("commands.ashfall.boss.no_spot", String.valueOf(id)));
	private static final DynamicCommandExceptionType FAILED = new DynamicCommandExceptionType(
			id -> Component.translatable("commands.ashfall.boss.failed", String.valueOf(id)));
	private static final SuggestionProvider<CommandSourceStack> LAIRS =
			(ctx, builder) -> SharedSuggestionProvider.suggestResource(BossLairs.all().keySet(), builder);

	private BossCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		// Permission on "boss", not on the shared root (see DirectorCommand for why).
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("boss")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("list").executes(BossCommand::list))
						.then(Commands.literal("spawn")
								.then(Commands.argument("lair", IdentifierArgument.id()).suggests(LAIRS)
										.executes(c -> spawn(c, false))
										.then(Commands.literal("here").executes(c -> spawn(c, true)))))
						.then(Commands.literal("unseal")
								.then(Commands.argument("lair", IdentifierArgument.id()).suggests(LAIRS)
										.executes(BossCommand::unseal)))));
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getServer().overworld();
		BossState state = BossState.get(source.getServer());
		Map<Identifier, BossLair> lairs = BossLairs.all();
		source.sendSuccess(() -> Component.translatable("commands.ashfall.boss.list.header", lairs.size()), false);
		for (Map.Entry<Identifier, BossLair> e : lairs.entrySet()) {
			BossState.Lair record = state.lair(e.getKey().toString());
			LivingEntity boss = BossLairs.boss(level, record);
			String where = record.spawnPos().map(BlockPos::toShortString).orElse("?");
			String health = boss == null ? "-" : String.format("%.0f/%.0f", boss.getHealth(), boss.getMaxHealth());
			boolean sealed = BossLairs.sealed(source.getServer(), e.getKey(), e.getValue());
			source.sendSuccess(() -> Component.translatable("commands.ashfall.boss.list.entry", e.getKey().toString(),
					Component.translatable(e.getValue().name()), record.status().name().toLowerCase(java.util.Locale.ROOT),
					sealed ? Component.translatable("commands.ashfall.boss.sealed") : Component.translatable("commands.ashfall.boss.open"),
					where, health, record.kills()), false);
		}
		return lairs.size();
	}

	private static BossLair lair(CommandContext<CommandSourceStack> ctx, Identifier id) throws CommandSyntaxException {
		BossLair lair = BossLairs.get(id);
		if (lair == null) {
			throw UNKNOWN.create(id);
		}
		return lair;
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx, boolean here) throws CommandSyntaxException {
		Identifier id = IdentifierArgument.getId(ctx, "lair");
		BossLair lair = lair(ctx, id);
		ServerLevel level = ctx.getSource().getServer().overworld();
		Optional<BlockPos> at = here ? Optional.of(BlockPos.containing(ctx.getSource().getPosition()))
				: BossLairs.resolveSpawn(level, id, lair);
		if (at.isEmpty()) {
			throw NO_SPOT.create(id);
		}
		LivingEntity old = BossLairs.boss(level, BossState.get(level.getServer()).lair(id.toString()));
		if (old != null) {
			old.discard();
		}
		Mob boss = BossLairs.spawnBoss(level, id, lair, at.get());
		if (boss == null) {
			throw FAILED.create(id);
		}
		BlockPos pos = at.get();
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.boss.spawned", id.toString(), pos.toShortString()), true);
		return 1;
	}

	private static int unseal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Identifier id = IdentifierArgument.getId(ctx, "lair");
		lair(ctx, id);
		int opened = BossLairs.unseal(ctx.getSource().getServer().overworld(), id);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.boss.unsealed", id.toString(), opened), true);
		return Math.max(1, opened);
	}
}
