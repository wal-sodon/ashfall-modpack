package xyz.toesinthesand.ashfall.arms.finale;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.Map;
import java.util.Optional;

/**
 * The Choir: what the Heart grew from the infected that reached it, a huge body grown through with crystal shards. It
 * defends the Heart pylons in the last phase of the Black Start.
 * <p>
 * Deliberately a reskin with no new boss AI (endgame-design section 4): a {@link Zombie} with the Abomination's
 * kind of numbers (health, armour, reach from its 1.8x scale, full knockback resistance), drawn with the vanilla
 * zombie renderer under a crystal tint and shard overlay. Being a zombie means ZB&B, the infection-on-hit rule and
 * every "is it a zombie" check treat it like the rest of the dead. What the base zombie does that would cheapen a
 * boss is taken away: no babies, no reinforcement calls, no random gear, no drowning conversion, no sunburn (it
 * fights inside the hull and must not catch fire at a hatch at noon), and it never despawns.
 * <p>
 * Its numbers come from {@code finale/settings.json} ({@code heart.choir_attributes}) on every finale spawn; the
 * defaults registered here match the shipped file for spawn eggs and commands.
 */
public class ChoirEntity extends Zombie {
	/** Entity tag on the Choir, so status checks and cleanup find it without the run's UUID. */
	public static final String TAG = "ashfall_choir";

	public ChoirEntity(EntityType<? extends Zombie> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	/** Registered defaults (the shipped JSON repeats them). */
	public static AttributeSupplier.Builder createChoirAttributes() {
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, 420.0)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.MOVEMENT_SPEED, 0.24)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.5)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.SCALE, 1.8)
				.add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
	}

	@Override
	public void setBaby(boolean baby) {
		super.setBaby(false);
	}

	@Override
	protected boolean convertsInWater() {
		return false;
	}

	@Override
	protected boolean isSunSensitive() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
	}

	@Override
	protected void randomizeReinforcementsChance() {
		AttributeInstance reinforcements = getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
		if (reinforcements != null) {
			reinforcements.setBaseValue(0.0);
		}
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, new Zombie.ZombieGroupData(false, false));
		setCanPickUpLoot(false);
		return data;
	}

	/**
	 * Sets each listed attribute's base value, first stripping vanilla's random spawn bonuses, so the file's numbers
	 * are the real numbers; then heals to the new maximum. Unknown attributes are logged, not fatal.
	 */
	public void applyAttributes(Map<Identifier, Double> attributes) {
		for (Map.Entry<Identifier, Double> e : attributes.entrySet()) {
			Optional<Holder.Reference<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(e.getKey());
			AttributeInstance instance = holder.map(this::getAttribute).orElse(null);
			if (instance == null) {
				AshfallArms.LOGGER.warn("finale/settings.json: Choir attribute {} is unknown or not on a zombie", e.getKey());
				continue;
			}
			instance.removeModifiers();
			instance.setBaseValue(e.getValue());
		}
		setHealth(getMaxHealth());
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.AMETHYST_BLOCK_RESONATE;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.AMETHYST_CLUSTER_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.AMETHYST_CLUSTER_BREAK;
	}

	@Override
	public float getVoicePitch() {
		return 0.5f;
	}
}
