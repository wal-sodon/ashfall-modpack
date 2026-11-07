package xyz.toesinthesand.ashfall.director.pets;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.traps.Compass8;
import xyz.toesinthesand.ashfall.director.traps.TriplineAlarmBlockEntity;

import java.util.Comparator;
import java.util.List;

/**
 * Dogs are vanilla wolves with four changes, all added through {@code WolfMixin}:
 * <ul>
 * <li>a tamed dog goes for zombie-family mobs within {@link #GUARD_RADIUS} blocks of its owner;</li>
 * <li>each bite does {@link #BITE_DAMAGE} (1 heart, the vanilla wolf's is 4), so a dog is help, not a weapon;</li>
 * <li>a tamed dog that sees a zombie within {@link #GROWL_RADIUS} blocks warns its owner on the action bar
 * ({@link DogGrowlGoal}), at most once per {@link #GROWL_COOLDOWN_TICKS}.</li>
 * <li>Rotten Flesh tames a wild dog as well as a bone does (same 1 in 3 chance); zombie loot is what survivors
 * have to spare.</li>
 * </ul>
 * Dogs are silent to the Director: none of this emits a stimulus, and no wolf sound is in the noise table, so a dog
 * never draws zombies. Infection only ever targets players, so dogs can't be infected.
 */
public final class Dogs {
	public static final float BITE_DAMAGE = 2.0f;
	public static final double GUARD_RADIUS = 12.0;
	public static final double GROWL_RADIUS = 16.0;
	/** The owner hears the growl only this close to the dog. */
	public static final double OWNER_HEARING = 64.0;
	public static final int GROWL_COOLDOWN_TICKS = 10 * 20;
	/** Tamed, standing dogs this close to the owner come along on a fast-travel trip. */
	public static final double TRAVEL_RADIUS = 10.0;
	/** Same slot as the wolf's own "attack an angry player" target goal: after the owner-defence goals. */
	static final int TARGET_PRIORITY = 4;
	static final int GROWL_PRIORITY = 10;
	private static final Identifier BITE_ID = Identifier.fromNamespaceAndPath("ashfall", "dog_bite");

	private Dogs() {
	}

	/** The extra taming item: Rotten Flesh (vanilla's bone still works through the wolf's own code). */
	public static boolean isFleshTreat(ItemStack stack) {
		return stack.is(Items.ROTTEN_FLESH);
	}

	/** Adds the guard target goal and the growl goal; from the end of {@code Wolf#registerGoals}. */
	public static void addGoals(Wolf dog, GoalSelector goals, GoalSelector targets) {
		targets.addGoal(TARGET_PRIORITY, new NearestAttackableTargetGoal<>(dog, LivingEntity.class, 10, true, false,
				(target, level) -> guards(dog, target)));
		goals.addGoal(GROWL_PRIORITY, new DogGrowlGoal(dog));
	}

	/**
	 * Brings the bite down to {@link #BITE_DAMAGE}, from the end of the wolf's constructor. A transient modifier
	 * rather than a new base value: it is never saved, so it also applies to wolves saved before this update.
	 */
	public static void applyBite(Wolf dog) {
		AttributeInstance attack = dog.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null && !attack.hasModifier(BITE_ID)) {
			attack.addTransientModifier(new AttributeModifier(BITE_ID, BITE_DAMAGE - attack.getBaseValue(),
					AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** Whether a tamed, standing dog should go for {@code target}: a zombie close to its owner. */
	public static boolean guards(Wolf dog, LivingEntity target) {
		if (!dog.isTame() || dog.isOrderedToSit() || !TriplineAlarmBlockEntity.isIntruder(target)) {
			return false;
		}
		LivingEntity owner = dog.getOwner();
		return owner != null && owner.level() == dog.level() && owner.distanceToSqr(target) <= GUARD_RADIUS * GUARD_RADIUS;
	}

	/** The owner who would hear {@code dog} growl: online, in the same level and within {@link #OWNER_HEARING}. */
	public static @Nullable ServerPlayer listener(Wolf dog) {
		if (!dog.isTame() || !(dog.getOwner() instanceof ServerPlayer owner) || owner.level() != dog.level()) {
			return null;
		}
		return owner.distanceToSqr(dog) <= OWNER_HEARING * OWNER_HEARING ? owner : null;
	}

	/** The nearest zombie-family mob within {@link #GROWL_RADIUS} of the dog, or null. */
	public static @Nullable LivingEntity nearestThreat(ServerLevel level, Wolf dog) {
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, dog.getBoundingBox().inflate(GROWL_RADIUS),
				e -> TriplineAlarmBlockEntity.isIntruder(e) && e.distanceToSqr(dog) <= GROWL_RADIUS * GROWL_RADIUS);
		return near.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(dog))).orElse(null);
	}

	/**
	 * One look around: if the owner can hear and a zombie is near, the owner (only) gets "Your dog growls: zombies to
	 * the north-east, 12 m", direction and distance from where the owner stands.
	 *
	 * @return the zombie the dog growled at, or null if it stayed quiet
	 */
	public static @Nullable LivingEntity growl(ServerLevel level, Wolf dog) {
		ServerPlayer owner = listener(dog);
		if (owner == null) {
			return null;
		}
		LivingEntity threat = nearestThreat(level, dog);
		if (threat != null) {
			double dx = threat.getX() - owner.getX();
			double dz = threat.getZ() - owner.getZ();
			owner.sendOverlayMessage(Component.translatable("message.ashfall.dog.growl", Compass8.component(dx, dz),
					(int) Math.round(Math.sqrt(dx * dx + dz * dz))));
		}
		return threat;
	}

	/** The dogs that travel with {@code player}: theirs, tamed, not sitting, not leashed or riding, within range. */
	public static List<Wolf> companions(ServerPlayer player) {
		return player.level().getEntitiesOfClass(Wolf.class, player.getBoundingBox().inflate(TRAVEL_RADIUS),
				dog -> dog.isAlive() && dog.isTame() && dog.isOwnedBy(player) && !dog.isOrderedToSit()
						&& !dog.isLeashed() && !dog.isPassenger() && dog.distanceToSqr(player) <= TRAVEL_RADIUS * TRAVEL_RADIUS);
	}
}
