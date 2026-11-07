package xyz.toesinthesand.ashfall.director.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import xyz.toesinthesand.ashfall.director.fortify.FortifyWear;
import xyz.toesinthesand.ashfall.director.fortify.Fortified;

/**
 * Jade's server half of the fortified-block condition line: puts a worn block's wear percentage in Jade's server
 * data ({@link #KEY}). The tooltip line is the client half ({@code WearJadeClientPlugin}). A {@code "jade"}
 * entrypoint, so it only loads when Jade is installed; Jade is compile-only.
 */
public final class WearJadePlugin implements IWailaPlugin {
	public static final Identifier UID = Identifier.fromNamespaceAndPath("ashfall", "block_wear");
	/** Wear as a whole percentage of the block's points; absent when unworn. */
	public static final String KEY = "ashfall_wear";

	@Override
	public void register(IWailaCommonRegistration registration) {
		registration.registerBlockDataProvider(Provider.INSTANCE, Block.class);
	}

	enum Provider implements IServerDataProvider<BlockAccessor> {
		INSTANCE;

		@Override
		public void appendServerData(CompoundTag data, BlockAccessor accessor) {
			if (accessor.getLevel() instanceof ServerLevel level) {
				double condition = FortifyWear.condition(level, accessor.getPosition());
				if (condition < 1.0) {
					data.putInt(KEY, (int) Math.round((1.0 - condition) * 100));
				}
			}
		}

		@Override
		public boolean shouldRequestData(BlockAccessor accessor) {
			return accessor.getBlockState().is(Fortified.FORTIFIED);
		}

		@Override
		public Identifier getUid() {
			return UID;
		}
	}
}
