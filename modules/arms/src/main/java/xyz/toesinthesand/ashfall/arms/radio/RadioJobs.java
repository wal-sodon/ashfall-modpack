package xyz.toesinthesand.ashfall.arms.radio;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.arms.gun.ArmsNoise;
import xyz.toesinthesand.ashfall.arms.story.Compass;
import xyz.toesinthesand.ashfall.arms.story.RelayLog;
import xyz.toesinthesand.ashfall.arms.story.RelayLogs;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRescue;
import xyz.toesinthesand.ashfall.director.claims.Claims;
import xyz.toesinthesand.ashfall.director.spawn.DirectorDay;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.spawn.SpawnContext;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariant;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariants;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.BuildingFinder;
import xyz.toesinthesand.ashfall.salvage.building.BuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.Footprint;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.Landmarks;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;
import xyz.toesinthesand.ashfall.salvage.loot.LootState;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Radio jobs: once a Shortwave Set stands somewhere, every morning it picks up {@link #OFFERS} calls pointing at real
 * buildings {@link #MIN_DISTANCE}-{@link #MAX_DISTANCE} blocks from a set. Accept one and the whole server has
 * {@link #DEADLINE_TICKS} (one day) to answer it:
 * <ul>
 * <li>a stash: a chest of supplies on the ground floor, placed as the first player comes within {@link #ARRIVE_RADIUS};
 * <li>a nest: zombies spawn on the ground floor as players arrive; kill every one and a reward chest appears;
 * <li>a distress call: a beacon on the top floor. Stand within {@link #DISTRESS_RADIUS} of it for
 * {@link #DISTRESS_HOLD_TICKS} while it calls zombies in; then it turns into a reward chest and leaves a note, and
 * from day 7 the caller comes along with whoever answered ({@link SurvivorRescue}).
 * </ul>
 * One job at a time, shared like supply drops, so a group answers a call together. Rewards come from
 * {@code ashfall:radio_job/<type>} or the {@code _rich} table in tier 3+ districts (Downtown, Mission, the ridge).
 * Calls are placed when a player gets there, not when offered: the building's chunks are unloaded until then.
 */
public final class RadioJobs {
	public static final int FIRST_DAY = 2;
	/** Morning: calls come in from here until {@link #START_UNTIL} (time of day). */
	public static final long START_FROM = 1_000L, START_UNTIL = 6_000L;
	public static final long DEADLINE_TICKS = 24_000L;
	public static final int OFFERS = 2;
	public static final int MIN_DISTANCE = 150, MAX_DISTANCE = 600, SEARCH_TRIES = 64;
	public static final int ARRIVE_RADIUS = 64;
	public static final int NEST_BASE = 5, NEST_PER_TIER = 2;
	public static final int DISTRESS_RADIUS = 6, DISTRESS_HOLD_TICKS = 60 * 20, DISTRESS_WAVE = 3;
	public static final float DISTRESS_NOISE = 40f;
	public static final String ZOMBIE_TAG = "ashfall_radio_job";
	private static final int UPDATE_TICKS = 20;

	/** The survivor's note a distress call leaves (title, author, pages). */
	private static final List<RelayLog> NOTES = List.of(
			new RelayLog(0, "Thank you", "M.", List.of(
					"You came. I had the radio on for nine days and you are the first voice that answered.",
					"I'm going over the ridge while the light holds. Take what's in the crate. I won't need it where I'm going.")),
			new RelayLog(0, "Roof, day 31", "Dee", List.of(
					"They hear the generator. They hear the radio. They hear me breathing, I think.",
					"If you're reading this I got out the back while they were busy with you. Sorry. And thanks.")),
			new RelayLog(0, "For whoever answers", "Unsigned", List.of(
					"There were four of us. The others went for water on Tuesday.",
					"I kept the beacon going in case. The case was you. Look after the crate better than we did.")),
			new RelayLog(0, "Signal log", "R. Okafor", List.of(
					"Transmitted every hour on the hour, 3 days. Battery at 4%.",
					"Contact made. Leaving the set on for the next one. Keep it running if you can.")));

	/** The last view each player was sent. */
	private static final Map<UUID, Optional<RadioJobPayload.View>> LAST = new HashMap<>();
	/**
	 * Job zombies that loaded while not belonging to the job under way (left over from a lost or finished job), to
	 * remove on the next tick. Deferred: removing inside the load callback is unsafe, and a zombie converting into a
	 * drowned loads before {@link #onConversion} hands it the job.
	 */
	private static final List<Mob> STRAYS = new ArrayList<>();

	private RadioJobs() {
	}

	public static void init() {
		RadioBlocks.init();
		PayloadTypeRegistry.clientboundPlay().register(RadioJobPayload.TYPE, RadioJobPayload.CODEC);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> RadioJobCommand.register(dispatcher));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			removeStrays(server.overworld());
			if (server.getTickCount() % UPDATE_TICKS == 7) {
				tick(server.overworld());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> onDeath(entity));
		ServerLivingEntityEvents.MOB_CONVERSION.register((from, to, params) -> onConversion(from, to));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && entity.entityTags().contains(ZOMBIE_TAG) && level.dimension() == Level.OVERWORLD) {
				STRAYS.add(mob);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> LAST.remove(handler.getPlayer().getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST.clear();
			STRAYS.clear();
		});
	}

	/** Whether {@code id} is one of the zombies of the job under way. */
	private static boolean ofActiveJob(ServerLevel level, UUID id) {
		return RadioJobState.get(level.getServer()).active().map(job -> job.zombies().contains(id)).orElse(false);
	}

	/** Removes job zombies that loaded without a job to belong to (see {@link #STRAYS}); public for gametests. */
	public static void removeStrays(ServerLevel level) {
		if (STRAYS.isEmpty()) {
			return;
		}
		List<Mob> due = List.copyOf(STRAYS);
		STRAYS.clear();
		for (Mob mob : due) {
			if (!mob.isRemoved() && mob.isAlive() && !ofActiveJob(level, mob.getUUID())) {
				mob.discard();
			}
		}
	}

	/** A nest zombie only counts as killed when it actually dies: it is crossed off the job's list. */
	static void onDeath(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level) || !entity.entityTags().contains(ZOMBIE_TAG)) {
			return;
		}
		RadioJobState state = RadioJobState.get(level.getServer());
		state.active().filter(job -> job.type() == RadioJob.Type.NEST && job.zombies().contains(entity.getUUID()))
				.ifPresent(job -> {
					List<UUID> left = new ArrayList<>(job.zombies());
					left.remove(entity.getUUID());
					state.setActive(Optional.of(job.withZombies(left)));
				});
	}

	/** A job zombie that converts (drowning into a drowned) is still the job's: the new mob takes its place. */
	static void onConversion(Mob from, Mob to) {
		if (!(from.level() instanceof ServerLevel level) || !from.entityTags().contains(ZOMBIE_TAG)) {
			return;
		}
		to.addTag(ZOMBIE_TAG);
		to.setPersistenceRequired();
		RadioJobState state = RadioJobState.get(level.getServer());
		state.active().filter(job -> job.zombies().contains(from.getUUID())).ifPresent(job -> {
			List<UUID> ids = new ArrayList<>(job.zombies());
			ids.set(ids.indexOf(from.getUUID()), to.getUUID());
			state.setActive(Optional.of(job.withZombies(ids)));
		});
	}

	public static void tick(ServerLevel level) {
		RadioJobState state = RadioJobState.get(level.getServer());
		pruneSets(level, state);
		clearLostJobs(level, state);
		Optional<RadioJob> active = state.active();
		if (active.isPresent()) {
			advance(level, state, active.get());
		} else {
			schedule(level, state);
		}
		sync(level, state);
	}

	/** Drops sets that were broken (only checked where the chunk is loaded, so far-off sets stay listed). */
	private static void pruneSets(ServerLevel level, RadioJobState state) {
		for (BlockPos pos : state.sets()) {
			if (level.isLoaded(pos) && !level.getBlockState(pos).is(RadioBlocks.SHORTWAVE_SET)) {
				state.removeSet(pos);
			}
		}
	}

	/**
	 * Clears what lost jobs left in chunks that were unloaded at their deadline, once those chunks load again; public for
	 * gametests.
	 */
	public static void clearLostJobs(ServerLevel level, RadioJobState state) {
		for (BlockPos pos : state.cleanups()) {
			if (level.isLoaded(pos)) {
				clearPlaced(level, pos);
				state.removeCleanup(pos);
			}
		}
	}

	/** Yesterday's unanswered calls go quiet; this morning's come in, if there is a set to hear them. */
	private static void schedule(ServerLevel level, RadioJobState state) {
		int day = DirectorDay.today(level);
		if (!state.offers().isEmpty() && day > state.offerDay()) {
			state.setOffers(List.of(), state.offerDay());
		}
		long timeOfDay = Math.floorMod(level.getOverworldClockTime(), 24_000L);
		if (!state.offers().isEmpty() || state.sets().isEmpty() || level.players().isEmpty() || day < FIRST_DAY
				|| day <= state.offerDay() || timeOfDay < START_FROM || timeOfDay >= START_UNTIL) {
			return;
		}
		RandomSource random = level.getRandom();
		BlockPos anchor = state.sets().get(random.nextInt(state.sets().size()));
		List<RadioJob> offers = makeOffers(level, anchor, random);
		state.setOffers(offers, day);
		if (!offers.isEmpty()) {
			for (ServerPlayer player : level.players()) {
				player.sendSystemMessage(Component.translatable("message.ashfall.radio_job.calls", offers.size()).withStyle(ChatFormatting.GOLD));
				level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.MASTER, 0.8f, 1.2f);
			}
		}
	}

	/** Up to {@link #OFFERS} calls of different types around {@code anchor}, never two for the same building. */
	public static List<RadioJob> makeOffers(ServerLevel level, BlockPos anchor, RandomSource random) {
		List<RadioJob.Type> types = new ArrayList<>(List.of(RadioJob.Type.values()));
		List<RadioJob> offers = new ArrayList<>();
		Set<Long> used = new HashSet<>();
		while (!types.isEmpty() && offers.size() < OFFERS) {
			RadioJob.Type type = types.remove(random.nextInt(types.size()));
			offer(level, anchor, type, random, used).ifPresent(job -> {
				offers.add(job);
				used.add(job.building());
			});
		}
		return offers;
	}

	/** A call of {@code type} for a suitable building in the ring around {@code anchor}, if one turns up. */
	public static Optional<RadioJob> offer(ServerLevel level, BlockPos anchor, RadioJob.Type type, RandomSource random, Set<Long> exclude) {
		BuildingSource source = AshfallSalvage.buildings();
		if (source.isEmpty()) {
			return Optional.empty();
		}
		return BuildingFinder.random(source, anchor, MIN_DISTANCE, MAX_DISTANCE,
				b -> !exclude.contains(b.id()) && suits(level, source, b, type), random, SEARCH_TRIES).map(b -> {
			Footprint f = b.footprint();
			int x = (f.minX() + f.maxX()) / 2, z = (f.minZ() + f.maxZ()) / 2;
			Zone zone = ZoneMap.zoneAt(x, z);
			return RadioJob.offer(type, b.id(), profile(b), zone.id(), zone.tier(), x, z);
		});
	}

	/** The right kind of building, standing whole, not a landmark and not someone's base. */
	static boolean suits(ServerLevel level, BuildingSource source, Building building, RadioJob.Type type) {
		if (!type.profiles().contains(profile(building))) {
			return false;
		}
		Footprint f = building.footprint();
		Optional<CityChunk> plan = source.chunk(f.minX() >> 4, f.minZ() >> 4);
		if (plan.isPresent()) {
			CityChunk c = plan.get();
			if (!c.hasBuilding() || c.ruinLevel() >= 0 || c.floors() < type.minFloors()
					|| Landmarks.isLandmarkBuilding(c.buildingId()) || Landmarks.isLandmarkBuilding(c.multiBuilding())) {
				return false;
			}
		}
		return !Claims.isClaimed(level, new BlockPos((f.minX() + f.maxX()) / 2, building.baseY(), (f.minZ() + f.maxZ()) / 2));
	}

	private static String profile(Building building) {
		Identifier id = BuildingProfiles.classifyByTags(building);
		return id == null ? "generic" : id.getPath();
	}

	/** Takes offer {@code index} (0-based) as the server's job. @return the message for whoever asked */
	public static Component accept(ServerLevel level, ServerPlayer by, int index) {
		RadioJobState state = RadioJobState.get(level.getServer());
		if (state.active().isPresent()) {
			return Component.translatable("message.ashfall.radio_job.busy").withStyle(ChatFormatting.GRAY);
		}
		if (index < 0 || index >= state.offers().size()) {
			return Component.translatable("message.ashfall.radio_job.no_offer").withStyle(ChatFormatting.GRAY);
		}
		RadioJob job = state.offers().get(index).accepted(level.getGameTime() + DEADLINE_TICKS);
		start(level, job);
		Component name = by == null ? Component.literal("?") : by.getDisplayName();
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(Component.translatable("message.ashfall.radio_job.accepted", name, describe(job, player))
					.withStyle(ChatFormatting.GOLD));
		}
		return Component.empty();
	}

	/** Makes {@code job} the one under way and drops the other offers (also how tests and admins start one). */
	public static void start(ServerLevel level, RadioJob job) {
		RadioJobState state = RadioJobState.get(level.getServer());
		state.setActive(Optional.of(job));
		state.setOffers(List.of(), state.offerDay());
		AshfallArms.LOGGER.info("Radio job: {} at {}, {} ({})", job.type().id(), job.x(), job.z(), job.profile());
	}

	/** Moves the job on: placing it when players arrive, checking it is done, or losing it at the deadline. */
	public static void advance(ServerLevel level, RadioJobState state, RadioJob job) {
		if (level.getGameTime() >= job.deadline()) {
			expire(level, state, job);
			return;
		}
		switch (job.stage()) {
			case RadioJob.WAITING -> arrive(level, state, job);
			case RadioJob.PLACED -> {
				switch (job.type()) {
					case STASH -> {
						if (claimed(level, job.spot().orElseThrow())) {
							complete(level, state, job);
						}
					}
					case NEST -> {
						// Each zombie is crossed off only when it dies (onDeath), so one in an unloaded chunk or
						// turned drowned still counts as alive. The chest needs the spot loaded to go in.
						BlockPos spot = job.spot().orElseThrow();
						if (level.isLoaded(spot) && job.zombies().isEmpty()) {
							placeChest(level, spot, job);
							state.setActive(Optional.of(job.rewarded(spot)));
							tell(level, spot, Component.translatable("message.ashfall.radio_job.nest_cleared").withStyle(ChatFormatting.GREEN));
						}
					}
					case DISTRESS -> {
						BlockPos beacon = job.spot().orElseThrow();
						if (level.isLoaded(beacon)) {
							ArmsNoise.emit(level, beacon, DISTRESS_NOISE);
							if (someoneBy(level, beacon)) {
								hold(level, state, job);
							}
						}
					}
				}
			}
			default -> {
				if (claimed(level, job.spot().orElseThrow())) {
					complete(level, state, job);
				}
			}
		}
	}

	/** The first player comes near: the stash chest, the nest's zombies or the beacon go in. */
	private static void arrive(ServerLevel level, RadioJobState state, RadioJob job) {
		if (playerNear(level, job.x(), job.z(), ARRIVE_RADIUS)) {
			place(level, state, job);
		}
	}

	/** Puts the job into the world (public for gametests, whose mock players never count as near). */
	public static void place(ServerLevel level, RadioJobState state, RadioJob job) {
		switch (job.type()) {
			case STASH -> spot(level, job, 0).ifPresent(pos -> {
				placeChest(level, pos, job);
				state.setActive(Optional.of(job.placed(pos, List.of())));
			});
			case NEST -> spot(level, job, 0).ifPresent(pos -> {
				List<UUID> zombies = spawnWave(level, pos, NEST_BASE + NEST_PER_TIER * job.tier());
				if (zombies.isEmpty()) {
					return; // nothing could spawn (Peaceful, no room): not placed, so not cleared either; tried again
				}
				state.setActive(Optional.of(job.placed(pos, zombies)));
				tell(level, pos, Component.translatable("message.ashfall.radio_job.nest_inside").withStyle(ChatFormatting.RED));
			});
			case DISTRESS -> spot(level, job, Integer.MAX_VALUE).ifPresent(pos -> {
				level.setBlock(pos, RadioBlocks.DISTRESS_BEACON.defaultBlockState(), Block.UPDATE_ALL);
				state.setActive(Optional.of(job.placed(pos, List.of())));
			});
		}
	}

	private static boolean someoneBy(ServerLevel level, BlockPos beacon) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && Math.abs(player.getY() - beacon.getY()) <= 4
					&& player.distanceToSqr(beacon.getX() + 0.5, player.getY(), beacon.getZ() + 0.5) <= DISTRESS_RADIUS * DISTRESS_RADIUS) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Distress, one update with a player by the beacon: the first one brings a wave up the stairs, and after
	 * {@link #DISTRESS_HOLD_TICKS} the beacon turns into the reward chest and the caller's note, and from day 7 the
	 * caller themselves joins the nearest player ({@link SurvivorRescue}).
	 */
	public static void hold(ServerLevel level, RadioJobState state, RadioJob job) {
		hold(level, state, job, DirectorDay.today(level));
	}

	/** {@link #hold} on a given Director day (gametests, whose world is not on day 7). */
	public static void hold(ServerLevel level, RadioJobState state, RadioJob job, int day) {
		BlockPos beacon = job.spot().orElseThrow();
		RadioJob next = job;
		if (job.zombies().isEmpty()) {
			// The first minute by the beacon: what was close by heard it too, and comes up the stairs.
			BlockPos ground = buildingSpot(level, job, 0).filter(p -> p.distSqr(beacon) > 16).orElse(beacon.east(5));
			next = next.withZombies(spawnWave(level, ground, DISTRESS_WAVE + job.tier()));
		}
		int held = job.held() + UPDATE_TICKS;
		if (held < DISTRESS_HOLD_TICKS) {
			state.setActive(Optional.of(next.withHeld(held)));
			Component left = Component.translatable("message.ashfall.radio_job.holding", (DISTRESS_HOLD_TICKS - held) / 20);
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(Vec3.atCenterOf(beacon)) <= 24 * 24) {
					player.sendOverlayMessage(left);
				}
			}
			return;
		}
		placeChest(level, beacon, job);
		Block.popResource(level, beacon.above(), RelayLogs.book(NOTES.get(level.getRandom().nextInt(NOTES.size()))));
		SurvivorRescue.atBeacon(level, beacon, day);
		state.setActive(Optional.of(next.withHeld(held).rewarded(beacon)));
		tell(level, beacon, Component.translatable("message.ashfall.radio_job.distress_answered").withStyle(ChatFormatting.GREEN));
	}

	/**
	 * Where on storey {@code floor} (0 ground, beyond the top = the top) of the job's building something goes: the
	 * test's fixed spot if it has one, else an open spot inside, else the ground at the building's middle.
	 */
	static Optional<BlockPos> spot(ServerLevel level, RadioJob job, int floor) {
		return job.spot().isPresent() ? job.spot() : buildingSpot(level, job, floor);
	}

	private static Optional<BlockPos> buildingSpot(ServerLevel level, RadioJob job, int floor) {
		Building building = AshfallSalvage.buildings().get(job.building());
		if (building != null) {
			Optional<BlockPos> inside = BuildingFinder.floorSpot(level, building, floor);
			if (inside.isPresent()) {
				return inside;
			}
		}
		if (!level.hasChunk(job.x() >> 4, job.z() >> 4)) {
			return Optional.empty();
		}
		return Optional.of(new BlockPos(job.x(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, job.x(), job.z()), job.z()));
	}

	/** Spawns {@code count} zombies in and around {@code at}, kept from despawning. @return their ids */
	static List<UUID> spawnWave(ServerLevel level, BlockPos at, int count) {
		RandomSource random = level.getRandom();
		int day = DirectorDay.today(level);
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			BlockPos pos = at.offset(random.nextIntBetweenInclusive(-3, 3), 0, random.nextIntBetweenInclusive(-3, 3));
			if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
				pos = at;
			}
			Map.Entry<Identifier, ZombieVariant> variant = ZombieVariants.pick(ZombieVariants.all(), random.nextDouble(), day,
					ZombieVariant.Environment.LAND, null, SpawnContext.NONE);
			Zombie zombie = DirectorSpawns.spawn(level, pos, variant, DirectorZombie.Source.AMBIENT, DirectorZombie.NO_BUILDING);
			if (zombie != null) {
				zombie.addTag(ZOMBIE_TAG);
				zombie.setPersistenceRequired();
				ids.add(zombie.getUUID());
			}
		}
		return ids;
	}

	private static void placeChest(ServerLevel level, BlockPos pos, RadioJob job) {
		level.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		Identifier table = Identifier.fromNamespaceAndPath("ashfall", "radio_job/" + job.rewardTable());
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof RandomizableContainer container) {
			container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, table), level.getRandom().nextLong());
			be.setAttached(AshfallSalvage.LOOT_STATE, new LootState(table, Optional.empty(), Optional.empty(), -1, 0));
			be.setChanged();
		}
	}

	/** Opened (its loot rolled) or gone. Unloaded counts as not yet. */
	static boolean claimed(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return !state.is(Blocks.CHEST) || level.getBlockEntity(pos) instanceof RandomizableContainer c && c.getLootTable() == null;
	}

	/**
	 * Loses the job: its beacon or unopened chest goes now if the spot is loaded, else when it next loads
	 * ({@link RadioJobState#cleanups}); its zombies go now if loaded, else when they next load ({@link #STRAYS}).
	 */
	private static void expire(ServerLevel level, RadioJobState state, RadioJob job) {
		if (job.stage() != RadioJob.WAITING) {
			job.spot().ifPresent(pos -> {
				if (level.isLoaded(pos)) {
					clearPlaced(level, pos);
				} else {
					state.addCleanup(pos);
				}
			});
		}
		for (UUID id : job.zombies()) {
			Entity zombie = level.getEntity(id);
			if (zombie != null && zombie.entityTags().contains(ZOMBIE_TAG)) {
				zombie.discard();
			}
		}
		state.setActive(Optional.empty());
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(Component.translatable("message.ashfall.radio_job.lost").withStyle(ChatFormatting.GRAY));
		}
	}

	/** Removes a lost job's beacon, or its reward chest if nobody opened it. */
	static void clearPlaced(ServerLevel level, BlockPos pos) {
		BlockState block = level.getBlockState(pos);
		boolean beacon = block.is(RadioBlocks.DISTRESS_BEACON);
		boolean unopened = block.is(Blocks.CHEST) && !claimed(level, pos) && level.getBlockEntity(pos) instanceof RandomizableContainer c
				&& c.getLootTable() != null && c.getLootTable().identifier().getPath().startsWith("radio_job/");
		if (beacon || unopened) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	private static void complete(ServerLevel level, RadioJobState state, RadioJob job) {
		state.setActive(Optional.empty());
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(Component.translatable("message.ashfall.radio_job.done." + job.type().id()).withStyle(ChatFormatting.GREEN));
		}
		AshfallArms.LOGGER.info("Radio job done: {} at {}, {}", job.type().id(), job.x(), job.z());
	}

	/** Cancels the job under way (admin), clearing anything it placed. */
	public static void cancel(ServerLevel level) {
		RadioJobState state = RadioJobState.get(level.getServer());
		state.active().ifPresent(job -> expire(level, state, job));
	}

	private static void tell(ServerLevel level, BlockPos near, Component message) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(Vec3.atCenterOf(near)) <= (double) ARRIVE_RADIUS * 2 * ARRIVE_RADIUS * 2) {
				player.sendSystemMessage(message);
			}
		}
	}

	private static boolean playerNear(ServerLevel level, int x, int z, int radius) {
		for (ServerPlayer player : level.players()) {
			double dx = player.getX() - x, dz = player.getZ() - z;
			if (!player.isSpectator() && dx * dx + dz * dz <= (double) radius * radius) {
				return true;
			}
		}
		return false;
	}

	/** What the Shortwave Set says: the job under way, or today's calls with their [Accept] buttons. */
	public static void listen(ServerPlayer player) {
		RadioJobState state = RadioJobState.get(player.level().getServer());
		player.sendSystemMessage(Component.translatable("message.ashfall.radio_job.header").withStyle(ChatFormatting.GOLD));
		Optional<RadioJob> active = state.active();
		if (active.isPresent()) {
			player.sendSystemMessage(status(player, active.get()));
			return;
		}
		if (state.offers().isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.ashfall.radio_job.static").withStyle(ChatFormatting.GRAY));
			return;
		}
		for (int i = 0; i < state.offers().size(); i++) {
			String command = "/radiojob accept " + (i + 1);
			MutableComponent button = Component.translatable("message.ashfall.radio_job.accept_button").withStyle(s -> s
					.withColor(ChatFormatting.GREEN).withBold(true)
					.withClickEvent(new ClickEvent.RunCommand(command))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
			player.sendSystemMessage(Component.literal((i + 1) + ". ").append(describe(state.offers().get(i), player)).append(" ").append(button));
		}
	}

	/** "Supplies left in a pharmacy in River Arts, 420 m north-east." */
	public static MutableComponent describe(RadioJob job, ServerPlayer from) {
		int metres = Compass.roughDistance(from.getX(), from.getZ(), job.x(), job.z());
		return Component.translatable("radio_job.ashfall.offer." + job.type().id(),
				Component.translatable("radio_job.ashfall.place." + job.profile()),
				Component.translatable("zone.ashfall." + job.district()), metres,
				Component.translatable("compass.ashfall." + Compass.point(from.getX(), from.getZ(), job.x(), job.z())));
	}

	/** The job under way, with its time left: the set's readout and the Field Radio line. */
	public static Component status(ServerPlayer player, RadioJob job) {
		long seconds = Math.max(0, job.deadline() - player.level().getGameTime()) / 20;
		return Component.translatable("message.ashfall.radio_job.status", describe(job, player),
				String.format("%d:%02d", seconds / 60, seconds % 60)).withStyle(ChatFormatting.YELLOW);
	}

	/** The Field Radio's line for the job under way, if any. */
	public static Optional<Component> radioLine(ServerPlayer player) {
		return RadioJobState.get(player.level().getServer()).active().map(job -> status(player, job));
	}

	private static void sync(ServerLevel level, RadioJobState state) {
		Optional<RadioJobPayload.View> view = state.active().map(RadioJobPayload.View::of);
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			if (!view.equals(LAST.get(player.getUUID())) && ServerPlayNetworking.canSend(player, RadioJobPayload.TYPE)) {
				LAST.put(player.getUUID(), view);
				ServerPlayNetworking.send(player, new RadioJobPayload(view));
			}
		}
	}
}
