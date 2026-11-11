package xyz.toesinthesand.ashfall.arms.mission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * One player's own mission flags ({@code ashfall:mission}).
 * <p>
 * Both flags are sticky on purpose. The briefing is once per player per world, so a rejoin, a death or a dropped
 * book never repeats the title and chat. "Armed" is remembered once the player has held an energy weapon and a Cell
 * Pack together: a player who loses their gun to a horde should be told to go and relight River Arts, not to start
 * the game over. It copies on death and is not synced: the server is the only reader.
 *
 * @param briefed the first-join briefing has been delivered in this world
 * @param armed   the player has been armed at least once
 * @param radioAt game time {@code /ashfall radio} last gave this player a Field Radio, or {@link #NEVER}
 */
public record MissionProgress(boolean briefed, boolean armed, long radioAt) {
	public static final long NEVER = Long.MIN_VALUE;
	public static final MissionProgress EMPTY = new MissionProgress(false, false, NEVER);

	public static final Codec<MissionProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("briefed", false).forGetter(MissionProgress::briefed),
			Codec.BOOL.optionalFieldOf("armed", false).forGetter(MissionProgress::armed),
			Codec.LONG.optionalFieldOf("radio_at", NEVER).forGetter(MissionProgress::radioAt)
	).apply(i, MissionProgress::new));

	public static final AttachmentType<MissionProgress> TYPE = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("ashfall", "mission"), builder -> builder
					.persistent(CODEC)
					.copyOnDeath()
					.initializer(() -> EMPTY));

	public static MissionProgress get(Player player) {
		return player.getAttachedOrElse(TYPE, EMPTY);
	}

	public static void set(Player player, MissionProgress progress) {
		if (!progress.equals(get(player))) {
			player.setAttached(TYPE, progress);
		}
	}

	public MissionProgress withBriefed() {
		return briefed ? this : new MissionProgress(true, armed, radioAt);
	}

	public MissionProgress withRadioAt(long gameTime) {
		return new MissionProgress(briefed, armed, gameTime);
	}

	public MissionProgress withArmed() {
		return armed ? this : new MissionProgress(briefed, true, radioAt);
	}
}
