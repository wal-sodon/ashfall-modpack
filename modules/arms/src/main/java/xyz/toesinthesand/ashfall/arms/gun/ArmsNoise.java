package xyz.toesinthesand.ashfall.arms.gun;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The one place loud things report their noise: gunshots, overheats and Power Relays all call
 * {@link #emit}. Every noise becomes a Director SOUND stimulus ({@link Stimuli#emit}) that pulls zombies
 * toward the source; arms only says how loud, the Director decides who hears it. The listener list
 * stays for tests and future consumers.
 */
public final class ArmsNoise {
	/** Something that reacts to noise (the Director, later; tests, now). */
	@FunctionalInterface
	public interface Listener {
		void onNoise(Level level, BlockPos pos, float radius);
	}

	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

	private ArmsNoise() {
	}

	/** Reports a noise of {@code radius} blocks at {@code pos}. Server side only; ignored on the client. */
	public static void emit(Level level, BlockPos pos, float radius) {
		if (level.isClientSide() || radius <= 0f) {
			return;
		}
		AshfallArms.LOGGER.debug("Noise radius {} at {} in {}", radius, pos, level.dimension().identifier());
		if (level instanceof ServerLevel serverLevel) {
			Stimuli.emit(serverLevel, Vec3.atCenterOf(pos), StimulusType.SOUND, radius, null);
		}
		for (Listener listener : LISTENERS) {
			listener.onNoise(level, pos, radius);
		}
	}

	public static void addListener(Listener listener) {
		LISTENERS.add(listener);
	}

	public static void removeListener(Listener listener) {
		LISTENERS.remove(listener);
	}
}
