package xyz.toesinthesand.ashfall.arms.supply;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Optional;

/** What the supply-drop scheduler must remember across restarts ({@code data/ashfall_supply_drops.dat}). */
public final class SupplyDropState extends SavedData {
	/** The drop under way: where it aims, when it is lost (game time), and the crate once it has landed. */
	public record Active(int x, int z, long deadline, Optional<BlockPos> crate) {
		public static final Codec<Active> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("x").forGetter(Active::x),
				Codec.INT.fieldOf("z").forGetter(Active::z),
				Codec.LONG.fieldOf("deadline").forGetter(Active::deadline),
				BlockPos.CODEC.optionalFieldOf("crate").forGetter(Active::crate)
		).apply(i, Active::new));

		Active landed(BlockPos pos) {
			return new Active(x, z, deadline, Optional.of(pos));
		}
	}

	public static final Codec<SupplyDropState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("next_day", 0).forGetter(s -> s.nextDay),
			Active.CODEC.optionalFieldOf("active").forGetter(s -> s.active)
	).apply(i, SupplyDropState::new));

	public static final SavedDataType<SupplyDropState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_supply_drops"), SupplyDropState::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private int nextDay;
	private Optional<Active> active = Optional.empty();

	public SupplyDropState() {
	}

	private SupplyDropState(int nextDay, Optional<Active> active) {
		this.nextDay = nextDay;
		this.active = active;
	}

	public static SupplyDropState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** World day of the next scheduled drop; 0 = not scheduled yet. */
	public int nextDay() {
		return nextDay;
	}

	public void setNextDay(int day) {
		nextDay = day;
		setDirty();
	}

	public Optional<Active> active() {
		return active;
	}

	public void setActive(Optional<Active> value) {
		active = value;
		setDirty();
	}
}
