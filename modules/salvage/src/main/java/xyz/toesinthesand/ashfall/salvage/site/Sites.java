package xyz.toesinthesand.ashfall.salvage.site;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.city.LostCityContext;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;
import xyz.toesinthesand.ashfall.salvage.loot.LootState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Authored sites outside the Lost Cities generator: the Wreck hull at its fixed ridge position (zone JSON
 * {@code sites}) and the seed-scattered Wreck crash sites of the ridge and wasteland ({@link CrashSiteRule}).
 * <p>
 * Placement is lazy and happens on the server thread: every {@link #CHECK_TICKS} ticks, sites within
 * {@link #PLACE_RADIUS} blocks of an overworld player are placed once their whole footprint is loaded, and
 * {@link SiteState} remembers them so nothing is placed twice. That keeps worldgen threads (where Lost Cities
 * and our zone hooks run) free of block writes, and the layout itself is still fixed per world, because it is
 * planned from the seed alone. The radius is larger than any view distance a client is likely to render at
 * full detail, so a site is normally in place before anyone sees its chunk.
 * <p>
 * Only levels generated with our Lost Cities profile get sites; a plain world with the mod installed keeps its
 * terrain untouched.
 */
public final class Sites {
	public static final int CHECK_TICKS = 40;
	public static final int PLACE_RADIUS = 160;
	/** Set-block flags: tell clients, but skip shape updates until the whole template is down (doors break otherwise). */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	/** How far a foundation column may pour down before giving up (a site over a ravine stays partly overhung). */
	private static final int MAX_FOUNDATION = 8;

	@FunctionalInterface
	public interface Placed {
		void onPlaced(ServerLevel level, PlacedSite site);
	}

	/** Fired once per site, right after its blocks and containers are in the world. */
	public static final Event<Placed> PLACED = EventFactory.createArrayBacked(Placed.class, listeners -> (level, site) -> {
		for (Placed listener : listeners) {
			listener.onPlaced(level, site);
		}
	});

	private static volatile Map<Identifier, SiteTemplate> templates = Map.of();
	private static volatile Map<String, CrashSiteRule> rules = Map.of();
	private static final Map<Identifier, Map<Character, BlockState>> RESOLVED = new HashMap<>();
	private static Boolean enabledOverride;

	/**
	 * Aftershock crash sites (endgame-design section 5): after the story's ending the Wreck keeps shedding debris, so
	 * Resonance Crystals and rare blasters stay obtainable and offset blade wear. Each generation is a second pass of
	 * every crash-site rule with its own salt and a scaled chance, so it scatters new sites between the old ones;
	 * the story (arms) says how many generations have fallen so far.
	 *
	 * @param generations aftershock passes so far (0 before the ending)
	 * @param chanceScale share of each rule's chance an aftershock pass uses
	 */
	public record Aftershocks(int generations, float chanceScale) {
		public static final Aftershocks NONE = new Aftershocks(0, 0f);
		/** A generation's salt offset: far from any shipped rule's salt, so passes never pick the same chunks. */
		public static final long SALT_STEP = 0x5EED_A57L;
		/** No world ever has more passes than this, however long it runs: each pass adds planning work per player. */
		public static final int MAX_GENERATIONS = 24;
		/** Aftershock sites keep this far from any placed site, so a new crash never lands on an old one. */
		public static final int MIN_SPACING = 24;
	}

	private static volatile java.util.function.Function<MinecraftServer, Aftershocks> aftershocks = server -> Aftershocks.NONE;

	private Sites() {
	}

	/** Registers the story's aftershock schedule; null turns aftershocks off. */
	public static void setAftershocks(java.util.function.Function<MinecraftServer, Aftershocks> source) {
		aftershocks = source == null ? server -> Aftershocks.NONE : source;
	}

	/** The aftershock passes on {@code server} right now. Guarded: a failing hook means no aftershocks. */
	public static Aftershocks aftershocks(MinecraftServer server) {
		try {
			Aftershocks a = aftershocks.apply(server);
			return a == null ? Aftershocks.NONE
					: new Aftershocks(Math.clamp(a.generations(), 0, Aftershocks.MAX_GENERATIONS), Math.clamp(a.chanceScale(), 0f, 1f));
		} catch (RuntimeException e) {
			return Aftershocks.NONE;
		}
	}

	/**
	 * Aftershock sites within {@code radius} of (x, z): every generation of every rule, re-keyed with
	 * {@link CrashSitePlanner#aftershockKey}. The planned {@code rule} stays the original id, so placement finds its guards.
	 */
	public static List<CrashSitePlanner.Planned> aftershockSitesNear(ServerLevel level, int x, int z, int radius) {
		Aftershocks a = aftershocks(level.getServer());
		List<CrashSitePlanner.Planned> out = new ArrayList<>();
		for (int g = 1; g <= a.generations(); g++) {
			for (Map.Entry<String, CrashSiteRule> e : rules.entrySet()) {
				CrashSiteRule rule = CrashSitePlanner.aftershockRule(e.getValue(), g, a.chanceScale(), Aftershocks.SALT_STEP);
				for (CrashSitePlanner.Planned p : CrashSitePlanner.near(level.getSeed(), Map.of(e.getKey(), rule), x, z, radius,
						(bx, bz) -> ZoneMap.zoneAt(bx, bz).id(), filter(level, rule))) {
					out.add(new CrashSitePlanner.Planned(CrashSitePlanner.aftershockKey(g, p), p.rule(), p.template(), p.chunkX(), p.chunkZ(),
							p.centerX(), p.centerZ(), p.lootSeed()));
				}
			}
		}
		return out;
	}

	/** Whether any placed site's centre is within {@link Aftershocks#MIN_SPACING} blocks of (x, z). */
	static boolean crowded(SiteState state, int x, int z) {
		long min = (long) Aftershocks.MIN_SPACING * Aftershocks.MIN_SPACING;
		for (PlacedSite site : state.all()) {
			long dx = site.center().getX() - x, dz = site.center().getZ() - z;
			if (dx * dx + dz * dz < min) {
				return true;
			}
		}
		return false;
	}

	public static void register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(AshfallSalvage.id("site_templates"), new TemplateLoader());
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(AshfallSalvage.id("crash_sites"), new RuleLoader());
		ServerTickEvents.END_SERVER_TICK.register(Sites::tick);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			synchronized (RESOLVED) {
				RESOLVED.clear();
			}
		});
	}

	public static Map<Identifier, SiteTemplate> templates() {
		return templates;
	}

	public static Map<String, CrashSiteRule> rules() {
		return rules;
	}

	/** True where sites are placed: the overworld of a world generated with our Lost Cities profile. */
	public static boolean enabled(ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD) {
			return false;
		}
		Boolean test = enabledOverride;
		return test != null ? test : LostCityContext.isAshfall(level);
	}

	/** Test hook: forces sites on or off regardless of the Lost Cities profile; null restores the default. */
	public static void setEnabledForTesting(Boolean enabled) {
		enabledOverride = enabled;
	}

	/** Every fixed site of the loaded zone layout, by site id. */
	public static Map<String, Zone.FixedSite> fixedSites() {
		Map<String, Zone.FixedSite> out = new LinkedHashMap<>();
		for (Zone zone : ZoneMap.current().zones()) {
			for (Zone.FixedSite site : zone.sites()) {
				out.putIfAbsent(site.id(), site);
			}
		}
		return out;
	}

	/**
	 * The chunk filter real worlds use: never on a Lost Cities building (the wasteland still has a few), and
	 * never within a rule's {@code avoid_fixed} of a fixed site, so the hull keeps its clearing.
	 */
	public static CrashSitePlanner.ChunkFilter filter(ServerLevel level, CrashSiteRule rule) {
		List<Zone.FixedSite> fixed = List.copyOf(fixedSites().values());
		return (cx, cz) -> {
			int x = (cx << 4) + 8, z = (cz << 4) + 8;
			for (Zone.FixedSite site : fixed) {
				long dx = site.x() - x, dz = site.z() - z;
				if (dx * dx + dz * dz < (long) rule.avoidFixed() * rule.avoidFixed()) {
					return false;
				}
			}
			return LostCityContext.chunk(level, cx, cz).map(c -> !c.hasBuilding()).orElse(true);
		};
	}

	/** Planned crash sites within {@code radius} of (x, z) in this level's world (seed, zones, filter). */
	public static List<CrashSitePlanner.Planned> plannedNear(ServerLevel level, int x, int z, int radius) {
		List<CrashSitePlanner.Planned> out = new ArrayList<>();
		for (Map.Entry<String, CrashSiteRule> e : rules.entrySet()) {
			out.addAll(CrashSitePlanner.near(level.getSeed(), Map.of(e.getKey(), e.getValue()), x, z, radius,
					(bx, bz) -> ZoneMap.zoneAt(bx, bz).id(), filter(level, e.getValue())));
		}
		return out;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (level == null || level.players().isEmpty() || !enabled(level)) {
			return;
		}
		SiteState state = SiteState.get(server);
		for (ServerPlayer player : level.players()) {
			int px = player.getBlockX(), pz = player.getBlockZ();
			for (Zone.FixedSite site : fixedSites().values()) {
				long dx = site.x() - px, dz = site.z() - pz;
				if (dx * dx + dz * dz <= (long) PLACE_RADIUS * PLACE_RADIUS && !state.placed(PlacedSite.fixedKey(site.id()))) {
					placeFixedIfLoaded(level, site);
				}
			}
			for (CrashSitePlanner.Planned planned : plannedNear(level, px, pz, PLACE_RADIUS)) {
				if (!state.placed(planned.key())) {
					placeCrashSiteIfLoaded(level, planned);
				}
			}
			for (CrashSitePlanner.Planned planned : aftershockSitesNear(level, px, pz, PLACE_RADIUS)) {
				if (!state.placed(planned.key()) && !crowded(state, planned.centerX(), planned.centerZ())) {
					placeCrashSiteIfLoaded(level, planned);
				}
			}
		}
	}

	private static void placeFixedIfLoaded(ServerLevel level, Zone.FixedSite site) {
		SiteTemplate template = template(site.template());
		if (template != null && footprintLoaded(level, template, site.x(), site.z())) {
			placeFixed(level, site);
		}
	}

	private static void placeCrashSiteIfLoaded(ServerLevel level, CrashSitePlanner.Planned planned) {
		SiteTemplate template = template(planned.template());
		if (template != null && footprintLoaded(level, template, planned.centerX(), planned.centerZ())) {
			placeCrashSite(level, planned);
		}
	}

	/** Places a fixed site (the hull) now, loading its chunks if needed. Commands and tests use this directly. */
	public static PlacedSite placeFixed(ServerLevel level, Zone.FixedSite site) {
		return place(level, PlacedSite.fixedKey(site.id()), site.template(), site.x(), site.z(),
				CrashSitePlanner.mix(level.getSeed() ^ site.id().hashCode()), CrashSiteRule.Guards.NONE, 0);
	}

	/** Places a planned crash site now, with its rule's guard count rolled from the site's own seed. */
	public static PlacedSite placeCrashSite(ServerLevel level, CrashSitePlanner.Planned planned) {
		CrashSiteRule rule = rules.get(planned.rule());
		CrashSiteRule.Guards guards = rule == null ? CrashSiteRule.Guards.NONE : rule.guards();
		int span = Math.max(0, guards.max() - guards.min());
		int count = guards.min() + (span == 0 ? 0 : (int) Math.floorMod(planned.lootSeed() >>> 7, (long) span + 1));
		return place(level, planned.key(), planned.template(), planned.centerX(), planned.centerZ(), planned.lootSeed(),
				guards, count);
	}

	public static SiteTemplate template(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? null : templates.get(key);
	}

	private static boolean footprintLoaded(ServerLevel level, SiteTemplate t, int centerX, int centerZ) {
		int minX = centerX - t.width() / 2, minZ = centerZ - t.depth() / 2;
		int maxX = minX + t.width() - 1, maxZ = minZ + t.depth() - 1;
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Places template {@code templateId} centred on column (centerX, centerZ) and records it under {@code key}.
	 * The surface is the lowest ground (heightmap, leaves ignored) under the template's corners and centre, so
	 * on a slope the site digs into the hill instead of floating off it.
	 *
	 * @return the record, or null when the template is unknown or its palette does not resolve
	 */
	public static PlacedSite place(ServerLevel level, String key, String templateId, int centerX, int centerZ, long lootSeed,
			CrashSiteRule.Guards guards, int guardCount) {
		SiteTemplate t = template(templateId);
		if (t == null) {
			AshfallSalvage.LOGGER.warn("Site {}: template {} is not loaded", key, templateId);
			return null;
		}
		int minX = centerX - t.width() / 2, minZ = centerZ - t.depth() / 2;
		int maxX = minX + t.width() - 1, maxZ = minZ + t.depth() - 1;
		// Load the footprint first: Level#getHeight answers the world's minimum Y for a chunk that is not loaded,
		// which buried a command-placed hull at bedrock in the Phase 2 playtest. The lazy placer only calls this
		// once the footprint is loaded, so for it this is a no-op.
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
		int surface = Integer.MAX_VALUE;
		for (int[] p : new int[][]{{minX, minZ}, {maxX, minZ}, {minX, maxZ}, {maxX, maxZ}, {centerX, centerZ}}) {
			surface = Math.min(surface, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p[0], p[1]));
		}
		BlockPos origin = new BlockPos(minX, surface - t.sink(), minZ);
		return placeAt(level, key, Identifier.parse(templateId), t, origin, new BlockPos(centerX, surface, centerZ), lootSeed,
				guards, guardCount);
	}

	/**
	 * Places a template with its (0, 0, 0) cell at {@code origin}, with no surface search. Gametests use it to
	 * stamp a site into their own test area.
	 */
	public static PlacedSite placeAt(ServerLevel level, String key, Identifier templateId, SiteTemplate t, BlockPos origin,
			BlockPos center, long lootSeed, CrashSiteRule.Guards guards, int guardCount) {
		Map<Character, BlockState> states = resolve(templateId, t);
		if (states == null) {
			return null;
		}
		Map<String, BlockPos> markers = new TreeMap<>();
		List<BlockPos> chests = new ArrayList<>();
		List<SiteTemplate.Cell> cells = t.cells();
		int index = 0;
		for (SiteTemplate.Cell cell : cells) {
			BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
			level.setBlock(pos, states.get(cell.symbol()), FLAGS);
			String symbol = String.valueOf(cell.symbol());
			String marker = t.markers().get(symbol);
			if (marker != null) {
				markers.putIfAbsent(marker, pos.immutable());
			}
			String table = t.loot().get(symbol);
			if (table != null && armLoot(level, pos, Identifier.parse(table), lootSeed + index)) {
				chests.add(pos.immutable());
			}
			index++;
		}
		pourFoundation(level, t, origin);
		// Now that every cell is down, let blocks that connect (bars, panes, walls) settle against each other.
		for (SiteTemplate.Cell cell : cells) {
			BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
			BlockState placed = level.getBlockState(pos);
			BlockState settled = Block.updateFromNeighbourShapes(placed, level, pos);
			if (settled != placed && !settled.isAir()) {
				level.setBlock(pos, settled, FLAGS);
			}
		}
		PlacedSite site = new PlacedSite(key, templateId.toString(), origin, center, markers, chests,
				guards.max() > 0 ? guardCount : 0, guards.radius());
		SiteState.get(level.getServer()).put(site);
		AshfallSalvage.LOGGER.info("Placed site {} ({}) at {}", key, templateId, origin.toShortString());
		PLACED.invoker().onPlaced(level, site);
		return site;
	}

	/**
	 * Points a container at its loot table with a fixed seed, and marks it as Ashfall-managed so the atlas
	 * container pass leaves it alone (it would otherwise give a wasteland-street chest generic building loot).
	 * The record names no building or loot zone, so the refill system never refills it: a crash site's chest
	 * is a one-time find.
	 */
	private static boolean armLoot(ServerLevel level, BlockPos pos, Identifier table, long seed) {
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof RandomizableContainer container)) {
			return false;
		}
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, table);
		container.setLootTable(key, seed);
		be.setAttached(AshfallSalvage.LOOT_STATE, new LootState(table, Optional.empty(), Optional.empty(), -1, 0));
		be.setChanged();
		return true;
	}

	private static void pourFoundation(ServerLevel level, SiteTemplate t, BlockPos origin) {
		if (t.foundation().isEmpty()) {
			return;
		}
		BlockState foundation = parse(t.foundation());
		if (foundation == null) {
			return;
		}
		for (SiteTemplate.Cell cell : t.cells()) {
			if (cell.y() != 0 || level.getBlockState(origin.offset(cell.x(), 0, cell.z())).isAir()) {
				continue;
			}
			BlockPos.MutableBlockPos p = origin.offset(cell.x(), -1, cell.z()).mutable();
			for (int i = 0; i < MAX_FOUNDATION && level.getBlockState(p).canBeReplaced(); i++) {
				level.setBlock(p, foundation, FLAGS);
				p.move(0, -1, 0);
			}
		}
	}

	/** The template's palette as block states, parsed once per reload; null (logged) if any entry is invalid. */
	static Map<Character, BlockState> resolve(Identifier id, SiteTemplate t) {
		synchronized (RESOLVED) {
			Map<Character, BlockState> cached = RESOLVED.get(id);
			if (cached != null) {
				return cached;
			}
			Map<Character, BlockState> out = new HashMap<>();
			for (Map.Entry<String, String> e : t.palette().entrySet()) {
				BlockState state = parse(e.getValue());
				if (state == null) {
					AshfallSalvage.LOGGER.error("Site template {}: palette '{}' = {} is not a valid block state", id, e.getKey(), e.getValue());
					return null;
				}
				out.put(e.getKey().charAt(0), state);
			}
			RESOLVED.put(id, out);
			return out;
		}
	}

	static BlockState parse(String state) {
		try {
			return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, state, false).blockState();
		} catch (CommandSyntaxException e) {
			return null;
		}
	}

	/** Loads {@code data/<ns>/ashfall/site_template/*.json}. */
	static final class TemplateLoader extends SimpleJsonResourceReloadListener<SiteTemplate> {
		TemplateLoader() {
			super(SiteTemplate.CODEC, FileToIdConverter.json("ashfall/site_template"));
		}

		@Override
		protected void apply(Map<Identifier, SiteTemplate> loaded, ResourceManager manager, ProfilerFiller profiler) {
			templates = Map.copyOf(loaded);
			synchronized (RESOLVED) {
				RESOLVED.clear();
			}
			AshfallSalvage.LOGGER.info("Loaded {} site templates", loaded.size());
		}
	}

	/** Loads {@code data/<ns>/ashfall/crash_sites/*.json}; sorted by id so planning order is stable. */
	static final class RuleLoader extends SimpleJsonResourceReloadListener<CrashSiteRule> {
		RuleLoader() {
			super(CrashSiteRule.CODEC, FileToIdConverter.json("ashfall/crash_sites"));
		}

		@Override
		protected void apply(Map<Identifier, CrashSiteRule> loaded, ResourceManager manager, ProfilerFiller profiler) {
			Map<String, CrashSiteRule> sorted = new TreeMap<>();
			loaded.forEach((id, rule) -> sorted.put(id.toString(), rule));
			rules = java.util.Collections.unmodifiableMap(sorted);
			AshfallSalvage.LOGGER.info("Loaded {} crash-site rules", sorted.size());
		}
	}
}
