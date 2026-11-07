package xyz.toesinthesand.ashfall.arms.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * One player's Field Radio progress ({@code ashfall:radio_progress}): whether they have found the radio
 * (which turns on the Grid HUD), which Relay Logs they have heard and which relays they have tuned into.
 * <p>
 * Per player because reading is personal: the grid itself is shared world-wide, but each player builds
 * their own log collection. It copies on death (a death must not wipe a lore collection) and syncs to the
 * owner only, since the HUD and the Grid Map's discovered relays are drawn client-side.
 *
 * @param radio  true once the player has the "Relight" root advancement (picked up the Field Radio)
 * @param logs   numbers of the logs heard, sorted
 * @param relays packed positions of the relays tuned into, sorted
 */
public record RadioProgress(boolean radio, List<Integer> logs, List<Long> relays) {
	public static final RadioProgress EMPTY = new RadioProgress(false, List.of(), List.of());
	/** Relays remembered per player; the atlas places about 75, so this only guards against abuse. */
	public static final int MAX_RELAYS = 512;

	public static final Codec<RadioProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("radio", false).forGetter(RadioProgress::radio),
			Codec.INT.listOf().optionalFieldOf("logs", List.of()).forGetter(RadioProgress::logs),
			Codec.LONG.listOf().optionalFieldOf("relays", List.of()).forGetter(RadioProgress::relays)
	).apply(i, RadioProgress::new));

	public static final AttachmentType<RadioProgress> TYPE = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("ashfall", "radio_progress"), builder -> builder
					.persistent(CODEC)
					.copyOnDeath()
					.initializer(() -> EMPTY)
					.syncWith(ByteBufCodecs.fromCodec(CODEC), AttachmentSyncPredicate.targetOnly()));

	public RadioProgress {
		logs = List.copyOf(new TreeSet<>(logs));
		relays = List.copyOf(new TreeSet<>(relays));
	}

	public static RadioProgress get(Player player) {
		return player.getAttachedOrElse(TYPE, EMPTY);
	}

	public static void set(Player player, RadioProgress progress) {
		if (!progress.equals(get(player))) {
			player.setAttached(TYPE, progress);
		}
	}

	public boolean hasLog(int number) {
		return logs.contains(number);
	}

	public RadioProgress withRadio() {
		return radio ? this : new RadioProgress(true, logs, relays);
	}

	public RadioProgress withLog(int number) {
		if (hasLog(number)) {
			return this;
		}
		List<Integer> next = new ArrayList<>(logs);
		next.add(number);
		return new RadioProgress(radio, next, relays);
	}

	public RadioProgress withRelay(long packedPos) {
		if (relays.contains(packedPos) || relays.size() >= MAX_RELAYS) {
			return this;
		}
		List<Long> next = new ArrayList<>(relays);
		next.add(packedPos);
		return new RadioProgress(radio, logs, next);
	}
}
