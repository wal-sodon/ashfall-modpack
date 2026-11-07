package xyz.toesinthesand.ashfall.salvage.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S {@code ashfall:loot_zone_action}: one button press in the Loot Zone screen. The server checks operator
 * level 2 on every one of these (see {@link LootZoneAdmin}).
 *
 * @param zoneId the zone acted on; for {@link Action#SAVE} empty means "create {@code form.id()}"
 * @param form   the edited zone, only read by {@link Action#SAVE}
 * @param text   pasted JSON for {@link Action#IMPORT_TEXT}
 */
public record LootZoneActionPayload(Action action, String zoneId, LootZoneForm form, String text) implements CustomPacketPayload {
	public enum Action {
		/** Open the screen: zones plus the loot table ids for autocomplete. */
		OPEN,
		REFRESH,
		SAVE,
		SCAN,
		REFILL,
		DELETE,
		TELEPORT,
		EXPORT,
		IMPORT_TEXT,
		IMPORT_FILE;

		static final Codec<Action> CODEC = Codec.STRING.comapFlatMap(s -> {
			try {
				return DataResult.success(Action.valueOf(s));
			} catch (IllegalArgumentException e) {
				return DataResult.error(() -> "unknown loot zone action " + s);
			}
		}, Action::name);
	}

	/** Serverbound custom payloads are capped near 32 KiB; longer pastes must use the export file instead. */
	public static final int MAX_TEXT = 30_000;

	public static final Type<LootZoneActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "loot_zone_action"));
	static final Codec<LootZoneActionPayload> DATA_CODEC = RecordCodecBuilder.create(i -> i.group(
			Action.CODEC.fieldOf("action").forGetter(LootZoneActionPayload::action),
			Codec.STRING.optionalFieldOf("zone", "").forGetter(LootZoneActionPayload::zoneId),
			LootZoneForm.CODEC.optionalFieldOf("form", LootZoneForm.BLANK).forGetter(LootZoneActionPayload::form),
			Codec.string(0, MAX_TEXT).optionalFieldOf("text", "").forGetter(LootZoneActionPayload::text)
	).apply(i, LootZoneActionPayload::new));
	public static final StreamCodec<ByteBuf, LootZoneActionPayload> CODEC = ByteBufCodecs.fromCodec(DATA_CODEC);

	public static LootZoneActionPayload of(Action action, String zoneId) {
		return new LootZoneActionPayload(action, zoneId, LootZoneForm.BLANK, "");
	}

	@Override
	public Type<LootZoneActionPayload> type() {
		return TYPE;
	}
}
