package xyz.toesinthesand.ashfall.arms.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/**
 * What one blaster attachment does, from {@code data/<ns>/ashfall/gun_attachment/<attachment id>.json}.
 * The file name is the attachment id, which is also the attachment item's path ({@code ashfall:optic}
 * is fitted as {@code "optic"}). Every number is a multiplier on the gun's {@link GunStats}, so one file
 * works for every gun and a datapack can rebalance it with {@code /reload}.
 *
 * @param slot             mount point; a gun holds one attachment per slot, so the Cell Regulator and the
 *                         Overcharger (both {@code "core"}) are mutually exclusive
 * @param damage           bolt damage multiplier
 * @param noise            noise (Director SOUND radius) multiplier
 * @param volume           shot sound volume multiplier (how far the shot is heard, 16 blocks per 1.0)
 * @param heatCapacity     heat at which the gun locks
 * @param heatPerShot      heat added per shot
 * @param coolPerSecond    cooling rate
 * @param energyPerShot    pack energy per shot
 * @param aimSpread        spread multiplier while aiming (sneaking)
 * @param zoom             field-of-view multiplier while aiming (1 = no zoom; lower zooms further)
 * @param requiresDistrict district that must be Reclaimed before the Weaponsmith Bench fits this
 */
public record AttachmentStats(String slot, float damage, float noise, float volume, float heatCapacity, float heatPerShot,
							  float coolPerSecond, float energyPerShot, float aimSpread, float zoom,
							  Optional<String> requiresDistrict) {
	/** The Dampener before any datapack loads, matching the shipped JSON (noise x0.4, damage x0.9, volume x0.45). */
	public static final AttachmentStats DAMPENER_FALLBACK = new AttachmentStats("muzzle", 0.9f, 0.4f, 0.45f, 1f, 1f, 1f, 1f, 1f, 1f,
			Optional.empty());

	private static final Codec<Float> MULTIPLIER = Codec.floatRange(0f, 100f);

	public static final Codec<AttachmentStats> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("slot").forGetter(AttachmentStats::slot),
			MULTIPLIER.optionalFieldOf("damage", 1f).forGetter(AttachmentStats::damage),
			MULTIPLIER.optionalFieldOf("noise", 1f).forGetter(AttachmentStats::noise),
			MULTIPLIER.optionalFieldOf("volume", 1f).forGetter(AttachmentStats::volume),
			Codec.floatRange(0.01f, 100f).optionalFieldOf("heat_capacity", 1f).forGetter(AttachmentStats::heatCapacity),
			MULTIPLIER.optionalFieldOf("heat_per_shot", 1f).forGetter(AttachmentStats::heatPerShot),
			MULTIPLIER.optionalFieldOf("cool_per_second", 1f).forGetter(AttachmentStats::coolPerSecond),
			MULTIPLIER.optionalFieldOf("energy_per_shot", 1f).forGetter(AttachmentStats::energyPerShot),
			MULTIPLIER.optionalFieldOf("aim_spread", 1f).forGetter(AttachmentStats::aimSpread),
			Codec.floatRange(0.05f, 1f).optionalFieldOf("zoom", 1f).forGetter(AttachmentStats::zoom),
			Codec.STRING.optionalFieldOf("requires_district").forGetter(AttachmentStats::requiresDistrict)
	).apply(i, AttachmentStats::new));

	/** Network form: the JSON shape, so the client tooltips and zoom match what the server loaded. */
	public static final StreamCodec<ByteBuf, AttachmentStats> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
