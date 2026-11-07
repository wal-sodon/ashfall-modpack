package xyz.toesinthesand.ashfall.arms.grid;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S2C {@code ashfall:grid_sync}: every district's state and the Grid %, sent on join and after every change. The
 * client keeps the last one for the HUD Grid % and the Grid Map (the client cache lives with the client UI).
 *
 * @param states  district id to state, in district order
 * @param percent the Grid % the server computed (the client never recomputes it)
 */
public record GridSyncPayload(Map<String, DistrictState> states, int percent) implements CustomPacketPayload {
	public static final Type<GridSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "grid_sync"));
	private static final DistrictState[] STATES = DistrictState.values();
	private static final StreamCodec<ByteBuf, DistrictState> STATE_CODEC = ByteBufCodecs.VAR_INT.map(
			i -> i >= 0 && i < STATES.length ? STATES[i] : DistrictState.DARK, DistrictState::ordinal);
	public static final StreamCodec<ByteBuf, GridSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, STATE_CODEC), GridSyncPayload::linkedStates,
			ByteBufCodecs.VAR_INT, GridSyncPayload::percent,
			GridSyncPayload::new);

	public GridSyncPayload {
		states = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(states));
	}

	private LinkedHashMap<String, DistrictState> linkedStates() {
		return new LinkedHashMap<>(states);
	}

	@Override
	public Type<GridSyncPayload> type() {
		return TYPE;
	}
}
