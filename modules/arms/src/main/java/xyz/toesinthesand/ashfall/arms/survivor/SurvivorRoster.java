package xyz.toesinthesand.ashfall.arms.survivor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.haven.BunkerCores;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Every living survivor in the world, with its leader and home ({@code data/ashfall_survivors.dat}), so the caps can be
 * counted while survivors sit in unloaded chunks. A survivor writes itself in whenever its leader or home changes and on
 * its periodic home check (which also adopts survivors from spawn eggs or older saves), and strikes itself off when it
 * dies, turns or is discarded ({@link Survivor#onRemoval}); unloading keeps the entry.
 * The caps themselves are in {@link SurvivorBalance.Caps}: a player's group (their own and their OPAC party's
 * survivors, settled or following) takes no more rescues past {@code group}, a bunker no more settlers past
 * {@code base}, and past {@code world} (a sanity limit for the whole server, counting every survivor but holdouts still
 * hiding unfound) distress calls only leave the note.
 */
public final class SurvivorRoster extends SavedData {
	/** One survivor's entry; {@code holdout} while it is still hiding, unfound ({@link Survivor#holdout}). */
	public record Entry(UUID id, Optional<UUID> leader, Optional<GlobalPos> home, boolean holdout) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
				UUIDUtil.CODEC.optionalFieldOf("leader").forGetter(Entry::leader),
				GlobalPos.CODEC.optionalFieldOf("home").forGetter(Entry::home),
				Codec.BOOL.optionalFieldOf("holdout", false).forGetter(Entry::holdout)
		).apply(i, Entry::new));
	}

	public static final Codec<SurvivorRoster> CODEC = RecordCodecBuilder.create(i -> i.group(
			Entry.CODEC.listOf().optionalFieldOf("survivors", List.of()).forGetter(r -> List.copyOf(r.entries.values()))
	).apply(i, SurvivorRoster::new));

	public static final SavedDataType<SurvivorRoster> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_survivors"), SurvivorRoster::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, Entry> entries = new LinkedHashMap<>();

	public SurvivorRoster() {
	}

	private SurvivorRoster(List<Entry> list) {
		for (Entry e : list) {
			entries.put(e.id(), e);
		}
	}

	public static SurvivorRoster get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Writes {@code survivor}'s current leader and home in (no-op, and nothing dirtied, when unchanged). */
	public void note(Survivor survivor) {
		BlockPos home = survivor.home();
		Entry next = new Entry(survivor.getUUID(), Optional.ofNullable(survivor.leader()),
				home == null ? Optional.empty() : Optional.of(GlobalPos.of(survivor.level().dimension(), home)),
				survivor.holdout() && survivor.leader() == null);
		if (!next.equals(entries.put(next.id(), next))) {
			setDirty();
		}
	}

	public void forget(UUID id) {
		if (entries.remove(id) != null) {
			setDirty();
		}
	}

	public boolean contains(UUID id) {
		return entries.containsKey(id);
	}

	/** Every survivor on the roster, hiding holdouts included. */
	public int total() {
		return entries.size();
	}

	/**
	 * Survivors that count toward the world cap: everyone but holdouts still hiding unfound, so holdouts nobody went
	 * upstairs for never block rescues ({@link #roomFor}).
	 */
	public int counted() {
		int n = 0;
		for (Entry e : entries.values()) {
			n += e.holdout() ? 0 : 1;
		}
		return n;
	}

	/** Holdouts still hiding unfound; at the world cap of them no more hide ({@link SurvivorHoldouts#tryHoldout}). */
	public int hiding() {
		return total() - counted();
	}

	/** Survivors that call the Bunker Core at {@code pos} home. */
	public int homedAt(ResourceKey<Level> dimension, BlockPos pos) {
		GlobalPos at = GlobalPos.of(dimension, pos);
		int n = 0;
		for (Entry e : entries.values()) {
			if (e.home().filter(at::equals).isPresent()) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Where {@code id} stands among the survivors homed at the core at {@code pos}, 0 for the one longest on the roster
	 * (upkeep rises for the later ones, {@link SurvivorUpkeep}); -1 when it is not homed there.
	 */
	public int homedRank(ResourceKey<Level> dimension, BlockPos pos, UUID id) {
		GlobalPos at = GlobalPos.of(dimension, pos);
		int n = 0;
		for (Entry e : entries.values()) {
			if (e.home().filter(at::equals).isPresent()) {
				if (e.id().equals(id)) {
					return n;
				}
				n++;
			}
		}
		return -1;
	}

	/** Survivors led by {@code player} or anyone in their OPAC party (the same key Bunker Cores are owned by). */
	public int groupOf(MinecraftServer server, UUID player) {
		UUID key = BunkerCores.ownerKey(server, player);
		int n = 0;
		for (Entry e : entries.values()) {
			@Nullable UUID leader = e.leader().orElse(null);
			if (leader != null && Objects.equals(key, BunkerCores.ownerKey(server, leader))) {
				n++;
			}
		}
		return n;
	}

	/** Whether {@code player}'s group has room for one more survivor, here and world-wide. */
	public boolean roomFor(MinecraftServer server, UUID player) {
		return worldHasRoom() && groupHasRoom(server, player);
	}

	/** Under the world cap ({@link #counted}: hiding holdouts left out). */
	public boolean worldHasRoom() {
		return counted() < SurvivorBalance.get().caps().world();
	}

	/** {@code player}'s group (with their OPAC party) is under its cap. */
	public boolean groupHasRoom(MinecraftServer server, UUID player) {
		return groupOf(server, player) < SurvivorBalance.get().caps().group();
	}
}
