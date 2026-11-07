package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import team.reborn.energy.api.base.SimpleEnergyItem;

/**
 * Test-only energy items, so the charger tests don't depend on how the real Energy Cell and cell packs
 * end up implemented: chargers only see {@code EnergyStorage.ITEM}, and so do these.
 * {@code test_relay_cell} is in the {@code ashfall:relay_charge_only} tag (test datapack), standing in for
 * a drained Energy Cell.
 */
public class ChargerTestItems implements ModInitializer {
	public static final long CAPACITY = 240;
	public static Item TEST_CELL;
	public static Item TEST_RELAY_CELL;

	@Override
	public void onInitialize() {
		TEST_CELL = register("test_cell");
		TEST_RELAY_CELL = register("test_relay_cell");
	}

	private static Item register(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ashfall_arms_test", path));
		return Registry.register(BuiltInRegistries.ITEM, key, new TestEnergyItem(new Item.Properties().stacksTo(1).setId(key)));
	}

	/** A plain 240-unit battery through Team Reborn Energy's simple item API. */
	static final class TestEnergyItem extends Item implements SimpleEnergyItem {
		TestEnergyItem(Properties properties) {
			super(properties);
		}

		@Override
		public long getEnergyCapacity(ItemStack stack) {
			return CAPACITY;
		}

		@Override
		public long getEnergyMaxInput(ItemStack stack) {
			return CAPACITY;
		}

		@Override
		public long getEnergyMaxOutput(ItemStack stack) {
			return CAPACITY;
		}
	}
}
