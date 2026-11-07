package xyz.toesinthesand.ashfall.salvage.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;

/**
 * One loot zone as the admin GUI edits it: the plain fields of the form, in both directions.
 * <p>
 * The client sends what the admin typed (ids as text) and the server parses and validates it, so a modified
 * client cannot smuggle in anything the command would refuse, and every error message comes from one place.
 *
 * @param dimension dimension id; empty on a create means "the admin's current dimension"
 */
public record LootZoneForm(String id, String dimension, int x, int y, int z, int radius, String containerType,
						   String table, boolean regen, long delayTicks, long quietTicks, int noPlayerRadius) {
	public static final Codec<LootZoneForm> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(LootZoneForm::id),
			Codec.STRING.optionalFieldOf("dimension", "").forGetter(LootZoneForm::dimension),
			Codec.INT.fieldOf("x").forGetter(LootZoneForm::x),
			Codec.INT.fieldOf("y").forGetter(LootZoneForm::y),
			Codec.INT.fieldOf("z").forGetter(LootZoneForm::z),
			Codec.INT.fieldOf("radius").forGetter(LootZoneForm::radius),
			Codec.STRING.fieldOf("container_type").forGetter(LootZoneForm::containerType),
			Codec.STRING.fieldOf("table").forGetter(LootZoneForm::table),
			Codec.BOOL.fieldOf("regen").forGetter(LootZoneForm::regen),
			Codec.LONG.fieldOf("delay_ticks").forGetter(LootZoneForm::delayTicks),
			Codec.LONG.fieldOf("quiet_ticks").forGetter(LootZoneForm::quietTicks),
			Codec.INT.fieldOf("no_player_radius").forGetter(LootZoneForm::noPlayerRadius)
	).apply(i, LootZoneForm::new));

	/** An empty form: the client fills the centre with the player's position. */
	public static final LootZoneForm BLANK = new LootZoneForm("", "", 0, 0, 0, 16, LootZone.ANY, "",
			BuildingProfile.Refill.DEFAULT.enabled(), BuildingProfile.Refill.DEFAULT.delayTicks(),
			BuildingProfile.Refill.DEFAULT.quietTicks(), BuildingProfile.Refill.DEFAULT.noPlayerRadius());

	public static LootZoneForm of(LootZone zone) {
		BuildingProfile.Refill r = zone.refill();
		return new LootZoneForm(zone.id(), zone.dimension().identifier().toString(), zone.center().getX(), zone.center().getY(),
				zone.center().getZ(), zone.radius(), zone.containerTypeName(), zone.table().toString(), r.enabled(),
				r.delayTicks(), r.quietTicks(), r.noPlayerRadius());
	}
}
