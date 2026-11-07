package xyz.toesinthesand.ashfall.salvage.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Ashfall bookkeeping on a loot container block entity, kept after vanilla clears the container's
 * own loot table on first open, so the container can be refilled (and rerolled) later.
 *
 * @param table      loot table to reroll on refill
 * @param profile    building profile that placed it (its refill rules apply), if any
 * @param zone       admin loot zone that owns it (overrides the profile's rules), if any
 * @param lastOpened game time the container was last opened by a player; -1 = never
 * @param refills    how many times it has refilled, which also varies the reroll seed
 */
public record LootState(Identifier table, Optional<Identifier> profile, Optional<String> zone,
						long lastOpened, int refills) {
	public static final Codec<LootState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("table").forGetter(LootState::table),
			Identifier.CODEC.optionalFieldOf("profile").forGetter(LootState::profile),
			Codec.STRING.optionalFieldOf("zone").forGetter(LootState::zone),
			Codec.LONG.optionalFieldOf("last_opened", -1L).forGetter(LootState::lastOpened),
			Codec.INT.optionalFieldOf("refills", 0).forGetter(LootState::refills)
	).apply(i, LootState::new));

	public LootState opened(long gameTime) {
		return new LootState(table, profile, zone, gameTime, refills);
	}

	public LootState refilled() {
		return new LootState(table, profile, zone, -1, refills + 1);
	}
}
