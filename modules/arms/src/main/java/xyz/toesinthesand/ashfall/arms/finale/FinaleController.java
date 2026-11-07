package xyz.toesinthesand.ashfall.arms.finale;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.arms.charger.ChargerBlockEntity;
import xyz.toesinthesand.ashfall.arms.finale.FinaleRun.Phase;
import xyz.toesinthesand.ashfall.arms.finale.FinaleRun.Status;
import xyz.toesinthesand.ashfall.director.horde.Horde;
import xyz.toesinthesand.ashfall.director.horde.HordeNights;
import xyz.toesinthesand.ashfall.director.horde.HordeSchedule;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.special.SpecialInfected;
import xyz.toesinthesand.ashfall.director.special.SpecialInfectedEntity;
import xyz.toesinthesand.ashfall.director.special.SpecialKind;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runs the Black Start once a second (endgame-design section 4): the Uplink's noise and light, the waves at the ridge
 * edge, the energy buffer and the Surge clock, the Relay Breakers and hull vents of the Feedback phase, the Heart
 * pylons and the Choir, Uplink damage, the boss bars, and what each phase end does.
 * <p>
 * The rules are {@link FinaleLogic}'s; this class only measures the world for it (who is near, what is still
 * standing) and acts on the world for it (blocks, spawns, effects). Nothing happens while the socket's chunk is
 * unloaded: the run simply waits, which is also what makes a server restart in the middle of the finale harmless.
 * <p>
 * Cost while a run is going: one player scan, two entity box queries around the socket and a few block reads per
 * second. No pathfinding of our own: waves are the horde scheduler's ({@link HordeNights#spawnWaveAt}), which spawn
 * out of sight at the ridge edge and walk in as a horde, so they never path in from across the map.
 */
public final class FinaleController {
	public static final int UPDATE_TICKS = 20;
	/** Entity tag on every mob the finale spawns: they are counted against the finale's mob cap. */
	public static final String FINALE_TAG = "ashfall_finale";
	public static final Identifier RELIT_ADVANCEMENT = Identifier.fromNamespaceAndPath("ashfall", "relight/asheville_relit");
	/** The criterion of {@link #RELIT_ADVANCEMENT} the finish awards (it is {@code minecraft:impossible} otherwise). */
	public static final String RELIT_CRITERION = "black_start";

	/** Gametest instances by socket position; they are ticked by their tests, never by the server loop. */
	private static final Map<Long, FinaleInstance> TESTS = new HashMap<>();
	private static final Map<Long, ServerBossEvent> RUN_BARS = new HashMap<>();
	private static final Map<Long, ServerBossEvent> CHOIR_BARS = new HashMap<>();
	/**
	 * Admin "unattended" mode for the world's run ({@code /ashfall finale unattended on|<players>}): an invisible party
	 * of this many stands at the Uplink and at every Relay Breaker (0: off). Why: the finale pauses after {@code abandon_seconds} with nobody
	 * near and a breaker only closes while someone holds it, so without this a headless server (a dedicated-server
	 * soak, a console-only admin) could never see waves, holds or the Heart. Kept in memory only, so a restart always
	 * returns to real presence, and gametest instances never see it.
	 */
	private static volatile int unattended;

	private FinaleController() {
	}

	/** Stand-in players of unattended mode (0: off). */
	public static int unattended() {
		return unattended;
	}

	public static void setUnattended(int players) {
		unattended = Math.max(0, players);
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % UPDATE_TICKS == 13) {
				FinaleInstance main = main(server);
				tick(server.overworld(), main, UPDATE_TICKS);
			}
			HullSite.tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RUN_BARS.values().forEach(ServerBossEvent::removeAllPlayers);
			CHOIR_BARS.values().forEach(ServerBossEvent::removeAllPlayers);
			RUN_BARS.clear();
			CHOIR_BARS.clear();
			unattended = 0;
			synchronized (TESTS) {
				TESTS.clear();
			}
		});
	}

	// ---------------------------------------------------------------------------------------------- instances

	/** The world's Black Start, over {@link FinaleState}, with the effects that light the real city. */
	public static FinaleInstance main(MinecraftServer server) {
		FinaleState state = FinaleState.get(server);
		return new FinaleInstance(state.run(), FinaleSettings::get, state::changed, FinaleEffects.WORLD);
	}

	/** Test hook: an instance that owns the socket at {@code socket} (its interactions go to it). */
	public static void registerTest(BlockPos socket, FinaleInstance instance) {
		synchronized (TESTS) {
			TESTS.put(socket.asLong(), instance);
		}
	}

	public static void unregisterTest(BlockPos socket) {
		synchronized (TESTS) {
			TESTS.remove(socket.asLong());
		}
		hideBars(socket.asLong());
	}

	/** The instance a socket at {@code pos} belongs to: a test's, or the world's. */
	public static FinaleInstance forSocket(ServerLevel level, BlockPos pos) {
		synchronized (TESTS) {
			FinaleInstance test = TESTS.get(pos.asLong());
			if (test != null) {
				return test;
			}
		}
		return main(level.getServer());
	}

	/** Whether unattended mode stands in for players at this socket (the world's run only). */
	private static boolean standIn(BlockPos socket) {
		return unattended > 0 && !isTest(socket);
	}

	public static boolean isTest(BlockPos socket) {
		synchronized (TESTS) {
			return TESTS.containsKey(socket.asLong());
		}
	}

	// ---------------------------------------------------------------------------------------------- the tick

	/**
	 * One controller step. Public so gametests drive their own instance with an explicit step.
	 *
	 * @param step ticks this step stands for (clocks, the Surge and breaker holds advance by it)
	 */
	public static void tick(ServerLevel level, FinaleInstance instance, long step) {
		FinaleRun run = instance.run();
		FinaleSettings s = instance.s();
		if (!run.active() || run.socket == FinaleRun.NONE) {
			hideBars(run.socket);
			return;
		}
		BlockPos socket = BlockPos.of(run.socket);
		if (!level.hasChunkAt(socket)) {
			hideBars(run.socket);
			return;
		}
		List<ServerPlayer> near = playersNear(level, socket, s.presenceRadius());
		near.forEach(p -> run.participants.add(p.getUUID()));
		int present = near.size() + (standIn(socket) ? unattended : 0);
		int party = Math.max(1, present);
		try {
			if (run.status == Status.PAUSED) {
				return;
			}
			if (FinaleLogic.presence(run, s, present, step)) {
				announcePause(level, instance);
				return;
			}
			emitStimulus(level, run, s, socket, step);
			if (batterUplink(level, instance, socket, step)) {
				return;
			}
			switch (run.phase) {
				case SURGE -> {
					if (FinaleLogic.tickSurge(run, s, step)) {
						endPhase(level, instance);
						return;
					}
				}
				case FEEDBACK -> {
					if (tickFeedback(level, instance, socket, party, step)) {
						return;
					}
				}
				case HEART -> {
					if (tickHeart(level, instance, socket)) {
						return;
					}
				}
			}
			run.clocks.wave += step;
			if (FinaleLogic.due(run.clocks.wave, FinaleLogic.waveInterval(s, run.phase))) {
				run.clocks.wave = 0;
				spawnWave(level, run, s, socket, party);
			}
		} finally {
			showSocket(level, socket, run);
			updateBars(level, instance, socket);
			instance.save().run();
		}
	}

	/** The Uplink is the loudest and brightest thing in the game: a SOUND and a LIGHT stimulus, with the relay hum. */
	private static void emitStimulus(ServerLevel level, FinaleRun run, FinaleSettings s, BlockPos socket, long step) {
		run.clocks.stimulus += step;
		if (run.clocks.stimulus < s.stimulus().intervalSeconds() * 20L) {
			return;
		}
		run.clocks.stimulus = 0;
		Vec3 c = Vec3.atCenterOf(socket);
		Stimuli.emit(level, c, StimulusType.SOUND, s.stimulus().soundRadius(), null);
		Stimuli.emit(level, c, StimulusType.LIGHT, s.stimulus().lightRadius(), null);
		level.playSound(null, socket, ChargerBlockEntity.RELAY_HUM, SoundSource.BLOCKS, 3.0f, 0.5f);
	}

	/** Mobs at the socket batter the Uplink. @return true if this knocked it out (the run is now PAUSED) */
	private static boolean batterUplink(ServerLevel level, FinaleInstance instance, BlockPos socket, long step) {
		FinaleRun run = instance.run();
		FinaleSettings.Uplink u = instance.s().uplink();
		double r = u.attackRadius();
		AABB box = new AABB(socket).inflate(r, 2.0, r);
		Vec3 c = Vec3.atCenterOf(socket);
		int attackers = level.getEntitiesOfClass(Zombie.class, box, z -> z.isAlive()
				&& horizontalSqr(z.position(), c) <= r * r).size();
		int damage = (int) (attackers * (long) u.damagePerZombieSecond() * step / 20);
		if (damage > 0 && FinaleLogic.damage(run, instance.s(), damage)) {
			announcePause(level, instance);
			return true;
		}
		return false;
	}

	private static double horizontalSqr(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	/** Feedback: raise the breakers, count holds, arc the Uplink, vent specials. @return true if the phase ended */
	private static boolean tickFeedback(ServerLevel level, FinaleInstance instance, BlockPos socket, int party, long step) {
		FinaleRun run = instance.run();
		FinaleSettings s = instance.s();
		placeBreakers(level, run, s, socket);
		for (FinaleRun.Breaker breaker : run.breakers) {
			BlockPos pos = BlockPos.of(breaker.pos);
			boolean held = !breaker.closed && (standIn(socket) || playerAt(level, pos, s.breakers().radius()));
			if (FinaleLogic.tickBreaker(breaker, s, held, party, step)) {
				if (level.getBlockState(pos).is(FinaleBlocks.RELAY_BREAKER)) {
					level.setBlock(pos, level.getBlockState(pos).setValue(RelayBreakerBlock.TRIPPED, false), Block.UPDATE_ALL);
				}
				level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0f, 1.2f);
				broadcastNear(level, socket, s.presenceRadius() * 2, Component.translatable("message.ashfall.finale.breaker_closed",
						run.breakers.stream().filter(b -> b.closed).count(), run.breakers.size()).withStyle(ChatFormatting.GOLD));
			} else if (held && level.getGameTime() % 40 < UPDATE_TICKS) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
			}
		}
		if (FinaleLogic.breakersClosed(run)) {
			endPhase(level, instance);
			return true;
		}
		run.clocks.arc += step;
		if (FinaleLogic.due(run.clocks.arc, s.uplink().arcIntervalSeconds())) {
			run.clocks.arc = 0;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, socket.getX() + 0.5, socket.getY() + 1.0, socket.getZ() + 0.5, 30, 0.6, 0.8, 0.6, 0.2);
			level.playSound(null, socket, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 1.0f, 1.6f);
			if (FinaleLogic.damage(run, s, s.uplink().arcDamage())) {
				announcePause(level, instance);
				return true;
			}
		}
		run.clocks.vent += step;
		if (FinaleLogic.due(run.clocks.vent, s.vents().intervalSeconds())) {
			run.clocks.vent = 0;
			vent(level, s, socket, party);
		}
		return false;
	}

	/** The Heart: raise the pylons and the Choir, count broken pylons, keep the Choir near. @return true if it ended */
	private static boolean tickHeart(ServerLevel level, FinaleInstance instance, BlockPos socket) {
		FinaleRun run = instance.run();
		FinaleSettings s = instance.s();
		placePylons(level, run, s, socket);
		spawnChoir(level, run, s, socket);
		if (FinaleLogic.pylons(run, run.pylons.size(), standingPylons(level, run))) {
			endPhase(level, instance);
			return true;
		}
		LivingEntity choir = choir(level, run);
		if (choir instanceof ChoirEntity c && c.getTarget() == null
				&& c.distanceToSqr(Vec3.atCenterOf(socket)) > (double) s.heart().leashRadius() * s.heart().leashRadius()) {
			c.getNavigation().moveTo(socket.getX() + 0.5, socket.getY(), socket.getZ() + 0.5, 1.0);
		}
		return false;
	}

	// ---------------------------------------------------------------------------------------------- phases

	/** Ends the run's phase: the band lights, then the next phase is set up (or the ending choice opens). */
	public static void endPhase(ServerLevel level, FinaleInstance instance) {
		FinaleRun run = instance.run();
		Phase ended = run.phase;
		int bonus = FinaleLogic.endPhase(run, instance.s());
		AshfallArms.LOGGER.info("Finale: {} phase ended, band {} lit, Grid bonus {}", ended.getSerializedName(), run.bandsLit, bonus);
		instance.effects().phaseEnded(level, instance, run.bandsLit - 1, bonus);
		if (run.status == Status.COMPLETE) {
			shatterChoir(level, run);
			hideBars(run.socket);
			instance.effects().completed(level, instance, onlineParticipants(level.getServer(), run));
		} else {
			enterPhase(level, instance);
		}
		instance.save().run();
	}

	/** Sets up the run's current phase in the world (breakers, pylons and the Choir) and announces it. */
	public static void enterPhase(ServerLevel level, FinaleInstance instance) {
		FinaleRun run = instance.run();
		FinaleSettings s = instance.s();
		if (run.socket == FinaleRun.NONE) {
			return;
		}
		BlockPos socket = BlockPos.of(run.socket);
		switch (run.phase) {
			case SURGE -> {
			}
			case FEEDBACK -> placeBreakers(level, run, s, socket);
			case HEART -> {
				placePylons(level, run, s, socket);
				spawnChoir(level, run, s, socket);
			}
		}
		if (!isTest(socket)) {
			title(level.getServer(), Component.translatable("title.ashfall.finale." + run.phase.getSerializedName()),
					Component.translatable("subtitle.ashfall.finale." + run.phase.getSerializedName()));
		}
	}

	/** Raises the Relay Breakers on the ground at their offsets, once per run. */
	static void placeBreakers(ServerLevel level, FinaleRun run, FinaleSettings s, BlockPos socket) {
		if (!run.breakers.isEmpty()) {
			return;
		}
		for (Vec3i offset : s.breakers().offsets()) {
			BlockPos at = ground(level, socket.getX() + offset.getX(), socket.getZ() + offset.getZ(), socket.getY() + offset.getY());
			level.setBlock(at, FinaleBlocks.RELAY_BREAKER.defaultBlockState().setValue(RelayBreakerBlock.TRIPPED, true), Block.UPDATE_ALL);
			FinaleRun.Breaker breaker = new FinaleRun.Breaker();
			breaker.pos = at.asLong();
			run.breakers.add(breaker);
			level.playSound(null, at, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0f, 0.6f);
		}
	}

	/** Raises the Heart pylons at their offsets, once per run; a spot holding something solid is left alone. */
	static void placePylons(ServerLevel level, FinaleRun run, FinaleSettings s, BlockPos socket) {
		if (!run.pylons.isEmpty()) {
			return;
		}
		for (Vec3i offset : s.heart().pylons()) {
			BlockPos at = socket.offset(offset);
			BlockState current = level.getBlockState(at);
			if (current.canBeReplaced() || current.is(FinaleBlocks.HEART_PYLON) || current.is(Blocks.COBWEB)) {
				level.setBlock(at, FinaleBlocks.HEART_PYLON.defaultBlockState(), Block.UPDATE_ALL);
				run.pylons.add(at.asLong());
			} else {
				AshfallArms.LOGGER.warn("Finale: no room for a Heart pylon at {} ({})", at.toShortString(), current);
			}
		}
	}

	/** Pylons still standing (a pylon in an unloaded chunk counts as standing). */
	public static int standingPylons(ServerLevel level, FinaleRun run) {
		int standing = 0;
		for (long packed : run.pylons) {
			BlockPos pos = BlockPos.of(packed);
			if (!level.hasChunkAt(pos) || level.getBlockState(pos).is(FinaleBlocks.HEART_PYLON)) {
				standing++;
			}
		}
		return standing;
	}

	/** Raises the Choir once per run (a Choir the party killed stays dead). */
	static void spawnChoir(ServerLevel level, FinaleRun run, FinaleSettings s, BlockPos socket) {
		if (!s.heart().choir() || run.choir.isPresent()) {
			return;
		}
		ChoirEntity choir = FinaleBlocks.CHOIR.create(level, EntitySpawnReason.EVENT);
		if (choir == null) {
			return;
		}
		BlockPos at = socket.offset(s.heart().choirOffset());
		choir.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
		choir.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		choir.applyAttributes(s.heart().attributes());
		choir.addTag(ChoirEntity.TAG);
		choir.addTag(FINALE_TAG);
		choir.setCustomName(Component.translatable("entity.ashfall.choir"));
		if (level.addFreshEntity(choir)) {
			run.choir = Optional.of(choir.getUUID());
			level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3.0f, 0.4f);
			AshfallArms.LOGGER.info("Finale: the Choir rises at {}", at.toShortString());
		}
	}

	/** The Choir entity if it is alive and loaded. */
	public static LivingEntity choir(ServerLevel level, FinaleRun run) {
		return run.choir.map(level::getEntity).filter(e -> e instanceof LivingEntity l && l.isAlive())
				.map(LivingEntity.class::cast).orElse(null);
	}

	/** The Heart is dark: the Choir comes apart with it. */
	private static void shatterChoir(ServerLevel level, FinaleRun run) {
		LivingEntity choir = choir(level, run);
		if (choir != null) {
			level.sendParticles(ParticleTypes.END_ROD, choir.getX(), choir.getY() + 1.5, choir.getZ(), 60, 0.8, 1.2, 0.8, 0.1);
			level.playSound(null, choir.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 3.0f, 0.5f);
			choir.discard();
		}
	}

	/**
	 * The ground at column (x, z) near {@code aroundY}: the nearest free spot standing on something sturdy, searched
	 * alternately down and up, then the surface heightmap. The ridge is uneven, so a fixed height would bury a breaker.
	 */
	static BlockPos ground(ServerLevel level, int x, int z, int aroundY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, aroundY, z);
		for (int i = 0; i <= 24; i++) {
			int dy = (i & 1) == 1 ? -((i + 1) / 2) : i / 2;
			pos.setY(aroundY + dy);
			BlockPos below = pos.below();
			if (level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.above()).canBeReplaced()
					&& level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				return pos.immutable();
			}
		}
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	// ---------------------------------------------------------------------------------------------- spawns

	/** Finale mobs alive near the socket (the mob cap's count). */
	public static int finaleMobs(ServerLevel level, BlockPos socket, int radius) {
		AABB box = new AABB(socket).inflate(radius);
		return level.getEntitiesOfClass(Zombie.class, box, z -> z.isAlive() && z.entityTags().contains(FINALE_TAG)).size();
	}

	/**
	 * One wave at the ridge edge through the horde scheduler: the ring 40-64 blocks out, out of sight and out of
	 * claims, with its specials roll and its caps, sent at the Uplink as one horde. The table is tonight's horde tier,
	 * which at Grid 80% is at least the grid tier.
	 */
	static int spawnWave(ServerLevel level, FinaleRun run, FinaleSettings s, BlockPos socket, int party) {
		int alive = finaleMobs(level, socket, s.waves().capRadius());
		int size = FinaleLogic.room(s, alive, FinaleLogic.waveSize(s, party));
		if (size <= 0) {
			return 0;
		}
		HordeSchedule schedule = HordeSchedule.get();
		int day = HordeSchedule.day(level.getOverworldClockTime());
		HordeSchedule.Tier row = schedule.tierRow(HordeNights.tier(level.getServer()));
		HordeSchedule.Wave template = row.waves().isEmpty() ? null : row.waves().getLast();
		Map<Identifier, Integer> spawns = template == null || template.spawns().isEmpty()
				? Map.of(Identifier.fromNamespaceAndPath("ashfall", "walker"), 1) : template.spawns();
		HordeSchedule.Wave wave = new HordeSchedule.Wave(size, 0, spawns, template == null ? Optional.empty() : template.lootTable());
		int hordeCap = FinaleLogic.hordeCap(s, schedule.spawn().maxAlivePerPlayer(), party);
		Horde horde = HordeNights.spawnWaveAt(level, Vec3.atCenterOf(socket), schedule, wave, day, hordeCap);
		if (horde == null) {
			return 0;
		}
		int n = 0;
		for (UUID id : horde.members()) {
			Entity e = level.getEntity(id);
			if (e != null) {
				e.addTag(FINALE_TAG);
				n++;
			}
		}
		AshfallArms.LOGGER.info("Finale: wave of {} at the ridge edge ({} finale mobs alive before)", n, alive);
		return n;
	}

	/** A burst of special infected out of the hull vents, at the Uplink. */
	static int vent(ServerLevel level, FinaleSettings s, BlockPos socket, int party) {
		List<Vec3i> vents = s.vents().offsets();
		List<SpecialKind> kinds = new ArrayList<>();
		for (String id : s.vents().kinds()) {
			SpecialKind kind = SpecialKind.byId(id);
			if (kind != null) {
				kinds.add(kind);
			}
		}
		if (vents.isEmpty() || kinds.isEmpty()) {
			return 0;
		}
		int alive = finaleMobs(level, socket, s.waves().capRadius());
		int count = FinaleLogic.room(s, alive, FinaleLogic.ventCount(s, party));
		RandomSource random = level.getRandom();
		int spawned = 0;
		for (int i = 0; i < count; i++) {
			BlockPos vent = socket.offset(vents.get(random.nextInt(vents.size())));
			SpecialKind kind = kinds.get(random.nextInt(kinds.size()));
			if (!SpecialInfected.fits(level, vent, kind)) {
				kind = SpecialKind.CRAWLER; // the smallest always fits where a brute does not
				if (!SpecialInfected.fits(level, vent, kind)) {
					continue;
				}
			}
			SpecialInfectedEntity special = SpecialInfected.spawn(level, vent, kind, DirectorZombie.Source.SIEGE);
			if (special != null) {
				special.addTag(FINALE_TAG);
				special.getNavigation().moveTo(socket.getX() + 0.5, socket.getY(), socket.getZ() + 0.5, 1.0);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, vent.getX() + 0.5, vent.getY() + 1.0, vent.getZ() + 0.5, 10, 0.3, 0.5, 0.3, 0.02);
				spawned++;
			}
		}
		return spawned;
	}

	// ---------------------------------------------------------------------------------------------- players

	/** Living, non-spectator players within {@code radius} of {@code pos}. */
	public static List<ServerPlayer> playersNear(ServerLevel level, BlockPos pos, double radius) {
		Vec3 c = Vec3.atCenterOf(pos);
		double r2 = radius * radius;
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(c) <= r2) {
				out.add(p);
			}
		}
		return out;
	}

	/** Whether a living player stands at the breaker: within {@code radius} of its base. */
	static boolean playerAt(ServerLevel level, BlockPos pos, double radius) {
		Vec3 base = Vec3.atBottomCenterOf(pos);
		double r2 = (radius + 0.5) * (radius + 0.5);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.isAlive() && p.position().distanceToSqr(base) <= r2) {
				return true;
			}
		}
		return false;
	}

	/** The run's participants who are online now. */
	public static List<ServerPlayer> onlineParticipants(MinecraftServer server, FinaleRun run) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : run.participants) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				out.add(p);
			}
		}
		return out;
	}

	// ---------------------------------------------------------------------------------------------- feedback to players

	private static void announcePause(ServerLevel level, FinaleInstance instance) {
		FinaleRun run = instance.run();
		AshfallArms.LOGGER.info("Finale: paused ({}), {} phase", run.pause.getSerializedName(), run.phase.getSerializedName());
		BlockPos socket = BlockPos.of(run.socket);
		level.playSound(null, socket, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 3.0f, 0.5f);
		broadcastNear(level, socket, instance.s().presenceRadius() * 3, Component.translatable(
				"message.ashfall.finale.paused." + run.pause.getSerializedName(), FinaleLogic.resumeHp(instance.s()))
				.withStyle(ChatFormatting.RED));
	}

	/** The socket's look follows the run: dark, a standby glow while paused, full light while it draws. */
	static void showSocket(ServerLevel level, BlockPos socket, FinaleRun run) {
		BlockState state = level.getBlockState(socket);
		if (!state.is(FinaleBlocks.UPLINK_SOCKET)) {
			return;
		}
		int power = switch (run.status) {
			// an Uplink left in standby by the replay reset glows like a paused one, so the socket reads "not empty"
			case IDLE -> run.standby == socket.asLong() ? 1 : 0;
			case PAUSED -> 1;
			case RUNNING, COMPLETE -> 2;
		};
		if (state.getValue(UplinkSocketBlock.POWER) != power) {
			level.setBlock(socket, state.setValue(UplinkSocketBlock.POWER, power), Block.UPDATE_ALL);
		}
	}

	private static void updateBars(ServerLevel level, FinaleInstance instance, BlockPos socket) {
		FinaleRun run = instance.run();
		FinaleSettings s = instance.s();
		if (!run.active()) {
			hideBars(run.socket);
			return;
		}
		ServerBossEvent bar = RUN_BARS.computeIfAbsent(run.socket, k -> new ServerBossEvent(
				UUID.nameUUIDFromBytes(("ashfall:finale/" + k).getBytes(StandardCharsets.UTF_8)), Component.empty(),
				BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10));
		int hp = run.uplinkHp, max = s.uplink().hp();
		float progress;
		Component name;
		BossEvent.BossBarColor color;
		if (run.status == Status.PAUSED) {
			name = Component.translatable("bossbar.ashfall.finale.paused." + run.pause.getSerializedName(), hp, max,
					FinaleLogic.resumeHp(s), run.bufferTicks / 20);
			color = BossEvent.BossBarColor.WHITE;
			progress = max <= 0 ? 0f : Math.clamp(hp / (float) max, 0f, 1f);
		} else {
			switch (run.phase) {
				case SURGE -> {
					progress = Math.clamp(run.phaseTicks / (float) Math.max(1, s.surgeTicks()), 0f, 1f);
					name = Component.translatable(FinaleLogic.starved(run) ? "bossbar.ashfall.finale.surge_starved" : "bossbar.ashfall.finale.surge",
							Math.round(progress * 100), run.bufferTicks / 20, hp, max);
					color = FinaleLogic.starved(run) ? BossEvent.BossBarColor.WHITE : BossEvent.BossBarColor.YELLOW;
				}
				case FEEDBACK -> {
					double done = 0;
					for (FinaleRun.Breaker b : run.breakers) {
						done += b.closed ? 1.0 : b.holdTicks / (double) Math.max(1, s.holdTicks());
					}
					progress = run.breakers.isEmpty() ? 0f : (float) Math.clamp(done / run.breakers.size(), 0.0, 1.0);
					long closed = run.breakers.stream().filter(b -> b.closed).count();
					name = Component.translatable("bossbar.ashfall.finale.feedback", closed, run.breakers.size(), hp, max);
					color = BossEvent.BossBarColor.RED;
				}
				default -> {
					int total = Math.max(1, run.pylons.size());
					progress = Math.clamp(run.pylonsBroken / (float) total, 0f, 1f);
					name = Component.translatable("bossbar.ashfall.finale.heart", run.pylonsBroken, run.pylons.size(), hp, max);
					color = BossEvent.BossBarColor.PURPLE;
				}
			}
		}
		bar.setName(name);
		bar.setColor(color);
		bar.setProgress(progress);
		showNear(level, bar, socket, s.presenceRadius() * 1.5);
		LivingEntity choir = run.phase == Phase.HEART ? choir(level, run) : null;
		if (choir != null) {
			ServerBossEvent cbar = CHOIR_BARS.computeIfAbsent(run.socket, k -> new ServerBossEvent(
					UUID.nameUUIDFromBytes(("ashfall:finale_choir/" + k).getBytes(StandardCharsets.UTF_8)),
					Component.translatable("entity.ashfall.choir"), BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.NOTCHED_20));
			cbar.setProgress(Math.clamp(choir.getHealth() / Math.max(1f, choir.getMaxHealth()), 0f, 1f));
			showNear(level, cbar, choir.blockPosition(), s.heart().barRadius());
		} else {
			ServerBossEvent cbar = CHOIR_BARS.get(run.socket);
			if (cbar != null) {
				cbar.removeAllPlayers();
			}
		}
	}

	static void showNear(ServerLevel level, ServerBossEvent bar, BlockPos pos, double radius) {
		Vec3 c = Vec3.atCenterOf(pos);
		Set<ServerPlayer> wanted = new HashSet<>();
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(c) <= radius * radius) {
				wanted.add(p);
			}
		}
		for (ServerPlayer p : List.copyOf(bar.getPlayers())) {
			if (!wanted.remove(p)) {
				bar.removePlayer(p);
			}
		}
		wanted.forEach(bar::addPlayer);
	}

	static void hideBars(long socket) {
		ServerBossEvent bar = RUN_BARS.get(socket);
		if (bar != null) {
			bar.removeAllPlayers();
		}
		ServerBossEvent cbar = CHOIR_BARS.get(socket);
		if (cbar != null) {
			cbar.removeAllPlayers();
		}
	}

	/** The run bar of a socket (gametests). */
	public static ServerBossEvent barForTesting(BlockPos socket) {
		return RUN_BARS.get(socket.asLong());
	}

	/** A chat line to every player on the server: the finale is the whole server's. */
	public static void broadcast(MinecraftServer server, Component message) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.sendSystemMessage(message);
		}
	}

	/** A chat line to the players within {@code radius} of {@code pos} (breaker and pause news is local). */
	static void broadcastNear(ServerLevel level, BlockPos pos, double radius, Component message) {
		Vec3 c = Vec3.atCenterOf(pos);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(c) <= radius * radius) {
				p.sendSystemMessage(message);
			}
		}
	}

	/** A title for everyone on the server (phase starts and phase ends). */
	public static void title(MinecraftServer server, Component title, Component subtitle) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
			p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
			p.connection.send(new ClientboundSetTitleTextPacket(title));
		}
	}

	/**
	 * Admin abort: takes the finale's blocks and the Choir out of the world and resets the run to IDLE (the Uplink is
	 * out of the socket). Broken pylons and closed breakers are simply removed; nothing a player built is touched.
	 */
	public static void abort(ServerLevel level, FinaleInstance instance) {
		FinaleRun run = instance.run();
		long socket = clearWorld(level, run);
		run.reset();
		if (socket != FinaleRun.NONE) {
			showSocket(level, BlockPos.of(socket), run);
		}
		instance.effects().bonusChanged(level, 0);
		instance.save().run();
	}

	/**
	 * The replay reset ({@code /ashfall story reset}): like {@link #abort}, the finale's blocks and the Choir leave the
	 * world and the run goes back to IDLE with the Grid bonus at 0, but a seated Uplink stays in its socket, powered
	 * down ({@link FinaleLogic#rearm}). Using that socket with an empty hand starts the replay
	 * ({@link FinaleActions#restart}), so replaying for the other ending does not mean crafting a second Uplink from
	 * three more boss crystals that the world may no longer have.
	 *
	 * @return true if an Uplink waits in its socket
	 */
	public static boolean rearm(ServerLevel level, FinaleInstance instance) {
		FinaleRun run = instance.run();
		long socket = clearWorld(level, run);
		boolean kept = FinaleLogic.rearm(run);
		long shown = kept ? run.standby : socket;
		if (shown != FinaleRun.NONE && level.hasChunkAt(BlockPos.of(shown))) {
			showSocket(level, BlockPos.of(shown), run);
		}
		instance.effects().bonusChanged(level, 0);
		instance.save().run();
		return kept;
	}

	/** Removes the run's breakers, pylons and Choir and hides its bars. @return the socket the run was seated in */
	private static long clearWorld(ServerLevel level, FinaleRun run) {
		for (FinaleRun.Breaker b : run.breakers) {
			BlockPos pos = BlockPos.of(b.pos);
			if (level.getBlockState(pos).is(FinaleBlocks.RELAY_BREAKER)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		for (long packed : run.pylons) {
			BlockPos pos = BlockPos.of(packed);
			if (level.getBlockState(pos).is(FinaleBlocks.HEART_PYLON)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		LivingEntity choir = choir(level, run);
		if (choir != null) {
			choir.discard();
		}
		hideBars(run.socket);
		return run.socket;
	}

	/**
	 * Admin phase jump ({@code /ashfall finale phase}): earlier phases count as won, their Grid bonus applies, and the
	 * bands they would have lit are relit now ({@link FinaleInstance.Effects#bandRelit}), so a jump leaves the city
	 * looking the way a played run would. Before phase 7 a jump only set the bonus, which left skipped bands dark in a
	 * world whose districts had not been lit by a reclaim. Jumping backwards relights nothing and darkens nothing.
	 *
	 * @return the Grid bonus after the jump
	 */
	public static int jumpTo(ServerLevel level, FinaleInstance instance, Phase phase) {
		FinaleRun run = instance.run();
		int litBefore = run.bandsLit;
		int bonus = FinaleLogic.jump(run, instance.s(), phase);
		instance.effects().bonusChanged(level, bonus);
		for (int band = litBefore; band < run.bandsLit; band++) {
			instance.effects().bandRelit(level, instance, band);
		}
		enterPhase(level, instance);
		instance.save().run();
		return bonus;
	}
}
