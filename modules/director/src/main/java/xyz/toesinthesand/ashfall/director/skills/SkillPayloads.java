package xyz.toesinthesand.ashfall.director.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The Survivor Skills packets. Class and ranks themselves travel in the synced player attachment. */
public final class SkillPayloads {
	private SkillPayloads() {
	}

	/** C2S {@code ashfall:buy_skill}: the Train button. The server checks the class, the cost and the max rank. */
	public record Buy(String skill) implements CustomPacketPayload {
		public static final Type<Buy> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "buy_skill"));
		public static final StreamCodec<ByteBuf, Buy> CODEC = ByteBufCodecs.stringUtf8(64).map(Buy::new, Buy::skill);

		@Override
		public Type<Buy> type() {
			return TYPE;
		}
	}

	/** C2S {@code ashfall:choose_class}: the class picker. A change (not the first pick) needs home and the cooldown. */
	public record Choose(String survivorClass) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "choose_class"));
		public static final StreamCodec<ByteBuf, Choose> CODEC = ByteBufCodecs.stringUtf8(64).map(Choose::new, Choose::survivorClass);

		@Override
		public Type<Choose> type() {
			return TYPE;
		}
	}

	/** S2C {@code ashfall:skill_rules}: costs, classes and values for the screens, sent on join and after a reload. */
	public record Rules(SkillRules rules) implements CustomPacketPayload {
		public static final Type<Rules> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "skill_rules"));
		public static final StreamCodec<ByteBuf, Rules> CODEC = ByteBufCodecs.fromCodec(SkillRules.CODEC).map(Rules::new, Rules::rules);

		@Override
		public Type<Rules> type() {
			return TYPE;
		}
	}

	/** S2C {@code ashfall:pick_class}: opens the class picker on a player who has no class yet. */
	public record PickClass() implements CustomPacketPayload {
		public static final PickClass INSTANCE = new PickClass();
		public static final Type<PickClass> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "pick_class"));
		public static final StreamCodec<ByteBuf, PickClass> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<PickClass> type() {
			return TYPE;
		}
	}
}
