package xyz.toesinthesand.ashfall.director.special;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

import java.util.Map;
import java.util.Optional;

/**
 * Shared base of the special infected. Extends {@link Zombie} on purpose: ZB&B's block breaking, the senses goal,
 * infection on hit, the zombies-only rule and other mods' "is it a zombie" checks all key off the class or the
 * {@code #minecraft:zombies} tag, so a special is a zombie everywhere without any per-mod glue.
 * <p>
 * What the base takes away from a vanilla zombie, and why:
 * <ul>
 *   <li>No babies, no chicken jockeys and no random gear: a special's silhouette is its warning, and a baby brute
 *   or a bloated in diamond armour would lie about what it is.</li>
 *   <li>No reinforcement calls: vanilla spawns reinforcements of the hurt zombie's own type, which would turn one
 *   brute into three.</li>
 *   <li>No drowning into a drowned: the conversion would silently drop the special's behaviour.</li>
 * </ul>
 * Stats come from {@code special_infected/<kind>.json} on every spawn (eggs and commands included), so the file is
 * the single source of truth; the registered defaults in {@link SpecialEntities} match it for when no file loads.
 */
public class SpecialInfectedEntity extends Zombie {
	private final SpecialKind kind;

	protected SpecialInfectedEntity(EntityType<? extends Zombie> type, Level level, SpecialKind kind) {
		super(type, level);
		this.kind = kind;
	}

	public SpecialKind kind() {
		return kind;
	}

	public SpecialInfectedRule rule() {
		return SpecialInfectedRules.get(kind);
	}

	/** One behaviour number from this kind's file, or {@code fallback}. */
	public float tuning(String key, float fallback) {
		return rule().tuning(key, fallback);
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
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
	}

	@Override
	protected void randomizeReinforcementsChance() {
		AttributeInstance reinforcements = getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
		if (reinforcements != null) {
			reinforcements.setBaseValue(0.0);
		}
	}

	/** Always an adult with no passenger, then the file's stats on top of vanilla's spawn rolls. */
	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
										@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, new Zombie.ZombieGroupData(false, false));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
				setItemSlot(slot, ItemStack.EMPTY);
			}
		}
		setCanPickUpLoot(false);
		applyRuleAttributes(rule().attributes());
		return data;
	}

	/**
	 * Sets each listed attribute's base value, first stripping the random spawn bonuses vanilla added to it, so the
	 * file's numbers are the real numbers. Unknown attributes are logged, not fatal: a typo must not stop spawning.
	 */
	public void applyRuleAttributes(Map<Identifier, Double> attributes) {
		for (Map.Entry<Identifier, Double> e : attributes.entrySet()) {
			Optional<Holder.Reference<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(e.getKey());
			AttributeInstance instance = holder.map(this::getAttribute).orElse(null);
			if (instance == null) {
				AshfallDirector.LOGGER.warn("special_infected/{}.json: attribute {} is unknown or not on a zombie", kind.id(), e.getKey());
				continue;
			}
			instance.removeModifiers();
			instance.setBaseValue(e.getValue());
		}
		setHealth(getMaxHealth());
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SpecialSounds.ambient(kind);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SpecialSounds.hurt(kind);
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SpecialSounds.death(kind);
	}
}
