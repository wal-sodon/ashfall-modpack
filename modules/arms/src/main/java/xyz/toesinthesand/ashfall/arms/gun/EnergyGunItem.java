package xyz.toesinthesand.ashfall.arms.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.arms.item.EnergyItems;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An energy gun. Right-click fires: semi-automatic guns once per click (rate-limited by an item cooldown),
 * automatic guns continuously while the button is held (via {@link #onUseTick}), both capped by the
 * gun's RPM. Items carry a {@code minecraft:use_effects} component (see ArmsItems) so holding the
 * trigger doesn't crawl at the vanilla 20% "using an item" speed. All balance numbers come from {@link GunStats} (datapack); what is fixed here is identity:
 * the weapon class (which pack it takes), semi or auto, the firing sound and the bolt's look.
 * <p>
 * State lives in components: {@link ArmsComponents#GUN_STATE} (lazy heat), {@link ArmsComponents#LOADED_PACK},
 * {@link ArmsComponents#ATTACHMENTS} and {@link ArmsComponents#FIRE_MODE}. The only tick is the main-hand one that times the draw ({@link DrawTime});
 * heat cools lazily. Firing logic is in {@link GunLogic}.
 */
public class EnergyGunItem extends Item {
	/**
	 * Which shot sound a gun uses, and how loud it is. Loudness is the volume passed to the server, so it
	 * sets how far the shot carries (16 blocks per 1.0, the vanilla rule for variable-range sounds);
	 * the quieter-or-louder character of each gun is in sounds.json.
	 */
	public enum FireSound {
		LIGHT(() -> ArmsSounds.GUN_FIRE_LIGHT, 1.0f),
		HEAVY(() -> ArmsSounds.GUN_FIRE_HEAVY, 2.0f),
		AUTO(() -> ArmsSounds.GUN_FIRE_AUTO, 1.5f),
		/** Light pistols: quiet, high zaps. */
		PISTOL(() -> ArmsSounds.GUN_FIRE_PISTOL, 1.0f),
		MAGNUM(() -> ArmsSounds.GUN_FIRE_MAGNUM, 1.75f),
		CARBINE(() -> ArmsSounds.GUN_FIRE_CARBINE, 1.5f),
		RIFLE(() -> ArmsSounds.GUN_FIRE_RIFLE, 2.0f),
		/** Deep and loud: heard 48 blocks away. */
		LANCER(() -> ArmsSounds.GUN_FIRE_LANCER, 3.0f),
		/** Fast, rattly stream. */
		STORM(() -> ArmsSounds.GUN_FIRE_STORM, 2.0f);

		private final Supplier<SoundEvent> sound;
		private final float loudness;

		FireSound(Supplier<SoundEvent> sound, float loudness) {
			this.sound = sound;
			this.loudness = loudness;
		}

		public SoundEvent sound() {
			return sound.get();
		}

		/** Server-side volume of an undampened shot (the Dampener scales it down). */
		public float loudness() {
			return loudness;
		}
	}

	public static final String TOOLTIP_PACK = "tooltip.ashfall.gun.pack";
	public static final String TOOLTIP_NO_PACK = "tooltip.ashfall.gun.no_pack";
	public static final String TOOLTIP_DAMPENER = "tooltip.ashfall.gun.dampener";
	/** "+ %s fitted", one line per attachment. */
	public static final String TOOLTIP_FITTED = "tooltip.ashfall.gun.fitted";
	public static final String TOOLTIP_RELOAD = "tooltip.ashfall.gun.reload";
	public static final String TOOLTIP_SCOPE = "tooltip.ashfall.gun.scope";
	public static final String TOOLTIP_DAMAGE = "tooltip.ashfall.gun.damage";
	public static final String TOOLTIP_FIRE = "tooltip.ashfall.gun.fire";
	/** "Fire mode: %s · press %s to switch", on guns with a stun mode. */
	public static final String TOOLTIP_MODE = "tooltip.ashfall.gun.mode";

	private final GunClass gunClass;
	private final boolean automatic;
	private final FireSound fireSound;
	private final BoltStyle boltStyle;

	public EnergyGunItem(Properties properties, GunClass gunClass, boolean automatic, FireSound fireSound, BoltStyle boltStyle) {
		super(properties);
		this.gunClass = gunClass;
		this.automatic = automatic;
		this.fireSound = fireSound;
		this.boltStyle = boltStyle;
	}

	public GunClass gunClass() {
		return gunClass;
	}

	public boolean isAutomatic() {
		return automatic;
	}

	public FireSound fireSound() {
		return fireSound;
	}

	public BoltStyle boltStyle() {
		return boltStyle;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		// Guns fire only from the main hand, where the draw time and the weight apply. PASS lets an off-hand
		// gun do nothing at all (vanilla would otherwise try the off hand whenever the main hand passes).
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		// Every gun starts "using" on both sides. For auto guns that keeps onUseTick firing while the button
		// is held. For semi guns it is what makes them one shot per click: vanilla only repeats use() every
		// 4 ticks while the button is held and the player is *not* using an item, so a held click on a gun
		// that is in use never fires again until it is released and pressed anew.
		player.startUsingItem(hand);
		if (level instanceof ServerLevel server) {
			GunLogic.FireResult result = GunLogic.tryFire(server, player, stack);
			if (automatic && result.stopsAutoFire()) {
				player.stopUsingItem();
			}
		}
		// CONSUME, not SUCCESS: a gun shouldn't swing the arm like a tool.
		return InteractionResult.CONSUME;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
		if (!automatic || !(level instanceof ServerLevel server)) {
			return;
		}
		// A mob holding the trigger down (another mod's AI, a zombie that picked one up) is stopped at once, not every tick.
		if (!GunLogic.canFire(entity)) {
			entity.stopUsingItem();
			return;
		}
		// The item cooldown (reload, dry click) also holds back a trigger that is already down.
		if (entity instanceof Player player && player.getCooldowns().isOnCooldown(stack)) {
			return;
		}
		GunLogic.FireResult result = GunLogic.tryFire(server, entity, stack);
		if (result.stopsAutoFire()) {
			entity.stopUsingItem();
		}
	}

	/** Drawing the gun into the main hand puts it on its draw-time cooldown; see {@link DrawTime}. */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
		if (slot == EquipmentSlot.MAINHAND && owner instanceof ServerPlayer player && !player.isSpectator()) {
			GunStats stats = GunStatsManager.get(false, this);
			DrawTime.onMainHandTick(player, stack, stats == null ? 0 : stats.drawTicks());
		}
	}

	/** Held for as long as the button is down (see {@link #use}); release ends it, nothing happens on release. */
	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return APPROXIMATELY_INFINITE_USE_DURATION;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	/** Firing rewrites components every shot; never bob the held gun for that. */
	@Override
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		return false;
	}

	/** The bar shows the loaded pack's charge, so ammo is visible from the hotbar. */
	@Override
	public boolean isBarVisible(ItemStack stack) {
		return stack.has(ArmsComponents.LOADED_PACK);
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		ItemStackTemplate pack = stack.get(ArmsComponents.LOADED_PACK);
		return pack == null ? 0 : EnergyItems.barWidth(EnergyItems.stored(pack), EnergyItems.capacity(pack));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return EnergyItems.CHARGE_BAR_COLOR;
	}

	@Override
	@SuppressWarnings("deprecation") // still the only per-item tooltip hook short of a custom data component
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
								Consumer<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, tooltip, flag);
		tooltip.accept(Component.translatable("tooltip.ashfall.gun.class." + gunClass.name().toLowerCase(java.util.Locale.ROOT))
				.append(" · ")
				.append(Component.translatable(automatic ? "tooltip.ashfall.gun.auto" : "tooltip.ashfall.gun.semi"))
				.withStyle(ChatFormatting.GRAY));
		// Live numbers from the synced datapack stats, after attachments, so /reload balance changes show up here.
		GunAttachments fittedAttachments = stack.getOrDefault(ArmsComponents.ATTACHMENTS, GunAttachments.NONE);
		GunStats base = GunStatsManager.get(true, this);
		if (base != null) {
			GunStats live = AttachmentMath.apply(base, AttachmentStatsManager.fitted(true, fittedAttachments), false);
			tooltip.accept(Component.translatable(TOOLTIP_DAMAGE, number(live.damage()), number(live.damage() * live.headshotMultiplier()))
					.withStyle(ChatFormatting.WHITE));
			tooltip.accept(Component.translatable(TOOLTIP_FIRE, number(live.rpm()), number(live.energyPerShot()), number(live.noiseRadius()))
					.withStyle(ChatFormatting.GRAY));
			if (GunLogic.hasStunMode(live)) {
				FireMode mode = GunLogic.fireMode(stack);
				tooltip.accept(Component.translatable(TOOLTIP_MODE,
						Component.translatable("tooltip.ashfall.gun.mode." + mode.getSerializedName()),
						Component.keybind("key.ashfall.fire_mode"))
						.withStyle(mode == FireMode.STUN ? ChatFormatting.AQUA : ChatFormatting.GRAY));
			}
		}
		String ability = "tooltip.ashfall.gun.ability." + BuiltInRegistries.ITEM.getKey(this).getPath();
		tooltip.accept(Component.translatableWithFallback(ability, "").withStyle(ChatFormatting.GOLD));
		ItemStackTemplate pack = stack.get(ArmsComponents.LOADED_PACK);
		if (pack == null) {
			tooltip.accept(Component.translatable(TOOLTIP_NO_PACK).withStyle(ChatFormatting.RED));
		} else {
			tooltip.accept(Component.translatable(TOOLTIP_PACK, EnergyItems.stored(pack), EnergyItems.capacity(pack))
					.withStyle(ChatFormatting.AQUA));
		}
		GunAttachments attachments = stack.getOrDefault(ArmsComponents.ATTACHMENTS, GunAttachments.NONE);
		for (String id : attachments.ids()) {
			tooltip.accept(Component.translatable(TOOLTIP_FITTED, Component.translatableWithFallback("item.ashfall." + id, id))
					.withStyle(ChatFormatting.DARK_GREEN));
		}
		if (!attachments.isEmpty()) {
			// Tooltips render on the client, so read the synced copies; a marksman rifle shows its higher scope.
			GunStats stats = GunStatsManager.get(true, this);
			ScopeMath.Levels scope = ScopeMath.levels(AttachmentMath.zoom(AttachmentStatsManager.fitted(true, attachments)),
					stats == null ? 0f : stats.scopeZoom());
			if (scope != null) {
				tooltip.accept(Component.translatable(scope.count() == 1 ? TOOLTIP_SCOPE + "_single" : TOOLTIP_SCOPE, scope.describe()).withStyle(ChatFormatting.DARK_GREEN));
			}
		}
		tooltip.accept(Component.translatable(TOOLTIP_RELOAD, Component.keybind("key.ashfall.reload"))
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	/** 4 for 4.0, 1.5 for 1.5: stats are mostly whole numbers and a trailing ".0" is noise in a tooltip. */
	static String number(float value) {
		float rounded = Math.round(value * 10f) / 10f;
		return rounded == (int) rounded ? Integer.toString((int) rounded) : Float.toString(rounded);
	}
}
