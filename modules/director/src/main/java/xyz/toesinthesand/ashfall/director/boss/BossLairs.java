package xyz.toesinthesand.ashfall.director.boss;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.AshfallDirector;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariant;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariants;
import xyz.toesinthesand.ashfall.director.story.StoryFlags;
import xyz.toesinthesand.ashfall.salvage.atlas.LoadedChunks;
import xyz.toesinthesand.ashfall.salvage.site.PlacedSite;
import xyz.toesinthesand.ashfall.salvage.site.SiteState;
import xyz.toesinthesand.ashfall.salvage.site.Sites;
import xyz.toesinthesand.ashfall.salvage.city.Zone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Story boss lairs (endgame design section 3, plan section 3.2 M9): data in {@code data/<ns>/ashfall/boss_lair/},
 * state in {@link BossState}, and the runtime that wakes, tracks and rewards each boss.
 * <ul>
 * <li><b>Waking.</b> Every second, each overworld player near a READY lair wakes it: the boss spawns on the lair's
 * boss marker and is persistent, so it waits in its lair across unloads and restarts. The lair then stays
 * spent ({@link LairRules}).</li>
 * <li><b>Boss bar.</b> Players within {@code bar_radius} of a living boss see its bar.</li>
 * <li><b>Drops.</b> On any death of a boss, whoever or whatever killed it, it drops its guaranteed items (a
 * Resonance Crystal for each story boss) and rolls its loot table. The crystals gate the finale, so they must
 * never be lost to a lava kill or a lucky zombie.</li>
 * <li><b>Seals.</b> A sealed lair (the deep ward) never wakes and its doors cannot be broken until
 * {@link #unseal} runs; the grid event that restores the hospital plant will call it.</li>
 * </ul>
 * Bosses are not Director zombies (no {@link DirectorSpawns#TAG}), so they never count toward spawn caps.
 */
public final class BossLairs {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "boss_lairs");
	/** Lair id on a boss entity; persistent so a boss reloaded from disk still drops its crystal. */
	public static final AttachmentType<String> BOSS = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("ashfall", "boss_lair"), builder -> builder.persistent(Codec.STRING));
	public static final String BOSS_TAG = "ashfall_boss";
	public static final String SUMMON_TAG_PREFIX = "ashfall_summon=";
	public static final TagKey<Block> BOSS_MARKER = TagKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath("ashfall", "landmark/boss_marker"));
	public static final TagKey<Block> SEALED_DOOR = TagKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath("ashfall", "landmark/sealed_door"));

	/** Seconds between lair checks, and the finer cadence for bars and abilities. */
	private static final int TRIGGER_TICKS = 20, ABILITY_TICKS = 10;
	/** Lairs start resolving their spawn point (which may scan the landmark) this far beyond the trigger radius. */
	private static final int RESOLVE_MARGIN = 64;

	/**
	 * Game time the story ended, if it has. The story state does not exist yet; whatever implements the Black
	 * Start ending sets this hook so post-ending respawns work. Default: no ending.
	 */
	private static volatile Function<MinecraftServer, OptionalLong> endingTick = server -> OptionalLong.empty();

	private static volatile Map<Identifier, BossLair> lairs = Map.of();
	private static final Map<Identifier, BossLair> TEST_LAIRS = new TreeMap<>();
	private static final Map<String, ServerBossEvent> BARS = new HashMap<>();
	private static final Map<String, Integer> SUMMON_CLOCK = new HashMap<>();
	private static final Set<String> WARNED = new HashSet<>();

	private BossLairs() {
	}

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, new Loader());
		ServerTickEvents.END_SERVER_TICK.register(BossLairs::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(BossLairs::onDeath);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !sealHolds(level, player, pos, state));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BARS.values().forEach(ServerBossEvent::removeAllPlayers);
			BARS.clear();
			SUMMON_CLOCK.clear();
			// Log-once warnings are per world: a lair missing its marker in the next world must still say so.
			WARNED.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> BossCommand.register(dispatcher));
		CrashSiteGuards.init();
	}

	/** Every loaded lair (datapack plus any test lairs), sorted by id. */
	public static Map<Identifier, BossLair> all() {
		Map<Identifier, BossLair> out = new TreeMap<>(lairs);
		synchronized (TEST_LAIRS) {
			out.putAll(TEST_LAIRS);
		}
		return out;
	}

	public static BossLair get(Identifier id) {
		synchronized (TEST_LAIRS) {
			BossLair test = TEST_LAIRS.get(id);
			if (test != null) {
				return test;
			}
		}
		return lairs.get(id);
	}

	/** Test hook: adds (or with null removes) a lair that survives datapack reloads. */
	public static void setForTesting(Identifier id, BossLair lair) {
		synchronized (TEST_LAIRS) {
			if (lair == null) {
				TEST_LAIRS.remove(id);
			} else {
				TEST_LAIRS.put(id, lair);
			}
		}
	}

	/** The story's hook: tells lairs when the ending happened (see {@link #endingTick}). */
	public static void setEndingTickSource(Function<MinecraftServer, OptionalLong> source) {
		endingTick = source == null ? server -> OptionalLong.empty() : source;
	}

	public static OptionalLong endingTick(MinecraftServer server) {
		return endingTick.apply(server);
	}

	/** True while the lair is sealed: its JSON says sealed and nothing has unsealed it yet. */
	public static boolean sealed(MinecraftServer server, Identifier id, BossLair lair) {
		return lair.sealed() && !BossState.get(server).lair(id.toString()).unsealed();
	}

	// ---------------------------------------------------------------------------------------------- ticking

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % ABILITY_TICKS != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (level == null) {
			return;
		}
		Map<Identifier, BossLair> loaded = all();
		BossState state = BossState.get(server);
		for (Map.Entry<Identifier, BossLair> e : loaded.entrySet()) {
			BossState.Lair record = state.lair(e.getKey().toString());
			if (record.status() == LairRules.Status.ACTIVE) {
				tickActive(level, e.getKey(), e.getValue(), record);
			}
		}
		if (t % TRIGGER_TICKS != 0 || level.players().isEmpty()) {
			return;
		}
		for (Map.Entry<Identifier, BossLair> e : loaded.entrySet()) {
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator() && tryWake(level, e.getKey(), e.getValue(), player.position()) != null) {
					break;
				}
			}
		}
	}

	/**
	 * Wakes the lair for a player standing at {@code playerPos}, if the lair may spawn and the player is within its
	 * trigger radius of the spawn point. The tick loop calls this for every player; gametests call it directly.
	 *
	 * @return the boss that spawned, or null
	 */
	public static Mob tryWake(ServerLevel level, Identifier id, BossLair lair, Vec3 playerPos) {
		MinecraftServer server = level.getServer();
		BossState state = BossState.get(server);
		if (lair.requiresFlag().isPresent() && !StoryFlags.get(server).has(lair.requiresFlag().get())) {
			return null;
		}
		BossState.Lair record = state.lair(id.toString());
		if (!LairRules.maySpawn(record.status(), sealed(server, id, lair), record.defeatedTick(), endingTick(server),
				lair.respawn(), level.getGameTime(), lair.requiresFlag().isPresent())) {
			return null;
		}
		Vec3 coarse = coarseCentre(lair);
		if (coarse == null) {
			return null;
		}
		double reach = lair.triggerRadius() + RESOLVE_MARGIN + coarseRadius(lair);
		double dx = coarse.x - playerPos.x, dz = coarse.z - playerPos.z;
		if (dx * dx + dz * dz > reach * reach) {
			return null;
		}
		Optional<BlockPos> spawn = resolveSpawn(level, id, lair);
		if (spawn.isEmpty() || Vec3.atBottomCenterOf(spawn.get()).distanceTo(playerPos) > lair.triggerRadius()) {
			return null;
		}
		return spawnBoss(level, id, lair, spawn.get());
	}

	/** Horizontal centre of the lair before its exact spawn point is known; null when its site is unknown. */
	static Vec3 coarseCentre(BossLair lair) {
		BossLair.Anchor a = lair.anchor();
		if (a.pos().isPresent()) {
			return Vec3.atBottomCenterOf(a.pos().get());
		}
		if (a.isLandmark()) {
			return new Vec3((a.chunkX() << 4) + a.widthChunks() * 8.0, 0, (a.chunkZ() << 4) + a.depthChunks() * 8.0);
		}
		Zone.FixedSite site = Sites.fixedSites().get(a.site().orElseThrow());
		return site == null ? null : new Vec3(site.x() + 0.5, 0, site.z() + 0.5);
	}

	private static double coarseRadius(BossLair lair) {
		BossLair.Anchor a = lair.anchor();
		return a.isLandmark() ? Math.hypot(a.widthChunks() * 8.0, a.depthChunks() * 8.0) : 16.0;
	}

	/**
	 * Where the boss stands: the fixed position, the placed site's {@code boss} marker, or the landmark's boss marker
	 * block (found by scanning the landmark's chunks once they are loaded, then remembered). Empty while that is
	 * not known yet (site not placed, chunks not loaded, marker missing).
	 */
	public static Optional<BlockPos> resolveSpawn(ServerLevel level, Identifier id, BossLair lair) {
		BossState state = BossState.get(level.getServer());
		BossState.Lair record = state.lair(id.toString());
		if (record.spawnPos().isPresent()) {
			return record.spawnPos();
		}
		BossLair.Anchor a = lair.anchor();
		Optional<BlockPos> found = Optional.empty();
		if (a.pos().isPresent()) {
			found = a.pos();
		} else if (a.site().isPresent()) {
			PlacedSite site = SiteState.get(level.getServer()).site(PlacedSite.fixedKey(a.site().get()));
			if (site != null) {
				found = Optional.of(site.marker("boss").map(BlockPos::above).orElse(site.center()));
			}
		} else if (landmarkLoaded(level, a)) {
			found = scanMarker(level, a).map(BlockPos::above);
			if (found.isEmpty() && WARNED.add(id.toString())) {
				AshfallDirector.LOGGER.warn("Boss lair {}: no boss marker in {} at chunk {},{}", id, a.landmark().orElse("?"),
						a.chunkX(), a.chunkZ());
			}
		}
		found.ifPresent(pos -> state.resolved(record, pos));
		return found;
	}

	private static boolean landmarkLoaded(ServerLevel level, BossLair.Anchor a) {
		for (int cx = a.chunkX(); cx < a.chunkX() + a.widthChunks(); cx++) {
			for (int cz = a.chunkZ(); cz < a.chunkZ() + a.depthChunks(); cz++) {
				if (!LoadedChunks.chunkReady(level, cx, cz)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * The landmark's boss marker: the lowest, then north-west-most marker block in its chunks. Sections whose
	 * palette holds no marker are skipped without reading a block, so the scan is cheap even for the deep
	 * hospital.
	 */
	static Optional<BlockPos> scanMarker(ServerLevel level, BossLair.Anchor a) {
		List<BlockPos> markers = new ArrayList<>();
		for (int cx = a.chunkX(); cx < a.chunkX() + a.widthChunks(); cx++) {
			for (int cz = a.chunkZ(); cz < a.chunkZ() + a.depthChunks(); cz++) {
				LevelChunk chunk = level.getChunk(cx, cz);
				LevelChunkSection[] sections = chunk.getSections();
				for (int s = 0; s < sections.length; s++) {
					LevelChunkSection section = sections[s];
					if (section.hasOnlyAir() || !section.maybeHas(st -> st.is(BOSS_MARKER))) {
						continue;
					}
					int baseY = level.getSectionYFromSectionIndex(s) << 4;
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								if (section.getBlockState(x, y, z).is(BOSS_MARKER)) {
									markers.add(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z));
								}
							}
						}
					}
				}
			}
		}
		return markers.stream().min(Comparator.<BlockPos>comparingInt(p -> p.getY()).thenComparingInt(p -> p.getZ())
				.thenComparingInt(p -> p.getX()));
	}

	// ---------------------------------------------------------------------------------------------- spawning

	/**
	 * Spawns the lair's boss at {@code pos} and records the lair as ACTIVE. Ignores the spawn-once rule and the
	 * seal: {@link #tryWake} checks those, and {@code /ashfall boss spawn} is meant to override them.
	 *
	 * @return the boss, or null when its entity or variant is not available
	 */
	public static Mob spawnBoss(ServerLevel level, Identifier id, BossLair lair, BlockPos pos) {
		Mob mob = createBoss(level, lair, pos);
		if (mob == null) {
			if (WARNED.add(id + "/entity")) {
				AshfallDirector.LOGGER.warn("Boss lair {}: boss {} is not available", id, lair.boss());
			}
			return null;
		}
		mob.setAttached(BOSS, id.toString());
		mob.addTag(BOSS_TAG);
		mob.setCustomName(Component.translatable(lair.name()));
		mob.setCustomNameVisible(true);
		mob.setGlowingTag(lair.glowing());
		mob.setPersistenceRequired();
		if (!level.addFreshEntity(mob)) {
			return null;
		}
		BossState.get(level.getServer()).spawned(BossState.get(level.getServer()).lair(id.toString()), mob.getUUID(), pos,
				level.getGameTime());
		AshfallDirector.LOGGER.info("Boss lair {} woke at {}", id, pos.toShortString());
		return mob;
	}

	private static Mob createBoss(ServerLevel level, BossLair lair, BlockPos pos) {
		BossLair.Boss boss = lair.boss();
		Optional<Identifier> variantId = boss.variant();
		Mob mob = null;
		if (boss.entity().isPresent()) {
			Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(boss.entity().get());
			if (type.isPresent() && type.get().create(level, EntitySpawnReason.EVENT) instanceof Mob created) {
				mob = created;
				mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
				mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
			} else {
				variantId = boss.fallbackVariant();
			}
		}
		if (mob == null) {
			if (variantId.isEmpty()) {
				return null;
			}
			ZombieVariant variant = ZombieVariants.get(variantId.get());
			if (variant == null) {
				return null;
			}
			Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(variant.entity());
			if (type.isEmpty() || !(type.get().create(level, EntitySpawnReason.EVENT) instanceof Zombie zombie)) {
				return null;
			}
			zombie.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
			zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, new Zombie.ZombieGroupData(false, false));
			DirectorSpawns.applyVariant(zombie, variant, level.getRandom());
			zombie.addTag(DirectorSpawns.VARIANT_TAG_PREFIX + variantId.get().getPath());
			mob = zombie;
		}
		applyAttributes(mob, lair.attributes());
		return mob;
	}

	private static void applyAttributes(Mob mob, Map<Identifier, Double> attributes) {
		for (Map.Entry<Identifier, Double> e : attributes.entrySet()) {
			Optional<Holder.Reference<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(e.getKey());
			AttributeInstance instance = holder.map(mob::getAttribute).orElse(null);
			if (instance == null) {
				AshfallDirector.LOGGER.warn("Boss attribute {} is unknown or not on {}", e.getKey(), mob.getType());
				continue;
			}
			instance.removeModifiers();
			instance.setBaseValue(e.getValue());
		}
		mob.setHealth(mob.getMaxHealth());
	}

	/** The loaded boss entity of an ACTIVE lair, or null (dead, or its chunk is unloaded). */
	public static LivingEntity boss(ServerLevel level, BossState.Lair record) {
		return record.boss().map(level::getEntity).filter(e -> e instanceof LivingEntity l && l.isAlive())
				.map(LivingEntity.class::cast).orElse(null);
	}

	private static void tickActive(ServerLevel level, Identifier id, BossLair lair, BossState.Lair record) {
		LivingEntity boss = boss(level, record);
		ServerBossEvent bar = BARS.computeIfAbsent(id.toString(), k -> new ServerBossEvent(
				net.minecraft.util.Mth.createInsecureUUID(level.getRandom()), Component.translatable(lair.name()),
				lair.bossbar().color(), lair.bossbar().overlay()));
		if (boss == null) {
			bar.removeAllPlayers();
			return;
		}
		bar.setProgress(boss.getHealth() / Math.max(1f, boss.getMaxHealth()));
		double r2 = (double) lair.barRadius() * lair.barRadius();
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(boss) <= r2) {
				bar.addPlayer(player);
			} else {
				bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (player.level() != level || player.isRemoved()) {
				bar.removePlayer(player);
			}
		}
		lair.particle().flatMap(BuiltInRegistries.PARTICLE_TYPE::getOptional).ifPresent(type -> {
			if (type instanceof SimpleParticleType simple) {
				level.sendParticles(simple, boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(), 6,
						boss.getBbWidth() * 0.6, boss.getBbHeight() * 0.4, boss.getBbWidth() * 0.6, 0.02);
			}
		});
		lair.summon().ifPresent(summon -> {
			int clock = SUMMON_CLOCK.merge(id.toString(), ABILITY_TICKS, Integer::sum);
			if (clock < summon.everyTicks() || !(boss instanceof Mob mob) || mob.getTarget() == null) {
				return;
			}
			SUMMON_CLOCK.put(id.toString(), 0);
			summon(level, id, mob, summon);
		});
	}

	/** Calls up to {@code count} minions around the boss, never more than {@code maxAlive} near it at once. */
	public static int summon(ServerLevel level, Identifier id, Mob boss, BossLair.Summon summon) {
		String tag = SUMMON_TAG_PREFIX + id;
		AABB box = boss.getBoundingBox().inflate(32);
		int alive = level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.entityTags().contains(tag)).size();
		ZombieVariant variant = ZombieVariants.get(summon.variant());
		Map.Entry<Identifier, ZombieVariant> entry = variant == null ? null : Map.entry(summon.variant(), variant);
		RandomSource random = level.getRandom();
		int spawned = 0;
		for (int i = 0; i < summon.count() && alive + spawned < summon.maxAlive(); i++) {
			BlockPos at = standable(level, boss.blockPosition(), summon.radius(), random);
			Zombie minion = DirectorSpawns.spawn(level, at, entry, DirectorZombie.Source.AMBIENT, DirectorZombie.NO_BUILDING);
			if (minion != null) {
				minion.addTag(tag);
				if (boss.getTarget() != null) {
					minion.setTarget(boss.getTarget());
				}
				spawned++;
			}
		}
		return spawned;
	}

	private static BlockPos standable(ServerLevel level, BlockPos around, int radius, RandomSource random) {
		for (int attempt = 0; attempt < 8; attempt++) {
			BlockPos p = around.offset(random.nextIntBetweenInclusive(-radius, radius), 0, random.nextIntBetweenInclusive(-radius, radius));
			for (int dy = 2; dy >= -2; dy--) {
				BlockPos feet = p.above(dy);
				if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
						&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
						&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
					return feet;
				}
			}
		}
		return around;
	}

	// ---------------------------------------------------------------------------------------------- death and drops

	private static void onDeath(LivingEntity entity, DamageSource source) {
		String lairId = entity.getAttached(BOSS);
		if (lairId == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Identifier id = Identifier.tryParse(lairId);
		BossLair lair = id == null ? null : get(id);
		if (lair != null) {
			drop(level, entity, source, lair.drops());
		}
		BossState state = BossState.get(level.getServer());
		BossState.Lair record = state.lair(lairId);
		if (record.boss().isEmpty() || record.boss().get().equals(entity.getUUID())) {
			state.defeated(record, level.getGameTime());
		}
		ServerBossEvent bar = BARS.remove(lairId);
		if (bar != null) {
			bar.removeAllPlayers();
		}
		SUMMON_CLOCK.remove(lairId);
		AshfallDirector.LOGGER.info("Boss lair {} defeated", lairId);
	}

	/**
	 * The guaranteed items, then the loot table. Both ignore {@code doMobLoot} and the killer: the crystal is a
	 * story item, so it drops on any death. Items are fire resistant where it matters (the crystal is), so a boss
	 * that dies in lava still leaves its crystal.
	 */
	public static List<ItemStack> drop(ServerLevel level, LivingEntity entity, DamageSource source, BossLair.Drops drops) {
		List<ItemStack> out = new ArrayList<>();
		for (BossLair.ItemCount ic : drops.guaranteed()) {
			Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ic.item());
			if (item.isEmpty()) {
				if (WARNED.add("item/" + ic.item())) {
					AshfallDirector.LOGGER.warn("Boss drop {} is not a registered item (is Ashfall Arms installed?)", ic.item());
				}
				continue;
			}
			out.add(new ItemStack(item.get(), ic.count()));
		}
		drops.lootTable().ifPresent(tableId -> {
			ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, tableId);
			LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
			LootParams.Builder params = new LootParams.Builder(level)
					.withParameter(LootContextParams.THIS_ENTITY, entity)
					.withParameter(LootContextParams.ORIGIN, entity.position())
					.withParameter(LootContextParams.DAMAGE_SOURCE, source)
					.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
					.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity());
			Player player = entity.getLastHurtByPlayer();
			if (player != null) {
				params.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player).withLuck(player.getLuck());
			}
			table.getRandomItems(params.create(LootContextParamSets.ENTITY), out::add);
		});
		for (ItemStack stack : out) {
			if (!stack.isEmpty()) {
				entity.spawnAtLocation(level, stack.copy());
			}
		}
		return out;
	}

	// ---------------------------------------------------------------------------------------------- seals

	/**
	 * Opens a sealed lair: records it as unsealed (the boss may now wake) and opens the seal's iron doors on the
	 * boss's storey. Doors whose chunks are not loaded stay shut; {@code /ashfall boss unseal} can be run again
	 * when standing there.
	 *
	 * @return how many doors were opened
	 */
	public static int unseal(ServerLevel level, Identifier id) {
		BossLair lair = get(id);
		if (lair == null) {
			return 0;
		}
		BossState state = BossState.get(level.getServer());
		state.unseal(state.lair(id.toString()));
		Optional<BlockPos> spawn = resolveSpawn(level, id, lair);
		if (spawn.isEmpty()) {
			return 0;
		}
		int opened = 0;
		for (BlockPos pos : sealDoors(level, lair, spawn.get())) {
			BlockState st = level.getBlockState(pos);
			if (st.getBlock() instanceof DoorBlock door && !door.isOpen(st)) {
				door.setOpen(null, level, st, pos, true);
				opened++;
			}
		}
		AshfallDirector.LOGGER.info("Boss lair {} unsealed ({} doors opened)", id, opened);
		return opened;
	}

	/** Lower halves of the seal's doors: sealed-door blocks on the boss's storey within the seal radius. */
	static List<BlockPos> sealDoors(ServerLevel level, BossLair lair, BlockPos spawn) {
		List<BlockPos> out = new ArrayList<>();
		int r = lair.sealRadius();
		for (BlockPos pos : BlockPos.betweenClosed(spawn.offset(-r, -1, -r), spawn.offset(r, 1, r))) {
			if (!LoadedChunks.ready(level, pos)) {
				continue;
			}
			BlockState st = level.getBlockState(pos);
			if (st.is(SEALED_DOOR) && st.hasProperty(DoorBlock.HALF) && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				out.add(pos.immutable());
			}
		}
		return out;
	}

	/**
	 * Whether a survival player's break of {@code pos} is refused because it is a door of a still-sealed lair
	 * ({@link #sealedDoor}).
	 */
	static boolean sealHolds(Level level, Player player, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel server) || player.isCreative() || !sealedDoor(server, pos, state)) {
			return false;
		}
		player.sendOverlayMessage(Component.translatable("message.ashfall.boss.seal_holds"));
		return true;
	}

	/**
	 * Whether {@code pos} is a door of a still-sealed lair: a sealed-door block on the boss's storey within the seal
	 * radius. Only the seal's own doors count, so the rest of the building can be dismantled as usual. Players can't
	 * break these ({@link #sealHolds}), and zombies, bosses included, neither break nor wear them
	 * ({@link xyz.toesinthesand.ashfall.director.fortify.Fortified#zombieBreak}), so a passing horde can't open the
	 * deep ward before the plant is restored.
	 */
	public static boolean sealedDoor(ServerLevel server, BlockPos pos, BlockState state) {
		if (server.dimension() != Level.OVERWORLD || !state.is(SEALED_DOOR)) {
			return false;
		}
		for (Map.Entry<Identifier, BossLair> e : all().entrySet()) {
			BossLair lair = e.getValue();
			if (!sealed(server.getServer(), e.getKey(), lair)) {
				continue;
			}
			Optional<BlockPos> spawn = BossState.get(server.getServer()).lair(e.getKey().toString()).spawnPos();
			if (spawn.isEmpty() && lair.anchor().isLandmark()) {
				ChunkPos cp = ChunkPos.containing(pos);
				BossLair.Anchor a = lair.anchor();
				if (cp.x() >= a.chunkX() && cp.x() < a.chunkX() + a.widthChunks() && cp.z() >= a.chunkZ()
						&& cp.z() < a.chunkZ() + a.depthChunks()) {
					spawn = resolveSpawn(server, e.getKey(), lair);
				}
			}
			if (spawn.isPresent()) {
				BlockPos s = spawn.get();
				int r = lair.sealRadius();
				if (Math.abs(pos.getX() - s.getX()) <= r && Math.abs(pos.getZ() - s.getZ()) <= r && pos.getY() >= s.getY() - 1
						&& pos.getY() <= s.getY() + 2) {
					return true;
				}
			}
		}
		return false;
	}

	/** Loads {@code data/<ns>/ashfall/boss_lair/*.json}. */
	static final class Loader extends SimpleJsonResourceReloadListener<BossLair> {
		Loader() {
			super(BossLair.CODEC, FileToIdConverter.json("ashfall/boss_lair"));
		}

		@Override
		protected void apply(Map<Identifier, BossLair> loaded, ResourceManager manager, ProfilerFiller profiler) {
			lairs = Map.copyOf(loaded);
			AshfallDirector.LOGGER.info("Loaded {} boss lairs", loaded.size());
		}
	}
}
