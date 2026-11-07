package xyz.toesinthesand.ashfall.arms.mission;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * S2C {@code ashfall:objective}: the player's current objective and their Bunker Core, sent on join and whenever
 * either changes (checked every {@link MissionSettings#syncIntervalTicks}).
 * <p>
 * The server computes, the client only draws: the objective needs saved data the client never sees (hold ticks,
 * relay links, boss records). Distance and heading are worked out on the client from {@code target} each frame, so
 * walking around never needs a packet.
 *
 * @param prompts false when {@code ashfall:objective} is off: the client hides the HUD line
 * @param step    the objective's step id ({@link MissionStep#id()})
 * @param key     the objective's text key, without the {@code objective.ashfall.} prefix
 * @param args    the text's arguments
 * @param target  where the objective points, if anywhere
 * @param bunker  the player's own or party's Bunker Core, for the Grid Map marker
 */
public record MissionSyncPayload(boolean prompts, String step, String key, List<ObjectiveArg> args, Optional<MissionPos> target,
		Optional<MissionPos> bunker) implements CustomPacketPayload {
	public static final Type<MissionSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "objective"));

	public static final StreamCodec<ByteBuf, ObjectiveArg> ARG_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, ObjectiveArg::translate,
			ByteBufCodecs.STRING_UTF8, ObjectiveArg::value,
			ObjectiveArg::new);
	public static final StreamCodec<ByteBuf, MissionPos> POS_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MissionPos::x,
			ByteBufCodecs.VAR_INT, MissionPos::z,
			MissionPos::new);
	public static final StreamCodec<ByteBuf, MissionSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, MissionSyncPayload::prompts,
			ByteBufCodecs.STRING_UTF8, MissionSyncPayload::step,
			ByteBufCodecs.STRING_UTF8, MissionSyncPayload::key,
			ARG_CODEC.apply(ByteBufCodecs.list(16)), MissionSyncPayload::args,
			ByteBufCodecs.optional(POS_CODEC), MissionSyncPayload::target,
			ByteBufCodecs.optional(POS_CODEC), MissionSyncPayload::bunker,
			MissionSyncPayload::new);

	public MissionSyncPayload {
		args = List.copyOf(args);
	}

	/** The payload for {@code objective}. */
	public static MissionSyncPayload of(boolean prompts, Objective objective, Optional<MissionPos> bunker) {
		return new MissionSyncPayload(prompts, objective.step().id(), objective.key(), objective.args(), objective.target(), bunker);
	}

	/** The objective this payload carries, rebuilt on the client. */
	public Objective objective() {
		MissionStep s = MissionStep.AFTER;
		for (MissionStep candidate : MissionStep.values()) {
			if (candidate.id().equals(step)) {
				s = candidate;
			}
		}
		return new Objective(s, key, args, target);
	}

	@Override
	public Type<MissionSyncPayload> type() {
		return TYPE;
	}
}
