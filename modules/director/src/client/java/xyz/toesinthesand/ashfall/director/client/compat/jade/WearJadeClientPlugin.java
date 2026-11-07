package xyz.toesinthesand.ashfall.director.client.compat.jade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import xyz.toesinthesand.ashfall.director.compat.jade.WearJadePlugin;

/** Jade's client half of the condition line: "Worn 40%" on a worn fortified block (see {@link WearJadePlugin}). */
public final class WearJadeClientPlugin implements IWailaPlugin {
	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerBlockComponent(Provider.INSTANCE, Block.class);
	}

	enum Provider implements IBlockComponentProvider {
		INSTANCE;

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			int worn = accessor.getServerData().getIntOr(WearJadePlugin.KEY, 0);
			if (worn > 0) {
				tooltip.add(Component.translatable("jade.ashfall.wear", worn));
			}
		}

		@Override
		public Identifier getUid() {
			return WearJadePlugin.UID;
		}
	}
}
