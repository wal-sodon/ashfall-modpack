package xyz.toesinthesand.ashfall.director.claims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * What Ashfall needs from a land-claim mod: a lookup, chunk claiming for Bunker Cores and safe zones, and
 * the party a player belongs to. One implementation per claims mod (today only Open Parties and Claims),
 * plus {@link #NONE}, so the rest of the code never names a claims mod's types and the pack can swap the
 * mod for simple custom claims if a beta OPAC ever breaks (plan risk section).
 */
public interface ClaimBackend {
	/** No claims mod: nothing is claimed, claiming does nothing, nobody is in a party. */
	ClaimBackend NONE = new ClaimBackend() {
		@Override
		public boolean available() {
			return false;
		}

		@Override
		public boolean isClaimed(ServerLevel level, BlockPos pos) {
			return false;
		}

		@Override
		public boolean claim(ServerLevel level, @Nullable UUID owner, int chunkX, int chunkZ, boolean open) {
			return false;
		}

		@Override
		public void unclaim(ServerLevel level, @Nullable UUID owner, int chunkX, int chunkZ) {
		}

		@Override
		public boolean adoptServerClaim(ServerLevel level, int chunkX, int chunkZ, boolean open) {
			return false;
		}

		@Override
		public @Nullable UUID partyId(MinecraftServer server, UUID player) {
			return null;
		}
	};

	/** Whether a real claims mod answers; false means callers use their own fallback regions. */
	boolean available();

	/** Whether {@code pos} lies in any player, party or server claim. */
	boolean isClaimed(ServerLevel level, BlockPos pos);

	/**
	 * Claims one chunk for {@code owner}, or as a server claim when {@code owner} is null. Never takes a chunk
	 * someone else already claimed. Returns true only when this call created the claim, so the caller knows
	 * which chunks are its own to release later. {@code open} (server claims only) picks the open safe-zone settings,
	 * where every player has full access.
	 */
	boolean claim(ServerLevel level, @Nullable UUID owner, int chunkX, int chunkZ, boolean open);

	/**
	 * Moves a server claim Ashfall made for a safe zone onto the current safe-zone claim settings (the open ones when
	 * {@code open}), so worlds whose safe zones were claimed before those settings existed, or whose zone became
	 * open since, get them too. Only called for chunks the haven state records as ours; returns true when the claim
	 * changed.
	 */
	boolean adoptServerClaim(ServerLevel level, int chunkX, int chunkZ, boolean open);

	/** Releases one chunk, but only while it is still claimed by {@code owner} (null = the server). */
	void unclaim(ServerLevel level, @Nullable UUID owner, int chunkX, int chunkZ);

	/** The id of the party {@code player} is in, or null when they are in none. */
	@Nullable UUID partyId(MinecraftServer server, UUID player);
}
