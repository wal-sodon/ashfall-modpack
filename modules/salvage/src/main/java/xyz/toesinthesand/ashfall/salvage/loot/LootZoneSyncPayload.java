package xyz.toesinthesand.ashfall.salvage.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * S2C {@code ashfall:loot_zone_sync}: the zone list after every admin action, plus the outcome message.
 * <p>
 * The loot table ids for autocomplete only ride along on {@code open} (about 1,500 ids): they change only with a
 * datapack reload, and the screen asks for a fresh copy each time it opens.
 *
 * @param tables    every loot table id the server knows, sorted; empty unless {@code open}
 * @param open      open the screen (an answer to {@code /lootzone gui}, the key, or {@code OPEN})
 * @param error     whether {@code message} reports a failure
 * @param clipboard text for the client to put on the clipboard (an export); empty for none
 */
public record LootZoneSyncPayload(List<LootZoneForm> zones, List<String> tables, boolean open, String message,
								  boolean error, String clipboard) implements CustomPacketPayload {
	public static final Type<LootZoneSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "loot_zone_sync"));
	static final Codec<LootZoneSyncPayload> DATA_CODEC = RecordCodecBuilder.create(i -> i.group(
			LootZoneForm.CODEC.listOf().fieldOf("zones").forGetter(LootZoneSyncPayload::zones),
			Codec.STRING.listOf().optionalFieldOf("tables", List.of()).forGetter(LootZoneSyncPayload::tables),
			Codec.BOOL.optionalFieldOf("open", false).forGetter(LootZoneSyncPayload::open),
			Codec.STRING.optionalFieldOf("message", "").forGetter(LootZoneSyncPayload::message),
			Codec.BOOL.optionalFieldOf("error", false).forGetter(LootZoneSyncPayload::error),
			Codec.STRING.optionalFieldOf("clipboard", "").forGetter(LootZoneSyncPayload::clipboard)
	).apply(i, LootZoneSyncPayload::new));
	public static final StreamCodec<ByteBuf, LootZoneSyncPayload> CODEC = ByteBufCodecs.fromCodec(DATA_CODEC);

	@Override
	public Type<LootZoneSyncPayload> type() {
		return TYPE;
	}
}
