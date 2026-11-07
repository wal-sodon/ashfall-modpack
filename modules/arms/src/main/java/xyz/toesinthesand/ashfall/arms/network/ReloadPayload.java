package xyz.toesinthesand.ashfall.arms.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: the player pressed the reload key. Carries nothing; the server reloads whatever gun is held. */
public record ReloadPayload() implements CustomPacketPayload {
	public static final ReloadPayload INSTANCE = new ReloadPayload();
	public static final Type<ReloadPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "reload"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ReloadPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<ReloadPayload> type() {
		return TYPE;
	}
}
