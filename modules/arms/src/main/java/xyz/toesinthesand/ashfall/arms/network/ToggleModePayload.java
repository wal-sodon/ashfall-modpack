package xyz.toesinthesand.ashfall.arms.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: the player pressed the fire mode key. Carries nothing; the server switches whatever gun is held. */
public record ToggleModePayload() implements CustomPacketPayload {
	public static final ToggleModePayload INSTANCE = new ToggleModePayload();
	public static final Type<ToggleModePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "toggle_fire_mode"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleModePayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<ToggleModePayload> type() {
		return TYPE;
	}
}
