package xyz.toesinthesand.ashfall.director.pets;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;

/**
 * Which stray-dog cells already had their dog ({@code data/ashfall/ashfall_stray_dogs.dat}). Each cell gets one
 * stray, ever: a dog that was tamed and led away, or killed, is not replaced, so strays can't be farmed.
 */
public final class StrayDogState extends SavedData {
	public static final Codec<StrayDogState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.listOf().optionalFieldOf("seeded", List.of()).forGetter(s -> List.copyOf(s.seeded))
	).apply(i, StrayDogState::new));

	/** Same fixer choice as {@code DirectorState}: a non-null, schema-less type so no vanilla fix runs. */
	public static final SavedDataType<StrayDogState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_stray_dogs"), StrayDogState::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final LongSet seeded = new LongOpenHashSet();

	public StrayDogState() {
	}

	private StrayDogState(List<Long> cells) {
		seeded.addAll(cells);
	}

	public static StrayDogState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean seeded(long cell) {
		return seeded.contains(cell);
	}

	public void markSeeded(long cell) {
		if (seeded.add(cell)) {
			setDirty();
		}
	}

	/** Test hook: lets a cell have its dog again. */
	public void forget(long cell) {
		if (seeded.remove(cell)) {
			setDirty();
		}
	}
}
