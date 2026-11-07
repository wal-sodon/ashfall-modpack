package xyz.toesinthesand.ashfall.director.test;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.fortify.BarricadeBlock;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;
import xyz.toesinthesand.ashfall.director.fortify.FortifyWear;
import xyz.toesinthesand.ashfall.director.fortify.Fortified;
import xyz.toesinthesand.ashfall.director.fortify.WearCarry;
import xyz.toesinthesand.ashfall.director.fortify.WearSettings;
import xyz.toesinthesand.ashfall.director.haven.ChunkArea;
import xyz.toesinthesand.ashfall.director.haven.HavenState;
import xyz.toesinthesand.ashfall.director.haven.Havens;
import xyz.toesinthesand.ashfall.director.haven.SafeZone;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fortified blocks wear down (10.2.0, {@link FortifyWear}). ZB&B is not on the gametest classpath, so each test calls
 * the gate its mixin calls ({@link Fortified#zombieBreak}) with real zombies standing next to the block. Numbers come
 * from the shipped {@code wear.json}.
 */
public class FortifyWearGameTest {
	private static final int PADDING = 16;
	private static final String TEMP_ZONE = "gametest_wear_zone";

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 6; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static Zombie zombieAt(GameTestHelper helper, int x, int z) {
		return helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(x + 0.5, 1, z + 0.5));
	}

	@GameTest(padding = PADDING)
	public void zombieHitAddsWear(GameTestHelper helper) {
		floor(helper);
		BlockPos wall = new BlockPos(2, 1, 2);
		helper.setBlock(wall, DirectorBlocks.REINFORCED_PLANKS);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(wall);
		Zombie zombie = zombieAt(helper, 2, 3);
		BlockState state = level.getBlockState(abs);
		helper.assertFalse(Fortified.zombieBreak(level, abs, state, zombie), "a zombie may break reinforced planks outright");
		helper.assertValueEqual(FortifyWear.wear(level, abs), 1, "wear after one hit");
		helper.assertFalse(Fortified.zombieBreak(level, abs, state, zombie), "second ask");
		helper.assertValueEqual(FortifyWear.wear(level, abs), 1, "the same zombie counted twice in one tick");
		helper.assertValueEqual(FortifyWear.points(state), 120, "reinforced planks' points");
		Zombie far = zombieAt(helper, 5, 5);
		helper.assertFalse(FortifyWear.zombieHit(level, abs, state, far), "a zombie out of reach wore the block");
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void fullWearDowngradesPlanksToOak(GameTestHelper helper) {
		floor(helper);
		BlockPos wall = new BlockPos(2, 1, 2);
		helper.setBlock(wall, DirectorBlocks.REINFORCED_PLANKS);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(wall);
		BlockState state = level.getBlockState(abs);
		FortifyWear.addWear(level, abs, state, FortifyWear.points(state) - 1);
		helper.assertValueEqual(FortifyWear.wear(level, abs), FortifyWear.points(state) - 1, "wear one short of full");
		Zombie zombie = zombieAt(helper, 2, 3);
		helper.assertFalse(Fortified.zombieBreak(level, abs, state, zombie), "the last hit broke the planks outright");
		helper.assertTrue(level.getBlockState(abs).is(Blocks.OAK_PLANKS), "worn-out reinforced planks became "
				+ level.getBlockState(abs));
		helper.assertValueEqual(FortifyWear.wear(level, abs), 0, "the oak planks carry wear");
		helper.assertTrue(Fortified.zombieBreak(level, abs, level.getBlockState(abs), zombie), "ZB&B may break the oak planks");
		helper.succeed();
	}

	@GameTest(padding = PADDING, maxTicks = 200)
	public void twoZombiesWearTwiceAsFast(GameTestHelper helper) {
		floor(helper);
		BlockPos one = new BlockPos(1, 1, 1), two = new BlockPos(4, 1, 1);
		helper.setBlock(one, Blocks.IRON_BLOCK);
		helper.setBlock(two, Blocks.IRON_BLOCK);
		ServerLevel level = helper.getLevel();
		BlockPos absOne = helper.absolutePos(one), absTwo = helper.absolutePos(two);
		Zombie solo = zombieAt(helper, 1, 2);
		Zombie pairA = zombieAt(helper, 4, 2), pairB = zombieAt(helper, 3, 1);
		// ZB&B asks every tick while a zombie stands at a block it wants through
		helper.onEachTick(() -> {
			Fortified.zombieBreak(level, absOne, level.getBlockState(absOne), solo);
			Fortified.zombieBreak(level, absTwo, level.getBlockState(absTwo), pairA);
			Fortified.zombieBreak(level, absTwo, level.getBlockState(absTwo), pairB);
		});
		helper.runAfterDelay(101, () -> {
			int single = FortifyWear.wear(level, absOne), pair = FortifyWear.wear(level, absTwo);
			helper.assertTrue(single >= 4 && single <= 6, "one zombie over 5 s: " + single + " points, want about 5");
			helper.assertTrue(Math.abs(pair - 2 * single) <= 1, "two zombies: " + pair + " points against " + single);
			helper.succeed();
		});
	}

	@GameTest(padding = PADDING)
	public void repairWithMaterialTakesWearOff(GameTestHelper helper) {
		floor(helper);
		BlockPos wall = new BlockPos(2, 1, 2);
		helper.setBlock(wall, Blocks.IRON_BLOCK);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(wall);
		int step = WearSettings.get().repairPoints();
		FortifyWear.addWear(level, abs, level.getBlockState(abs), step + 10);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 4));
		helper.assertValueEqual(UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit),
				InteractionResult.PASS, "planks repairing iron");
		helper.assertValueEqual(FortifyWear.wear(level, abs), step + 10, "wear after the wrong material");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SalvageItems.SCRAP_METAL, 2));
		helper.assertTrue(UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit).consumesAction(),
				"scrap did not repair iron");
		helper.assertValueEqual(FortifyWear.wear(level, abs), 10, "wear after one repair");
		helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "scrap left after one repair");
		coolDown(player);
		UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
		helper.assertValueEqual(FortifyWear.wear(level, abs), 0, "wear after the second repair");
		helper.assertTrue(player.getMainHandItem().isEmpty(), "scrap left after two repairs");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SalvageItems.SCRAP_METAL, 1));
		coolDown(player);
		helper.assertValueEqual(UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit),
				InteractionResult.PASS, "repairing an unworn block");
		helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "scrap spent on an unworn block");
		helper.succeed();
	}

	/** Runs out the repair cooldown on {@code player}'s items. */
	private static void coolDown(Player player) {
		for (int t = 0; t < WearSettings.get().repairCooldownTicks(); t++) {
			player.getCooldowns().tick();
		}
	}

	@GameTest(padding = PADDING)
	public void repairCooldownBlocksTheNextRepair(GameTestHelper helper) {
		floor(helper);
		BlockPos wall = new BlockPos(2, 1, 2);
		helper.setBlock(wall, Blocks.IRON_BLOCK);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(wall);
		int step = WearSettings.get().repairPoints();
		helper.assertValueEqual(WearSettings.get().repairCooldownTicks(), 20, "shipped repair_cooldown_ticks");
		FortifyWear.addWear(level, abs, level.getBlockState(abs), 3 * step);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SalvageItems.SCRAP_METAL, 3));
		helper.assertTrue(UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit).consumesAction(),
				"scrap did not repair iron");
		helper.assertValueEqual(FortifyWear.wear(level, abs), 2 * step, "wear after one repair");
		helper.assertTrue(player.getCooldowns().isOnCooldown(player.getMainHandItem()), "scrap not cooling down after a repair");
		UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
		helper.assertValueEqual(FortifyWear.wear(level, abs), 2 * step, "wear after a click during the cooldown");
		helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "scrap spent during the cooldown");
		coolDown(player);
		UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
		helper.assertValueEqual(FortifyWear.wear(level, abs), step, "wear after the cooldown ran out");
		helper.succeed();
	}

	/** Scrolling to another plank type, or to a fresh stack after the held one ran out, must not dodge the cooldown. */
	@GameTest(padding = PADDING)
	public void repairCooldownCoversEveryMaterialStack(GameTestHelper helper) {
		floor(helper);
		BlockPos wall = new BlockPos(2, 1, 2);
		helper.setBlock(wall, DirectorBlocks.REINFORCED_PLANKS);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(wall);
		int step = WearSettings.get().repairPoints();
		FortifyWear.addWear(level, abs, level.getBlockState(abs), 2 * step);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		// every other material of the rule the player carries (Nails exist only with arms loaded)
		player.getInventory().setItem(5, new ItemStack(Items.SPRUCE_PLANKS, 4));
		BuiltInRegistries.ITEM.getOptional(Identifier.parse("ashfall:nails"))
				.ifPresent(nails -> player.getInventory().setItem(6, new ItemStack(nails, 8)));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 1));
		helper.assertTrue(UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit).consumesAction(),
				"planks did not repair reinforced planks");
		helper.assertValueEqual(FortifyWear.wear(level, abs), step, "wear after one repair");
		helper.assertTrue(player.getMainHandItem().isEmpty(), "the last oak plank was not used");
		helper.assertTrue(player.getCooldowns().isOnCooldown(new ItemStack(Items.OAK_PLANKS)),
				"oak planks not cooling down once the held stack ran out");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 4));
		UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
		helper.assertValueEqual(FortifyWear.wear(level, abs), step, "wear after a fresh oak stack during the cooldown");
		player.setItemInHand(InteractionHand.MAIN_HAND, player.getInventory().getItem(5).copy());
		player.getInventory().setItem(5, ItemStack.EMPTY);
		UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
		helper.assertValueEqual(FortifyWear.wear(level, abs), step, "wear after switching to spruce during the cooldown");
		helper.assertValueEqual(player.getMainHandItem().getCount(), 4, "spruce spent during the cooldown");
		coolDown(player);
		UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
		helper.assertValueEqual(FortifyWear.wear(level, abs), 0, "wear after the cooldown ran out");
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void obsidianRepairNeedsScrap(GameTestHelper helper) {
		floor(helper);
		BlockPos wall = new BlockPos(2, 1, 2);
		helper.setBlock(wall, Blocks.OBSIDIAN);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(wall);
		FortifyWear.addWear(level, abs, level.getBlockState(abs), 30);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 4));
		helper.assertValueEqual(UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit),
				InteractionResult.PASS, "cobblestone repairing obsidian");
		helper.assertValueEqual(FortifyWear.wear(level, abs), 30, "obsidian wear after cobblestone");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SalvageItems.SCRAP_METAL, 1));
		helper.assertTrue(UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit).consumesAction(),
				"scrap did not repair obsidian");
		helper.assertValueEqual(FortifyWear.wear(level, abs), 0, "obsidian wear after scrap");
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void wearSurvivesChunkSaveAndReload(GameTestHelper helper) {
		floor(helper);
		BlockPos wall = new BlockPos(2, 1, 2);
		helper.setBlock(wall, Blocks.OBSIDIAN);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(wall);
		FortifyWear.addWear(level, abs, level.getBlockState(abs), 33);
		LevelChunk chunk = level.getChunkAt(abs);
		// the same path a chunk takes to disk and back: SerializableChunkData out, NBT, back into a ProtoChunk
		CompoundTag saved = SerializableChunkData.copyOf(level, chunk).write();
		ProtoChunk loaded = SerializableChunkData.parse(level, level.palettedContainerFactory(), saved)
				.read(level, level.getPoiManager(), new RegionStorageInfo("gametest", level.dimension(), "chunk"), chunk.getPos());
		FortifyWear.WearMap map = loaded.getAttached(FortifyWear.WEAR);
		helper.assertTrue(map != null, "the reloaded chunk has no wear");
		helper.assertValueEqual(map.points().get(abs.asLong()), 33, "wear after reloading");
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void wearClearedWhenBlockReplaced(GameTestHelper helper) {
		floor(helper);
		BlockPos wall = new BlockPos(2, 1, 2), panel = new BlockPos(3, 1, 3);
		helper.setBlock(wall, DirectorBlocks.REINFORCED_PLANKS);
		helper.setBlock(panel, DirectorBlocks.BARRICADE.defaultBlockState().setValue(BarricadeBlock.FACING, Direction.NORTH));
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(wall), absPanel = helper.absolutePos(panel);
		FortifyWear.addWear(level, abs, level.getBlockState(abs), 50);
		FortifyWear.addWear(level, absPanel, level.getBlockState(absPanel), 20);
		helper.setBlock(wall, Blocks.STONE);
		helper.setBlock(wall, DirectorBlocks.REINFORCED_PLANKS);
		helper.assertValueEqual(FortifyWear.wear(level, abs), 0, "new planks in a worn spot");
		helper.setBlock(panel, DirectorBlocks.BARRICADE.defaultBlockState().setValue(BarricadeBlock.FACING, Direction.EAST));
		helper.assertValueEqual(FortifyWear.wear(level, absPanel), 20, "turning a barricade (same block) kept its wear");
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void bossStillBreaksOutright(GameTestHelper helper) {
		floor(helper);
		BlockPos wall = new BlockPos(2, 1, 2);
		helper.setBlock(wall, Blocks.OBSIDIAN);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(wall);
		// the gametest datapack adds minecraft:giant to #ashfall:bosses
		Giant giant = helper.spawnWithNoFreeWill(EntityTypes.GIANT, new Vec3(2.5, 1, 4.5));
		helper.assertTrue(Fortified.zombieBreak(level, abs, level.getBlockState(abs), giant), "a boss may not break obsidian");
		helper.assertValueEqual(FortifyWear.wear(level, abs), 0, "a boss wore the block instead of breaking it");
		giant.discard();
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void safeZoneBlocksAllBreakingAndWear(GameTestHelper helper) {
		floor(helper);
		BlockPos wall = new BlockPos(2, 1, 2), plain = new BlockPos(3, 1, 2);
		helper.setBlock(wall, DirectorBlocks.REINFORCED_PLANKS);
		helper.setBlock(plain, Blocks.OAK_PLANKS);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(wall), absPlain = helper.absolutePos(plain);
		Zombie zombie = zombieAt(helper, 2, 3);
		HavenState state = HavenState.get(level.getServer());
		// radius 1: the two blocks can sit either side of a chunk border. The zone lives only inside this one call, so
		// no other test's code runs while it exists.
		state.putCustomZone(TEMP_ZONE, new SafeZone("Gametest", Level.OVERWORLD, ChunkArea.around(abs.getX(), abs.getZ(), 1),
				Optional.of(abs), false, Optional.empty()));
		Havens.rebuild(level.getServer());
		try {
			helper.assertFalse(Fortified.zombieBreak(level, absPlain, level.getBlockState(absPlain), zombie),
					"a zombie may break plain planks in a safe zone");
			helper.assertFalse(Fortified.zombieBreak(level, abs, level.getBlockState(abs), zombie),
					"a zombie may break reinforced planks in a safe zone");
			helper.assertValueEqual(FortifyWear.wear(level, abs), 0, "a zombie wore a block in a safe zone");
		} finally {
			state.removeCustomZone(TEMP_ZONE);
			Havens.rebuild(level.getServer());
		}
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void doorHalvesShareWearAndBreakWithoutScrap(GameTestHelper helper) {
		floor(helper);
		BlockPos lower = new BlockPos(2, 1, 2);
		helper.setBlock(lower, Blocks.IRON_DOOR.defaultBlockState());
		helper.setBlock(lower.above(), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(lower);
		BlockState upper = level.getBlockState(abs.above());
		Zombie zombie = zombieAt(helper, 2, 3);
		helper.assertFalse(Fortified.zombieBreak(level, abs.above(), upper, zombie), "a zombie may break an iron door outright");
		helper.assertValueEqual(FortifyWear.wear(level, abs), 1, "a hit on the upper half wears the lower");
		FortifyWear.addWear(level, abs, level.getBlockState(abs), FortifyWear.points(upper));
		helper.assertTrue(level.getBlockState(abs).isAir() && level.getBlockState(abs.above()).isAir(), "the worn-out door still stands");
		// 10.2.2: an iron door drops nothing, so a fresh door costs its full 4 Scrap, the same as a full repair
		helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2),
				e -> e.getItem().is(SalvageItems.SCRAP_METAL)).isEmpty(), "the worn-out iron door dropped scrap");
		helper.succeed();
	}

	// ---- wear that goes with its block ----

	/** A survival player standing clear of the test blocks, holding {@code tool}. */
	private static ServerPlayer survivalPlayer(GameTestHelper helper, ItemStack tool) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		BlockPos stand = helper.absolutePos(new BlockPos(5, 1, 5));
		player.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, tool);
		return player;
	}

	/** The one dropped stack of {@code item} near {@code pos}, picked up (the entity goes). */
	private static ItemStack pickUp(GameTestHelper helper, BlockPos pos, Item item) {
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2),
				e -> e.getItem().is(item));
		helper.assertValueEqual(drops.size(), 1, "dropped " + item + " stacks");
		ItemStack stack = drops.getFirst().getItem().copy();
		drops.getFirst().discard();
		return stack;
	}

	/** {@code player} places what it holds on top of the floor block under {@code pos}. */
	private static void placeOnFloor(ServerPlayer player, ServerLevel level, BlockPos pos) {
		BlockPos floor = pos.below();
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
		player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
	}

	@GameTest(padding = PADDING)
	public void brokenPlanksKeepWearWhenPlacedAgain(GameTestHelper helper) {
		floor(helper);
		BlockPos wall = new BlockPos(2, 1, 2);
		helper.setBlock(wall, DirectorBlocks.REINFORCED_PLANKS);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(wall);
		FortifyWear.addWear(level, abs, level.getBlockState(abs), 48);
		ServerPlayer player = survivalPlayer(helper, new ItemStack(Items.NETHERITE_AXE));
		helper.assertTrue(player.gameMode.destroyBlock(abs), "the player could not break the planks");
		helper.assertValueEqual(FortifyWear.wear(level, abs), 0, "wear left in the empty spot");
		ItemStack dropped = pickUp(helper, abs, DirectorBlocks.REINFORCED_PLANKS.asItem());
		WearCarry.ItemWear carried = dropped.get(WearCarry.ITEM_WEAR);
		helper.assertTrue(carried != null, "the dropped planks carry no wear");
		helper.assertValueEqual(carried.wear(), 48, "wear on the dropped planks");
		List<Component> tooltip = new ArrayList<>();
		carried.addToTooltip(Item.TooltipContext.EMPTY, tooltip::add, TooltipFlag.NORMAL, dropped);
		helper.assertTrue(tooltip.size() == 1 && tooltip.getFirst().getContents() instanceof TranslatableContents t
				&& t.getKey().equals("tooltip.ashfall.wear") && t.getArgs()[0].equals(40), "tooltip " + tooltip);

		player.setItemInHand(InteractionHand.MAIN_HAND, dropped);
		placeOnFloor(player, level, abs);
		helper.assertTrue(level.getBlockState(abs).is(DirectorBlocks.REINFORCED_PLANKS), "the planks were not placed again");
		helper.assertValueEqual(FortifyWear.wear(level, abs), 48, "wear after placing the planks again");
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void brokenDoorKeepsWearOnBothHalves(GameTestHelper helper) {
		floor(helper);
		BlockPos lower = new BlockPos(2, 1, 2);
		helper.setBlock(lower, Blocks.IRON_DOOR.defaultBlockState());
		helper.setBlock(lower.above(), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(lower);
		// a hit on the upper half wears the shared (lower) spot
		FortifyWear.addWear(level, FortifyWear.anchor(level.getBlockState(abs.above()), abs.above()),
				level.getBlockState(abs.above()), 72);
		ServerPlayer player = survivalPlayer(helper, new ItemStack(Items.NETHERITE_PICKAXE));
		helper.assertTrue(player.gameMode.destroyBlock(abs), "the player could not break the door");
		helper.assertTrue(level.getBlockState(abs.above()).isAir(), "the upper half outlived the lower");
		ItemStack dropped = pickUp(helper, abs, Items.IRON_DOOR);
		helper.assertValueEqual(dropped.get(WearCarry.ITEM_WEAR), new WearCarry.ItemWear(72, 180), "wear on the dropped door");

		player.setItemInHand(InteractionHand.MAIN_HAND, dropped);
		placeOnFloor(player, level, abs);
		helper.assertTrue(level.getBlockState(abs).is(Blocks.IRON_DOOR) && level.getBlockState(abs.above()).is(Blocks.IRON_DOOR),
				"the door was not placed again");
		helper.assertValueEqual(FortifyWear.wear(level, abs), 72, "wear after placing the door again");
		helper.assertTrue(Math.abs(FortifyWear.condition(level, abs.above()) - 0.6) < 1e-9,
				"the upper half's condition: " + FortifyWear.condition(level, abs.above()));
		helper.assertTrue(Math.abs(FortifyWear.condition(level, abs) - 0.6) < 1e-9,
				"the lower half's condition: " + FortifyWear.condition(level, abs));
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void wornAndUnwornPlanksDoNotStack(GameTestHelper helper) {
		ItemStack plain = new ItemStack(DirectorBlocks.REINFORCED_PLANKS);
		ItemStack worn = new ItemStack(DirectorBlocks.REINFORCED_PLANKS);
		worn.set(WearCarry.ITEM_WEAR, new WearCarry.ItemWear(30, 120));
		helper.assertFalse(ItemStack.isSameItemSameComponents(plain, worn), "worn planks match unworn ones");
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().add(plain.copy());
		player.getInventory().add(worn.copy());
		player.getInventory().add(plain.copy());
		int stacks = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).is(DirectorBlocks.REINFORCED_PLANKS.asItem())) {
				stacks++;
			}
		}
		helper.assertValueEqual(stacks, 2, "inventory stacks of reinforced planks (unworn, worn)");
		helper.succeed();
	}

	/** A piston at x = 0 facing east with worn planks at x = 1 (50 wear) and x = 2 (30 wear); powered from below. */
	private static void pistonRow(GameTestHelper helper, boolean sticky) {
		floor(helper);
		helper.setBlock(new BlockPos(0, 1, 2), (sticky ? Blocks.STICKY_PISTON : Blocks.PISTON).defaultBlockState()
				.setValue(PistonBaseBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(1, 1, 2), DirectorBlocks.REINFORCED_PLANKS);
		helper.setBlock(new BlockPos(2, 1, 2), DirectorBlocks.REINFORCED_PLANKS);
		ServerLevel level = helper.getLevel();
		BlockPos one = helper.absolutePos(new BlockPos(1, 1, 2)), two = helper.absolutePos(new BlockPos(2, 1, 2));
		FortifyWear.addWear(level, one, level.getBlockState(one), 50);
		FortifyWear.addWear(level, two, level.getBlockState(two), 30);
	}

	private static int wearAt(GameTestHelper helper, int x) {
		return FortifyWear.wear(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 2)));
	}

	/** Both planks pushed one step east: x = 2 and 3 hold them, x = 1 is the piston head. */
	private static void assertPushed(GameTestHelper helper) {
		helper.assertBlockPresent(DirectorBlocks.REINFORCED_PLANKS, new BlockPos(3, 1, 2));
		helper.assertBlockPresent(DirectorBlocks.REINFORCED_PLANKS, new BlockPos(2, 1, 2));
		helper.assertBlockPresent(Blocks.PISTON_HEAD, new BlockPos(1, 1, 2));
		helper.assertValueEqual(wearAt(helper, 3), 30, "wear on the far planks after the push");
		helper.assertValueEqual(wearAt(helper, 2), 50, "wear on the near planks after the push");
		helper.assertValueEqual(wearAt(helper, 1), 0, "wear left under the piston head");
	}

	@GameTest(padding = PADDING, maxTicks = 60)
	public void pistonPushCarriesWear(GameTestHelper helper) {
		pistonRow(helper, false);
		helper.setBlock(new BlockPos(0, 0, 2), Blocks.REDSTONE_BLOCK);
		helper.succeedWhen(() -> assertPushed(helper));
	}

	@GameTest(padding = PADDING, maxTicks = 80)
	public void pistonPullCarriesWear(GameTestHelper helper) {
		pistonRow(helper, true);
		helper.setBlock(new BlockPos(0, 0, 2), Blocks.REDSTONE_BLOCK);
		helper.startSequence()
				.thenWaitUntil(() -> assertPushed(helper))
				.thenExecute(() -> helper.setBlock(new BlockPos(0, 0, 2), Blocks.STONE))
				.thenWaitUntil(() -> {
					// a sticky piston pulls back only the block touching its head
					helper.assertBlockPresent(DirectorBlocks.REINFORCED_PLANKS, new BlockPos(1, 1, 2));
					helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 1, 2));
					helper.assertValueEqual(wearAt(helper, 1), 50, "wear on the pulled planks");
					helper.assertValueEqual(wearAt(helper, 2), 0, "wear left where the pulled planks stood");
					helper.assertValueEqual(wearAt(helper, 3), 30, "wear on the planks left behind");
				})
				.thenSucceed();
	}
}
