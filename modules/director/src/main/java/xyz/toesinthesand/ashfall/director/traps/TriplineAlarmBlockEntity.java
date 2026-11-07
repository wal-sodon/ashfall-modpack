package xyz.toesinthesand.ashfall.director.traps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;
import xyz.toesinthesand.ashfall.director.spawn.ZombiesOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The alarm's watch: every {@link #SCAN_INTERVAL} ticks it looks for zombie-family mobs in the 5x3x5 box in front
 * of it. While one is there the block is powered; the first sighting (then at most once per {@link #COOLDOWN_TICKS})
 * rings a bell for the owner and anyone within {@link #HEAR_RANGE} blocks and shows them where it came from.
 * <p>
 * The bell is a {@link ClientboundSoundPacket} per player (the {@code HordeSiren} trick), not a world sound, so no
 * zombie hears it and it adds no stimulus.
 */
public class TriplineAlarmBlockEntity extends BlockEntity {
	public static final int SCAN_INTERVAL = 10;
	public static final int COOLDOWN_TICKS = 10 * 20;
	public static final double HEAR_RANGE = 96.0;
	/** The watched box: 5 blocks out from the alarm, 5 wide, from one below to one above it. */
	private static final int REACH = 5;
	private static final int HALF_WIDTH = 2;
	/** The bell is placed this far from the listener towards the alarm, so it pans the right way. */
	private static final double BELL_OFFSET = 3.0;

	@Nullable
	private UUID owner;
	private int cooldown;

	public TriplineAlarmBlockEntity(BlockPos pos, BlockState state) {
		super(DirectorBlocks.TRIPLINE_ALARM_ENTITY, pos, state);
	}

	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
		setChanged();
	}

	@Nullable
	public UUID owner() {
		return owner;
	}

	public int cooldown() {
		return cooldown;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, TriplineAlarmBlockEntity alarm) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		if (alarm.cooldown > 0) {
			alarm.cooldown--;
		}
		if ((server.getGameTime() + pos.asLong()) % SCAN_INTERVAL == 0) {
			alarm.scan(server);
		}
	}

	/** Who sets it off: zombies only, never players or animals. */
	public static boolean isIntruder(Entity entity) {
		return entity.isAlive() && ZombiesOnly.isZombieFamily(entity);
	}

	/** The watched box for an alarm at {@code pos} facing {@code facing}. */
	public static AABB watchBox(BlockPos pos, Direction facing) {
		Direction side = facing.getClockWise();
		BlockPos near = pos.relative(facing).relative(side, -HALF_WIDTH).below();
		BlockPos far = pos.relative(facing, REACH).relative(side, HALF_WIDTH).above();
		return AABB.encapsulatingFullBlocks(near, far);
	}

	/** Looks once, updates the redstone signal and rings if due. @return whether a zombie is in the box */
	public boolean scan(ServerLevel level) {
		BlockState state = getBlockState();
		if (!state.hasProperty(TriplineAlarmBlock.FACING)) {
			return false;
		}
		AABB box = watchBox(worldPosition, state.getValue(TriplineAlarmBlock.FACING));
		boolean tripped = !level.getEntitiesOfClass(LivingEntity.class, box, TriplineAlarmBlockEntity::isIntruder).isEmpty();
		if (state.getValue(TriplineAlarmBlock.POWERED) != tripped) {
			BlockState updated = state.setValue(TriplineAlarmBlock.POWERED, tripped);
			level.setBlock(worldPosition, updated, Block.UPDATE_ALL);
			TriplineAlarmBlock.updateNeighbours(updated, level, worldPosition);
		}
		if (tripped && cooldown <= 0) {
			cooldown = COOLDOWN_TICKS;
			setChanged();
			ring(level);
		}
		return tripped;
	}

	/** The players who get the alert: the owner wherever they are in this level, and anyone within range. */
	public List<ServerPlayer> listeners(ServerLevel level) {
		Vec3 here = Vec3.atCenterOf(worldPosition);
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.getUUID().equals(owner) || player.distanceToSqr(here) <= HEAR_RANGE * HEAR_RANGE) {
				out.add(player);
			}
		}
		return out;
	}

	private void ring(ServerLevel level) {
		Holder<SoundEvent> bell = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BELL_BLOCK);
		Vec3 here = Vec3.atCenterOf(worldPosition);
		for (ServerPlayer player : listeners(level)) {
			Vec3 eyes = player.getEyePosition();
			Vec3 toward = here.subtract(eyes);
			double distance = toward.length();
			Vec3 at = distance > BELL_OFFSET ? eyes.add(toward.scale(BELL_OFFSET / distance)) : here;
			player.connection.send(new ClientboundSoundPacket(bell, SoundSource.BLOCKS, at.x, at.y, at.z, 1.0f, 1.2f,
					level.getRandom().nextLong()));
			player.sendOverlayMessage(Component.translatable("message.ashfall.tripline_alarm.tripped",
					Compass8.component(here.x - player.getX(), here.z - player.getZ()), (int) Math.round(distance)));
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		cooldown = input.getIntOr("cooldown", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("owner", UUIDUtil.CODEC, owner);
		output.putInt("cooldown", cooldown);
	}
}
