package xyz.toesinthesand.ashfall.arms.drops;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * S2C {@code ashfall:mob_drops_sync}: every entity with Ashfall drop rules and the outcome of the last action.
 * Entity and item ids for autocomplete only ride along on {@code open}.
 */
public record MobDropsSyncPayload(List<MobDropView> rules, List<String> entities, List<String> items, boolean open,
								  String message, boolean error) implements CustomPacketPayload {
	public static final Type<MobDropsSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "mob_drops_sync"));
	static final Codec<MobDropsSyncPayload> DATA_CODEC = RecordCodecBuilder.create(i -> i.group(
			MobDropView.CODEC.listOf().fieldOf("rules").forGetter(MobDropsSyncPayload::rules),
			Codec.STRING.listOf().optionalFieldOf("entities", List.of()).forGetter(MobDropsSyncPayload::entities),
			Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(MobDropsSyncPayload::items),
			Codec.BOOL.optionalFieldOf("open", false).forGetter(MobDropsSyncPayload::open),
			Codec.STRING.optionalFieldOf("message", "").forGetter(MobDropsSyncPayload::message),
			Codec.BOOL.optionalFieldOf("error", false).forGetter(MobDropsSyncPayload::error)
	).apply(i, MobDropsSyncPayload::new));
	public static final StreamCodec<ByteBuf, MobDropsSyncPayload> CODEC = ByteBufCodecs.fromCodec(DATA_CODEC);

	@Override
	public Type<MobDropsSyncPayload> type() {
		return TYPE;
	}
}
