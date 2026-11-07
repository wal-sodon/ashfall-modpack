package xyz.toesinthesand.ashfall.arms.radio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One call the Shortwave Set picked up: what kind of job, which building it points at and how far it has got.
 *
 * @param type     what the job asks for
 * @param building the target's {@code Building#id()} (packed origin chunk for Lost Cities)
 * @param profile  the building's profile path ("pharmacy"), for the broadcast text
 * @param district the zone id at the building, for the text and the reward size
 * @param tier     that zone's tier (1 suburbs .. 5 ridge): tier 3 and up pays out the rich table
 * @param x        middle of the building, block x (where the HUD and map point)
 * @param z        middle of the building, block z
 * @param deadline game time the job is lost at; 0 while it is still only an offer
 * @param stage    {@link #WAITING} until a player arrives, then {@link #PLACED}, then {@link #REWARD} once a nest or
 *                 a distress call has been dealt with and its reward chest is out
 * @param spot     where the chest or beacon went (set by a test before arrival to skip the building search)
 * @param zombies  the nest's (or the distress wave's) zombies, to tell when they are all dead
 * @param held     distress only: ticks a player has stood by the beacon
 */
public record RadioJob(Type type, long building, String profile, String district, int tier, int x, int z, long deadline,
		int stage, Optional<BlockPos> spot, List<UUID> zombies, int held) {
	public static final int WAITING = 0, PLACED = 1, REWARD = 2;

	public enum Type implements StringRepresentable {
		/** Supplies left in a building: find the chest. */
		STASH("stash", Set.of("pharmacy", "grocery", "shop", "home", "workshop"), 1),
		/** Zombies holed up in a building: kill them all, then the reward chest appears. */
		NEST("nest", Set.of("school", "office", "hotel", "warehouse"), 1),
		/** Someone calling from a top floor: hold out by their beacon while it draws zombies in. */
		DISTRESS("distress", Set.of("hotel", "office", "home"), 3);

		public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

		private final String id;
		private final Set<String> profiles;
		private final int minFloors;

		Type(String id, Set<String> profiles, int minFloors) {
			this.id = id;
			this.profiles = profiles;
			this.minFloors = minFloors;
		}

		public String id() {
			return id;
		}

		/** Building profiles this job may point at. */
		public Set<String> profiles() {
			return profiles;
		}

		public int minFloors() {
			return minFloors;
		}

		@Override
		public String getSerializedName() {
			return id;
		}
	}

	public static final Codec<RadioJob> CODEC = RecordCodecBuilder.create(i -> i.group(
			Type.CODEC.fieldOf("type").forGetter(RadioJob::type),
			Codec.LONG.fieldOf("building").forGetter(RadioJob::building),
			Codec.STRING.fieldOf("profile").forGetter(RadioJob::profile),
			Codec.STRING.fieldOf("district").forGetter(RadioJob::district),
			Codec.INT.fieldOf("tier").forGetter(RadioJob::tier),
			Codec.INT.fieldOf("x").forGetter(RadioJob::x),
			Codec.INT.fieldOf("z").forGetter(RadioJob::z),
			Codec.LONG.optionalFieldOf("deadline", 0L).forGetter(RadioJob::deadline),
			Codec.INT.optionalFieldOf("stage", WAITING).forGetter(RadioJob::stage),
			BlockPos.CODEC.optionalFieldOf("spot").forGetter(RadioJob::spot),
			UUIDUtil.CODEC.listOf().optionalFieldOf("zombies", List.of()).forGetter(RadioJob::zombies),
			Codec.INT.optionalFieldOf("held", 0).forGetter(RadioJob::held)
	).apply(i, RadioJob::new));

	public RadioJob {
		zombies = List.copyOf(zombies);
	}

	/** A fresh offer for this building. */
	public static RadioJob offer(Type type, long building, String profile, String district, int tier, int x, int z) {
		return new RadioJob(type, building, profile, district, tier, x, z, 0L, WAITING, Optional.empty(), List.of(), 0);
	}

	public RadioJob accepted(long deadline) {
		return new RadioJob(type, building, profile, district, tier, x, z, deadline, WAITING, spot, zombies, held);
	}

	public RadioJob placed(BlockPos at, List<UUID> spawned) {
		return new RadioJob(type, building, profile, district, tier, x, z, deadline, PLACED, Optional.of(at), spawned, held);
	}

	public RadioJob withZombies(List<UUID> spawned) {
		return new RadioJob(type, building, profile, district, tier, x, z, deadline, stage, spot, spawned, held);
	}

	public RadioJob withHeld(int ticks) {
		return new RadioJob(type, building, profile, district, tier, x, z, deadline, stage, spot, zombies, ticks);
	}

	public RadioJob rewarded(BlockPos at) {
		return new RadioJob(type, building, profile, district, tier, x, z, deadline, REWARD, Optional.of(at), zombies, held);
	}

	/** A test's job aimed at a fixed spot, already accepted. */
	public RadioJob at(BlockPos pos) {
		return new RadioJob(type, building, profile, district, tier, pos.getX(), pos.getZ(), deadline, stage, Optional.of(pos), zombies, held);
	}

	/** The reward's loot table path under {@code ashfall:radio_job/}. */
	public String rewardTable() {
		return type.id() + (tier >= 3 ? "_rich" : "");
	}
}
