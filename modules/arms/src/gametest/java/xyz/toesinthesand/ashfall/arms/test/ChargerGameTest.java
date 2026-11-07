package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyItem;
import xyz.toesinthesand.ashfall.arms.charger.ArmsBlocks;
import xyz.toesinthesand.ashfall.arms.charger.ChargerBlock;
import xyz.toesinthesand.ashfall.arms.charger.ChargerBlockEntity;
import xyz.toesinthesand.ashfall.arms.charger.ChargerKind;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.atlas.AtlasData;
import xyz.toesinthesand.ashfall.salvage.atlas.AtlasProcessor;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.Footprint;
import xyz.toesinthesand.ashfall.salvage.building.StaticBuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.Unit;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Chargers on a real server: charge rates, the cell slot feeding a Field Charger, the relay-only rule,
 * the {@code active} blockstate, the insert-only sided storage, the menu's slot rules, registration,
 * the recipe, and the atlas placing a Power Relay at a recorded relay candidate.
 */
public class ChargerGameTest {
	private static final BlockPos POS = new BlockPos(1, 1, 1);
	/** Measuring window for rates; long enough that a one-tick timing difference stays inside the tolerance. */
	private static final int WINDOW = 40;

	private static ItemStack cell(long energy) {
		return energyStack(new ItemStack(ChargerTestItems.TEST_CELL), energy);
	}

	private static ItemStack energyStack(ItemStack stack, long energy) {
		SimpleEnergyItem.setStoredEnergyUnchecked(stack, energy);
		return stack;
	}

	private static long energy(ItemStack stack) {
		return SimpleEnergyItem.getStoredEnergyUnchecked(stack);
	}

	private static ChargerBlockEntity place(GameTestHelper helper, Block block) {
		helper.setBlock(POS, block);
		return helper.getBlockEntity(POS, ChargerBlockEntity.class);
	}

	@GameTest(maxTicks = 100)
	public void relayChargesTwoPerTickPerSlot(GameTestHelper helper) {
		ChargerBlockEntity relay = place(helper, ArmsBlocks.POWER_RELAY);
		relay.setItem(0, cell(0));
		relay.setItem(1, cell(0));
		helper.runAfterDelay(5, () -> {
			long start0 = energy(relay.getItem(0)), start1 = energy(relay.getItem(1));
			helper.runAfterDelay(WINDOW, () -> {
				long gained0 = energy(relay.getItem(0)) - start0, gained1 = energy(relay.getItem(1)) - start1;
				helper.assertTrue(Math.abs(gained0 - 2L * WINDOW) <= 4 && Math.abs(gained1 - 2L * WINDOW) <= 4,
						"relay should charge each slot 2/tick: got " + gained0 + " and " + gained1 + " in " + WINDOW + " ticks");
				helper.assertBlockProperty(POS, ChargerBlock.ACTIVE, true);
				helper.assertTrue(relay.status() == ChargerBlockEntity.STATUS_CHARGING, "relay status is not charging");
				helper.assertTrue(relay.buffer().amount > 0, "relay generation should keep its buffer up");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 120)
	public void relayRechargesRelayOnlyCells(GameTestHelper helper) {
		ChargerBlockEntity relay = place(helper, ArmsBlocks.POWER_RELAY);
		ItemStack drained = energyStack(new ItemStack(ChargerTestItems.TEST_RELAY_CELL), 0);
		helper.assertTrue(relay.canPlaceItem(0, drained), "a relay must accept a drained Energy Cell in a charge slot");
		relay.setItem(0, drained);
		helper.succeedWhen(() -> helper.assertTrue(energy(relay.getItem(0)) >= 20, "relay did not recharge the drained cell"));
	}

	@GameTest(maxTicks = 100)
	public void fieldChargerRunsOnItsCellAtAQuarterPerTick(GameTestHelper helper) {
		ChargerBlockEntity charger = place(helper, ArmsBlocks.FIELD_CHARGER);
		charger.setItem(ChargerKind.CELL_SLOT, cell(ChargerTestItems.CAPACITY));
		charger.setItem(0, cell(0));
		helper.runAfterDelay(5, () -> {
			long start = energy(charger.getItem(0));
			helper.runAfterDelay(WINDOW, () -> {
				long gained = energy(charger.getItem(0)) - start;
				helper.assertTrue(Math.abs(gained - WINDOW / 4) <= 2,
						"field charger should charge 0.25/tick: got " + gained + " in " + WINDOW + " ticks");
				long cellLeft = energy(charger.getItem(ChargerKind.CELL_SLOT));
				helper.assertTrue(cellLeft < ChargerTestItems.CAPACITY, "the cell slot was not drawn on");
				helper.assertTrue(charger.buffer().amount + cellLeft + energy(charger.getItem(0)) == ChargerTestItems.CAPACITY,
						"energy was created or lost: buffer " + charger.buffer().amount + ", cell " + cellLeft + ", item " + energy(charger.getItem(0)));
				helper.assertTrue(charger.getItem(ChargerKind.CELL_SLOT).is(ChargerTestItems.TEST_CELL), "the cell must stay in its slot");
				helper.assertBlockProperty(POS, ChargerBlock.ACTIVE, true);
				helper.succeed();
			});
		});
	}

	/** A relay powers itself, so a cell left in its cell slot must not be emptied into the buffer. */
	@GameTest(maxTicks = 60)
	public void relayLeavesItsCellSlotAlone(GameTestHelper helper) {
		ChargerBlockEntity relay = place(helper, ArmsBlocks.POWER_RELAY);
		relay.setItem(ChargerKind.CELL_SLOT, cell(ChargerTestItems.CAPACITY));
		relay.setItem(0, cell(0));
		relay.setItem(1, cell(0));
		relay.setItem(2, cell(0));
		relay.setItem(3, cell(0));
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(energy(relay.getItem(ChargerKind.CELL_SLOT)) == ChargerTestItems.CAPACITY,
					"relay drained its cell slot: " + energy(relay.getItem(ChargerKind.CELL_SLOT)) + " left");
			helper.assertTrue(energy(relay.getItem(3)) > 0, "relay did not charge all four slots");
			helper.succeed();
		});
	}

	/** Buffer and slots survive a save and load (chunk unload, server restart). */
	@GameTest
	public void chargerStatePersists(GameTestHelper helper) {
		ChargerBlockEntity charger = place(helper, ArmsBlocks.FIELD_CHARGER);
		charger.buffer().amount = 123;
		charger.setItem(0, cell(17));
		charger.setItem(ChargerKind.CELL_SLOT, cell(200));
		var registries = helper.getLevel().registryAccess();
		var tag = charger.saveWithFullMetadata(registries);
		var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(charger.getBlockPos(), charger.getBlockState(), tag, registries);
		helper.assertTrue(loaded instanceof ChargerBlockEntity, "saved charger did not load as a charger: " + loaded);
		ChargerBlockEntity copy = (ChargerBlockEntity) loaded;
		helper.assertTrue(copy.kind() == ChargerKind.FIELD_CHARGER, "kind lost on load");
		helper.assertTrue(copy.buffer().amount == 123, "buffer not persisted: " + copy.buffer().amount);
		helper.assertTrue(copy.getItem(0).is(ChargerTestItems.TEST_CELL) && energy(copy.getItem(0)) == 17, "charge slot not persisted");
		helper.assertTrue(energy(copy.getItem(ChargerKind.CELL_SLOT)) == 200, "cell slot not persisted");
		helper.succeed();
	}

	/** Atlas relays are world fixtures: no survival break, no pistons, no withers or dragons. */
	@GameTest
	public void powerRelayIsAFixture(GameTestHelper helper) {
		var state = ArmsBlocks.POWER_RELAY.defaultBlockState();
		helper.assertTrue(state.getDestroySpeed(helper.getLevel(), POS) < 0, "Power Relay must be unbreakable in survival");
		helper.assertTrue(state.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.IMMOVEABLE,
				"Power Relay must not be pushed: " + state.getPistonPushReaction());
		helper.assertTrue(state.is(net.minecraft.tags.BlockTags.WITHER_IMMUNE) && state.is(net.minecraft.tags.BlockTags.DRAGON_IMMUNE),
				"Power Relay must be wither and dragon immune");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void fieldChargerRefusesRelayOnlyCells(GameTestHelper helper) {
		ChargerBlockEntity charger = place(helper, ArmsBlocks.FIELD_CHARGER);
		ItemStack drained = energyStack(new ItemStack(ChargerTestItems.TEST_RELAY_CELL), 0);
		helper.assertFalse(charger.canPlaceItem(0, drained), "a Field Charger must refuse a drained Energy Cell");
		helper.assertTrue(ChargerBlockEntity.canCharge(ChargerKind.FIELD_CHARGER, cell(0)), "a Field Charger must accept other energy items");
		charger.setItem(ChargerKind.CELL_SLOT, cell(ChargerTestItems.CAPACITY));
		charger.setItem(0, drained); // forced in, e.g. by a command
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(energy(charger.getItem(0)) == 0, "a Field Charger charged a relay-only cell");
			helper.assertBlockProperty(POS, ChargerBlock.ACTIVE, false);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void unpoweredFieldChargerStaysIdle(GameTestHelper helper) {
		ChargerBlockEntity charger = place(helper, ArmsBlocks.FIELD_CHARGER);
		charger.setItem(0, cell(0));
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(energy(charger.getItem(0)) == 0, "an unpowered charger produced energy");
			helper.assertTrue(charger.status() == ChargerBlockEntity.STATUS_IDLE, "status should be idle, was " + charger.status());
			helper.assertBlockProperty(POS, ChargerBlock.ACTIVE, false);
			helper.succeed();
		});
	}

	@GameTest
	public void sidedStorageIsInsertOnly(GameTestHelper helper) {
		ChargerBlockEntity charger = place(helper, ArmsBlocks.FIELD_CHARGER);
		EnergyStorage sided = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), Direction.UP);
		helper.assertTrue(sided != null, "no EnergyStorage.SIDED on the charger");
		helper.assertTrue(sided.supportsInsertion() && !sided.supportsExtraction(), "sided storage must be insert-only");
		helper.assertTrue(sided.getCapacity() == ChargerKind.BUFFER, "buffer capacity should be " + ChargerKind.BUFFER);
		try (Transaction tx = Transaction.openOuter()) {
			long inserted = sided.insert(1000, tx);
			helper.assertTrue(inserted == ChargerKind.EXTERNAL_INSERT_PER_TICK, "insert should be capped per call, got " + inserted);
			helper.assertTrue(sided.extract(10, tx) == 0, "extraction must be refused");
			tx.commit();
		}
		helper.assertTrue(charger.buffer().amount == ChargerKind.EXTERNAL_INSERT_PER_TICK, "inserted energy did not reach the buffer");
		helper.succeed();
	}

	@GameTest
	public void menuSlotRules(GameTestHelper helper) {
		ChargerBlockEntity charger = place(helper, ArmsBlocks.FIELD_CHARGER);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		AbstractContainerMenu menu = charger.createMenu(1, player.getInventory(), player);
		helper.assertTrue(menu != null && menu.getType() == ArmsBlocks.FIELD_CHARGER_MENU, "wrong or missing menu type");
		helper.assertTrue(menu.slots.size() == ChargerKind.SLOTS + 36, "expected 5 charger slots plus the inventory, got " + menu.slots.size());
		helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)), "charge slot accepted dirt");
		helper.assertFalse(menu.getSlot(ChargerKind.CELL_SLOT).mayPlace(new ItemStack(Items.DIRT)), "cell slot accepted dirt");
		helper.assertTrue(menu.getSlot(0).mayPlace(cell(0)), "charge slot refused an energy item");
		helper.assertTrue(menu.getSlot(ChargerKind.CELL_SLOT).mayPlace(cell(100)), "cell slot refused an energy item");
		helper.assertTrue(menu.getSlot(0).getMaxStackSize() == 1, "charger slots must hold one item");

		// Shift-clicking a charged cell from the inventory fuels the charger.
		player.getInventory().setItem(0, cell(100));
		int hotbar0 = ChargerKind.SLOTS + 27;
		menu.quickMoveStack(player, hotbar0);
		helper.assertTrue(charger.getItem(ChargerKind.CELL_SLOT).is(ChargerTestItems.TEST_CELL), "shift-click did not put the charged cell in the cell slot");
		helper.succeed();
	}

	@GameTest
	public void blocksRegisteredInTabAndCraftable(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (String id : List.of("field_charger", "power_relay")) {
			Identifier key = Identifier.fromNamespaceAndPath("ashfall", id);
			helper.assertTrue(BuiltInRegistries.BLOCK.getValue(key) instanceof ChargerBlock, "block ashfall:" + id + " missing");
			helper.assertTrue(BuiltInRegistries.ITEM.getValue(key) != Items.AIR, "block item ashfall:" + id + " missing");
		}
		CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(ArmsItems.ASHFALL_TAB);
		tab.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
		helper.assertTrue(tab.contains(new ItemStack(ArmsBlocks.FIELD_CHARGER_ITEM)) && tab.contains(new ItemStack(ArmsBlocks.POWER_RELAY_ITEM)),
				"chargers are not in the Ashfall tab");

		var recipes = level.getServer().getRecipeManager();
		helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("ashfall", "field_charger"))).isPresent(),
				"recipe ashfall:field_charger did not load");
		ItemStack i = new ItemStack(ArmsItems.ALLOY_PLATE), c = new ItemStack(ArmsItems.WIRE_SPOOL);
		CraftingInput input = CraftingInput.of(3, 3, List.of(i, c, i,
				new ItemStack(ArmsItems.SCRAP_ELECTRONICS), new ItemStack(ArmsItems.WIRE_SPOOL), new ItemStack(ArmsItems.BATTERY),
				i, c, i));
		ItemStack result = recipes.getRecipeFor(RecipeType.CRAFTING, input, level).map(h -> h.value().assemble(input)).orElse(ItemStack.EMPTY);
		helper.assertTrue(result.is(ArmsBlocks.FIELD_CHARGER_ITEM), "field charger recipe crafted " + result);
		helper.succeed();
	}

	/** The atlas puts a real Power Relay (looked up by id from salvage) at the building's relay candidate. */
	@GameTest(maxTicks = 40)
	public void atlasPlacesPowerRelay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		int y0 = o.getY();
		// The gametest world (and its atlas saved data) survives between runs: a fixed id would already be
		// marked placed on the second run. The test's position is unique within a run and across runs.
		long building = o.asLong() & Long.MAX_VALUE;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				boolean edge = x == 0 || z == 0 || x == 7 || z == 7;
				level.setBlock(o.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
				for (int y = 1; y <= 4; y++) {
					level.setBlock(o.offset(x, y, z), (edge ? Blocks.STONE_BRICKS : Blocks.AIR).defaultBlockState(), Block.UPDATE_ALL);
				}
				level.setBlock(o.offset(x, 5, z), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
			}
		}

		Map<Identifier, BuildingProfile> savedProfiles = BuildingProfiles.all();
		try {
			BuildingProfiles.setForTesting(Map.of(AshfallSalvage.id("test_relay_home"), new BuildingProfile(List.of(), List.of("Home"), 0, 1,
					Optional.of(Identifier.withDefaultNamespace("chests/simple_dungeon")),
					new BuildingProfile.Containers(0f, 1, 1, List.of(new BuildingProfile.ContainerBlock(Identifier.withDefaultNamespace("chest"), 1))),
					BuildingProfile.Refill.DEFAULT, BuildingProfile.Population.NONE, false, 1f, Optional.empty())));
			AtlasProcessor.setBuildingsForTesting(sidecar(o, y0, building));

			Set<Long> chunks = new LinkedHashSet<>();
			for (int x = 0; x < 8; x += 7) {
				for (int z = 0; z < 8; z += 7) {
					chunks.add(ChunkPos.pack(o.offset(x, 0, z)));
				}
			}
			int placedByPasses = 0;
			for (long key : chunks) {
				LevelChunk chunk = level.getChunk(ChunkPos.getX(key), ChunkPos.getZ(key));
				placedByPasses += AtlasProcessor.processChunk(level, chunk).relaysPlaced();
			}

			AtlasData data = AtlasData.get(level.getServer());
			helper.assertTrue(data.relays().stream().anyMatch(r -> r.building() == building), "relay candidate not recorded");
			helper.assertTrue(data.relayPlaced(building), "relay candidate not marked placed");
			int relays = 0;
			for (int x = 1; x < 7; x++) {
				for (int z = 1; z < 7; z++) {
					if (level.getBlockState(o.offset(x, 1, z)).is(ArmsBlocks.POWER_RELAY)) {
						relays++;
					}
				}
			}
			BlockPos recorded = data.relays().stream().filter(r -> r.building() == building).findFirst().orElseThrow().pos();
			helper.assertTrue(relays == 1, "expected exactly one Power Relay on the ground floor, found " + relays
					+ " (passes placed " + placedByPasses + "; candidate " + recorded.subtract(o) + " holds " + level.getBlockState(recorded) + ", below " + level.getBlockState(recorded.below()) + ")");

			// Reprocessing or sweeping must not add a second one.
			helper.assertTrue(xyz.toesinthesand.ashfall.salvage.atlas.RelayPlacer.placeLoaded(level, null) == 0, "a sweep placed a duplicate relay");
		} finally {
			AtlasProcessor.setBuildingsForTesting(null);
			BuildingProfiles.setForTesting(savedProfiles);
		}
		helper.succeed();
	}

	/**
	 * The one building the old Arnis sidecar test fixture described, built directly now that the sidecar reader
	 * is archived: an 8x8 shell with a 6x6 "Home" unit inside, served by a {@link StaticBuildingSource}.
	 */
	private static StaticBuildingSource sidecar(BlockPos o, int y0, long building) {
		List<int[]> shellRows = new java.util.ArrayList<>(), unitRows = new java.util.ArrayList<>();
		for (int z = 0; z < 8; z++) {
			shellRows.add(new int[]{o.getZ() + z, o.getX(), o.getX() + 7});
		}
		for (int z = 1; z < 7; z++) {
			unitRows.add(new int[]{o.getZ() + z, o.getX() + 1, o.getX() + 6});
		}
		Unit unit = new Unit(0, y0, y0 + 4, "Home", Footprint.of(unitRows));
		Building house = new Building(building, Map.of("building", "house"), new int[]{y0}, y0, y0 + 5, Footprint.of(shellRows), List.of(unit));
		return new StaticBuildingSource("test", List.of(house));
	}
}
