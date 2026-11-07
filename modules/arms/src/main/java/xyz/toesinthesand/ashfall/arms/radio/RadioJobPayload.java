package xyz.toesinthesand.ashfall.arms.radio;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * S2C {@code ashfall:radio_job}: the job under way, for the HUD line under the objective and the Grid Map marker.
 * Sent when it changes; the client works out distance, heading and time left itself.
 */
public record RadioJobPayload(Optional<View> job) implements CustomPacketPayload {
	public static final Type<RadioJobPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "radio_job"));

	/**
	 * @param type     {@link RadioJob.Type#id()}
	 * @param profile  the building's profile path
	 * @param district the zone id
	 * @param deadline game time the job is lost at
	 */
	public record View(String type, String profile, String district, int x, int z, long deadline) {
		public static final StreamCodec<ByteBuf, View> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, View::type,
				ByteBufCodecs.STRING_UTF8, View::profile,
				ByteBufCodecs.STRING_UTF8, View::district,
				ByteBufCodecs.VAR_INT, View::x,
				ByteBufCodecs.VAR_INT, View::z,
				ByteBufCodecs.VAR_LONG, View::deadline,
				View::new);

		static View of(RadioJob job) {
			return new View(job.type().id(), job.profile(), job.district(), job.x(), job.z(), job.deadline());
		}
	}

	public static final StreamCodec<ByteBuf, RadioJobPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.optional(View.CODEC), RadioJobPayload::job,
			RadioJobPayload::new);

	@Override
	public Type<RadioJobPayload> type() {
		return TYPE;
	}
}
