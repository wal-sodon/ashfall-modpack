package xyz.toesinthesand.ashfall.arms.gun;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code ashfall:attachments} component: the ids of the attachments fitted to a gun. A plain list of
 * names keeps the format open without a registry: what each id does is datapack data
 * ({@link AttachmentStats}, applied by {@link AttachmentMath}), and an id with no data does nothing.
 */
public record GunAttachments(List<String> ids) {
	/** Noise x0.4, damage x0.9, volume x0.45 (see {@code gun_attachment/dampener.json}). */
	public static final String DAMPENER = "dampener";
	/** Zoom and tighter spread while aiming. */
	public static final String OPTIC = "optic";
	/** More heat capacity, faster cooling. */
	public static final String HEAT_SINK = "heat_sink";
	/** Less energy per shot; shares the core slot with the Overcharger. */
	public static final String CELL_REGULATOR = "cell_regulator";
	/** More damage, more heat; shares the core slot with the Cell Regulator. */
	public static final String OVERCHARGER = "overcharger";

	/** Every attachment the pack ships, in tooltip order. */
	public static final List<String> ALL = List.of(OPTIC, DAMPENER, HEAT_SINK, CELL_REGULATOR, OVERCHARGER);

	public static final GunAttachments NONE = new GunAttachments(List.of());

	public static final Codec<GunAttachments> CODEC = Codec.STRING.listOf().xmap(GunAttachments::new, GunAttachments::ids);
	public static final StreamCodec<ByteBuf, GunAttachments> STREAM_CODEC =
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(GunAttachments::new, GunAttachments::ids);

	public GunAttachments {
		ids = List.copyOf(ids);
	}

	public boolean has(String id) {
		return ids.contains(id);
	}

	public boolean isEmpty() {
		return ids.isEmpty();
	}

	public GunAttachments with(String id) {
		if (has(id)) {
			return this;
		}
		List<String> next = new ArrayList<>(ids);
		next.add(id);
		return new GunAttachments(next);
	}

	public GunAttachments without(String id) {
		if (!has(id)) {
			return this;
		}
		List<String> next = new ArrayList<>(ids);
		next.remove(id);
		return new GunAttachments(next);
	}
}
