package xyz.toesinthesand.ashfall.arms.survivor;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.infection.Infection;
import xyz.toesinthesand.ashfall.director.spawn.DirectorContexts;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariant;
import xyz.toesinthesand.ashfall.director.special.SpecialInfectedEntity;

/**
 * Survivor infection (survivors plan, phase 1). A survivor bitten by a zombie rolls the same chance a player would
 * ({@link Infection#rules}' sources, by Director variant and zone, armour counted), and a bitten survivor carries a
 * plain clock instead of the player's four stages: {@link #TURN_TICKS} later it turns. Turning swaps it for a Director
 * zombie on the spot, holding its weapon, and tells its leader; the survivor is gone for good. A survivor that dies
 * while bitten turns the same way.
 * <p>
 * The clock lives on the survivor and is saved with it ({@link Survivor#infection}); it only runs while the survivor is
 * loaded, like the rest of its AI. Antibiotics used on a bitten survivor (right-click with them) cure it outright, the
 * player's Sever-ending cure: there is no Medic yet to nurse it through stages. Its nameplate says it is bitten.
 */
public final class SurvivorInfection {
	/** One in-game day from bite to turning. */
	public static final long TURN_TICKS = 24_000L;
	/** The variant name turned survivors spawn as (a plain walker). */
	public static final String TURNED_VARIANT = ZombieVariant.FALLBACK_ID;

	private SurvivorInfection() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(SurvivorInfection::afterDamage);
	}

	/** Only direct, damaging melee from a zombie-family mob counts as a bite, as for players. */
	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (blocked || damageTaken <= 0 || !(entity instanceof Survivor survivor) || !survivor.isAlive()) {
			return;
		}
		if (source.getDirectEntity() instanceof Zombie zombie) {
			onZombieHit(survivor, zombie, survivor.getRandom().nextFloat());
		}
	}

	/**
	 * One zombie hit with a given roll in [0, 1), so tests can pin both outcomes. A second bite does not restart the clock.
	 *
	 * @return whether it infected
	 */
	public static boolean onZombieHit(Survivor survivor, Zombie zombie, float roll) {
		if (survivor.infected() || roll >= hitChance(survivor, zombie)) {
			return false;
		}
		infect(survivor);
		return true;
	}

	/** The chance one hit from {@code zombie} infects {@code survivor} where it stands: the player rules, no Hazmat. */
	public static float hitChance(Survivor survivor, Zombie zombie) {
		DirectorZombie tag = zombie.getAttached(DirectorSpawns.TAG);
		String variant = tag != null ? tag.variant()
				: zombie instanceof SpecialInfectedEntity special ? special.kind().id() : null;
		String zone = DirectorContexts.zoneAt(survivor.blockPosition()).id();
		return Infection.rules().sources().chance(variant, zone, survivor.getArmorValue());
	}

	/** Starts the clock on a healthy survivor (bites, tests); its leader hears about it. */
	public static void infect(Survivor survivor) {
		if (survivor.infected()) {
			return;
		}
		survivor.setInfection(0L);
		if (survivor.leaderPlayer() instanceof ServerPlayer leader) {
			leader.sendSystemMessage(Component.translatable("message.ashfall.survivor.bitten_note", survivor.survivorName())
					.withStyle(ChatFormatting.RED));
		}
	}

	/** Antibiotics: cures a bitten survivor. Returns false (and nothing is used) when it was not bitten. */
	public static boolean cure(Survivor survivor) {
		if (!survivor.infected()) {
			return false;
		}
		survivor.setInfection(Survivor.HEALTHY);
		return true;
	}

	/** One tick of a bitten survivor's clock (from its AI step); turns it at the end. */
	static void tick(ServerLevel level, Survivor survivor) {
		if (!survivor.infected()) {
			return;
		}
		long next = survivor.infection() + 1;
		survivor.setInfection(next);
		if (next >= TURN_TICKS) {
			turn(level, survivor);
		}
	}

	/** {@code ticks} of the clock at once (gametests), turning it if that runs out. */
	public static void advance(ServerLevel level, Survivor survivor, long ticks) {
		if (!survivor.infected()) {
			return;
		}
		survivor.setInfection(Math.min(TURN_TICKS, survivor.infection() + ticks));
		if (survivor.infection() >= TURN_TICKS) {
			turn(level, survivor);
		}
	}

	/**
	 * The end: a Director walker takes the survivor's place, holding its weapon (dropped for sure when it dies), and the
	 * survivor is removed without dying (no death message, nothing else dropped). The leader is told either way.
	 *
	 * @return the zombie, or null if it could not be spawned (the survivor is gone regardless)
	 */
	public static @Nullable Zombie turn(ServerLevel level, Survivor survivor) {
		ItemStack weapon = survivor.getMainHandItem().copy();
		survivor.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		SurvivorWork.dropPack(level, survivor);
		Zombie zombie = rise(level, survivor, weapon);
		if (zombie == null && !weapon.isEmpty()) {
			survivor.spawnAtLocation(level, weapon);
		}
		survivor.discard();
		return zombie;
	}

	/**
	 * Spawns the zombie a bitten survivor becomes, at its feet and facing its way, and tells the leader. Also used when a
	 * bitten survivor dies of something else (its weapon then drops with the body, so {@code weapon} is empty).
	 */
	static @Nullable Zombie rise(ServerLevel level, Survivor survivor, ItemStack weapon) {
		Zombie zombie = DirectorSpawns.spawn(level, survivor.blockPosition(), EntityTypes.ZOMBIE, TURNED_VARIANT,
				ZombieVariant.FALLBACK, DirectorZombie.Source.AMBIENT, DirectorZombie.NO_BUILDING);
		if (zombie != null) {
			zombie.setYRot(survivor.getYRot());
			zombie.setPersistenceRequired();
			if (!weapon.isEmpty()) {
				zombie.setItemSlot(EquipmentSlot.MAINHAND, weapon);
				zombie.setGuaranteedDrop(EquipmentSlot.MAINHAND);
			}
		}
		if (survivor.leaderPlayer() instanceof ServerPlayer leader) {
			leader.sendSystemMessage(Component.translatable("message.ashfall.survivor.turned", survivor.survivorName())
					.withStyle(ChatFormatting.DARK_RED));
		}
		return zombie;
	}
}
