package xyz.toesinthesand.ashfall.salvage.lock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.BuildingFinder;
import xyz.toesinthesand.ashfall.salvage.building.Footprint;
import xyz.toesinthesand.ashfall.salvage.building.LostCitiesBuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.Unit;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Placing, unlocking and forcing Strongboxes. The Director calls {@link #tryPlace} once per building, when its first
 * population spawns, and marks one of the new zombies as the key carrier if a box went in. Placement is once only: the
 * box never refills and a repopulated building gets no second one.
 */
public final class Strongboxes {
	/** Chance an eligible building gets a Strongbox. */
	public static final float CHANCE = 0.4f;
	/** Profiles at or above this tier always qualify; the ones in {@link #ALWAYS} qualify below it too. */
	public static final int MIN_TIER = 3;
	/** Places that keep a safe whatever their tier (bank, pharmacy and police are tier 3+ anyway; kept for clarity). */
	public static final Set<String> ALWAYS = Set.of("bank", "police", "office", "hotel", "pharmacy");

	private static Float chanceOverride;

	private Strongboxes() {
	}

	/** {@code ashfall:strongbox/tier_<1-5>}: rolled into the box on first open. */
	public static ResourceKey<LootTable> table(int tier) {
		return ResourceKey.create(Registries.LOOT_TABLE,
				Identifier.fromNamespaceAndPath("ashfall", "strongbox/tier_" + Mth.clamp(tier, 1, 5)));
	}

	/** Whether a building of this profile may get a Strongbox (before the {@link #CHANCE} roll). */
	public static boolean eligible(Identifier profileId) {
		if (profileId == null) {
			return false;
		}
		if (ALWAYS.contains(profileId.getPath())) {
			return true;
		}
		BuildingProfile profile = BuildingProfiles.get(profileId);
		return profile != null && profile.tier() >= MIN_TIER;
	}

	/**
	 * Rolls for and places a locked Strongbox on a random storey of an eligible building, at that storey's
	 * {@link BuildingFinder#floorSpot}. Storeys Lost Cities ruined are skipped, as for populations.
	 *
	 * @param tier the district tier, which picks the loot table
	 * @return where the box went, or empty (not eligible, lost the roll, or no free floor)
	 */
	public static Optional<BlockPos> tryPlace(ServerLevel level, Building building, Identifier profileId, int tier, RandomSource random) {
		Float test = chanceOverride;
		if (!eligible(profileId) || random.nextFloat() >= (test != null ? test : CHANCE)) {
			return Optional.empty();
		}
		int ruinY = LostCitiesBuildingSource.ruinY(building);
		List<Integer> storeys = new ArrayList<>();
		for (Unit unit : building.units()) {
			if (unit.floor() >= 0 && unit.floorY() < ruinY && !storeys.contains(unit.floor())) {
				storeys.add(unit.floor());
			}
		}
		int floor = storeys.isEmpty() ? 0 : storeys.get(random.nextInt(storeys.size()));
		Optional<BlockPos> spot = BuildingFinder.floorSpot(level, building, floor);
		if (spot.isEmpty() && floor != 0) {
			spot = BuildingFinder.floorSpot(level, building, 0);
		}
		spot.ifPresent(pos -> place(level, pos, Direction.Plane.HORIZONTAL.getRandomDirection(random), building.id(), tier, random.nextLong()));
		return spot;
	}

	/** Puts a locked Strongbox for {@code building} at {@code pos} with the tier's loot table. */
	public static void place(ServerLevel level, BlockPos pos, Direction facing, long building, int tier, long seed) {
		level.setBlock(pos, Locks.STRONGBOX.defaultBlockState().setValue(StrongboxBlock.FACING, facing)
				.setValue(StrongboxBlock.LOCKED, true), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof StrongboxBlockEntity box) {
			box.setBuilding(building);
			box.setLootTable(table(tier), seed);
		}
	}

	/** "Bank at 120, -340": the profile's name and the middle of the building, for the key's tooltip. */
	public static String keyName(Building building, Identifier profileId) {
		String path = profileId != null ? profileId.getPath() : "building";
		StringBuilder name = new StringBuilder();
		for (String word : path.split("_")) {
			if (!word.isEmpty()) {
				name.append(name.isEmpty() ? "" : " ").append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
			}
		}
		Footprint f = building.footprint();
		return name + " at " + (f.minX() + f.maxX()) / 2 + ", " + (f.minZ() + f.maxZ()) / 2;
	}

	/** A Building Key for this building. */
	public static ItemStack key(KeyData data) {
		ItemStack stack = new ItemStack(Locks.BUILDING_KEY);
		stack.set(Locks.KEY_DATA, data);
		return stack;
	}

	public static boolean isLocked(BlockState state) {
		return state.is(Locks.STRONGBOX) && state.getValue(StrongboxBlock.LOCKED);
	}

	/**
	 * Tries {@code key} on the box at {@code pos}. The right key unlocks it with only a soft click (no noise for
	 * zombies), is used up, and opens the box for the player; any other key just doesn't fit.
	 *
	 * @return true if the box opened
	 */
	public static boolean unlockWithKey(ServerLevel level, BlockPos pos, Player player, ItemStack key) {
		BlockState state = level.getBlockState(pos);
		if (!isLocked(state) || !(level.getBlockEntity(pos) instanceof StrongboxBlockEntity box)) {
			return false;
		}
		KeyData data = key.get(Locks.KEY_DATA);
		if (data == null || box.building() == 0L || data.building() != box.building()) {
			level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.6f, 1.3f);
			player.sendOverlayMessage(Component.translatable("message.ashfall.strongbox.wrong_key"));
			return false;
		}
		unlock(level, pos, state);
		level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.4f, 1.4f);
		key.shrink(1);
		player.sendOverlayMessage(Component.translatable("message.ashfall.strongbox.unlocked"));
		player.openMenu(box);
		return true;
	}

	/**
	 * Breaks the lock for good: the Crowbar's end. Noise is the caller's business (arms reports it), so this only does
	 * the block and the sound of the door giving way.
	 *
	 * @return false if there was no locked box at {@code pos}
	 */
	public static boolean force(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!isLocked(state)) {
			return false;
		}
		unlock(level, pos, state);
		level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.BLOCKS, 1.0f, 0.8f);
		return true;
	}

	private static void unlock(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state.setValue(StrongboxBlock.LOCKED, false), Block.UPDATE_ALL);
	}

	/** Test hook: a fixed placement chance; null restores {@link #CHANCE}. */
	public static void setChanceForTesting(Float chance) {
		chanceOverride = chance;
	}
}
