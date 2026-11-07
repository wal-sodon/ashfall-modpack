package xyz.toesinthesand.ashfall.arms.grid;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;

/**
 * Grid events other systems listen to instead of polling {@link GridState}: the sync payload, the Reclaimed
 * effects, and later the Grid Map, advancements and the Black Start all react to the same change.
 */
public final class GridEvents {
	@FunctionalInterface
	public interface DistrictChanged {
		/** {@code districtId} moved from {@code from} to {@code to}; fired on the server thread after the save data changed. */
		void onChange(ServerLevel level, String districtId, DistrictState from, DistrictState to);
	}

	/** Fired once per real state change (never for a no-op set). */
	public static final Event<DistrictChanged> DISTRICT_CHANGED = EventFactory.createArrayBacked(DistrictChanged.class,
			listeners -> (level, districtId, from, to) -> {
				for (DistrictChanged listener : listeners) {
					listener.onChange(level, districtId, from, to);
				}
			});

	private GridEvents() {
	}
}
