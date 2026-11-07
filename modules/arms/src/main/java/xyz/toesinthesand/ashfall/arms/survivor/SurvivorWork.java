package xyz.toesinthesand.ashfall.arms.survivor;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.arms.grid.DistrictLoop;
import xyz.toesinthesand.ashfall.arms.grid.GridActions;
import xyz.toesinthesand.ashfall.arms.grid.GridDistricts;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;
import xyz.toesinthesand.ashfall.director.fortify.FortifyWear;
import xyz.toesinthesand.ashfall.director.fortify.WearSettings;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreRecord;
import xyz.toesinthesand.ashfall.director.haven.BunkerCores;
import xyz.toesinthesand.ashfall.director.haven.ChunkArea;
import xyz.toesinthesand.ashfall.director.infection.Infection;
import xyz.toesinthesand.ashfall.director.infection.InfectionData;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;
import xyz.toesinthesand.ashfall.director.spawn.DirectorDay;
import xyz.toesinthesand.ashfall.director.traps.SpikeStripBlock;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * What the base roles do (survivors plan, build order step 2). Each works only while settled and living at home
 * ({@link SurvivorMode#HOME}) and takes everything it uses from the Supply Lockers of its home claim, and puts
 * everything it brings back into them ({@link SupplyLockers}).
 * <ul>
 * <li><b>Scavenger:</b> once a day, in the morning (06:00 to 10:00 on the day clock), it takes one food (the least
 * nutritious) and goes out for half a day. While out it stays where it stood at home, hidden, harmless and
 * untouchable ({@link Survivor#away}); the return time is saved with it, so a restart or an unloaded base just brings
 * it back as soon as it loads again. It comes home with about half a building room: one building loot table rolled
 * twice (a room holds about four containers), picked from the profiles common in its home's zone ({@link #haulTables}).
 * 8% come back at half health, 3% never come back (the leader hears it). What does not fit in the lockers stays in its pack and goes in when there is room;
 * one that comes back to a broken core (no home any more) drops the haul at its feet.
 * <li><b>Medic:</b> every 5 seconds, within 16 blocks of home, and only for its own group (players in its leader's
 * party and survivors they lead, {@link #medicGroup}): cures a bitten survivor with
 * Antibiotics; holds an infected player's infection still for as long as an Antibiotics dose would (but without the
 * dose's stage back) with a Bandage, once per player per day ({@link #NURSED_DAY}); else bandages the most hurt player
 * or survivor for the Bandage's own heal.
 * <li><b>Mechanic:</b> every 30 seconds: repairs the worn fortified block nearest home in its home claim
 * ({@link FortifyWear}) by one set of the block's repair materials from the lockers, {@code repair_points} of wear
 * (Scrap Metal for metal and obsidian, planks and two Nails for wood; at most {@code sets_per_turn} sets a turn, one
 * since 10.2.2, so a wall under attack slows its fall rather than holding forever); else hammers all wear out of
 * one worn Spike Strip in its home claim for one Scrap Metal, else puts one worn key relay (in its claim or its home's
 * district) back in full condition for a Wire Spool and Duct Tape, the same repair as a player's
 * ({@link GridActions#repairRelayChunk}). Ordinary blocks' damage is not repaired: it lives inside Zombies Break and
 * Build (which restores them itself) with no API to reach it.
 * </ul>
 * {@link #workSpeed} scales all three (intervals and the haul) and a guard's swings. Every number is in
 * {@link SurvivorBalance} (the ones above are the shipped values).
 */
public final class SurvivorWork {
	/** How often (ticks) a survivor carrying a haul tries the lockers again. */
	static final int DEPOSIT_TICKS = 100;
	/** Profiles used when no profile names the home's zone. */
	static final Map<Identifier, Integer> GENERIC_MIX = Map.of(
			id("buildings/home"), 4, id("buildings/shop"), 2, id("buildings/grocery"), 2,
			id("buildings/office"), 1, id("buildings/warehouse"), 1, id("buildings/generic"), 2);

	/** The day clock's day a medic last nursed this player's infection. Persistent so a relog does not reset it. */
	public static final AttachmentType<Integer> NURSED_DAY = AttachmentRegistry.createPersistent(
			id("survivor_nursed_day"), Codec.INT);

	/** Food survivors won't eat however hungry they are: rotten flesh, spider eyes, poisonous potatoes. */
	public static final TagKey<Item> REFUSED_FOOD = TagKey.create(Registries.ITEM, id("survivors_refuse"));
	/** Anything edible that fills them, bar {@link #REFUSED_FOOD}; survivors eat the least nutritious first ({@link #LEAST_NUTRITIOUS}). */
	public static final Predicate<ItemStack> FOOD = s -> s.has(DataComponents.FOOD) && !s.is(REFUSED_FOOD) && nutrition(s) > 0;
	public static final Comparator<ItemStack> LEAST_NUTRITIOUS = Comparator.comparingInt(SurvivorWork::nutrition);
	public static final Predicate<ItemStack> BANDAGE = s -> s.is(DirectorItems.BANDAGE);
	public static final Predicate<ItemStack> ANTIBIOTICS = s -> s.is(DirectorItems.ANTIBIOTICS);
	public static final Predicate<ItemStack> SCRAP = s -> s.is(SalvageItems.SCRAP_METAL);
	public static final Predicate<ItemStack> TAPE = s -> s.is(SalvageItems.DUCT_TAPE);
	public static final Predicate<ItemStack> WIRE = s -> s.is(ArmsItems.WIRE_SPOOL);

	private SurvivorWork() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	/** Loads the class so {@link #NURSED_DAY} registers at startup. */
	static void init() {
	}

	/** A survivor's work state, saved with it under {@code "work"}. */
	public static final class State {
		public static final int PACK_SIZE = 27;
		static final long NOT_AWAY = -1L;

		/** Game time the scavenger is due back, or {@link #NOT_AWAY}. */
		long awayUntil = NOT_AWAY;
		/** Day-clock day of the last trip, so it goes once a day. */
		int lastTripDay;
		/** A haul that did not fit in the lockers yet. */
		final NonNullList<ItemStack> pack = NonNullList.withSize(PACK_SIZE, ItemStack.EMPTY);
		/** Ticks until the next medic or mechanic job; not saved (a reload just starts the wait again). */
		int cooldown;

		public boolean away() {
			return awayUntil != NOT_AWAY;
		}

		public long awayUntil() {
			return awayUntil;
		}

		public boolean carrying() {
			for (ItemStack stack : pack) {
				if (!stack.isEmpty()) {
					return true;
				}
			}
			return false;
		}

		void save(ValueOutput output) {
			output.putLong("away_until", awayUntil);
			output.putInt("last_trip_day", lastTripDay);
			ContainerHelper.saveAllItems(output, pack);
		}

		void load(ValueInput input) {
			awayUntil = input.getLongOr("away_until", NOT_AWAY);
			lastTripDay = input.getIntOr("last_trip_day", 0);
			pack.clear();
			ContainerHelper.loadAllItems(input, pack);
		}
	}

	/** Hunger points one of {@code stack} fills. */
	public static int nutrition(ItemStack stack) {
		FoodProperties food = stack.get(DataComponents.FOOD);
		return food == null ? Integer.MAX_VALUE : food.nutrition();
	}

	/**
	 * How fast {@code survivor} works, 1.0 at full speed: the medic's and mechanic's intervals and a guard's time between
	 * swings are divided by it, and a scavenger keeps each found stack with this chance. Half speed for the first day in
	 * a new role ({@link #newInRole}), half while hungry ({@link SurvivorUpkeep}); both together are a quarter, and never
	 * below {@link SurvivorBalance.Roles#minSpeed}.
	 */
	public static double workSpeed(Survivor survivor) {
		SurvivorBalance balance = SurvivorBalance.get();
		double speed = 1.0;
		if (newInRole(survivor)) {
			speed *= balance.roles().firstDaySpeed();
		}
		if (survivor.hungry()) {
			speed *= balance.upkeep().hungrySpeed();
		}
		return speed >= 1.0 ? 1.0 : Math.max(balance.roles().minSpeed(), speed);
	}

	/** Still in its first in-game day in the role it was last given (the role it spawned with does not count). */
	public static boolean newInRole(Survivor survivor) {
		return survivor.role() != SurvivorRole.SETTLER
				&& survivor.level().getGameTime() - survivor.roleSince() < SurvivorBalance.get().roles().firstDayTicks();
	}

	/** Living at home: settled, told to live there, and the core still stands. */
	public static boolean atHome(ServerLevel level, Survivor survivor) {
		return survivor.mode() == SurvivorMode.HOME && survivor.home() != null && Survivors.coreAt(level, survivor.home()).isPresent();
	}

	/** One AI step at home (not while away, see {@link #tickAway}). */
	static void tick(ServerLevel level, Survivor survivor) {
		State work = survivor.work();
		if (work.carrying() && survivor.tickCount % DEPOSIT_TICKS == 0) {
			depositOrDrop(level, survivor);
		}
		if (!atHome(level, survivor)) {
			return;
		}
		switch (survivor.role()) {
			case SCAVENGER -> {
				if (survivor.tickCount % 20 == 0) {
					tryLeave(level, survivor, level.getOverworldClockTime());
				}
			}
			case MEDIC, MECHANIC -> {
				if (--work.cooldown > 0) {
					return;
				}
				SurvivorBalance balance = SurvivorBalance.get();
				int base = survivor.role() == SurvivorRole.MEDIC ? balance.medic().intervalTicks() : balance.mechanic().intervalTicks();
				work.cooldown = (int) Math.ceil(base / Math.max(0.05, workSpeed(survivor)));
				if (survivor.role() == SurvivorRole.MEDIC) {
					medic(level, survivor);
				} else {
					mechanic(level, survivor);
				}
			}
			default -> {
			}
		}
	}

	// ---- scavenger ----

	/** Why a scavenger at home would not set out now, or null when it would (the day clock is passed in for tests). */
	public static @Nullable String notLeaving(ServerLevel level, Survivor survivor, long clock) {
		State work = survivor.work();
		if (work.away()) {
			return "away";
		}
		if (work.lastTripDay == DirectorDay.fromClock(clock)) {
			return "been_out";
		}
		SurvivorBalance.Scavenger rules = SurvivorBalance.get().scavenger();
		long time = Math.floorMod(clock, 24_000L);
		if (time < rules.leaveFrom() || time >= rules.leaveUntil()) {
			return "morning";
		}
		// below this share of its health it stays home until a Medic patches it up (one back hurt, at exactly half, goes)
		if (survivor.infected() || survivor.getHealth() < survivor.getMaxHealth() * rules.fitToLeave()) {
			return "hurt";
		}
		if (work.carrying()) {
			return "full";
		}
		if (SupplyLockers.count(level, survivor, FOOD) == 0) {
			return "no_food";
		}
		return null;
	}

	/** Sets out when it is time ({@link #notLeaving}). @return whether it left */
	public static boolean tryLeave(ServerLevel level, Survivor survivor, long clock) {
		return notLeaving(level, survivor, clock) == null && leave(level, survivor, clock);
	}

	/** Takes one food (the least nutritious) from the lockers and goes. @return false (and stays) when there is no food */
	public static boolean leave(ServerLevel level, Survivor survivor, long clock) {
		if (SupplyLockers.takeOne(level, survivor, FOOD, LEAST_NUTRITIOUS).isEmpty()) {
			return false;
		}
		State work = survivor.work();
		work.lastTripDay = DirectorDay.fromClock(clock);
		work.awayUntil = level.getGameTime() + SurvivorBalance.get().scavenger().tripTicks();
		survivor.goAway(true);
		return true;
	}

	/** While out: comes back once the trip time is over. */
	static void tickAway(ServerLevel level, Survivor survivor) {
		if (level.getGameTime() >= survivor.work().awayUntil) {
			comeBack(level, survivor, survivor.getRandom().nextFloat());
		}
	}

	/**
	 * The end of a trip with a given risk roll in [0, 1), so tests can pin each outcome: below the lost chance it never
	 * comes back, below that plus the hurt chance it comes back at half health; otherwise unhurt. A survivor
	 * that comes back brings a haul ({@link #rollHaul}) and puts it in the lockers.
	 *
	 * @return the haul (empty when it was lost)
	 */
	public static List<ItemStack> comeBack(ServerLevel level, Survivor survivor, float roll) {
		State work = survivor.work();
		work.awayUntil = State.NOT_AWAY;
		SurvivorBalance.Scavenger rules = SurvivorBalance.get().scavenger();
		if (roll < rules.lostChance()) {
			ServerPlayer leader = survivor.leader() == null ? null : level.getServer().getPlayerList().getPlayer(survivor.leader());
			if (leader != null) {
				leader.sendSystemMessage(Component.translatable("message.ashfall.survivor.scavenger_lost", survivor.survivorName())
						.withStyle(ChatFormatting.RED));
			}
			survivor.discard();
			return List.of();
		}
		survivor.goAway(false);
		if (roll < rules.lostChance() + rules.hurtChance()) {
			survivor.setHealth(Math.min(survivor.getHealth(), survivor.getMaxHealth() * 0.5f));
		}
		List<ItemStack> haul = rollHaul(level, survivor);
		for (ItemStack stack : haul) {
			pack(work, stack);
		}
		depositOrDrop(level, survivor);
		return haul;
	}

	/**
	 * Into the lockers of its home; with no home left (its core broken while it was out, or since) the haul goes on the
	 * floor at its feet instead of being carried around for good.
	 */
	static void depositOrDrop(ServerLevel level, Survivor survivor) {
		BlockPos home = survivor.home();
		if (home == null || Survivors.coreAt(level, home).isEmpty()) {
			dropPack(level, survivor);
		} else {
			deposit(level, survivor);
		}
	}

	/** Into the first pack slots with room; anything past a full pack is lost on the road. */
	private static void pack(State work, ItemStack stack) {
		ItemStack left = stack.copy();
		for (ItemStack in : work.pack) {
			if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, left)) {
				int move = Math.min(left.getCount(), in.getMaxStackSize() - in.getCount());
				in.grow(move);
				left.shrink(move);
			}
		}
		for (int i = 0; i < work.pack.size() && !left.isEmpty(); i++) {
			if (work.pack.get(i).isEmpty()) {
				work.pack.set(i, left.split(left.getMaxStackSize()));
			}
		}
	}

	/** Moves what it carries into the lockers, as far as there is room. @return whether it is all in */
	public static boolean deposit(ServerLevel level, Survivor survivor) {
		State work = survivor.work();
		boolean empty = true;
		for (int i = 0; i < work.pack.size(); i++) {
			ItemStack stack = work.pack.get(i);
			if (!stack.isEmpty()) {
				ItemStack left = SupplyLockers.fit(level, survivor, stack);
				work.pack.set(i, left);
				empty &= left.isEmpty();
			}
		}
		return empty;
	}

	/** Drops whatever it carries (it died or turned, or has no home to put it in). */
	static void dropPack(ServerLevel level, Survivor survivor) {
		NonNullList<ItemStack> pack = survivor.work().pack;
		for (int i = 0; i < pack.size(); i++) {
			if (!pack.get(i).isEmpty()) {
				survivor.spawnAtLocation(level, pack.get(i));
				pack.set(i, ItemStack.EMPTY);
			}
		}
	}

	/**
	 * One trip's finds: a building table from {@link #haulTables} rolled {@link SurvivorBalance.Scavenger#haulRolls}
	 * times, rerolled up to {@link SurvivorBalance.Scavenger#haulAttempts} times when nothing came of it, each stack kept
	 * with chance {@link #workSpeed}, and an Antibiotics stack only with chance
	 * {@link SurvivorBalance.Scavenger#antibioticsKeep} on top (10.2.2, in every zone).
	 */
	public static List<ItemStack> rollHaul(ServerLevel level, Survivor survivor) {
		RandomSource random = survivor.getRandom();
		BlockPos at = survivor.home() != null ? survivor.home() : survivor.blockPosition();
		Identifier tableId = pick(haulTables(at), random);
		LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, tableId));
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at))
				.create(LootContextParamSets.CHEST);
		double keep = workSpeed(survivor);
		SurvivorBalance.Scavenger rules = SurvivorBalance.get().scavenger();
		List<ItemStack> out = new ArrayList<>();
		for (int attempt = 0; attempt < rules.haulAttempts() && out.isEmpty(); attempt++) {
			for (int roll = 0; roll < rules.haulRolls(); roll++) {
				for (ItemStack stack : table.getRandomItems(params)) {
					if (!stack.isEmpty() && (keep >= 1.0 || random.nextDouble() < keep)
							&& (!ANTIBIOTICS.test(stack) || keepAntibiotics(rules.antibioticsKeep(), random))) {
						out.add(stack);
					}
				}
			}
		}
		return out;
	}

	/**
	 * The building tables a scavenger from {@code home} picks from, with weights: every profile whose tag rules name the
	 * home's zone in {@code lc_zone} (weighted by how many rules do), or {@link #GENERIC_MIX} when none does.
	 */
	public static Map<Identifier, Integer> haulTables(BlockPos home) {
		String zone = ZoneMap.zoneAt(home.getX(), home.getZ()).id();
		Map<Identifier, Integer> out = new LinkedHashMap<>();
		for (BuildingProfile profile : BuildingProfiles.all().values()) {
			Optional<Identifier> table = profile.lootTable();
			if (table.isEmpty() || !table.get().getPath().startsWith("buildings/")) {
				continue;
			}
			for (Map<String, String> rule : profile.match()) {
				String zones = rule.get("lc_zone");
				if (zones != null && List.of(zones.split("\\|")).contains(zone)) {
					out.merge(table.get(), 1, Integer::sum);
				}
			}
		}
		return out.isEmpty() ? GENERIC_MIX : out;
	}

	/** Whether a hauled Antibiotics stack comes home: always at 1, never at 0, else with chance {@code keep}. */
	public static boolean keepAntibiotics(float keep, RandomSource random) {
		return keep >= 1f || (keep > 0f && random.nextFloat() < keep);
	}

	private static Identifier pick(Map<Identifier, Integer> weighted, RandomSource random) {
		int total = weighted.values().stream().mapToInt(Integer::intValue).sum();
		int r = random.nextInt(Math.max(1, total));
		for (Map.Entry<Identifier, Integer> e : weighted.entrySet()) {
			r -= e.getValue();
			if (r < 0) {
				return e.getKey();
			}
		}
		return weighted.keySet().iterator().next();
	}

	/** "Back around 14:00": the day-clock time the trip ends at. */
	static String returnTime(ServerLevel level, Survivor survivor) {
		long clock = level.getOverworldClockTime() + Math.max(0L, survivor.work().awayUntil - level.getGameTime());
		long time = Math.floorMod(clock, 24_000L);
		long minutes = Math.round((time % 1000) * 60 / 1000.0 / 15.0) * 15;
		long hours = (time / 1000 + 6 + minutes / 60) % 24;
		return String.format("%02d:%02d", hours, minutes % 60);
	}

	// ---- medic ----

	/** What a medic did on its turn. */
	public enum Treatment { NONE, CURED, NURSED, HEALED }

	/** One medic turn (see the class comment); public for gametests. */
	public static Treatment medic(ServerLevel level, Survivor medic) {
		BlockPos home = medic.home();
		if (home == null) {
			return Treatment.NONE;
		}
		Vec3 centre = Vec3.atCenterOf(home);
		SurvivorBalance.Medic rules = SurvivorBalance.get().medic();
		double rangeSqr = rules.range() * rules.range();
		UUID group = medicGroup(level, medic);
		List<Survivor> survivors = level.getEntitiesOfClass(Survivor.class, new net.minecraft.world.phys.AABB(centre, centre).inflate(rules.range()),
				s -> s.isAlive() && !s.away() && s.distanceToSqr(centre) <= rangeSqr
						&& (s == medic || s.leader() != null && inGroup(level, group, s.leader())));
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(centre) <= rangeSqr
					&& inGroup(level, group, player.getUUID())) {
				players.add(player);
			}
		}
		// a bite first: it is a death sentence
		for (Survivor bitten : survivors) {
			if (bitten.infected() && !SupplyLockers.takeOne(level, medic, ANTIBIOTICS).isEmpty()) {
				SurvivorInfection.cure(bitten);
				treated(level, medic, bitten);
				return Treatment.CURED;
			}
		}
		int today = DirectorDay.today(level);
		for (ServerPlayer player : players) {
			InfectionData data = Infection.get(player);
			if (data.infected() && player.getAttachedOrElse(NURSED_DAY, 0) != today
					&& !SupplyLockers.takeOne(level, medic, BANDAGE).isEmpty()) {
				int pause = Infection.rules().treatments().antibioticPauseTicks();
				Infection.set(player, new InfectionData(true, data.progress(), Math.max(data.pauseTicks(), pause)));
				player.setAttached(NURSED_DAY, today);
				player.sendSystemMessage(SurvivorTalk.line(medic, "medic.nursed").withStyle(ChatFormatting.GREEN));
				treated(level, medic, player);
				return Treatment.NURSED;
			}
		}
		LivingEntity worst = null;
		float worstMissing = rules.worthABandage() - 0.001f;
		for (LivingEntity patient : concat(players, survivors)) {
			float missing = patient.getMaxHealth() - patient.getHealth();
			if (missing > worstMissing) {
				worst = patient;
				worstMissing = missing;
			}
		}
		if (worst != null && !SupplyLockers.takeOne(level, medic, BANDAGE).isEmpty()) {
			worst.heal(Infection.rules().treatments().bandageHeal());
			treated(level, medic, worst);
			return Treatment.HEALED;
		}
		return Treatment.NONE;
	}

	/**
	 * Whose people a medic treats: its leader's group (the leader and their OPAC party, the key Bunker Cores are owned
	 * by), or for a medic without a leader its home core's owners. Null when it has neither.
	 */
	static @Nullable UUID medicGroup(ServerLevel level, Survivor medic) {
		if (medic.leader() != null) {
			return BunkerCores.ownerKey(level.getServer(), medic.leader());
		}
		return medic.home() == null ? null : Survivors.coreAt(level, medic.home()).map(BunkerCoreRecord::ownerKey).orElse(null);
	}

	private static boolean inGroup(ServerLevel level, @Nullable UUID group, UUID player) {
		return group != null && group.equals(BunkerCores.ownerKey(level.getServer(), player));
	}

	private static List<LivingEntity> concat(List<? extends LivingEntity> a, List<? extends LivingEntity> b) {
		List<LivingEntity> out = new ArrayList<>(a);
		out.addAll(b);
		return out;
	}

	private static void treated(ServerLevel level, Survivor medic, LivingEntity patient) {
		medic.getLookControl().setLookAt(patient);
		if (patient != medic) {
			medic.getNavigation().moveTo(patient, 1.0);
		}
		level.playSound(null, patient.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 0.8f, 1.2f);
	}

	// ---- mechanic ----

	/** What a mechanic fixed on its turn. */
	public enum Repair { NONE, WALL, SPIKE_STRIP, RELAY }

	/** One mechanic turn (see the class comment); public for gametests. */
	public static Repair mechanic(ServerLevel level, Survivor mechanic) {
		BlockPos home = mechanic.home();
		Optional<BunkerCoreRecord> core = home == null ? Optional.empty() : Survivors.coreAt(level, home);
		if (core.isEmpty()) {
			return Repair.NONE;
		}
		ChunkArea area = core.get().area();
		BlockPos wall = repairWall(level, mechanic, area, home);
		if (wall != null) {
			level.playSound(null, wall, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.2f);
			mechanic.getNavigation().moveTo(wall.getX() + 0.5, wall.getY(), wall.getZ() + 0.5, 1.0);
			return Repair.WALL;
		}
		if (SupplyLockers.count(level, mechanic, SCRAP) > 0) {
			BlockPos strip = wornStrip(level, area, home);
			if (strip != null && !SupplyLockers.takeOne(level, mechanic, SCRAP).isEmpty()) {
				level.setBlock(strip, level.getBlockState(strip).setValue(SpikeStripBlock.WEAR, 0), Block.UPDATE_ALL);
				level.playSound(null, strip, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.4f);
				mechanic.getNavigation().moveTo(strip.getX() + 0.5, strip.getY(), strip.getZ() + 0.5, 1.0);
				return Repair.SPIKE_STRIP;
			}
		}
		if (SupplyLockers.count(level, mechanic, WIRE) > 0 && SupplyLockers.count(level, mechanic, TAPE) > 0) {
			for (Map.Entry<String, Long> relay : candidateRelays(level, area, home)) {
				if (GridActions.relayNeedsRepair(level, relay.getKey(), relay.getValue())
						&& GridActions.repairRelayChunk(level, relay.getKey(), relay.getValue()) == DistrictLoop.Result.COMPLETED) {
					SupplyLockers.takeOne(level, mechanic, WIRE);
					SupplyLockers.takeOne(level, mechanic, TAPE);
					level.playSound(null, mechanic.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.5f, 1.3f);
					return Repair.RELAY;
				}
			}
		}
		return Repair.NONE;
	}

	/**
	 * Repairs the worn fortified block nearest home that the lockers hold a full set of materials for (every material
	 * at the count its rule lists), one set per {@code repair_points}, up to {@code sets_per_turn} sets or until it is
	 * whole. @return the block repaired, or null
	 */
	static @Nullable BlockPos repairWall(ServerLevel level, Survivor mechanic, ChunkArea area, BlockPos home) {
		List<BlockPos> worn = new ArrayList<>(FortifyWear.worn(level, area).keySet());
		worn.sort(Comparator.comparingDouble(pos -> pos.distSqr(home)));
		int step = WearSettings.get().repairPoints();
		int sets = SurvivorBalance.get().mechanic().setsPerTurn();
		for (BlockPos pos : worn) {
			Map<Predicate<ItemStack>, Integer> materials = FortifyWear.materialCounts(level.getBlockState(pos));
			if (materials.isEmpty()) {
				continue;
			}
			int done = 0;
			while (done < sets && FortifyWear.wear(level, pos) > 0 && materials.entrySet().stream()
					.allMatch(m -> SupplyLockers.count(level, mechanic, m.getKey()) >= m.getValue())) {
				materials.forEach((m, count) -> {
					for (int n = 0; n < count; n++) {
						SupplyLockers.takeOne(level, mechanic, m);
					}
				});
				FortifyWear.repair(level, pos, step);
				done++;
			}
			if (done > 0) {
				return pos;
			}
		}
		return null;
	}

	/** The worn Spike Strip nearest home in loaded chunks of the claim, or null. */
	static @Nullable BlockPos wornStrip(ServerLevel level, ChunkArea area, BlockPos home) {
		Predicate<BlockState> worn = s -> s.is(DirectorBlocks.SPIKE_STRIP) && s.getValue(SpikeStripBlock.WEAR) > 0;
		BlockPos best = null;
		double bestSqr = Double.MAX_VALUE;
		for (int cx = area.chunkX() - area.radius(); cx <= area.chunkX() + area.radius(); cx++) {
			for (int cz = area.chunkZ() - area.radius(); cz <= area.chunkZ() + area.radius(); cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				LevelChunkSection[] sections = chunk.getSections();
				for (int index = 0; index < sections.length; index++) {
					LevelChunkSection section = sections[index];
					if (section.hasOnlyAir() || !section.maybeHas(worn)) {
						continue;
					}
					int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								if (worn.test(section.getBlockState(x, y, z))) {
									BlockPos pos = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
									double d = pos.distSqr(home);
									if (d < bestSqr) {
										best = pos;
										bestSqr = d;
									}
								}
							}
						}
					}
				}
			}
		}
		return best;
	}

	/** Key relays a home's mechanic looks after: those in its claim's chunks, then every one of its home's district. */
	static List<Map.Entry<String, Long>> candidateRelays(ServerLevel level, ChunkArea area, BlockPos home) {
		List<Map.Entry<String, Long>> out = new ArrayList<>();
		for (int cx = area.chunkX() - area.radius(); cx <= area.chunkX() + area.radius(); cx++) {
			for (int cz = area.chunkZ() - area.radius(); cz <= area.chunkZ() + area.radius(); cz++) {
				long chunk = ChunkPos.pack(cx, cz);
				GridDistricts.keyRelayDistrict(cx, cz).ifPresent(d -> out.add(Map.entry(d, chunk)));
			}
		}
		GridDistricts.districtAt(level, home).ifPresent(d -> {
			for (long chunk : GridDistricts.keyRelayChunks(d)) {
				Map.Entry<String, Long> entry = Map.entry(d, chunk);
				if (!out.contains(entry)) {
					out.add(entry);
				}
			}
		});
		return out;
	}

	// ---- talk ----

	/** The role's status line for the talk menu, or null for roles with nothing to report. */
	static @Nullable Component status(ServerLevel level, Survivor survivor) {
		SurvivorRole role = survivor.role();
		if (survivor.work().away()) {
			return SurvivorTalk.line(survivor, "status.away", returnTime(level, survivor));
		}
		if (survivor.work().carrying()) {
			return SurvivorTalk.line(survivor, "status.carrying");
		}
		if (!role.needsHome()) {
			return null;
		}
		if (!atHome(level, survivor)) {
			return SurvivorTalk.line(survivor, "status.not_home");
		}
		return switch (role) {
			case SCAVENGER -> {
				String why = notLeaving(level, survivor, level.getOverworldClockTime());
				yield SurvivorTalk.line(survivor, "status.scavenger." + (why == null ? "ready" : why));
			}
			case MEDIC -> {
				int bandages = SupplyLockers.count(level, survivor, BANDAGE);
				int antibiotics = SupplyLockers.count(level, survivor, ANTIBIOTICS);
				yield bandages + antibiotics == 0 ? SurvivorTalk.line(survivor, "status.medic.empty")
						: bandages == 0 ? SurvivorTalk.line(survivor, "status.medic.no_bandages", antibiotics)
						: SurvivorTalk.line(survivor, "status.medic.stock", bandages, antibiotics);
			}
			case MECHANIC -> {
				int scrap = SupplyLockers.count(level, survivor, SCRAP);
				int wire = SupplyLockers.count(level, survivor, WIRE);
				int tape = SupplyLockers.count(level, survivor, TAPE);
				yield scrap == 0 && (wire == 0 || tape == 0) ? SurvivorTalk.line(survivor, "status.mechanic.empty")
						: SurvivorTalk.line(survivor, "status.mechanic.stock", scrap, wire, tape);
			}
			default -> null;
		};
	}
}
