package xyz.toesinthesand.ashfall.arms.blade;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import team.reborn.energy.api.base.SimpleEnergyItem;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.gun.ArmsNoise;
import xyz.toesinthesand.ashfall.arms.item.EnergyItems;

import java.util.function.Consumer;

/**
 * The Plasma Blade: a melee weapon with one long-term cost (Resonance Crystal wear, {@link BladeCrystal})
 * and one in-the-moment limit (charge, a Team Reborn Energy store refilled at Field Chargers and Power
 * Relays like a Cell Pack).
 * <p>
 * The item attribute says 9 damage (the plan's number, shown in the tooltip); every hit is then reshaped
 * in {@link #getAttackDamageBonus}: powered or bare hilt, the sweep's 60% for targets 2 and 3, nothing
 * for a 4th ({@link BladeLogic}), and the bonus against crystal pylons. The costs are paid in
 * {@link #hurtEnemy}, which vanilla only calls for a hit that landed. Better Combat supplies the sweep
 * itself from {@code data/ashfall/weapon_attributes/plasma_blade.json}; without it the blade hits one
 * target per swing and every rule still holds.
 * <p>
 * The blade can't be a charger's fuel: its energy output is 0, so a full blade in the cell slot is refused.
 */
public class PlasmaBladeItem extends Item implements SimpleEnergyItem {
	/** Charge capacity. Identity, not balance: the client draws the bar from it before any datapack syncs. */
	public static final long CAPACITY = 120L;
	/** Total attack damage the item attribute gives (player base 1 + modifier 8): the scale strikes are rescaled from. */
	public static final float ATTRIBUTE_DAMAGE = 9f;
	public static final float ATTRIBUTE_SPEED = 1.6f;
	/**
	 * Integrity a crystal starts at in the Resonant Plasma Blade (the Bind ending's blade): four times the plain
	 * blade's 600, i.e. wear at a quarter of the rate. Identity like {@link #CAPACITY}: the item's default component
	 * needs it before any datapack loads; the datapack's {@code crystal_integrity} for that blade should match it.
	 */
	public static final int RESONANT_CRYSTAL_INTEGRITY = 2400;

	/**
	 * Entities that take {@link BladeStats#pylonDamageMultiplier} (the finale's Heart pylons, M9.5). The tag already
	 * names {@code ashfall:heart_pylon} as an optional entry, so the pylon gets the blade bonus the day it is
	 * registered under that id, with no change here.
	 */
	public static final TagKey<EntityType<?>> CRYSTAL_PYLON_ENTITIES = TagKey.create(Registries.ENTITY_TYPE,
			Identifier.fromNamespaceAndPath("ashfall", "crystal_pylons"));
	/**
	 * Blocks a lit blade cuts through fast: the Heart pylons, which endgame-design section 4 makes blocks. Like the
	 * entity tag it optionally names {@code ashfall:heart_pylon} ahead of M9.5.
	 */
	public static final TagKey<Block> CRYSTAL_PYLON_BLOCKS = TagKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath("ashfall", "crystal_pylons"));

	public static final String TOOLTIP_CRYSTAL = "tooltip.ashfall.blade.crystal";
	public static final String TOOLTIP_DULL = "tooltip.ashfall.blade.dull";
	public static final String TOOLTIP_UNPOWERED = "tooltip.ashfall.blade.unpowered";
	public static final String TOOLTIP_SWEEP = "tooltip.ashfall.blade.sweep";
	public static final String MESSAGE_DULL = "message.ashfall.blade.dull";

	public PlasmaBladeItem(Properties properties) {
		super(properties);
	}

	// ------------------------------------------------------------------ energy

	@Override
	public long getEnergyCapacity(ItemStack stack) {
		return CAPACITY;
	}

	@Override
	public long getEnergyMaxInput(ItemStack stack) {
		return CAPACITY;
	}

	/** 0: chargers charge a blade but never draw on it, so it can't fuel one. */
	@Override
	public long getEnergyMaxOutput(ItemStack stack) {
		return 0;
	}

	public static BladeCrystal crystal(ItemStack stack) {
		return stack.getOrDefault(ArmsComponents.BLADE_CRYSTAL, BladeCrystal.FRESH);
	}

	/** Whether the blade would cut right now: a crystal with integrity left and charge for one swing. */
	public static boolean canIgnite(ItemStack stack, BladeStats stats) {
		return !crystal(stack).dull() && SimpleEnergyItem.getStoredEnergyUnchecked(stack) >= stats.energyPerSwing();
	}

	/**
	 * Mirrors {@link #canIgnite} into the {@code ashfall:blade_lit} marker, which the item model (beam on or
	 * off), LambDynamicLights (data-driven light) and the Director's light sense read without knowing the
	 * blade's rules. Returns whether it changed.
	 */
	public static boolean refreshLit(ItemStack stack) {
		boolean lit = canIgnite(stack, BladeStatsManager.get(stack.getItem()));
		if (lit == stack.has(ArmsComponents.BLADE_LIT)) {
			return false;
		}
		if (lit) {
			stack.set(ArmsComponents.BLADE_LIT, Unit.INSTANCE);
		} else {
			stack.remove(ArmsComponents.BLADE_LIT);
		}
		return true;
	}

	/** Charge changes behind the item's back (chargers, other mods), so the marker is re-checked while carried. */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
		refreshLit(stack);
	}

	// ------------------------------------------------------------------ combat

	@Override
	public float getAttackDamageBonus(Entity target, float damage, DamageSource source) {
		// The client also runs Player.attack for prediction; only the server counts swings.
		if (target.level().isClientSide() || !(source.getEntity() instanceof Player player)) {
			return 0f;
		}
		ItemStack stack = source.getWeaponItem() != null ? source.getWeaponItem() : player.getMainHandItem();
		BladeStats stats = BladeStatsManager.get(this);
		long now = target.level().getGameTime();
		BladeLogic.Swing swing = BladeLogic.registerHit(player.getUUID(), now, canIgnite(stack, stats));
		boolean pylon = target.typeHolder().is(CRYSTAL_PYLON_ENTITIES);
		float dealt = BladeLogic.strikeDamage(damage, ATTRIBUTE_DAMAGE, stats, swing.powered(), swing.hits(), pylon);
		return dealt - damage;
	}

	/** Pays for a landed hit: charge once per swing, crystal wear per target, hunger for the extra targets. */
	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (!(attacker.level() instanceof ServerLevel level) || !(attacker instanceof Player player)) {
			return;
		}
		BladeLogic.Swing swing = BladeLogic.current(player.getUUID(), level.getGameTime());
		if (swing == null || !swing.powered()) {
			return; // a bare hilt costs nothing
		}
		BladeStats stats = BladeStatsManager.get(this);
		boolean free = player.hasInfiniteMaterials();
		if (swing.first()) {
			if (!free) {
				long stored = SimpleEnergyItem.getStoredEnergyUnchecked(stack);
				SimpleEnergyItem.setStoredEnergyUnchecked(stack, Math.max(0L, stored - stats.energyPerSwing()));
			}
			// Quiet next to any blaster, but not silent: the hum and crackle carry a few blocks.
			ArmsNoise.emit(level, player.blockPosition(), stats.noiseRadius());
		}
		float exhaustion = BladeLogic.extraExhaustion(swing.hits(), stats.maxTargets(), stats.extraTargetExhaustion());
		if (exhaustion > 0f) {
			player.causeFoodExhaustion(exhaustion);
		}
		if (!free) {
			BladeCrystal before = crystal(stack);
			BladeCrystal after = before.wear(stats.wearPerHit());
			stack.set(ArmsComponents.BLADE_CRYSTAL, after);
			if (after.dull() && !before.dull()) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK,
						SoundSource.PLAYERS, 1.0f, 0.8f);
				player.sendOverlayMessage(Component.translatable(MESSAGE_DULL).withStyle(ChatFormatting.RED));
			}
		}
		refreshLit(stack);
	}

	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		if (stack.has(ArmsComponents.BLADE_LIT) && state.is(CRYSTAL_PYLON_BLOCKS)) {
			return BladeStatsManager.get(this).pylonMiningSpeed();
		}
		return super.getDestroySpeed(stack, state);
	}

	// ------------------------------------------------------------------ display

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return EnergyItems.barWidth(getStoredEnergy(stack), CAPACITY);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return EnergyItems.CHARGE_BAR_COLOR;
	}

	/** Charge and wear rewrite components on every hit; don't bob the held blade for that. */
	@Override
	public boolean allowComponentsUpdateAnimation(Player player, net.minecraft.world.InteractionHand hand, ItemStack oldStack,
												  ItemStack newStack) {
		return false;
	}

	@Override
	@SuppressWarnings("deprecation") // still the only per-item tooltip hook short of a custom data component
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
								Consumer<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, tooltip, flag);
		tooltip.accept(EnergyItems.energyLine(getStoredEnergy(stack), CAPACITY));
		BladeCrystal crystal = crystal(stack);
		if (crystal.dull()) {
			tooltip.accept(Component.translatable(TOOLTIP_DULL).withStyle(ChatFormatting.RED));
		} else {
			tooltip.accept(Component.translatable(TOOLTIP_CRYSTAL, crystal.integrity(), crystal.max())
					.withStyle(ChatFormatting.LIGHT_PURPLE));
			if (!stack.has(ArmsComponents.BLADE_LIT)) {
				tooltip.accept(Component.translatable(TOOLTIP_UNPOWERED).withStyle(ChatFormatting.GOLD));
			}
		}
		tooltip.accept(Component.translatable(TOOLTIP_SWEEP).withStyle(ChatFormatting.DARK_GRAY));
	}
}
