package xyz.toesinthesand.ashfall.director.haven;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Beds only work at home (10.3.1): a player can sleep only inside their own Bunker Core's claim, or their party's
 * ({@link BunkerCores#forPlayer}), or in an open safe zone (a reclaimed substation compound, {@link SafeZone#open}).
 * Anywhere else, the safehouse included, the bed refuses before vanilla sets the respawn
 * point, so a bed out in the city can't be used as a spawn point either. A player with no core can't sleep at all.
 * Traveler's Backpack sleeping bags are covered too: they lie down through vanilla's {@code startSleepInBed}, which
 * asks this veto first, and their own "sleeping bag sets spawn" option is off in the pack (its default).
 * Creative and spectator players are exempt. {@code /gamerule ashfall:bunker_beds_only false} turns the rule off.
 */
public final class BunkerBeds {
	public static final GameRule<Boolean> ENABLED = GameRuleBuilder.forBoolean(true)
			.category(GameRuleCategory.PLAYER)
			.buildAndRegister(Identifier.fromNamespaceAndPath("ashfall", "bunker_beds_only"));

	private BunkerBeds() {
	}

	static void init() {
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) ->
				player instanceof ServerPlayer serverPlayer ? problem(serverPlayer, pos) : null);
	}

	/** Why {@code player} may not sleep in the bed at {@code pos}, or null when it is inside their core's claim. */
	public static Player.@Nullable BedSleepingProblem problem(ServerPlayer player, BlockPos pos) {
		if (player.isCreative() || player.isSpectator() || !(player.level() instanceof ServerLevel level)
				|| !level.getGameRules().get(ENABLED)) {
			return null;
		}
		return atHome(level, player, pos) || Havens.inOpenZone(level, pos) ? null
				: new Player.BedSleepingProblem(Component.translatable("message.ashfall.no_sleep.bunker"));
	}

	/** Whether {@code pos} in {@code level} lies inside the claim of {@code player}'s (or their party's) Bunker Core. */
	public static boolean atHome(ServerLevel level, ServerPlayer player, BlockPos pos) {
		Optional<BunkerCoreRecord> core = BunkerCores.forPlayer(level.getServer(), player.getUUID());
		return core.isPresent() && core.get().dimension() == level.dimension()
				&& core.get().area().contains(pos.getX(), pos.getZ());
	}
}
