package xyz.toesinthesand.ashfall.arms.finale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The world's Black Start ({@code data/ashfall_finale.dat}): the one run (there is one Wreck), and the hull's
 * state. World-wide like the grid, because the finale is the whole server's.
 * <p>
 * Hull flags: {@code hullPrepared} once arms has swapped the placed hull's placeholders for the Uplink Socket and the
 * Hull Seal; {@code hullOpen} once the seal has really been removed in the world. Opening is asked for by the grid
 * reaching 80% (or {@code /ashfall finale open}) and happens the next time the hatch's chunk is loaded.
 */
public final class FinaleState extends SavedData {
	public static final Codec<FinaleState> CODEC = RecordCodecBuilder.create(i -> i.group(
			// Not optionalFieldOf("run", new FinaleRun()): that one default object would be shared by every world loaded
			// without the field and, being the default, never written back (see FinaleRun's clocks). A fresh run instead.
			FinaleRun.CODEC.optionalFieldOf("run").forGetter(s -> java.util.Optional.of(s.run)),
			Codec.BOOL.optionalFieldOf("hull_prepared", false).forGetter(s -> s.hullPrepared),
			Codec.BOOL.optionalFieldOf("hull_open", false).forGetter(s -> s.hullOpen),
			Codec.BOOL.optionalFieldOf("open_requested", false).forGetter(s -> s.openRequested)
	).apply(i, FinaleState::new));

	/** Same schema-less fixer choice as the other Ashfall saved data, so no vanilla fix runs. */
	public static final SavedDataType<FinaleState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_finale"), FinaleState::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private FinaleRun run;
	private boolean hullPrepared;
	private boolean hullOpen;
	private boolean openRequested;

	public FinaleState() {
		this(new FinaleRun(), false, false, false);
	}

	private FinaleState(java.util.Optional<FinaleRun> run, boolean prepared, boolean open, boolean requested) {
		this(run.orElseGet(FinaleRun::new), prepared, open, requested);
	}

	private FinaleState(FinaleRun run, boolean prepared, boolean open, boolean requested) {
		this.run = run;
		this.hullPrepared = prepared;
		this.hullOpen = open;
		this.openRequested = requested;
	}

	public static FinaleState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public FinaleRun run() {
		return run;
	}

	/** Call after changing the run in place. */
	public void changed() {
		setDirty();
	}

	public boolean hullPrepared() {
		return hullPrepared;
	}

	public void setHullPrepared(boolean prepared) {
		if (prepared != hullPrepared) {
			hullPrepared = prepared;
			setDirty();
		}
	}

	public boolean hullOpen() {
		return hullOpen;
	}

	public void setHullOpen(boolean open) {
		if (open != hullOpen) {
			hullOpen = open;
			setDirty();
		}
	}

	public boolean openRequested() {
		return openRequested;
	}

	public void requestOpen(boolean requested) {
		if (requested != openRequested) {
			openRequested = requested;
			setDirty();
		}
	}
}
