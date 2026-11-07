package xyz.toesinthesand.ashfall.director.haven;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * After a death the player chooses where to come back: their Bunker Core, the nearest safe zone, or the world
 * spawn (their bed, if they set one).
 * <p>
 * <b>Why a chat prompt and not a screen.</b> A clickable chat line needs no client code, works on a vanilla
 * client and on a dedicated server alike, and cannot leave a player stuck behind a screen that failed to open.
 * <b>Why the default is applied at once.</b> The player is moved to the default place the moment they
 * respawn (Bunker Core, else their own bed, else the nearest safe zone, else the world spawn; see
 * {@link RespawnOption#pickDefault}) and may then click another option within
 * {@link HavenSettings#respawnChoiceSeconds}. Nobody waits in limbo at the world spawn, and a player who
 * ignores chat gets the sensible choice with no timer to sit through.
 * <p>
 * <b>Respawn protection.</b> For {@link HavenSettings#respawnProtectionSeconds} after a respawn (restarted by a
 * choice teleport) the player has Resistance V and zombies cannot target them (see
 * {@code MobTargetMixin}). Hurting anything ends it at once.
 */
public final class RespawnChoice {
	/** Amplifier 4 = Resistance V, which blocks all damage Resistance can block. */
	private static final int RESISTANCE_AMPLIFIER = 4;
	/** Radius in which zombies already chasing a respawned player lose them. */
	private static final double DROP_TARGET_RADIUS = 64;

	private static final ProtectionTimers TIMERS = new ProtectionTimers();
	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	/** A respawned player's options, valid until {@code expires}. */
	private record Pending(Map<RespawnOption, Target> options, long expires) {
	}

	/** A place to put a player. */
	public record Target(ResourceKey<Level> dimension, BlockPos pos, Component label) {
	}

	private RespawnChoice() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			TIMERS.clear();
			PENDING.clear();
		});
		ServerPlayerEvents.AFTER_RESPAWN.register(RespawnChoice::afterRespawn);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(RespawnChoice::onDamage);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
			TIMERS.cancel(handler.player.getUUID(), s.getTickCount());
			PENDING.remove(handler.player.getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (s.getTickCount() % 200 == 0) {
				long now = s.getTickCount();
				TIMERS.purge(now);
				PENDING.values().removeIf(p -> now >= p.expires);
			}
		});
	}

	static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
		var respawn = Commands.literal("respawn");
		for (RespawnOption option : RespawnOption.values()) {
			respawn.then(Commands.literal(option.arg()).executes(c -> choose(c, option)));
		}
		dispatcher.register(Commands.literal("ashfall").then(respawn));
	}

	private static void afterRespawn(ServerPlayer oldPlayer, ServerPlayer player, boolean alive) {
		if (alive) {
			return; // leaving the End, not a death
		}
		MinecraftServer srv = player.level().getServer();
		Map<RespawnOption, Target> options = options(srv, player, oldPlayer.level().dimension(), oldPlayer.blockPosition());
		RespawnOption choice = RespawnOption.pickDefault(options.containsKey(RespawnOption.BUNKER),
				player.getRespawnConfig() != null, options.containsKey(RespawnOption.SAFE_ZONE));
		Target target = options.get(choice);
		if (target != null && choice != RespawnOption.SPAWN) {
			moveTo(player, target);
		}
		HavenSettings settings = HavenSettings.get();
		long now = srv.getTickCount();
		if (settings.respawnChoiceTicks() > 0 && options.size() > 1) {
			PENDING.put(player.getUUID(), new Pending(options, now + settings.respawnChoiceTicks()));
			player.sendSystemMessage(prompt(options, choice, settings.respawnChoiceSeconds()));
		}
		protect(player, settings.respawnProtectionTicks());
	}

	/**
	 * The options for a player who died at {@code deathPos}: their Bunker Core if they have one, the safe zone
	 * nearest the death spot, and always the spot vanilla respawned them at (bed or world spawn).
	 */
	public static Map<RespawnOption, Target> options(MinecraftServer srv, ServerPlayer player, ResourceKey<Level> deathDim, BlockPos deathPos) {
		Map<RespawnOption, Target> options = new EnumMap<>(RespawnOption.class);
		BunkerCores.forPlayer(srv, player.getUUID()).ifPresent(core -> {
			ServerLevel level = srv.getLevel(core.dimension());
			if (level != null) {
				options.put(RespawnOption.BUNKER, new Target(core.dimension(), BunkerCores.respawnSpot(level, core.pos()),
						Component.translatable("message.ashfall.respawn.option.bunker")));
			}
		});
		Havens.nearestSafeZone(srv, deathDim, deathPos).ifPresent(e -> {
			ServerLevel level = srv.getLevel(e.getValue().dimension());
			if (level != null) {
				options.put(RespawnOption.SAFE_ZONE, new Target(e.getValue().dimension(), Havens.respawnPoint(level, e.getValue()),
						Component.translatable("message.ashfall.respawn.option.safe_zone", e.getValue().name())));
			}
		});
		options.put(RespawnOption.SPAWN, new Target(player.level().dimension(), player.blockPosition(),
				Component.translatable("message.ashfall.respawn.option.spawn")));
		return options;
	}

	private static Component prompt(Map<RespawnOption, Target> options, RespawnOption current, int seconds) {
		MutableComponent line = Component.translatable("message.ashfall.respawn.prompt", options.get(current).label(), seconds)
				.withStyle(ChatFormatting.GRAY);
		for (Map.Entry<RespawnOption, Target> e : options.entrySet()) {
			if (e.getKey() == current) {
				continue;
			}
			String command = "/ashfall respawn " + e.getKey().arg();
			line.append(" ").append(Component.literal("[").append(e.getValue().label()).append("]")
					.withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withUnderlined(true)
							.withClickEvent(new ClickEvent.RunCommand(command))
							.withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.ashfall.respawn.hover", e.getValue().label())))));
		}
		return line;
	}

	private static int choose(CommandContext<CommandSourceStack> ctx, RespawnOption option) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		long now = ctx.getSource().getServer().getTickCount();
		Pending pending = PENDING.get(player.getUUID());
		if (pending == null || now >= pending.expires) {
			PENDING.remove(player.getUUID());
			ctx.getSource().sendFailure(Component.translatable("message.ashfall.respawn.expired"));
			return 0;
		}
		Target target = pending.options.get(option);
		if (target == null) {
			ctx.getSource().sendFailure(Component.translatable("message.ashfall.respawn.unavailable"));
			return 0;
		}
		PENDING.remove(player.getUUID()); // one change per death: no hopping between havens
		moveTo(player, target);
		protect(player, HavenSettings.get().respawnProtectionTicks());
		ctx.getSource().sendSuccess(() -> Component.translatable("message.ashfall.respawn.moved", target.label()), false);
		return 1;
	}

	private static void moveTo(ServerPlayer player, Target target) {
		ServerLevel level = player.level().getServer().getLevel(target.dimension());
		if (level == null) {
			return;
		}
		BlockPos pos = target.pos();
		player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
	}

	/** Starts (or restarts) protection: the timer, Resistance V for the same time, and zombies drop their target. */
	public static void protect(ServerPlayer player, int ticks) {
		if (ticks <= 0) {
			return;
		}
		ServerLevel level = player.level();
		TIMERS.start(player.getUUID(), level.getServer().getTickCount(), ticks);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks, RESISTANCE_AMPLIFIER, false, true));
		AABB box = player.getBoundingBox().inflate(DROP_TARGET_RADIUS);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.getTarget() == player && affectedBy(m))) {
			mob.setTarget(null);
		}
	}

	/** Ends protection early and removes the Resistance it gave (never a Resistance from another source). */
	public static void cancel(ServerPlayer player) {
		long now = player.level().getServer().getTickCount();
		int left = TIMERS.remaining(player.getUUID(), now);
		if (!TIMERS.cancel(player.getUUID(), now)) {
			return;
		}
		MobEffectInstance effect = player.getEffect(MobEffects.RESISTANCE);
		if (effect != null && effect.getAmplifier() == RESISTANCE_AMPLIFIER && effect.getDuration() <= left + 1) {
			player.removeEffect(MobEffects.RESISTANCE);
		}
	}

	/** Whether {@code player} is under respawn protection right now. */
	public static boolean isProtected(ServerPlayer player) {
		return TIMERS.isProtected(player.getUUID(), player.level().getServer().getTickCount());
	}

	/** Ticks of protection left for {@code player}. */
	public static int remaining(ServerPlayer player) {
		return TIMERS.remaining(player.getUUID(), player.level().getServer().getTickCount());
	}

	/** Which mobs respect protection: every zombie (Director, vanilla and horde zombies alike) and anything the Director spawned. */
	public static boolean affectedBy(Mob mob) {
		return mob instanceof Zombie || mob.hasAttached(DirectorSpawns.TAG);
	}

	/** Protection ends the moment the protected player hurts anything (melee, blaster bolt, arrow). */
	private static boolean onDamage(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim && isProtected(attacker)) {
			cancel(attacker);
		}
		return true;
	}

	/** Test hook: the pending choice for a player, if any. */
	public static Optional<Map<RespawnOption, Target>> pendingForTesting(UUID player) {
		Pending pending = PENDING.get(player);
		return pending == null ? Optional.empty() : Optional.of(pending.options);
	}
}
