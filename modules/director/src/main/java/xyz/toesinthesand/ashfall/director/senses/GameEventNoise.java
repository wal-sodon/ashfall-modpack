package xyz.toesinthesand.ashfall.director.senses;

import xyz.toesinthesand.ashfall.director.item.DirectorArmor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns vanilla game events (doors, block breaking, explosions, eating, sprinting...) into SOUND
 * stimuli. Vanilla already routes every audible world event through {@code ServerLevel#gameEvent} for
 * the sculk sensors and the warden, so one hook there ({@code ServerLevelGameEventMixin}) hears all of
 * them, including other mods' blocks that behave like vanilla ones.
 * <p>
 * The event to radius table is datapack data, {@code data/<ns>/ashfall/noise/*.json}, each file holding
 * {@code {"events": {"minecraft:block_open": {"radius": 10}, ...}}}. Files merge in id order, so a pack
 * can override one entry or silence it with radius 0.
 * <p>
 * The hook runs for every game event in the world (mobs stepping included), so the hot path is a single
 * identity-map lookup that misses for anything not in the table.
 */
public final class GameEventNoise extends SimpleJsonResourceReloadListener<GameEventNoise.Table> {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "noise");
	public static final String DIRECTORY = "ashfall/noise";

	/**
	 * One row of the table.
	 *
	 * @param radius        stimulus radius in blocks; 0 disables the event
	 * @param playersOnly   only count events a player caused (so cows eating grass stay silent)
	 * @param sprintingOnly only count it while the causing entity sprints (footsteps while running)
	 * @param sample        emit for 1 in {@code sample} events, for very frequent ones like steps
	 * @param scavengerMuffle share of the radius each worn Scavenger piece removes (footsteps: soft soles)
	 */
	public record Entry(double radius, boolean playersOnly, boolean sprintingOnly, int sample, float scavengerMuffle) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.doubleRange(0.0, 256.0).fieldOf("radius").forGetter(Entry::radius),
				Codec.BOOL.optionalFieldOf("players_only", false).forGetter(Entry::playersOnly),
				Codec.BOOL.optionalFieldOf("sprinting_only", false).forGetter(Entry::sprintingOnly),
				Codec.intRange(1, 1000).optionalFieldOf("sample", 1).forGetter(Entry::sample),
				Codec.floatRange(0f, 0.25f).optionalFieldOf("scavenger_muffle", 0f).forGetter(Entry::scavengerMuffle)
		).apply(i, Entry::new));
	}

	/** One file: game event id to its row. */
	public record Table(Map<Identifier, Entry> events) {
		public static final Codec<Table> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(Identifier.CODEC, Entry.CODEC).fieldOf("events").forGetter(Table::events)
		).apply(i, Table::new));
	}

	/** Keyed by the registered GameEvent instance so the hot path never allocates or hashes strings. */
	private static volatile Map<GameEvent, Entry> table = Map.of();

	public GameEventNoise() {
		super(Table.CODEC, FileToIdConverter.json(DIRECTORY));
	}

	static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, new GameEventNoise());
	}

	@Override
	protected void apply(Map<Identifier, Table> loaded, ResourceManager manager, ProfilerFiller profiler) {
		Map<GameEvent, Entry> resolved = new IdentityHashMap<>();
		for (Table file : new TreeMap<>(loaded).values()) {
			file.events().forEach((eventId, entry) -> {
				GameEvent event = BuiltInRegistries.GAME_EVENT.getOptional(eventId).orElse(null);
				if (event == null) {
					AshfallDirector.LOGGER.warn("Unknown game event {} in the noise table, ignored", eventId);
				} else if (entry.radius() <= 0.0) {
					resolved.remove(event);
				} else {
					resolved.put(event, entry);
				}
			});
		}
		table = resolved;
		AshfallDirector.LOGGER.info("Loaded {} game event noise entries", resolved.size());
	}

	/** The row for an event, or null when it makes no noise zombies care about. */
	public static Entry entry(Holder<GameEvent> event) {
		return table.get(event.value());
	}

	/** Called for every server-side game event, from {@code ServerLevelGameEventMixin}. */
	public static void onGameEvent(ServerLevel level, Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
		Entry entry = table.get(event.value());
		if (entry == null) {
			return;
		}
		Entity source = context.sourceEntity();
		if (entry.playersOnly() && !(source instanceof Player)) {
			return;
		}
		if (entry.sprintingOnly() && (source == null || !source.isSprinting())) {
			return;
		}
		if (entry.sample() > 1 && level.getRandom().nextInt(entry.sample()) != 0) {
			return;
		}
		double radius = entry.radius();
		if (entry.scavengerMuffle() > 0f && source instanceof Player player) {
			radius *= DirectorArmor.perkFactor(DirectorArmor.pieces(player, DirectorArmor.SCAVENGER_SET), entry.scavengerMuffle());
		}
		Stimuli.emit(level, pos, StimulusType.SOUND, radius, source);
	}
}
