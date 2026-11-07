package xyz.toesinthesand.ashfall.director.haven;

import net.minecraft.server.MinecraftServer;

/**
 * Where the siren reads the horde schedule from: our own schedule ({@code ScheduledHordeSource}, plan M8), which
 * replaced the Undead Nights reader the MVP used. Kept as an interface so another scheduler can be swapped in and
 * gametests can pass a fake.
 */
@FunctionalInterface
public interface HordeSource {
	HordeSource NONE = server -> HordeSnapshot.NONE;

	HordeSnapshot read(MinecraftServer server);
}
