package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.survivor.SupplyLockerBlockEntity;
import xyz.toesinthesand.ashfall.arms.survivor.Survivor;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorBalance;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorInfection;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorMode;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRole;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorWork;
import xyz.toesinthesand.ashfall.arms.survivor.Survivors;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;
import xyz.toesinthesand.ashfall.director.fortify.FortifyWear;
import xyz.toesinthesand.ashfall.director.fortify.WearSettings;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreRecord;
import xyz.toesinthesand.ashfall.director.haven.HavenState;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;
import xyz.toesinthesand.ashfall.director.traps.SpikeStripBlock;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Survivors, step 2's base roles ({@link SurvivorWork}), each run by calling the role's turn directly: a scavenger's
 * trip takes a locker's food and its haul ends up in the locker, and it can't be reassigned while out; a medic bandages
 * a hurt player from the locker and does nothing with an empty one, and cures a bitten survivor with locker Antibiotics;
 * a medic leaves a stranger and a stranger's survivor alone (it treats only its own group); a mechanic hammers a Spike
 * Strip's wear out with locker Scrap Metal and repairs a worn iron wall with it, one set a turn (wood needs both its
 * Nails, obsidian needs Scrap Metal); a scavenger keeps a hauled Antibiotics stack only at {@code antibiotics_keep}; and a scavenger whose core broke while it was out
 * drops its haul at its feet. Padded wide: lockers are found anywhere in the saved core's area and a medic treats anyone
 * near home, so neighbouring tests must stay out of reach.
 */
public class SurvivorWorkGameTest {
	private static final int PADDING = 32;
	private static final BlockPos CORE = new BlockPos(0, 1, 0);
	private static final BlockPos LOCKER = new BlockPos(5, 1, 5);
	/** Day-clock 07:00 on day 1: scavenging time. */
	private static final long MORNING = 1_000L;

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/**
	 * A saved Bunker Core at {@link #CORE} (no OPAC claim or region rebuild: lockers are found through the saved core's
	 * area), a Supply Locker holding {@code stock}, and a survivor with {@code role} living there; the core is removed
	 * after {@code body}.
	 */
	private static void atBase(GameTestHelper helper, SurvivorRole role, List<ItemStack> stock, Consumer<Base> body) {
		floor(helper);
		MinecraftServer server = helper.getLevel().getServer();
		UUID owner = UUID.randomUUID();
		BlockPos core = helper.absolutePos(CORE);
		HavenState.get(server).putCore(new BunkerCoreRecord(owner, owner, Level.OVERWORLD, core, 1, List.of()));
		Survivor survivor = null;
		try {
			helper.setBlock(LOCKER, Survivors.SUPPLY_LOCKER);
			SupplyLockerBlockEntity locker = helper.getBlockEntity(LOCKER, SupplyLockerBlockEntity.class);
			for (int i = 0; i < stock.size(); i++) {
				locker.setItem(i, stock.get(i).copy());
			}
			survivor = Survivors.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), role, null);
			helper.assertTrue(survivor != null, "survivor spawned");
			survivor.setHome(core);
			survivor.setMode(SurvivorMode.HOME);
			body.accept(new Base(helper.getLevel(), survivor, locker, owner));
		} finally {
			if (survivor != null) {
				survivor.discard();
			}
			HavenState.get(server).removeCore(owner);
		}
		helper.succeed();
	}

	private record Base(ServerLevel level, Survivor survivor, SupplyLockerBlockEntity locker, UUID owner) {
		int count(net.minecraft.world.item.Item item) {
			int n = 0;
			for (int i = 0; i < locker.getContainerSize(); i++) {
				if (locker.getItem(i).is(item)) {
					n += locker.getItem(i).getCount();
				}
			}
			return n;
		}

		int stacks() {
			int n = 0;
			for (int i = 0; i < locker.getContainerSize(); i++) {
				n += locker.getItem(i).isEmpty() ? 0 : 1;
			}
			return n;
		}
	}

	@GameTest(padding = PADDING)
	public void scavengerTripTakesFoodAndDepositsHaul(GameTestHelper helper) {
		atBase(helper, SurvivorRole.SCAVENGER, List.of(new ItemStack(Items.BREAD)), base -> {
			Survivor scavenger = base.survivor();
			helper.assertTrue(SurvivorWork.tryLeave(base.level(), scavenger, MORNING),
					"scavenger did not leave: " + SurvivorWork.notLeaving(base.level(), scavenger, MORNING));
			helper.assertTrue(scavenger.away(), "scavenger is not away");
			helper.assertValueEqual(base.count(Items.BREAD), 0, "bread left in the locker after setting out");
			helper.assertTrue(!SurvivorWork.tryLeave(base.level(), scavenger, MORNING), "left twice the same day");

			List<ItemStack> haul = SurvivorWork.comeBack(base.level(), scavenger, 0.5f); // neither hurt nor lost
			helper.assertTrue(!scavenger.away(), "scavenger still away after the trip");
			helper.assertTrue(!haul.isEmpty(), "the scavenger came back empty-handed");
			helper.assertTrue(base.stacks() > 0, "the haul " + haul + " is not in the locker");
			helper.assertTrue(!scavenger.work().carrying(), "the scavenger kept part of the haul with room in the locker");
		});
	}

	@GameTest(padding = PADDING)
	public void scavengerBackToABrokenCoreDropsItsHaul(GameTestHelper helper) {
		atBase(helper, SurvivorRole.SCAVENGER, List.of(new ItemStack(Items.BREAD)), base -> {
			Survivor scavenger = base.survivor();
			helper.assertTrue(SurvivorWork.leave(base.level(), scavenger, MORNING), "scavenger did not leave");
			HavenState.get(base.level().getServer()).removeCore(base.owner());
			List<ItemStack> haul = SurvivorWork.comeBack(base.level(), scavenger, 0.5f);
			helper.assertTrue(!haul.isEmpty(), "the scavenger came back empty-handed");
			helper.assertTrue(!scavenger.work().carrying(), "a scavenger with no core to come back to still carries its haul");
			helper.assertValueEqual(base.stacks(), 0, "stacks in the locker of a broken core");
			List<ItemEntity> dropped = base.level().getEntitiesOfClass(ItemEntity.class, scavenger.getBoundingBox().inflate(3));
			dropped.forEach(ItemEntity::discard);
			helper.assertTrue(!dropped.isEmpty(), "the haul " + haul + " is not on the floor at the scavenger's feet");
		});
	}

	@GameTest(padding = PADDING)
	public void scavengerCannotBeReassignedWhileOut(GameTestHelper helper) {
		atBase(helper, SurvivorRole.SCAVENGER, List.of(new ItemStack(Items.BREAD)), base -> {
			Survivor scavenger = base.survivor();
			helper.assertTrue(SurvivorWork.leave(base.level(), scavenger, MORNING), "scavenger did not leave");
			helper.assertTrue(Survivors.roleChangeRefusal(scavenger, SurvivorRole.GUARD, MORNING).isPresent(),
					"a scavenger out on a trip took a new role");
			SurvivorWork.comeBack(base.level(), scavenger, 0.5f);
			helper.assertTrue(Survivors.roleChangeRefusal(scavenger, SurvivorRole.GUARD, MORNING).isEmpty(),
					"a scavenger back home still refuses a new role");
		});
	}

	@GameTest(padding = PADDING)
	public void medicBandagesHurtPlayerFromLocker(GameTestHelper helper) {
		atBase(helper, SurvivorRole.MEDIC, List.of(new ItemStack(DirectorItems.BANDAGE)), base -> {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			try {
				BlockPos at = helper.absolutePos(new BlockPos(4, 1, 2));
				player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				player.setHealth(10.0f);
				base.survivor().setLeader(player.getUUID()); // a medic treats only its own group
				helper.assertValueEqual(SurvivorWork.medic(base.level(), base.survivor()), SurvivorWork.Treatment.HEALED, "first turn");
				helper.assertTrue(player.getHealth() > 10.0f, "player health still " + player.getHealth());
				helper.assertValueEqual(base.count(DirectorItems.BANDAGE), 0, "bandages left in the locker");

				float before = player.getHealth();
				helper.assertValueEqual(SurvivorWork.medic(base.level(), base.survivor()), SurvivorWork.Treatment.NONE,
						"a turn with an empty locker");
				helper.assertValueEqual(player.getHealth(), before, "health after a turn with no bandages");
			} finally {
				player.level().getServer().getPlayerList().remove(player);
			}
		});
	}

	@GameTest(padding = PADDING)
	public void medicCuresBittenSurvivorWithLockerAntibiotics(GameTestHelper helper) {
		atBase(helper, SurvivorRole.MEDIC, List.of(new ItemStack(DirectorItems.ANTIBIOTICS)), base -> {
			Survivor patient = Survivors.spawn(base.level(), helper.absolutePos(new BlockPos(4, 1, 4)), SurvivorRole.SETTLER, null);
			helper.assertTrue(patient != null, "patient spawned");
			try {
				patient.setLeader(base.owner()); // one of the core owner's own
				SurvivorInfection.infect(patient);
				helper.assertTrue(patient.infected(), "patient not bitten");
				helper.assertValueEqual(SurvivorWork.medic(base.level(), base.survivor()), SurvivorWork.Treatment.CURED, "medic's turn");
				helper.assertTrue(!patient.infected(), "patient still bitten");
				helper.assertValueEqual(base.count(DirectorItems.ANTIBIOTICS), 0, "antibiotics left in the locker");
			} finally {
				patient.discard();
			}
		});
	}

	@GameTest(padding = PADDING)
	public void medicLeavesStrangersAlone(GameTestHelper helper) {
		atBase(helper, SurvivorRole.MEDIC, List.of(new ItemStack(DirectorItems.ANTIBIOTICS), new ItemStack(DirectorItems.BANDAGE)), base -> {
			ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
			Survivor theirs = Survivors.spawn(base.level(), helper.absolutePos(new BlockPos(4, 1, 4)), SurvivorRole.SETTLER, stranger.getUUID());
			helper.assertTrue(theirs != null, "stranger's survivor spawned");
			try {
				BlockPos at = helper.absolutePos(new BlockPos(4, 1, 2));
				stranger.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				stranger.setHealth(10.0f);
				SurvivorInfection.infect(theirs);
				helper.assertValueEqual(SurvivorWork.medic(base.level(), base.survivor()), SurvivorWork.Treatment.NONE,
						"a turn with only a stranger and their survivor near");
				helper.assertTrue(theirs.infected(), "the medic cured a stranger's survivor");
				helper.assertValueEqual(stranger.getHealth(), 10.0f, "the stranger's health");
				helper.assertValueEqual(base.count(DirectorItems.ANTIBIOTICS), 1, "antibiotics left in the locker");
				helper.assertValueEqual(base.count(DirectorItems.BANDAGE), 1, "bandages left in the locker");
			} finally {
				theirs.discard();
				stranger.level().getServer().getPlayerList().remove(stranger);
			}
		});
	}

	@GameTest(padding = PADDING)
	public void mechanicRemovesSpikeStripWearWithLockerScrap(GameTestHelper helper) {
		atBase(helper, SurvivorRole.MECHANIC, List.of(new ItemStack(SalvageItems.SCRAP_METAL)), base -> {
			BlockPos strip = new BlockPos(3, 1, 5);
			helper.setBlock(strip, DirectorBlocks.SPIKE_STRIP.defaultBlockState().setValue(SpikeStripBlock.WEAR, 2));
			helper.assertValueEqual(SurvivorWork.mechanic(base.level(), base.survivor()), SurvivorWork.Repair.SPIKE_STRIP, "mechanic's turn");
			helper.assertValueEqual(helper.getBlockState(strip).getValue(SpikeStripBlock.WEAR), 0, "strip wear after the repair");
			helper.assertValueEqual(base.count(SalvageItems.SCRAP_METAL), 0, "scrap left in the locker");
			helper.assertValueEqual(SurvivorWork.mechanic(base.level(), base.survivor()), SurvivorWork.Repair.NONE,
					"a turn with nothing worn and no scrap");
		});
	}

	@GameTest(padding = PADDING)
	public void mechanicRepairsWornWallWithLockerScrap(GameTestHelper helper) {
		atBase(helper, SurvivorRole.MECHANIC, List.of(new ItemStack(SalvageItems.SCRAP_METAL, 2)), base -> {
			BlockPos wall = new BlockPos(3, 1, 5);
			helper.setBlock(wall, Blocks.IRON_BLOCK);
			BlockPos abs = helper.absolutePos(wall);
			int step = WearSettings.get().repairPoints();
			FortifyWear.addWear(base.level(), abs, helper.getBlockState(wall), step + step / 2);
			// one set per turn (10.2.2): the first turn takes one step off, the second the rest
			helper.assertValueEqual(SurvivorWork.mechanic(base.level(), base.survivor()), SurvivorWork.Repair.WALL, "mechanic's turn");
			helper.assertValueEqual(FortifyWear.wear(base.level(), abs), step / 2, "wall wear after the first turn");
			helper.assertValueEqual(base.count(SalvageItems.SCRAP_METAL), 1, "scrap left after the first turn");
			helper.assertValueEqual(SurvivorWork.mechanic(base.level(), base.survivor()), SurvivorWork.Repair.WALL, "second turn");
			helper.assertValueEqual(FortifyWear.wear(base.level(), abs), 0, "wall wear after the second turn");
			helper.assertValueEqual(base.count(SalvageItems.SCRAP_METAL), 0, "two repairs, one scrap each");
			helper.assertTrue(helper.getBlockState(wall).is(Blocks.IRON_BLOCK), "the wall is still iron");
			helper.assertValueEqual(SurvivorWork.mechanic(base.level(), base.survivor()), SurvivorWork.Repair.NONE,
					"a turn with nothing worn and no scrap");
		});
	}

	@GameTest(padding = PADDING)
	public void mechanicNeedsTwoNailsForWood(GameTestHelper helper) {
		atBase(helper, SurvivorRole.MECHANIC, List.of(new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(ArmsItems.NAILS, 1)), base -> {
			BlockPos wall = new BlockPos(3, 1, 5);
			helper.setBlock(wall, DirectorBlocks.REINFORCED_PLANKS);
			BlockPos abs = helper.absolutePos(wall);
			FortifyWear.addWear(base.level(), abs, helper.getBlockState(wall), 30);
			helper.assertValueEqual(SurvivorWork.mechanic(base.level(), base.survivor()), SurvivorWork.Repair.NONE,
					"a turn with one Nail for a two-Nail repair");
			helper.assertValueEqual(FortifyWear.wear(base.level(), abs), 30, "wood wear repaired with one Nail");
			helper.assertValueEqual(base.count(ArmsItems.NAILS), 1, "Nails spent on a failed repair");
			base.locker().setItem(5, new ItemStack(ArmsItems.NAILS, 1));
			helper.assertValueEqual(SurvivorWork.mechanic(base.level(), base.survivor()), SurvivorWork.Repair.WALL, "turn with two Nails");
			helper.assertValueEqual(FortifyWear.wear(base.level(), abs), 0, "wood wear after the repair");
			helper.assertValueEqual(base.count(ArmsItems.NAILS), 0, "Nails left after the repair");
			helper.assertValueEqual(base.count(Items.OAK_PLANKS), 1, "planks left after the repair");
		});
	}

	@GameTest(padding = PADDING)
	public void mechanicNeedsScrapForObsidian(GameTestHelper helper) {
		atBase(helper, SurvivorRole.MECHANIC, List.of(new ItemStack(Items.COBBLESTONE, 16)), base -> {
			BlockPos wall = new BlockPos(3, 1, 5);
			helper.setBlock(wall, Blocks.OBSIDIAN);
			BlockPos abs = helper.absolutePos(wall);
			FortifyWear.addWear(base.level(), abs, helper.getBlockState(wall), 30);
			helper.assertValueEqual(SurvivorWork.mechanic(base.level(), base.survivor()), SurvivorWork.Repair.NONE,
					"a turn with only cobblestone for obsidian");
			helper.assertValueEqual(base.count(Items.COBBLESTONE), 16, "cobblestone spent on obsidian");
			base.locker().setItem(5, new ItemStack(SalvageItems.SCRAP_METAL, 1));
			helper.assertValueEqual(SurvivorWork.mechanic(base.level(), base.survivor()), SurvivorWork.Repair.WALL, "turn with scrap");
			helper.assertValueEqual(FortifyWear.wear(base.level(), abs), 0, "obsidian wear after the repair");
			helper.assertValueEqual(base.count(SalvageItems.SCRAP_METAL), 0, "scrap left after the repair");
		});
	}

	@GameTest
	public void scavengerKeepsAntibioticsAtTheKeepChance(GameTestHelper helper) {
		RandomSource random = RandomSource.create(42L);
		for (int i = 0; i < 100; i++) {
			helper.assertTrue(!SurvivorWork.keepAntibiotics(0f, random), "antibiotics kept at chance 0");
			helper.assertTrue(SurvivorWork.keepAntibiotics(1f, random), "antibiotics dropped at chance 1");
		}
		float keep = SurvivorBalance.get().scavenger().antibioticsKeep();
		helper.assertValueEqual(keep, 0.25f, "shipped antibiotics_keep");
		int kept = 0;
		int trials = 4000;
		for (int i = 0; i < trials; i++) {
			kept += SurvivorWork.keepAntibiotics(keep, random) ? 1 : 0;
		}
		double share = kept / (double) trials;
		helper.assertTrue(share > 0.21 && share < 0.29, "kept share " + share + " at chance " + keep);
		helper.succeed();
	}
}
