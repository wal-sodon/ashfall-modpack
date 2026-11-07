package xyz.toesinthesand.ashfall.arms.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * One player's own lines in the field report ({@code ashfall:survivor_stats}): deaths, and how many of them were while
 * infected. Copies on death, or the counter would reset at the very
 * moment it counts; not synced, since only the server writes the report.
 *
 * @param deaths deaths in this world
 * @param risen  deaths while infected (named for the save key, from before 10.3.0 when the infected rose)
 */
public record SurvivorStats(int deaths, int risen) {
	public static final SurvivorStats EMPTY = new SurvivorStats(0, 0);

	public static final Codec<SurvivorStats> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("deaths", 0).forGetter(SurvivorStats::deaths),
			Codec.INT.optionalFieldOf("risen", 0).forGetter(SurvivorStats::risen)
	).apply(i, SurvivorStats::new));

	public static final AttachmentType<SurvivorStats> TYPE = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("ashfall", "survivor_stats"), builder -> builder
					.persistent(CODEC)
					.copyOnDeath()
					.initializer(() -> EMPTY));

	/** Forces class loading, so the persistent attachment is registered before any player data loads. */
	static void init() {
	}

	public static SurvivorStats get(Player player) {
		return player.getAttachedOrElse(TYPE, EMPTY);
	}

	public static void set(Player player, SurvivorStats stats) {
		player.setAttached(TYPE, stats);
	}

	public SurvivorStats withDeath(boolean infected) {
		return new SurvivorStats(deaths + 1, risen + (infected ? 1 : 0));
	}
}
