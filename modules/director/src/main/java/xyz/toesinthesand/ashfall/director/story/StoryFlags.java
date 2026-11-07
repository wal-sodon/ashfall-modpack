package xyz.toesinthesand.ashfall.director.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * World-wide story flags ({@code data/ashfall/ashfall_story.dat}): facts about the run that rules elsewhere
 * read, such as {@code ashfall:sever} once the party chose the Sever ending. They are plain namespaced
 * strings in one set, so a later milestone can add a flag (districts relit, Bind chosen) without a new
 * file or a save format change.
 * <p>
 * Lives in director because the first reader, infection, is here; arms can read it through director.
 */
public final class StoryFlags extends SavedData {
	public static final Codec<StoryFlags> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(s -> List.copyOf(s.flags))
	).apply(i, StoryFlags::new));

	/** Same non-null, schema-less fixer choice as the other Ashfall saved data, so no vanilla fix runs. */
	public static final SavedDataType<StoryFlags> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_story"), StoryFlags::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Set<String> flags = new TreeSet<>();

	public StoryFlags() {
	}

	private StoryFlags(List<String> loaded) {
		flags.addAll(loaded);
	}

	public static StoryFlags get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean has(String flag) {
		return flags.contains(flag);
	}

	/** Sets or clears one flag. Returns whether anything changed. */
	public boolean set(String flag, boolean value) {
		boolean changed = value ? flags.add(flag) : flags.remove(flag);
		if (changed) {
			setDirty();
		}
		return changed;
	}

	/** Clears every flag (the testing reset). Returns how many were set. */
	public int clear() {
		int n = flags.size();
		if (n > 0) {
			flags.clear();
			setDirty();
		}
		return n;
	}

	public Set<String> flags() {
		return Set.copyOf(flags);
	}
}
