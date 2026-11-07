package xyz.toesinthesand.ashfall.arms.travel;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import team.reborn.energy.api.base.SimpleEnergyItem;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.item.CellPackItem;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreBlock;
import xyz.toesinthesand.ashfall.director.haven.RespawnChoice;
import xyz.toesinthesand.ashfall.director.horde.HordeSchedule;
import xyz.toesinthesand.ashfall.director.pets.Dogs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The transit network: fast travel between the safehouse, the party's Bunker Core and reclaimed substations,
 * paid for with Cell Pack energy (see {@link TravelRules}).
 * <p>
 * <b>Why chat lines and not a screen.</b> Same reason as the respawn choice: no client code, and works on a
 * dedicated server. Right-clicking a Bunker Core or a reclaimed Substation Core lists the stops; so does
 * {@code /ashfall travel} anywhere at a stop (the safehouse has no block to click).
 * <p>
 * <b>Why the restrictions.</b> Travel is for getting around, not for escaping: no trips on a horde night or while
 * a District Siege is set or running, none while a zombie is after you, and a warm-up that moving or being hurt
 * cancels. Energy is only taken when the trip goes through.
 */
public final class Travel {
	private static final double MAX_DRIFT_SQR = 1.5 * 1.5;

	/** A trip in its warm-up; a removed player (died, left, changed dimension by other means) cancels it. */
	private record Pending(ServerPlayer player, String nodeId, Vec3 start, long departAt) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private Travel() {
	}

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(TravelRules.Loader.ID, new TravelRules.Loader());
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
		BunkerCoreBlock.setUseHook(Travel::offer);
		ServerTickEvents.END_SERVER_TICK.register(Travel::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0 && PENDING.remove(player.getUUID()) != null) {
				player.sendSystemMessage(Component.translatable("message.ashfall.travel.cancelled.hurt").withStyle(ChatFormatting.RED));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> PENDING.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> PENDING.clear());
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ashfall").then(Commands.literal("travel")
				.executes(c -> {
					offer(c.getSource().getPlayerOrException());
					return 1;
				})
				.then(Commands.argument("stop", StringArgumentType.string()).executes(Travel::start))));
	}

	/** Lists the stops {@code player} can travel to from here, as clickable chat lines. */
	public static void offer(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		List<TravelNodes.Node> nodes = TravelNodes.all(server, player);
		Optional<TravelNodes.Node> here = TravelNodes.here(server, player, nodes);
		if (here.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.ashfall.travel.not_at_stop").withStyle(ChatFormatting.GRAY));
			return;
		}
		TravelRules rules = TravelRules.get();
		long carried = carried(player);
		player.sendSystemMessage(Component.translatable("message.ashfall.travel.header", here.get().name(), carried)
				.withStyle(ChatFormatting.AQUA));
		int listed = 0;
		for (TravelNodes.Node node : nodes) {
			if (node.id().equals(here.get().id())) {
				continue;
			}
			double distance = TravelNodes.distance(here.get(), node);
			int cost = rules.cost(distance, isBunker(here.get()));
			Component where = distance < 0 ? Component.translatable("message.ashfall.travel.far")
					: Component.translatable("message.ashfall.travel.blocks", (int) Math.round(distance));
			boolean affordable = carried >= cost;
			MutableComponent line = Component.literal(" ")
					.append(Component.literal("[").append(node.name()).append("]")
							.withStyle(Style.EMPTY.withColor(affordable ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
									.withUnderlined(affordable)
									.withClickEvent(new ClickEvent.RunCommand("/ashfall travel \"" + node.id() + "\""))
									.withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.ashfall.travel.hover",
											node.name(), cost)))))
					.append(Component.translatable("message.ashfall.travel.line", where, cost).withStyle(ChatFormatting.GRAY));
			player.sendSystemMessage(line);
			listed++;
		}
		if (listed == 0) {
			player.sendSystemMessage(Component.translatable("message.ashfall.travel.none").withStyle(ChatFormatting.GRAY));
		}
	}

	private static int start(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Optional<Component> problem = request(player, StringArgumentType.getString(ctx, "stop"));
		if (problem.isPresent()) {
			ctx.getSource().sendFailure(problem.get());
			return 0;
		}
		return 1;
	}

	/** Starts the warm-up for a trip to stop {@code id}; returns why not when the trip cannot start. */
	public static Optional<Component> request(ServerPlayer player, String id) {
		Optional<Component> problem = check(player, id);
		if (problem.isPresent()) {
			return problem;
		}
		TravelRules rules = TravelRules.get();
		MinecraftServer server = player.level().getServer();
		PENDING.put(player.getUUID(), new Pending(player, id, player.position(), server.getTickCount() + rules.warmupTicks()));
		player.sendSystemMessage(Component.translatable("message.ashfall.travel.warmup",
				Math.max(1, Math.round(rules.warmupTicks() / 20f))).withStyle(ChatFormatting.AQUA));
		player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.4f);
		if (rules.warmupTicks() == 0) {
			tick(server);
		}
		return Optional.empty();
	}

	/** Why {@code player} cannot travel to stop {@code id} right now, or empty when they can. */
	private static Optional<Component> check(ServerPlayer player, String id) {
		MinecraftServer server = player.level().getServer();
		List<TravelNodes.Node> nodes = TravelNodes.all(server, player);
		Optional<TravelNodes.Node> here = TravelNodes.here(server, player, nodes);
		if (here.isEmpty()) {
			return Optional.of(Component.translatable("message.ashfall.travel.not_at_stop"));
		}
		Optional<TravelNodes.Node> to = nodes.stream().filter(n -> n.id().equals(id)).findFirst();
		if (to.isEmpty()) {
			return Optional.of(Component.translatable("message.ashfall.travel.unknown"));
		}
		if (to.get().id().equals(here.get().id())) {
			return Optional.of(Component.translatable("message.ashfall.travel.already_here"));
		}
		ServerLevel overworld = server.overworld();
		if (HordeSchedule.get(server).noSleepAt(overworld.getOverworldClockTime(), overworld.getSeed())) {
			return Optional.of(Component.translatable("message.ashfall.travel.blocked.horde"));
		}
		boolean siege = GridState.get(server).snapshot().values().stream()
				.anyMatch(state -> state == DistrictState.ENERGIZED || state == DistrictState.SIEGE);
		if (siege) {
			return Optional.of(Component.translatable("message.ashfall.travel.blocked.siege"));
		}
		TravelRules rules = TravelRules.get();
		if (rules.threatRadius() > 0 && !player.level().getEntitiesOfClass(Mob.class,
				player.getBoundingBox().inflate(rules.threatRadius()), m -> m instanceof Enemy && m.getTarget() == player).isEmpty()) {
			return Optional.of(Component.translatable("message.ashfall.travel.blocked.threat"));
		}
		int cost = rules.cost(TravelNodes.distance(here.get(), to.get()), isBunker(here.get()));
		long carried = carried(player);
		if (carried < cost) {
			return Optional.of(Component.translatable("message.ashfall.travel.no_charge", cost, carried));
		}
		return Optional.empty();
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (UUID id : new ArrayList<>(PENDING.keySet())) {
			Pending pending = PENDING.get(id);
			ServerPlayer player = pending.player;
			if (player.isRemoved() || !player.isAlive()) {
				PENDING.remove(id);
				continue;
			}
			if (player.position().distanceToSqr(pending.start) > MAX_DRIFT_SQR) {
				PENDING.remove(id);
				player.sendSystemMessage(Component.translatable("message.ashfall.travel.cancelled.moved").withStyle(ChatFormatting.RED));
				continue;
			}
			if (now >= pending.departAt) {
				PENDING.remove(id);
				depart(player, pending.nodeId);
			}
		}
	}

	/** The warm-up is over: check again (things change in three seconds), pay, and go. */
	private static void depart(ServerPlayer player, String nodeId) {
		Optional<Component> problem = check(player, nodeId);
		if (problem.isPresent()) {
			player.sendSystemMessage(problem.get().copy().withStyle(ChatFormatting.RED));
			return;
		}
		MinecraftServer server = player.level().getServer();
		List<TravelNodes.Node> nodes = TravelNodes.all(server, player);
		TravelNodes.Node here = TravelNodes.here(server, player, nodes).orElseThrow();
		TravelNodes.Node to = nodes.stream().filter(n -> n.id().equals(nodeId)).findFirst().orElseThrow();
		ServerLevel level = server.getLevel(to.dimension());
		if (level == null) {
			return;
		}
		int cost = TravelRules.get().cost(TravelNodes.distance(here, to), isBunker(here));
		if (!pay(player, cost)) {
			return;
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.2f);
		BlockPos pos = to.arrive();
		teleport(player, level, pos);
		level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.2f);
		RespawnChoice.protect(player, TravelRules.get().arrivalProtectionTicks());
		player.sendSystemMessage(Component.translatable("message.ashfall.travel.arrived", to.name(), cost).withStyle(ChatFormatting.AQUA));
	}

	/**
	 * Moves the player to {@code pos} in {@code level}, and their dogs with them: tamed, standing dogs within
	 * {@link Dogs#TRAVEL_RADIUS} blocks ({@link Dogs#companions}), picked before the player leaves.
	 */
	public static void teleport(ServerPlayer player, ServerLevel level, BlockPos pos) {
		List<Wolf> dogs = Dogs.companions(player);
		player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
		for (Wolf dog : dogs) {
			dog.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), dog.getYRot(), dog.getXRot(), true);
		}
	}

	private static boolean isBunker(TravelNodes.Node node) {
		return node.id().equals(TravelNodes.BUNKER);
	}

	/** Energy in the loose Cell Packs in {@code player}'s inventory (packs loaded in a blaster do not count). */
	public static long carried(ServerPlayer player) {
		long total = 0;
		for (ItemStack stack : packs(player)) {
			total += SimpleEnergyItem.getStoredEnergyUnchecked(stack);
		}
		return total;
	}

	/** Takes {@code cost} energy from the player's packs, emptiest first; false (and nothing taken) if short. */
	static boolean pay(ServerPlayer player, long cost) {
		List<ItemStack> packs = packs(player);
		long[] stored = new long[packs.size()];
		for (int k = 0; k < stored.length; k++) {
			stored[k] = SimpleEnergyItem.getStoredEnergyUnchecked(packs.get(k));
		}
		long[] draw = TravelRules.drawPlan(stored, cost);
		if (draw == null) {
			return false;
		}
		for (int k = 0; k < draw.length; k++) {
			if (draw[k] > 0) {
				SimpleEnergyItem.setStoredEnergyUnchecked(packs.get(k), stored[k] - draw[k]);
			}
		}
		player.getInventory().setChanged();
		return true;
	}

	private static List<ItemStack> packs(ServerPlayer player) {
		List<ItemStack> out = new ArrayList<>();
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.getItem() instanceof CellPackItem) {
				out.add(stack);
			}
		}
		return out;
	}
}
