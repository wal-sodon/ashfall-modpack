package xyz.toesinthesand.ashfall.arms.drops;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * C2S {@code ashfall:mob_drops_action}: a button in the Mob Drops screen. Permission-checked on the server
 * ({@link MobDropsAdmin}) like every admin payload.
 *
 * @param entity the entity id the action is about (SAVE and RESET)
 * @param drops  the full new drop list for SAVE (replaces the entity's rule)
 */
public record MobDropsActionPayload(Action action, String entity, List<DropForm> drops) implements CustomPacketPayload {
	public enum Action {
		OPEN,
		REFRESH,
		/** Write the entity's rule into the override datapack and reload. */
		SAVE,
		/** Move the entity's override aside (never deleted) and reload, back to the shipped rule. */
		RESET;

		static final Codec<Action> CODEC = Codec.STRING.comapFlatMap(s -> {
			try {
				return DataResult.success(Action.valueOf(s));
			} catch (IllegalArgumentException e) {
				return DataResult.error(() -> "unknown mob drops action " + s);
			}
		}, Action::name);
	}

	public static final int MAX_DROPS = 16;

	public static final Type<MobDropsActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "mob_drops_action"));
	static final Codec<MobDropsActionPayload> DATA_CODEC = RecordCodecBuilder.create(i -> i.group(
			Action.CODEC.fieldOf("action").forGetter(MobDropsActionPayload::action),
			Codec.STRING.optionalFieldOf("entity", "").forGetter(MobDropsActionPayload::entity),
			DropForm.CODEC.sizeLimitedListOf(MAX_DROPS).optionalFieldOf("drops", List.of()).forGetter(MobDropsActionPayload::drops)
	).apply(i, MobDropsActionPayload::new));
	public static final StreamCodec<ByteBuf, MobDropsActionPayload> CODEC = ByteBufCodecs.fromCodec(DATA_CODEC);

	public static MobDropsActionPayload of(Action action, String entity) {
		return new MobDropsActionPayload(action, entity, List.of());
	}

	@Override
	public Type<MobDropsActionPayload> type() {
		return TYPE;
	}
}
