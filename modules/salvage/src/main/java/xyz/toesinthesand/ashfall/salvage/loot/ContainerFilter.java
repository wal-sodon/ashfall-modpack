package xyz.toesinthesand.ashfall.salvage.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Which containers a loot zone claims: {@code "any"} (chests and barrels), one block id, or a block tag
 * ({@code "#ns:path"}).
 * <p>
 * Stored as the plain string an admin typed so zone files and exports stay readable, and so a zone that names a
 * block from a mod that was later removed still loads (it just stops matching) instead of losing the zone.
 *
 * @param spec normalised spec: {@code "any"}, {@code "ns:path"} or {@code "#ns:path"}
 */
public record ContainerFilter(String spec) {
	public static final ContainerFilter ANY = new ContainerFilter(LootZone.ANY);

	public static final Codec<ContainerFilter> CODEC = Codec.STRING.comapFlatMap(ContainerFilter::parse, ContainerFilter::spec);

	/** Parses an admin's input; ids without a namespace get {@code minecraft:}, like everywhere else in the game. */
	public static DataResult<ContainerFilter> parse(String raw) {
		String s = raw == null ? "" : raw.trim();
		if (s.isEmpty() || s.equals(LootZone.ANY) || s.equals("minecraft:" + LootZone.ANY)) {
			return DataResult.success(ANY);
		}
		boolean tag = s.startsWith("#");
		return Identifier.read(tag ? s.substring(1) : s).map(id -> new ContainerFilter((tag ? "#" : "") + id));
	}

	public static ContainerFilter block(Identifier id) {
		return new ContainerFilter(id.toString());
	}

	public static ContainerFilter tag(Identifier id) {
		return new ContainerFilter("#" + id);
	}

	/** The pre-tag representation: empty = any. Kept so older call sites and tests read naturally. */
	public static ContainerFilter of(Optional<Identifier> block) {
		return block.map(ContainerFilter::block).orElse(ANY);
	}

	public boolean isAny() {
		return spec.equals(LootZone.ANY);
	}

	public boolean isTag() {
		return spec.startsWith("#");
	}

	/** The block or tag id; empty for "any". */
	public Optional<Identifier> id() {
		if (isAny()) {
			return Optional.empty();
		}
		return Optional.ofNullable(Identifier.tryParse(isTag() ? spec.substring(1) : spec));
	}

	/**
	 * Whether the block or tag exists right now: blocks are checked against the registry, tags only for a valid
	 * id (tags load with datapacks and an empty tag is legal).
	 */
	public boolean isKnown() {
		if (isAny()) {
			return true;
		}
		if (isTag()) {
			return id().isPresent();
		}
		return id().filter(BuiltInRegistries.BLOCK::containsKey).isPresent();
	}

	/** Explicit blocks and tags match by block; "any" is handled by the caller (it also checks the block entity). */
	public boolean matches(BlockState state) {
		Optional<Identifier> id = id();
		if (id.isEmpty()) {
			return false;
		}
		if (isTag()) {
			TagKey<Block> key = TagKey.create(Registries.BLOCK, id.get());
			return state.is(key);
		}
		return id.get().equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
	}

	@Override
	public String toString() {
		return spec;
	}
}
