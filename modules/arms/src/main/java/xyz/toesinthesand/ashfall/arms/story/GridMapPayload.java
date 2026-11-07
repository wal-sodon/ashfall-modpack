package xyz.toesinthesand.ashfall.arms.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;
import xyz.toesinthesand.ashfall.salvage.city.ZoneShape;

import java.util.List;

/**
 * S2C {@code ashfall:grid_map}: the zone shapes and map markers, sent on join and after {@code /reload}, so
 * the client's Grid Map is drawn from the server's data (a modpack that moves a district moves it on the map
 * too) rather than from a copy shipped in client assets that could drift.
 */
public record GridMapPayload(List<MapZone> zones, List<MapMarker> markers) implements CustomPacketPayload {
	public static final Type<GridMapPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "grid_map"));
	public static final Codec<GridMapPayload> DATA_CODEC = RecordCodecBuilder.create(i -> i.group(
			MapZone.CODEC.listOf().fieldOf("zones").forGetter(GridMapPayload::zones),
			MapMarker.CODEC.listOf().fieldOf("markers").forGetter(GridMapPayload::markers)
	).apply(i, GridMapPayload::new));
	public static final StreamCodec<ByteBuf, GridMapPayload> CODEC = ByteBufCodecs.fromCodec(DATA_CODEC);

	public GridMapPayload {
		zones = List.copyOf(zones);
		markers = List.copyOf(markers);
	}

	/** The payload for the currently loaded zones and markers. */
	public static GridMapPayload current() {
		return new GridMapPayload(ZoneMap.current().zones().stream().map(MapZone::of).toList(), MapMarkers.all());
	}

	@Override
	public Type<GridMapPayload> type() {
		return TYPE;
	}

	/** The part of a {@link Zone} the map needs: what it is, where, and which zone wins an overlap. */
	public record MapZone(String id, String kind, int districtIndex, int priority, ZoneShape shape) {
		public static final Codec<MapZone> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(MapZone::id),
				Codec.STRING.fieldOf("kind").forGetter(MapZone::kind),
				Codec.INT.fieldOf("district_index").forGetter(MapZone::districtIndex),
				Codec.INT.fieldOf("priority").forGetter(MapZone::priority),
				ZoneShape.CODEC.fieldOf("shape").forGetter(MapZone::shape)
		).apply(i, MapZone::new));

		public static MapZone of(Zone zone) {
			return new MapZone(zone.id(), zone.kind(), zone.districtIndex(), zone.priority(), zone.shape());
		}

		public boolean isDistrict() {
			return districtIndex > 0;
		}
	}
}
