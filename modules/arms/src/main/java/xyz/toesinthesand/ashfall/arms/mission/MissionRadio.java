package xyz.toesinthesand.ashfall.arms.mission;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.arms.story.Compass;
import xyz.toesinthesand.ashfall.arms.story.MapMarker;
import xyz.toesinthesand.ashfall.arms.story.MapMarkers;

import java.util.Optional;

/**
 * The Field Radio's mission lines: the current objective with a heading to it, and the way home.
 * <p>
 * "Home" is the player's own or party Bunker Core when one stands, else the Montford safehouse: players asked how to
 * find their base again, and the radio is the tool already in their hand. It gives a rough heading and distance in
 * the radio's own manner ({@link Compass}); exact positions stay on the Grid Map.
 */
public final class MissionRadio {
	private MissionRadio() {
	}

	/** "Objective: ... about 640 blocks west". */
	public static Component objectiveLine(ServerPlayer player) {
		Objective now = MissionResolver.current(MissionWorld.collect(player));
		MutableComponent line = Component.translatable("radio.ashfall.objective", now.text().copy().withStyle(ChatFormatting.YELLOW))
				.withStyle(ChatFormatting.GOLD);
		now.target().ifPresent(t -> line.append(" ").append(heading(player, t).withStyle(ChatFormatting.GRAY)));
		return line;
	}

	/** "Home (Bunker Core): about 340 blocks south-west", or the safehouse when there is no core. Empty off the map. */
	public static Optional<Component> homeLine(ServerPlayer player) {
		Optional<MissionPos> bunker = MissionWorld.bunker(player);
		String which = bunker.isPresent() ? "radio.ashfall.home.bunker" : "radio.ashfall.home.safehouse";
		Optional<MissionPos> home = bunker.or(() -> MapMarkers.all().stream().filter(m -> MapMarker.SAFEHOUSE.equals(m.kind())).findFirst()
				.map(m -> new MissionPos(m.x(), m.z())));
		return home.map(pos -> Component.translatable(which, heading(player, pos)).withStyle(ChatFormatting.GREEN));
	}

	/**
	 * "about N blocks <bearing>", or "here" within a few blocks. Targets are overworld spots, so outside the overworld
	 * a heading from the player's own x/z would be wrong (the Nether is 8:1): it says "back in the overworld" instead.
	 */
	public static MutableComponent heading(ServerPlayer player, MissionPos target) {
		if (player.level().dimension() != Level.OVERWORLD) {
			return Component.translatable("radio.ashfall.elsewhere");
		}
		int distance = Compass.roughDistance(player.getX(), player.getZ(), target.x(), target.z());
		if (distance < 10) {
			return Component.translatable("radio.ashfall.here");
		}
		return Component.translatable("radio.ashfall.heading", distance,
				Component.translatable("compass.ashfall." + Compass.point(player.getX(), player.getZ(), target.x(), target.z())));
	}
}
