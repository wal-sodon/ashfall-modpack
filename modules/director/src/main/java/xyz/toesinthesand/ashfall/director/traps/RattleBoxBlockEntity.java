package xyz.toesinthesand.ashfall.director.traps;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;

/**
 * The Rattle Box's spring: {@link #running} ticks left on the wind, then {@link #cooldown} ticks before it can be
 * wound again. While it runs it reports a SOUND every {@link #EMIT_INTERVAL} ticks. The stimulus has no source
 * entity; a sourceless SOUND re-emitted at the same spot refreshes the one already there (see
 * {@code StimulusField.emit}), so a running box stands for exactly one stimulus.
 */
public class RattleBoxBlockEntity extends BlockEntity {
	/** 30 seconds of rattling per wind. */
	public static final int RUN_TICKS = 30 * 20;
	/** 10 seconds after the ring before it winds again. */
	public static final int COOLDOWN_TICKS = 10 * 20;
	/** Same reach as arms' thrown Bottle Lure. */
	public static final double RADIUS = 32.0;
	/** Re-emit well inside the SOUND lifetime (100 ticks) so the stimulus never lapses. */
	public static final int EMIT_INTERVAL = 40;
	private static final int TICK_SOUND_INTERVAL = 10;

	private int running;
	private int cooldown;

	public RattleBoxBlockEntity(BlockPos pos, BlockState state) {
		super(DirectorBlocks.RATTLE_BOX_ENTITY, pos, state);
	}

	public boolean running() {
		return running > 0;
	}

	public int cooldown() {
		return cooldown;
	}

	/** Winds the spring and makes the first noise. @return false while running or cooling down */
	public boolean wind(ServerLevel level) {
		if (running > 0 || cooldown > 0) {
			return false;
		}
		running = RUN_TICKS;
		level.playSound(null, worldPosition, SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.BLOCKS, 0.8f, 0.7f);
		emit(level);
		setWound(true);
		setChanged();
		return true;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, RattleBoxBlockEntity box) {
		if (level instanceof ServerLevel server) {
			box.tick(server);
		}
	}

	private void tick(ServerLevel level) {
		if (cooldown > 0) {
			cooldown--;
			if (cooldown == 0) {
				setChanged();
			}
		}
		if (running <= 0) {
			return;
		}
		running--;
		int elapsed = RUN_TICKS - running;
		if (running == 0) {
			level.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0f, 1.4f);
			emit(level);
			cooldown = COOLDOWN_TICKS;
			setWound(false);
			setChanged();
			return;
		}
		if (elapsed % TICK_SOUND_INTERVAL == 0) {
			level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.7f,
					elapsed % (TICK_SOUND_INTERVAL * 2) == 0 ? 1.6f : 1.3f);
		}
		if (elapsed % EMIT_INTERVAL == 0) {
			emit(level);
			setChanged();
		}
	}

	private void emit(ServerLevel level) {
		Stimuli.emit(level, Vec3.atCenterOf(worldPosition), StimulusType.SOUND, RADIUS, null);
	}

	private void setWound(boolean wound) {
		if (level != null) {
			BlockState state = getBlockState();
			if (state.hasProperty(RattleBoxBlock.WOUND) && state.getValue(RattleBoxBlock.WOUND) != wound) {
				level.setBlock(worldPosition, state.setValue(RattleBoxBlock.WOUND, wound), Block.UPDATE_ALL);
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		running = input.getIntOr("running", 0);
		cooldown = input.getIntOr("cooldown", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("running", running);
		output.putInt("cooldown", cooldown);
	}
}
