package xyz.toesinthesand.ashfall.director.senses;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.skills.Skill;
import xyz.toesinthesand.ashfall.director.skills.Skills;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The public senses API: anything that makes noise or light reports it here, and zombies read it back
 * through {@link SenseGoal}. Arms forwards {@code ArmsNoise.emit} (gunshots, the relay hum), vanilla game
 * events arrive through {@link GameEventNoise}, players' light through {@link LightEmitter} and wounded players'
 * blood through {@link BloodTrails}.
 * <p>
 * One {@link StimulusField} per level, created on first use. Stimuli are transient (seconds), so nothing
 * is saved; the fields are dropped when the server stops.
 */
public final class Stimuli {
	/** How often expired stimuli are physically removed; queries already ignore them in between. */
	private static final int SWEEP_INTERVAL = 20;

	private static final Map<ResourceKey<Level>, StimulusField> FIELDS = new HashMap<>();

	private Stimuli() {
	}

	static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(Stimuli::tickLevel);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FIELDS.clear());
	}

	/**
	 * Reports a stimulus. {@code source} is optional; passing it lets a repeating source (a player's
	 * light, a running player's footsteps) refresh its own stimulus instead of stacking new ones.
	 *
	 * @return the stimulus now standing for this report, or null if it was dropped (bad radius, cap hit)
	 */
	@Nullable
	public static Stimulus emit(ServerLevel level, Vec3 pos, StimulusType type, double radius, @Nullable Entity source) {
		if (type == StimulusType.SOUND && source instanceof ServerPlayer player) {
			// Light Step: the sounds a player makes carry less far
			radius *= Skills.reduction(player, Skill.LIGHT_STEP);
		}
		int sourceId = source == null ? Stimulus.NO_SOURCE : source.getId();
		return field(level).emit(type, pos.x, pos.y, pos.z, radius, sourceId, level.getGameTime());
	}

	/** Every live stimulus whose radius (times {@code reachScale}) reaches {@code pos}. */
	public static List<Stimulus> query(ServerLevel level, Vec3 pos, double reachScale) {
		StimulusField field = FIELDS.get(level.dimension());
		if (field == null) {
			return List.of();
		}
		return field.query(pos.x, pos.y, pos.z, reachScale, level.getGameTime());
	}

	/** Allocation-free form of {@link #query(ServerLevel, Vec3, double)} for per-mob hot paths. */
	public static void query(ServerLevel level, Vec3 pos, double reachScale, Consumer<Stimulus> out) {
		StimulusField field = FIELDS.get(level.dimension());
		if (field != null) {
			field.query(pos.x, pos.y, pos.z, reachScale, level.getGameTime(), out);
		}
	}

	/** {@link StimulusField#queryWide}: a reach scale above the per-zombie clamp, for hordes. */
	public static void queryWide(ServerLevel level, Vec3 pos, double reachScale, Consumer<Stimulus> out) {
		StimulusField field = FIELDS.get(level.dimension());
		if (field != null) {
			field.queryWide(pos.x, pos.y, pos.z, reachScale, level.getGameTime(), out);
		}
	}

	/** Every live stimulus in the level (debug rendering, tests). */
	public static void forEachActive(ServerLevel level, Consumer<Stimulus> out) {
		StimulusField field = FIELDS.get(level.dimension());
		if (field != null) {
			field.forEachActive(level.getGameTime(), out);
		}
	}

	/** The level's field, created on demand. */
	public static StimulusField field(ServerLevel level) {
		return FIELDS.computeIfAbsent(level.dimension(), k -> new StimulusField());
	}

	private static void tickLevel(ServerLevel level) {
		if (level.getGameTime() % SWEEP_INTERVAL != 0) {
			return;
		}
		StimulusField field = FIELDS.get(level.dimension());
		if (field != null && !field.isEmpty()) {
			field.sweep(level.getGameTime());
		}
	}
}
