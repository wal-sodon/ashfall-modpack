package xyz.toesinthesand.ashfall.arms.finale;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.arms.grid.GridDistricts;
import xyz.toesinthesand.ashfall.arms.grid.GridNetworking;
import xyz.toesinthesand.ashfall.arms.grid.GridRelight;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.story.Ending;
import xyz.toesinthesand.ashfall.arms.story.Story;

import java.util.Collection;
import java.util.List;

/**
 * What the real Black Start's milestones do to the world. Each phase end lights a band of districts below the ridge in
 * real time: every loaded chunk of those districts replays its logged lights at once through {@link GridRelight}
 * (the rest come on lazily as they load, as for any reclaimed district), a soundless lightning flash splits the sky
 * over the Wreck, thunder rolls for everyone, and the Grid % steps up 80, 87, 94, 100 through
 * {@link GridState#setBlackStartBonus}. The finish opens the ending choice and records "Asheville Relit".
 */
public final class FinaleEffects implements FinaleInstance.Effects {
	public static final FinaleEffects WORLD = new FinaleEffects();

	private FinaleEffects() {
	}

	@Override
	public void phaseEnded(ServerLevel level, FinaleInstance instance, int band, int bonus) {
		MinecraftServer server = level.getServer();
		FinaleSettings s = instance.s();
		List<String> districts = bandDistricts(s, band);
		int lights = 0;
		for (String district : districts) {
			lights += GridRelight.relightLoaded(level, district);
		}
		bonusChanged(level, bonus);
		if (instance.run().socket != FinaleRun.NONE) {
			flash(level, BlockPos.of(instance.run().socket));
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.7f, 0.6f);
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 0.6f, 0.7f);
		}
		Component names = names(districts);
		FinaleController.title(server, Component.translatable("title.ashfall.finale.band_lit"),
				Component.translatable("subtitle.ashfall.finale.band_lit", names, GridState.get(server).gridPercent()));
		FinaleController.broadcast(server, Component.translatable("message.ashfall.finale.band_lit", names,
				GridState.get(server).gridPercent()).withStyle(ChatFormatting.GOLD));
		AshfallArms.LOGGER.info("Finale: band {} lit ({}), {} lights restored in loaded chunks, Grid {}%", band + 1, districts, lights,
				GridState.get(server).gridPercent());
	}

	/** A skipped band lights quietly: the admin jump announces itself, so only the lights and a log line remain. */
	@Override
	public void bandRelit(ServerLevel level, FinaleInstance instance, int band) {
		List<String> districts = bandDistricts(instance.s(), band);
		int lights = 0;
		for (String district : districts) {
			lights += GridRelight.relightLoaded(level, district);
		}
		AshfallArms.LOGGER.info("Finale: skipped band {} relit ({}), {} lights restored in loaded chunks", band + 1, districts, lights);
	}

	private static List<String> bandDistricts(FinaleSettings s, int band) {
		return band >= 0 && band < s.bands().size() ? s.bands().get(band).districts() : List.of();
	}

	@Override
	public void completed(ServerLevel level, FinaleInstance instance, Collection<ServerPlayer> participants) {
		for (ServerPlayer player : participants) {
			awardRelit(player);
		}
		FinaleController.broadcast(level.getServer(), Component.translatable("message.ashfall.finale.complete")
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		Ending.beginChoice(level, participants);
	}

	@Override
	public void bonusChanged(ServerLevel level, int bonus) {
		MinecraftServer server = level.getServer();
		GridState.get(server).setBlackStartBonus(bonus);
		// the bonus is not a district change, so nothing else tells the HUDs and the Grid % advancements
		GridNetworking.sendAll(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Story.fireGrid(p, server);
		}
	}

	/** "Asheville Relit" for one player (its only criterion; awarding twice is harmless). */
	public static boolean awardRelit(ServerPlayer player) {
		AdvancementHolder relit = player.level().getServer().getAdvancements().get(FinaleController.RELIT_ADVANCEMENT);
		return relit != null && player.getAdvancements().award(relit, FinaleController.RELIT_CRITERION);
	}

	/** A soundless lightning flash high over the Wreck: the sky lights for every client that can see it. */
	private static void flash(ServerLevel level, BlockPos socket) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt == null) {
			return;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, socket.getX(), socket.getZ());
		bolt.snapTo(socket.getX() + 0.5, top, socket.getZ() + 0.5);
		bolt.setVisualOnly(true);
		level.addFreshEntity(bolt);
	}

	private static Component names(List<String> districts) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < districts.size(); i++) {
			if (i > 0) {
				out.append(i == districts.size() - 1 ? Component.translatable("message.ashfall.finale.and") : Component.literal(", "));
			}
			out.append(GridDistricts.name(districts.get(i)));
		}
		return out;
	}
}
