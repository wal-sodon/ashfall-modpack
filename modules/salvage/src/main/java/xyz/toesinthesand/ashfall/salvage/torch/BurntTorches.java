package xyz.toesinthesand.ashfall.salvage.torch;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Function;

/**
 * The two burnt-out torch blocks ({@link TorchBurnout}). Built like vanilla's torch and wall torch minus the light:
 * no collision, instant to break, wooden, popped by pistons. The wall form shares the standing one's name and loot
 * table, as vanilla's wall torch does. Neither has an item: breaking one gives a stick back (loot table
 * {@code ashfall:blocks/burnt_torch}), the charred stick a new torch is crafted from.
 */
public final class BurntTorches {
	public static final BurntTorchBlock BURNT_TORCH = register("burnt_torch", BurntTorchBlock::new, properties());
	public static final BurntWallTorchBlock BURNT_WALL_TORCH = register("burnt_wall_torch", BurntWallTorchBlock::new, properties()
			.overrideLootTable(BURNT_TORCH.getLootTable())
			.overrideDescription(BURNT_TORCH.getDescriptionId()));

	private BurntTorches() {
	}

	private static BlockBehaviour.Properties properties() {
		return BlockBehaviour.Properties.of().noCollision().instabreak().sound(SoundType.WOOD).pushReaction(PushReaction.POPPED);
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static <B extends Block> B register(String path, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(path));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	/** Forces class loading, and so registration. */
	static void init() {
		BURNT_TORCH.getClass();
	}
}
