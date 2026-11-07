package xyz.toesinthesand.ashfall.director.fortify;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreRecord;
import xyz.toesinthesand.ashfall.director.haven.BunkerCores;
import xyz.toesinthesand.ashfall.director.haven.ChunkArea;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Fortified blocks wear down (10.2.0). A zombie that would break a {@code #ashfall:fortified} block adds wear to it
 * instead ({@link Fortified#zombieBreak}): one point per {@code hit_interval_ticks} per zombie, so a horde on one spot
 * adds up. At the block's points ({@link WearSettings}) it downgrades (planks to oak planks, doors break and only
 * copper doors drop scrap, iron and obsidian to cobblestone), and from then on Zombies Break and Build treats it as an
 * ordinary block.
 * <ul>
 *   <li><b>Storage.</b> Per-chunk saved attachment ({@link #WEAR}), block position to points, so vanilla blocks wear
 *   too without a new block property. A block that changes into another block loses its wear
 *   ({@code LevelChunkWearMixin}), whoever changed it, unless it leaves as its own item or on a piston
 *   ({@link WearCarry}).</li>
 *   <li><b>Cracks.</b> Vanilla crack overlays, sent on every change and again every {@link #RESEND_TICKS} to players
 *   within 32 blocks (the client forgets an overlay after 20 s without an update).</li>
 *   <li><b>Repair.</b> Right-click a worn block holding one of its repair materials: one of each material comes off
 *   for {@code repair_points}, and the held material cools down for {@code repair_cooldown_ticks} (10.2.2). Mechanic
 *   survivors do the same from their Supply Lockers (arms), one set per turn.</li>
 * </ul>
 */
public final class FortifyWear {
	/** Ticks between crack refreshes for players near worn blocks. */
	static final int RESEND_TICKS = 200;
	/** Crack refreshes reach this far (the vanilla destroy-progress range). */
	private static final double CRACK_RANGE = 32.0;

	/** One chunk's wear: packed block position to points. Immutable; every change attaches a new one. */
	public record WearMap(Map<Long, Integer> points) {
		private record Entry(long pos, int wear) {
			static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
					Codec.INT.fieldOf("wear").forGetter(Entry::wear)
			).apply(i, Entry::new));
		}

		public static final Codec<WearMap> CODEC = Entry.CODEC.listOf().xmap(list -> {
			Map<Long, Integer> map = new LinkedHashMap<>();
			list.forEach(e -> map.put(e.pos(), e.wear()));
			return new WearMap(Map.copyOf(map));
		}, map -> map.points().entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList());
	}

	/** Wear in a chunk; saved with the chunk. */
	public static final AttachmentType<WearMap> WEAR = AttachmentRegistry.create(id("block_wear"),
			builder -> builder.persistent(WearMap.CODEC));
	/**
	 * Game time of a zombie's last counted hit. Not saved: it only spaces one zombie's hits, and keeping it on the
	 * entity means there is no static map to clean up.
	 */
	public static final AttachmentType<Long> LAST_HIT = AttachmentRegistry.create(id("wear_last_hit"));

	private FortifyWear() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	public static void init() {
		WearCarry.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(WearSettings.Loader.ID, new WearSettings.Loader());
		UseBlockCallback.EVENT.register(FortifyWear::onUseBlock);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % RESEND_TICKS == 17) {
				resendCracks(server);
			}
		});
	}

	// ---- reading ----

	/** Points {@code state} holds before it downgrades, or 0 when it never wears. */
	public static int points(BlockState state) {
		WearSettings.Rule rule = WearSettings.get().ruleFor(state);
		return rule == null ? 0 : rule.points();
	}

	/** Wear on the block at {@code pos} (0 when none, or when its chunk is not loaded). */
	public static int wear(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
		WearMap map = chunk == null ? null : chunk.getAttached(WEAR);
		return map == null ? 0 : map.points().getOrDefault(pos.asLong(), 0);
	}

	/** Condition of the block at {@code pos}, 1.0 when unworn (or when it never wears). */
	public static double condition(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		int points = points(state);
		return points == 0 ? 1.0 : 1.0 - Math.min(points, wear(level, anchor(state, pos))) / (double) points;
	}

	/** Where a block's wear is kept: a door's lower half for both halves, so hits on either add up. */
	public static BlockPos anchor(BlockState state, BlockPos pos) {
		return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
	}

	/** Worn blocks in the loaded chunks of {@code area}: position to wear. */
	public static Map<BlockPos, Integer> worn(ServerLevel level, ChunkArea area) {
		Map<BlockPos, Integer> out = new HashMap<>();
		for (int cx = area.chunkX() - area.radius(); cx <= area.chunkX() + area.radius(); cx++) {
			for (int cz = area.chunkZ() - area.radius(); cz <= area.chunkZ() + area.radius(); cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				WearMap map = chunk == null ? null : chunk.getAttached(WEAR);
				if (map != null) {
					map.points().forEach((pos, wear) -> out.put(BlockPos.of(pos), wear));
				}
			}
		}
		return out;
	}

	/** The repair materials of {@code state}, one test each (empty when it never wears). */
	public static List<Predicate<ItemStack>> materials(BlockState state) {
		WearSettings.Rule rule = WearSettings.get().ruleFor(state);
		return rule == null ? List.of() : rule.materials();
	}

	/** The repair materials of {@code state} with how many of each one set takes (empty when it never wears). */
	public static Map<Predicate<ItemStack>, Integer> materialCounts(BlockState state) {
		WearSettings.Rule rule = WearSettings.get().ruleFor(state);
		return rule == null ? Map.of() : rule.materialCounts();
	}

	// ---- zombies ----

	/**
	 * A zombie hits the fortified block at {@code pos}: one wear point, at most one per {@code hit_interval_ticks} for
	 * this zombie and only within {@code reach}, so ZB&B asking every tick doesn't count every tick.
	 *
	 * @return whether the hit counted
	 */
	public static boolean zombieHit(ServerLevel level, BlockPos pos, BlockState state, Mob mob) {
		WearSettings settings = WearSettings.get();
		WearSettings.Rule rule = settings.ruleFor(state);
		if (rule == null || mob.distanceToSqr(Vec3.atCenterOf(pos)) > settings.reach() * settings.reach()) {
			return false;
		}
		long now = level.getGameTime();
		Long last = mob.getAttached(LAST_HIT);
		if (last != null && now - last < settings.hitIntervalTicks() && now >= last) {
			return false;
		}
		mob.setAttached(LAST_HIT, now);
		boolean wood = state.getSoundType() == SoundType.WOOD;
		level.levelEvent(null, wood ? LevelEvent.SOUND_ZOMBIE_WOODEN_DOOR : LevelEvent.SOUND_ZOMBIE_IRON_DOOR, pos, 0);
		addWear(level, anchor(state, pos), state, 1);
		return true;
	}

	/**
	 * Adds {@code amount} wear to the block at {@code pos} ({@code state}); at its points it downgrades.
	 *
	 * @return the wear now on the block, 0 after a downgrade or when it never wears
	 */
	public static int addWear(ServerLevel level, BlockPos pos, BlockState state, int amount) {
		WearSettings.Rule rule = WearSettings.get().ruleFor(state);
		if (rule == null) {
			return 0;
		}
		int wear = wear(level, pos) + amount;
		if (wear >= rule.points()) {
			downgrade(level, pos, state, rule);
			return 0;
		}
		set(level, pos, wear, rule.points());
		return wear;
	}

	/** Takes {@code amount} wear off the block at {@code pos} (an {@link #anchor}). @return the wear left */
	public static int repair(ServerLevel level, BlockPos pos, int amount) {
		int points = points(level.getBlockState(pos));
		int wear = Math.max(0, wear(level, pos) - amount);
		set(level, pos, wear, points);
		return wear;
	}

	private static void downgrade(ServerLevel level, BlockPos pos, BlockState state, WearSettings.Rule rule) {
		set(level, pos, 0, rule.points());
		WearSettings.Downgrade downgrade = rule.downgrade();
		level.levelEvent(null, LevelEvent.SOUND_ZOMBIE_DOOR_CRASH, pos, 0);
		level.levelEvent(null, LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, Block.getId(state));
		Block replacement = downgrade.block().flatMap(BuiltInRegistries.BLOCK::getOptional).orElse(null);
		if (replacement != null) {
			level.setBlock(pos, replacement.defaultBlockState(), Block.UPDATE_ALL);
		} else {
			// a door's other half goes with it (DoorBlock.updateShape)
			level.destroyBlock(pos, false);
		}
		downgrade.drop().flatMap(BuiltInRegistries.ITEM::getOptional).filter(item -> downgrade.count() > 0)
				.ifPresent(item -> Block.popResource(level, pos, new ItemStack(item, downgrade.count())));
	}

	// ---- storage and cracks ----

	private static void set(ServerLevel level, BlockPos pos, int wear, int points) {
		LevelChunk chunk = level.getChunkAt(pos);
		WearMap old = chunk.getAttached(WEAR);
		long key = pos.asLong();
		int before = old == null ? 0 : old.points().getOrDefault(key, 0);
		if (before == wear) {
			return;
		}
		Map<Long, Integer> map = old == null ? new HashMap<>() : new HashMap<>(old.points());
		if (wear > 0) {
			map.put(key, wear);
		} else {
			map.remove(key);
		}
		chunk.setAttached(WEAR, map.isEmpty() ? null : new WearMap(Map.copyOf(map)));
		level.destroyBlockProgress(crackId(pos), pos, stage(wear, points));
	}

	/** Sets carried wear on the {@code state} block at {@code pos} (an {@link #anchor}), short of a downgrade. */
	static void restore(ServerLevel level, BlockPos pos, BlockState state, int wear) {
		int points = points(state);
		if (points > 0) {
			set(level, pos, Math.min(wear, points - 1), points);
		}
	}

	/**
	 * Forgets the wear at {@code pos} when the block there ({@code old}) turns into a different block (called from
	 * {@code LevelChunkWearMixin}; cheap when the chunk has no wear). {@link WearCarry} keeps it for this tick, for
	 * the block's drop.
	 */
	public static void blockChanged(LevelChunk chunk, BlockPos pos, BlockState old) {
		WearMap stored = chunk.getAttached(WEAR);
		Integer wear = stored == null ? null : stored.points().get(pos.asLong());
		if (wear == null) {
			return;
		}
		Map<Long, Integer> map = new HashMap<>(stored.points());
		map.remove(pos.asLong());
		chunk.setAttached(WEAR, map.isEmpty() ? null : new WearMap(Map.copyOf(map)));
		if (chunk.getLevel() instanceof ServerLevel level) {
			level.destroyBlockProgress(crackId(pos), pos, -1);
			WearCarry.removed(level, pos, old, wear);
		}
	}

	/** Crack stage 0-9 for {@code wear} of {@code points}, or -1 (no overlay). */
	static int stage(int wear, int points) {
		return wear <= 0 || points <= 0 ? -1 : Math.min(9, wear * 10 / points);
	}

	/** A fake breaker id per position, negative so it never matches a real entity (whose own cracks it would hide). */
	static int crackId(BlockPos pos) {
		return Long.hashCode(pos.asLong()) | Integer.MIN_VALUE;
	}

	private static void resendCracks(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				int pcx = player.blockPosition().getX() >> 4, pcz = player.blockPosition().getZ() >> 4;
				for (int cx = pcx - 2; cx <= pcx + 2; cx++) {
					for (int cz = pcz - 2; cz <= pcz + 2; cz++) {
						LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
						WearMap map = chunk == null ? null : chunk.getAttached(WEAR);
						if (map == null) {
							continue;
						}
						map.points().forEach((key, wear) -> {
							BlockPos pos = BlockPos.of(key);
							if (player.distanceToSqr(Vec3.atCenterOf(pos)) <= CRACK_RANGE * CRACK_RANGE) {
								player.connection.send(new ClientboundBlockDestructionPacket(crackId(pos), pos,
										stage(wear, points(level.getBlockState(pos)))));
							}
						});
					}
				}
			}
		}
	}

	// ---- the Bunker Core warning ----

	/**
	 * After a horde night: tells each online player whose Bunker Core's claim holds a block under {@code warn_below}
	 * condition how many there are and where the worst one is (loaded chunks only; a base nobody is near has no
	 * loaded chunks and nobody to tell). @return how many players were warned
	 */
	public static int warnAfterHorde(MinecraftServer server) {
		double below = WearSettings.get().warnBelow();
		int warned = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Optional<BunkerCoreRecord> core = BunkerCores.forPlayer(server, player.getUUID());
			ServerLevel level = core.map(c -> server.getLevel(c.dimension())).orElse(null);
			if (level == null) {
				continue;
			}
			int weak = 0;
			BlockPos worst = null;
			double worstCondition = 2.0;
			for (BlockPos pos : worn(level, core.get().area()).keySet()) {
				double condition = condition(level, pos);
				if (condition < below) {
					weak++;
					if (condition < worstCondition) {
						worstCondition = condition;
						worst = pos;
					}
				}
			}
			if (worst != null) {
				player.sendSystemMessage(Component.translatable("message.ashfall.wear.core_warning", weak,
						worst.toShortString(), (int) Math.round(worstCondition * 100)));
				warned++;
			}
		}
		return warned;
	}

	// ---- player repair ----

	private static InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hit) {
		if (!(world instanceof ServerLevel level) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		BlockState state = level.getBlockState(hit.getBlockPos());
		BlockPos pos = anchor(state, hit.getBlockPos());
		WearSettings.Rule rule = WearSettings.get().ruleFor(state);
		ItemStack held = player.getItemInHand(hand);
		if (rule == null || held.isEmpty() || wear(level, pos) <= 0) {
			return InteractionResult.PASS;
		}
		List<Predicate<ItemStack>> materials = rule.materials();
		if (materials.stream().noneMatch(m -> m.test(held))) {
			return InteractionResult.PASS;
		}
		if (player.getCooldowns().isOnCooldown(held)) {
			// still cooling down from the last repair: swallow the click so it neither repairs nor opens the door
			return InteractionResult.FAIL;
		}
		// cooldown keys taken before the materials are used up (a spent stack reads as air), one per matching item
		List<ItemStack> cooldownKeys = cooldownKeys(player.getInventory(), held, materials);
		if (!player.hasInfiniteMaterials() && !takeMaterials(player.getInventory(), held, materials)) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.wear.needs",
					materialNames(rule)));
			return InteractionResult.FAIL;
		}
		int left = repair(level, pos, WearSettings.get().repairPoints());
		if (WearSettings.get().repairCooldownTicks() > 0) {
			for (ItemStack key : cooldownKeys) {
				player.getCooldowns().addCooldown(key, WearSettings.get().repairCooldownTicks());
			}
		}
		level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.8f);
		player.sendOverlayMessage(Component.translatable("message.ashfall.wear.repaired",
				(int) Math.round(100.0 * (1.0 - left / (double) rule.points()))));
		return InteractionResult.SUCCESS;
	}

	/** "any planks + 2 Nails"-style names of a rule's materials; a tag reads as "any planks". */
	static Component materialNames(WearSettings.Rule rule) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String name : rule.repair()) {
			counts.merge(name, 1, Integer::sum);
		}
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			String name = entry.getKey();
			Identifier id = Identifier.tryParse(name.startsWith("#") ? name.substring(1) : name);
			if (id == null) {
				continue;
			}
			Component part = name.startsWith("#") ? Component.translatable("message.ashfall.wear.any", id.getPath().replace('_', ' '))
					: BuiltInRegistries.ITEM.getOptional(id).map(item -> new ItemStack(item).getHoverName()).orElse(null);
			if (part != null) {
				if (!out.getSiblings().isEmpty()) {
					out.append(" + ");
				}
				if (entry.getValue() > 1) {
					out.append(entry.getValue() + " ");
				}
				out.append(part);
			}
		}
		return out;
	}

	/**
	 * One single-count copy of each distinct item in the inventory (and the held stack) that any of the rule's
	 * materials accepts, so a repair cools down every plank type and the Nails alike: scrolling to another material
	 * stack must not dodge the repair cooldown.
	 */
	static List<ItemStack> cooldownKeys(Inventory inventory, ItemStack held, List<Predicate<ItemStack>> materials) {
		List<ItemStack> keys = new java.util.ArrayList<>();
		java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
		for (int slot = -1; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = slot < 0 ? held : inventory.getItem(slot);
			if (!stack.isEmpty() && materials.stream().anyMatch(m -> m.test(stack)) && seen.add(stack.getItem())) {
				keys.add(stack.copyWithCount(1));
			}
		}
		return keys;
	}

	/** Takes one of each material (the held stack first for its own), or nothing when any is missing. */
	static boolean takeMaterials(Inventory inventory, ItemStack held, List<Predicate<ItemStack>> materials) {
		ItemStack[] picks = new ItemStack[materials.size()];
		Map<ItemStack, Integer> reserved = new IdentityHashMap<>();
		for (int m = 0; m < materials.size(); m++) {
			Predicate<ItemStack> material = materials.get(m);
			ItemStack pick = null;
			if (material.test(held) && held.getCount() > reserved.getOrDefault(held, 0)) {
				pick = held;
			}
			for (int slot = 0; pick == null && slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (!stack.isEmpty() && material.test(stack) && stack.getCount() > reserved.getOrDefault(stack, 0)) {
					pick = stack;
				}
			}
			if (pick == null) {
				return false;
			}
			reserved.merge(pick, 1, Integer::sum);
			picks[m] = pick;
		}
		for (ItemStack pick : picks) {
			pick.shrink(1);
		}
		return true;
	}

	/** Test hook and admin use: the wear map of the chunk holding {@code pos}, or null. */
	public static @Nullable WearMap chunkWear(ServerLevel level, BlockPos pos) {
		return level.getChunkAt(pos).getAttached(WEAR);
	}
}
