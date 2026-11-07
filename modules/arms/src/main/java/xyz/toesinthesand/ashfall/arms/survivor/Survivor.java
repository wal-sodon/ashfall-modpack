package xyz.toesinthesand.ashfall.arms.survivor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreRecord;
import xyz.toesinthesand.ashfall.director.haven.BunkerCores;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;
import xyz.toesinthesand.ashfall.director.spawn.ZombiesOnly;

import java.util.Optional;
import java.util.UUID;

/**
 * A living survivor (survivors plan, phase 1): a {@link PathfinderMob} drawn with the player model and one of
 * {@link #SKINS} original skins. It saves a name, a {@link SurvivorRole}, a leader (the player whose orders it takes,
 * along with that player's OPAC party), an optional home (the Bunker Core it settled at), a {@link SurvivorMode} and
 * the spot it was told to stay at. The nameplate reads "Name (Role)".
 * <p>
 * <b>Orders</b> come from right-clicking it (chat lines with clickable choices, {@link SurvivorTalk}) and run through
 * {@code /survivor <uuid> ...} ({@link SurvivorCommand}). Sneak-right-clicking a Guard with a weapon hands it over;
 * with an empty hand it takes the weapon back.
 * <p>
 * <b>Settling:</b> a survivor brought inside its leader's Bunker Core area and told [Stay] or [Settle here] there makes
 * that core its home ({@link #settle}) and switches to {@link SurvivorMode#HOME}: it wanders within {@link #HOME_RADIUS}
 * of the core. A core takes at most {@link SurvivorBalance.Caps#base} settlers. A broken core takes the home away again.
 * <p>
 * <b>Fighting:</b> Settlers run from zombies. Guards attack zombie-family mobs within {@link #GUARD_RANGE} with the
 * weapon in hand at {@link SurvivorBalance#guardDamageShare} of a player's damage (a transient multiplier on attack
 * damage, so the weapon's own modifiers count; bare-handed that is a player's 1.0), and the weapon wears as a player's
 * would. A Guard has {@link SurvivorBalance#guardBonusHealth} extra max health while in the role (10.2.2). Ranged
 * weapons are refused in phase 1: guards only swing.
 * <p>
 * <b>Danger:</b> zombies hunt survivors (director's {@code HuntSurvivorsGoal}, through the {@code #ashfall:survivors}
 * tag), a zombie's bite can infect one ({@link SurvivorInfection}: the clock is {@link #infection}, saved here, and the
 * nameplate reads "bitten"), and players can't hurt one (director's {@code PetGuard}). Antibiotics used on a bitten
 * survivor cure it. Every living survivor is on the {@link SurvivorRoster}, which the caps count.
 * <p>
 * <b>Base roles</b> (Scavenger, Medic, Mechanic) work only while living at home, from its Supply Lockers
 * ({@link SurvivorWork}, state saved under {@code "work"}). A scavenger out on a trip is {@link #away}: it stays where it
 * stood, hidden, with no AI, and cannot be seen (not even by spectators), touched or hurt until it comes back.
 * <p>
 * <b>Holdouts</b> ({@link SurvivorHoldouts}) spawn unled and hiding in a building's upper storey: they hold their spot,
 * zombies cannot find them until they call out to the first player who comes near (once, {@link #calledOut}), and the
 * first player to order one takes it in if their group has room.
 * <p>
 * <b>Upkeep:</b> a led survivor eats once a day, from its home's lockers while living at home and from its leader's
 * inventory anywhere else ({@link SurvivorUpkeep}: the day it last ate and how many days in a row it went without are
 * saved here). A hungry one works at half speed and its nameplate says so.
 * <p>
 * <b>Traders</b> ({@link SurvivorRole#TRADER}, {@link Traders}) stand at a reclaimed district's substation and trade
 * through the vanilla trading screen (it is the {@link Merchant}, forwarding to {@link #trade}, saved under
 * {@code "trade"}; every other role has no offers): right-click trades instead of
 * talking, they take no orders, stay within {@link SurvivorBalance.Trader#radius} of their post, and are not on the
 * roster.
 */
public class Survivor extends PathfinderMob implements Merchant {
	public static final int SKINS = 8;
	public static final double GUARD_RANGE = 16.0;
	public static final int HOME_RADIUS = 24;
	public static final double MAX_HEALTH = 20.0, MOVEMENT_SPEED = 0.3;
	/** How often (ticks) it checks settling and that its home still stands. */
	static final int HOME_CHECK_TICKS = 20;
	static final Identifier STRENGTH_ID = Identifier.fromNamespaceAndPath("ashfall", "survivor_strength");
	/** The Guard role's extra max health ({@link SurvivorBalance#guardBonusHealth}); permanent so it saves with the health. */
	public static final Identifier GUARD_HEALTH_ID = Identifier.fromNamespaceAndPath("ashfall", "survivor_guard_health");
	private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(Survivor.class, EntityDataSerializers.INT);
	/** {@link #away}, synced so the client also stops pushing against and aiming at a survivor that is out. */
	private static final EntityDataAccessor<Boolean> AWAY = SynchedEntityData.defineId(Survivor.class, EntityDataSerializers.BOOLEAN);

	private String survivorName = "";
	private SurvivorRole role = SurvivorRole.SETTLER;
	/** Game time of the last role change, for half speed on the first day in a new role ({@link SurvivorWork#workSpeed}). */
	private long roleSince = OLD_HAND;
	private @Nullable UUID leader;
	private @Nullable BlockPos home;
	private SurvivorMode mode = SurvivorMode.STAY;
	private @Nullable BlockPos stayPos;
	/** Ticks since the bite, or {@link #HEALTHY}. */
	private long infection = HEALTHY;
	/** The base roles' state: a scavenger's trip and pack ({@link SurvivorWork}). */
	private final SurvivorWork.State work = new SurvivorWork.State();
	/** Director day of its last daily meal (or of the day it settled), 0 before the first check ({@link SurvivorUpkeep}). */
	private int fedDay;
	/** Days in a row it went without food; above 0 it is hungry. */
	private int hungryDays;
	/** A trader's stock and customer; null for every other role. */
	private @Nullable SurvivorTrade trade;
	/** Hiding in a building, waiting to be found ({@link SurvivorHoldouts}); over once it has a leader. */
	private boolean holdout;
	/** A holdout that has called out to a player: zombies can find it now. */
	private boolean calledOut;

	/** {@link #infection} of a survivor that was never bitten (or was cured). */
	public static final long HEALTHY = -1L;
	/** {@link #roleSince} of a survivor that has had its role since it spawned: no first day to learn it. */
	static final long OLD_HAND = Long.MIN_VALUE / 2;

	/** Whether {@link #settle} made this survivor at home, and if not, why. */
	public enum Settle { SETTLED, ALREADY, NOT_LEADERS_BUNKER, FULL }

	public Survivor(EntityType<? extends Survivor> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		getNavigation().setCanOpenDoors(true);
		refreshStrength();
	}

	/** Scales attack damage to {@link SurvivorBalance#guardDamageShare} (again after a reload changes it). */
	void refreshStrength() {
		AttributeInstance attack = getAttribute(Attributes.ATTACK_DAMAGE);
		double amount = SurvivorBalance.get().guardDamageShare() - 1.0;
		if (attack != null) {
			AttributeModifier current = attack.getModifier(STRENGTH_ID);
			if (current == null || current.amount() != amount) {
				attack.removeModifier(STRENGTH_ID);
				attack.addTransientModifier(new AttributeModifier(STRENGTH_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
	}

	/**
	 * Adds or removes the Guard role's extra max health ({@link SurvivorBalance#guardBonusHealth}, again after a reload
	 * changes it), keeping the same share of health either way: a whole settler becomes a whole 30-health guard, and a
	 * guard leaving the job comes back to at most 20, so switching roles back and forth heals nothing.
	 */
	void refreshGuardHealth() {
		AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null) {
			return;
		}
		double amount = role == SurvivorRole.GUARD ? SurvivorBalance.get().guardBonusHealth() : 0.0;
		AttributeModifier current = maxHealth.getModifier(GUARD_HEALTH_ID);
		if ((current == null ? 0.0 : current.amount()) == amount) {
			return;
		}
		float share = getMaxHealth() > 0f ? getHealth() / getMaxHealth() : 1f;
		maxHealth.removeModifier(GUARD_HEALTH_ID);
		if (amount != 0.0) {
			maxHealth.addPermanentModifier(new AttributeModifier(GUARD_HEALTH_ID, amount, AttributeModifier.Operation.ADD_VALUE));
		}
		if (isAlive()) {
			setHealth(Math.min(getMaxHealth(), share * getMaxHealth()));
		}
	}

	/**
	 * Player-like numbers: 20 health (30 as a Guard, {@link #refreshGuardHealth}), a player's 1.0 base attack (a guard
	 * hits as hard as a player with the same weapon, but its fixed 1 s swing makes that about 60% of a player's damage
	 * a second with a sword), a quick walk.
	 */
	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
				.add(Attributes.ATTACK_DAMAGE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new SurvivorGoals.GuardMelee(this));
		goalSelector.addGoal(2, new AvoidEntityGoal<>(this, LivingEntity.class,
				e -> role != SurvivorRole.GUARD && ZombiesOnly.isZombieFamily(e), 8.0f, 1.0, 1.3, e -> true));
		goalSelector.addGoal(3, new OpenDoorGoal(this, true));
		goalSelector.addGoal(4, new SurvivorGoals.StayPut(this));
		goalSelector.addGoal(5, new SurvivorGoals.FollowLeader(this));
		goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 1.0));
		goalSelector.addGoal(7, new SurvivorGoals.HomeStroll(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new SurvivorGoals.GuardTarget(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SKIN, 0);
		builder.define(AWAY, false);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		ensureIdentity();
		if (stayPos == null) {
			stayPos = blockPosition();
		}
		return data;
	}

	/** A name and skin for a survivor that has none yet (spawn eggs, /summon, the spawn API). */
	void ensureIdentity() {
		if (survivorName.isEmpty()) {
			survivorName = SurvivorNames.pick(getRandom());
			entityData.set(SKIN, getRandom().nextInt(SKINS));
			refreshNameplate();
		}
	}

	// ---- state ----

	public String survivorName() {
		return survivorName;
	}

	public void setSurvivorName(String name) {
		survivorName = name;
		refreshNameplate();
	}

	public int skin() {
		return Math.floorMod(entityData.get(SKIN), SKINS);
	}

	public void setSkin(int skin) {
		entityData.set(SKIN, Math.floorMod(skin, SKINS));
	}

	public SurvivorRole role() {
		return role;
	}

	public long roleSince() {
		return roleSince;
	}

	public @Nullable UUID leader() {
		return leader;
	}

	public void setLeader(@Nullable UUID leader) {
		this.leader = leader;
		if (leader != null) {
			holdout = false;
		}
		noteRoster();
	}

	/** Hiding in a building until someone takes it in ({@link SurvivorHoldouts}). */
	public boolean holdout() {
		return holdout;
	}

	/** Whether this holdout has called out to a player yet. */
	public boolean calledOut() {
		return calledOut;
	}

	/** Makes it a holdout: hiding where it stands, not yet heard. */
	void hideAsHoldout() {
		holdout = true;
		calledOut = false;
		setMode(SurvivorMode.STAY);
		noteRoster();
	}

	void setCalledOut() {
		calledOut = true;
	}

	/** The leader when they are a player in this level, else null. */
	public @Nullable Player leaderPlayer() {
		return leader == null ? null : level().getPlayerByUUID(leader);
	}

	public @Nullable BlockPos home() {
		return home;
	}

	/** Sets (or with null clears) the Bunker Core this survivor calls home; a survivor at HOME without one stays put. */
	public void setHome(@Nullable BlockPos home) {
		this.home = home == null ? null : home.immutable();
		if (home == null && mode == SurvivorMode.HOME) {
			setMode(SurvivorMode.STAY);
		} else {
			applyMode();
		}
		noteRoster();
	}

	/**
	 * Writes the leader and home onto the {@link SurvivorRoster}, once this survivor is really in a server level. Traders
	 * stay off it (their own record is the {@link TraderRoster}).
	 */
	void noteRoster() {
		if (!isRemoved() && !isTrader() && level() instanceof ServerLevel level && level.getEntity(getUUID()) == this) {
			SurvivorRoster.get(level.getServer()).note(this);
		}
	}

	/** Ticks since the bite, or {@link #HEALTHY}. */
	public long infection() {
		return infection;
	}

	public boolean infected() {
		return infection != HEALTHY;
	}

	/** Sets the infection clock ({@link SurvivorInfection} drives it); the nameplate follows a bite or a cure. */
	public void setInfection(long ticks) {
		boolean was = infected();
		infection = ticks < 0 ? HEALTHY : ticks;
		if (was != infected()) {
			refreshNameplate();
		}
	}

	public SurvivorWork.State work() {
		return work;
	}

	public int fedDay() {
		return fedDay;
	}

	public int hungryDays() {
		return hungryDays;
	}

	/** Went without food on its last meal day: works at half speed ({@link SurvivorWork#workSpeed}). */
	public boolean hungry() {
		return hungryDays > 0;
	}

	/** Records a meal day ({@link SurvivorUpkeep} drives it); the nameplate follows hunger coming or going. */
	void setUpkeep(int fedDay, int hungryDays) {
		boolean was = hungry();
		this.fedDay = fedDay;
		this.hungryDays = Math.max(0, hungryDays);
		if (was != hungry()) {
			refreshNameplate();
		}
	}

	/** Out on a scavenging trip: hidden where it stood, with no AI, and nothing can see, touch or hurt it. */
	public boolean away() {
		return level().isClientSide() ? entityData.get(AWAY) : work.away();
	}

	/** Hides it for a trip, or shows it again on its return ({@link SurvivorWork} sets the trip itself). */
	void goAway(boolean away) {
		setInvisible(away);
		entityData.set(AWAY, away);
		getNavigation().stop();
		setTarget(null);
		if (away) {
			setDeltaMovement(Vec3.ZERO);
		}
	}

	/**
	 * Makes the leader's Bunker Core that holds this survivor its home and sends it to live there. Refused outside the
	 * leader's (or the leader's party's) core area, and when that core already has {@link SurvivorBalance.Caps#base}
	 * settlers. An unled survivor takes any core.
	 */
	public Settle settle(ServerLevel level) {
		Optional<BunkerCoreRecord> core = settleableCore(level);
		if (core.isEmpty()) {
			return Settle.NOT_LEADERS_BUNKER;
		}
		BlockPos at = core.get().pos();
		if (at.equals(home)) {
			setMode(SurvivorMode.HOME);
			return Settle.ALREADY;
		}
		if (SurvivorRoster.get(level.getServer()).homedAt(level.dimension(), at) >= SurvivorBalance.get().caps().base()) {
			return Settle.FULL;
		}
		home = at.immutable();
		setMode(SurvivorMode.HOME);
		noteRoster();
		return Settle.SETTLED;
	}

	/** The core whose area holds this survivor, when it belongs to its leader's side (any core while unled). */
	public Optional<BunkerCoreRecord> settleableCore(ServerLevel level) {
		Optional<BunkerCoreRecord> core = Survivors.coreContaining(level, blockPosition());
		if (core.isPresent() && leader != null && !core.get().ownerKey().equals(BunkerCores.ownerKey(level.getServer(), leader))) {
			return Optional.empty();
		}
		return core;
	}

	public SurvivorMode mode() {
		return mode;
	}

	/** Changes what it does between fights; STAY holds the current spot, HOME needs a home (else it stays). */
	public void setMode(SurvivorMode mode) {
		this.mode = mode == SurvivorMode.HOME && home == null ? SurvivorMode.STAY : mode;
		if (this.mode == SurvivorMode.STAY) {
			stayPos = blockPosition();
		}
		getNavigation().stop();
		applyMode();
	}

	public @Nullable BlockPos stayPos() {
		return stayPos;
	}

	public void setStayPos(@Nullable BlockPos pos) {
		stayPos = pos == null ? null : pos.immutable();
	}

	/**
	 * The vanilla home restriction is on only while living at home, so following and staying are not pulled back; a
	 * trader is always held to its post.
	 */
	private void applyMode() {
		if (isTrader() && stayPos != null) {
			setHomeTo(stayPos, SurvivorBalance.get().trader().radius());
		} else if (mode == SurvivorMode.HOME && home != null) {
			setHomeTo(home, HOME_RADIUS);
		} else {
			clearHome();
		}
	}

	/**
	 * Switches role at once (callers check {@link Survivors#roleChangeRefusal} first). A Guard leaving the job puts its
	 * weapon into the nearest Supply Locker in its home claim, or drops it at its feet.
	 *
	 * @return where the old guard's weapon went, or null when there was none to return
	 */
	public SupplyLockers.@Nullable Stored changeRole(SurvivorRole next) {
		if (next == role) {
			return null;
		}
		SurvivorRole old = role;
		role = next;
		roleSince = level().getGameTime();
		setTarget(null);
		refreshNameplate();
		refreshGuardHealth();
		if (old == SurvivorRole.GUARD && level() instanceof ServerLevel level) {
			ItemStack weapon = getMainHandItem();
			if (!weapon.isEmpty()) {
				setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				return SupplyLockers.store(level, this, weapon);
			}
		}
		return null;
	}

	/** The role it spawns with: set without a first day in it ({@link SurvivorWork#newInRole}). */
	void startAs(SurvivorRole first) {
		role = first;
		roleSince = OLD_HAND;
		refreshNameplate();
		refreshGuardHealth();
	}

	public boolean isTrader() {
		return role == SurvivorRole.TRADER;
	}

	/** A trader's side of the trading screen, or null for any other role. */
	public @Nullable SurvivorTrade trade() {
		return trade;
	}

	/** Takes up {@code district}'s stock at its current spot, its post from now on ({@link Traders#spawn}). */
	void becomeTrader(String district, long now) {
		startAs(SurvivorRole.TRADER);
		trade = new SurvivorTrade(this);
		trade.start(district, now);
		setMode(SurvivorMode.STAY);
	}

	/**
	 * A weapon handed over before guards refused it (a Plasma Blade from a pre-10.2.4 save, {@link Survivors#isMeleeWeapon})
	 * goes back: into the leader's inventory when they are online, otherwise dropped at its feet where it never despawns.
	 */
	public void returnRefusedWeapon(ServerLevel level) {
		ItemStack weapon = getMainHandItem();
		if (weapon.isEmpty() || Survivors.isMeleeWeapon(weapon)) {
			return;
		}
		equipWeapon(ItemStack.EMPTY);
		if (leaderPlayer() instanceof ServerPlayer sp) {
			sp.getInventory().placeItemBackInInventory(weapon, Prediction.SERVER_ONLY);
			sp.sendSystemMessage(SurvivorTalk.line(this, "weapon.plasma_blade"));
		} else {
			ItemEntity drop = spawnAtLocation(level, weapon);
			if (drop != null) {
				drop.setUnlimitedLifetime();
			}
		}
	}

	/** Puts a weapon in its hand, kept intact when it dies; returns what it held before. */
	public ItemStack equipWeapon(ItemStack weapon) {
		ItemStack old = getMainHandItem();
		setItemSlot(EquipmentSlot.MAINHAND, weapon);
		setGuaranteedDrop(EquipmentSlot.MAINHAND);
		return old;
	}

	private void refreshNameplate() {
		if (!survivorName.isEmpty()) {
			String key = "entity.ashfall.survivor.nameplate" + (infected() ? "_bitten" : "") + (hungry() ? "_hungry" : "");
			setCustomName(Component.translatable(key, survivorName, role.displayName()));
		}
	}

	// ---- behaviour ----

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		SurvivorInfection.tick(level, this);
		if (isRemoved()) {
			return; // turned
		}
		if (tickCount % HOME_CHECK_TICKS == 0) {
			ensureIdentity();
			checkHome(level);
			noteRoster();
			refreshStrength();
			refreshGuardHealth();
			returnRefusedWeapon(level);
			SurvivorUpkeep.tick(level, this);
			if (holdout && !calledOut) {
				SurvivorHoldouts.callOut(level, this);
			}
			if (isTrader()) {
				Traders.checkPost(level, this);
			}
			if (isRemoved()) {
				return; // left hungry, or a replaced trader
			}
		}
		if (trade != null && trade.getTradingPlayer() instanceof Player customer) {
			getNavigation().stop();
			getLookControl().setLookAt(customer);
		}
		SurvivorWork.tick(level, this);
	}

	/** While out scavenging nothing runs but the clock that brings it back ({@link SurvivorWork#tickAway}). */
	@Override
	public void aiStep() {
		if (away() && level() instanceof ServerLevel level) {
			SurvivorWork.tickAway(level, this);
			return;
		}
		super.aiStep();
	}

	/**
	 * Loses its home if that core is gone, and with it a base role (those need a home's lockers). Settling itself is an
	 * order ({@link #settle}).
	 */
	private void checkHome(ServerLevel level) {
		if (home != null && Survivors.coreAt(level, home).isEmpty()) {
			setHome(null);
			if (role.needsHome()) {
				changeRole(SurvivorRole.SETTLER);
			}
		}
	}

	/** Nothing hunts it while out, nor while a holdout is still hiding quietly. */
	@Override
	public boolean canBeSeenAsEnemy() {
		return !away() && !(holdout && !calledOut) && super.canBeSeenAsEnemy();
	}

	/** Away is invisible to everyone, spectators included (they otherwise see invisible mobs see-through). */
	@Override
	public boolean isInvisibleTo(Player player) {
		return away() || super.isInvisibleTo(player);
	}

	@Override
	public boolean shouldRender(double x, double y, double z) {
		return !away() && super.shouldRender(x, y, z);
	}

	@Override
	public boolean canBeHitByProjectile() {
		return !away() && super.canBeHitByProjectile();
	}

	/** Untouchable while away, except for what nothing survives (/kill, the void). */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return (!away() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) && super.hurtServer(level, source, amount);
	}

	@Override
	public boolean isPickable() {
		return !away() && super.isPickable();
	}

	@Override
	public boolean isPushable() {
		return !away() && super.isPushable();
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return away() || super.ignoreExplosion(explosion);
	}

	/** A scavenger's undelivered haul drops with it. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		SurvivorWork.dropPack(level, this);
	}

	/**
	 * Right-click talks (chat lines with the choices); sneak-right-click hands the held weapon over, or with an empty
	 * hand takes the guard's weapon back. A trader opens its trading screen either way (Antibiotics still treat it).
	 */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || away()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			if (player.getItemInHand(hand).is(DirectorItems.ANTIBIOTICS)) {
				SurvivorTalk.treat(sp, this, hand);
			} else if (isTrader()) {
				Traders.trade(sp, this);
			} else if (sp.isSecondaryUseActive()) {
				SurvivorTalk.handOver(sp, this, hand);
			} else {
				SurvivorTalk.talk(sp, this);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** After the vanilla hit (weapon damage, enchantments, knockback), the weapon wears like a player's. */
	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		ItemStack weapon = getMainHandItem();
		boolean hit = super.doHurtTarget(level, target);
		if (hit && !weapon.isEmpty() && target instanceof LivingEntity living) {
			weapon.postHurtEnemy(living, this);
		}
		return hit;
	}

	/** A bitten survivor that dies of anything rises (its leader hears it turned); otherwise the leader hears it died. */
	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel level && !isRemoved() && !dead) {
			if (infected()) {
				SurvivorInfection.rise(level, this, ItemStack.EMPTY);
			} else if (leaderPlayer() instanceof ServerPlayer sp) {
				SurvivorTalk.died(sp, this);
			}
		}
		super.die(source);
	}

	/**
	 * Struck off the roster when it dies, turns or is discarded; unloading and dimension changes keep the entry. A trader
	 * gone that way leaves its district due a new one ({@link Traders#gone}).
	 */
	@Override
	public void onRemoval(RemovalReason reason) {
		super.onRemoval(reason);
		if ((reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) && level() instanceof ServerLevel level) {
			SurvivorRoster.get(level.getServer()).forget(getUUID());
			if (isTrader()) {
				Traders.gone(level, this);
			}
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
		return false;
	}

	// ---- Merchant (traders only; forwards to the trade state) ----

	@Override
	public void setTradingPlayer(@Nullable Player player) {
		if (trade != null) {
			trade.setTradingPlayer(player);
		}
	}

	@Override
	public @Nullable Player getTradingPlayer() {
		return trade == null ? null : trade.getTradingPlayer();
	}

	@Override
	public MerchantOffers getOffers() {
		return trade == null ? new MerchantOffers() : trade.getOffers();
	}

	@Override
	public void overrideOffers(MerchantOffers offers) {
	}

	@Override
	public void notifyTrade(MerchantOffer offer) {
		if (trade != null) {
			trade.notifyTrade(offer);
		}
	}

	@Override
	public void notifyTradeUpdated(ItemStack stack) {
	}

	@Override
	public int getVillagerXp() {
		return 0;
	}

	@Override
	public void overrideXp(int xp) {
	}

	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return trade == null ? SoundEvents.ITEM_PICKUP : trade.getNotifyTradeSound();
	}

	@Override
	public boolean isClientSide() {
		return level().isClientSide();
	}

	@Override
	public boolean stillValid(Player player) {
		return trade != null && trade.stillValid(player);
	}

	// ---- save ----

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("survivor_name", survivorName);
		output.putString("role", role.id());
		output.putLong("role_since", roleSince);
		output.storeNullable("leader", UUIDUtil.CODEC, leader);
		output.storeNullable("home", BlockPos.CODEC, home);
		output.putString("mode", mode.id());
		output.storeNullable("stay_pos", BlockPos.CODEC, stayPos);
		output.putInt("skin", skin());
		output.putLong("infection", infection);
		work.save(output.child("work"));
		output.putInt("fed_day", fedDay);
		output.putInt("hungry_days", hungryDays);
		if (trade != null) {
			trade.save(output.child("trade"));
		}
		output.putBoolean("holdout", holdout);
		output.putBoolean("called_out", calledOut);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		survivorName = input.getStringOr("survivor_name", "");
		role = SurvivorRole.byId(input.getStringOr("role", SurvivorRole.SETTLER.id()));
		roleSince = input.getLongOr("role_since", OLD_HAND);
		leader = input.read("leader", UUIDUtil.CODEC).orElse(null);
		home = input.read("home", BlockPos.CODEC).orElse(null);
		mode = SurvivorMode.byId(input.getStringOr("mode", SurvivorMode.STAY.id()));
		stayPos = input.read("stay_pos", BlockPos.CODEC).orElse(null);
		setSkin(input.getIntOr("skin", 0));
		infection = Math.max(HEALTHY, input.getLongOr("infection", HEALTHY));
		work.load(input.childOrEmpty("work"));
		fedDay = input.getIntOr("fed_day", 0);
		hungryDays = Math.max(0, input.getIntOr("hungry_days", 0));
		trade = null;
		if (role == SurvivorRole.TRADER) {
			trade = new SurvivorTrade(this);
			trade.load(input.childOrEmpty("trade"));
		}
		holdout = input.getBooleanOr("holdout", false);
		calledOut = input.getBooleanOr("called_out", false);
		setInvisible(work.away());
		entityData.set(AWAY, work.away());
		applyMode();
		refreshNameplate();
	}
}
