package xyz.toesinthesand.ashfall.arms.client.grid;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import xyz.toesinthesand.ashfall.arms.client.mission.MissionClientState;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;
import xyz.toesinthesand.ashfall.arms.mission.MissionPos;
import xyz.toesinthesand.ashfall.arms.radio.RadioJobPayload;
import xyz.toesinthesand.ashfall.arms.client.mission.RadioJobClientState;
import xyz.toesinthesand.ashfall.arms.mission.Objective;
import xyz.toesinthesand.ashfall.arms.story.GridMapPayload.MapZone;
import xyz.toesinthesand.ashfall.arms.story.MapMarker;
import xyz.toesinthesand.ashfall.arms.story.RadioProgress;
import xyz.toesinthesand.ashfall.arms.travel.TravelNodes;
import xyz.toesinthesand.ashfall.director.graffiti.GraffitiPayload;
import xyz.toesinthesand.ashfall.director.graffiti.GraffitiSymbol;
import xyz.toesinthesand.ashfall.director.graffiti.MarkState;
import xyz.toesinthesand.ashfall.salvage.city.ZoneShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Grid Map: a flat plan of Asheville drawn from the synced zone shapes, coloured by each district's grid
 * state, with the safehouse, the four substations, the Wreck hull, the relays this player has tuned into, the
 * player's own (or party's) Bunker Core, the current objective's target (a blinking frame), the Graffiti marks of
 * the player and their party, and the player's own position.
 * <p>
 * It is a schematic, not a world render: the map rasterises the zone shapes into horizontal runs once per resize
 * (a few hundred fills), so it costs nothing to keep open and works anywhere, including far from the city. Xaero's
 * maps already show terrain; this screen shows what only Ashfall knows.
 */
public class GridMapScreen extends Screen {
	/** Screen pixels per raster cell: fine enough for round discs, coarse enough to stay a few hundred fills. */
	static final int CELL = 2;
	/** World margin around the scored area, in blocks. */
	static final int WORLD_MARGIN = 150;
	static final int TOP = 26, BOTTOM = 30, SIDE = 16;
	static final int BUNKER_COLOUR = 0xFFF0A040, OBJECTIVE_COLOUR = 0xFFFFE14D, RADIO_JOB_COLOUR = 0xFF4FC3F7;
	/** 5x5 Graffiti glyphs, one row per int, the high bit (0b10000) leftmost: tick, bang, crate, house. */
	private static final int[] GLYPH_CLEARED = {0b00001, 0b00010, 0b10100, 0b01000, 0b00000};
	private static final int[] GLYPH_DANGER = {0b00100, 0b00100, 0b00100, 0b00000, 0b00100};
	private static final int[] GLYPH_LOOT = {0b11111, 0b10101, 0b11111, 0b10001, 0b11111};
	private static final int[] GLYPH_SAFEHOUSE = {0b00100, 0b01110, 0b11111, 0b01010, 0b01110};

	/** One horizontal stretch of map cells owned by one zone. */
	record Run(int y, int x0, int x1, int zone) {
	}

	private final List<Run> runs = new ArrayList<>();
	private List<MapZone> zones = List.of();
	private int mapX, mapY, mapW, mapH;
	private double minWX, minWZ, scale;

	public GridMapScreen() {
		super(Component.translatable("screen.ashfall.grid_map"));
	}

	@Override
	protected void init() {
		zones = GridClientState.zones();
		layout();
		rasterise();
	}

	/** World bounds of the scored districts, the start zone and the ridge, fitted into the screen keeping aspect. */
	private void layout() {
		double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
		for (MapZone zone : zones) {
			if (!zone.isDistrict() && !"start".equals(zone.kind()) && !"ridge".equals(zone.kind())) {
				continue;
			}
			double[] b = bounds(zone.shape());
			if (b == null) {
				continue;
			}
			x0 = Math.min(x0, b[0]);
			z0 = Math.min(z0, b[1]);
			x1 = Math.max(x1, b[2]);
			z1 = Math.max(z1, b[3]);
		}
		if (x0 > x1) {
			// No zone data yet: a square around Downtown so the screen still draws something sane.
			x0 = z0 = -1600;
			x1 = z1 = 1600;
		}
		x0 -= WORLD_MARGIN;
		z0 -= WORLD_MARGIN;
		x1 += WORLD_MARGIN;
		z1 += WORLD_MARGIN;
		int availW = Math.max(40, width - 2 * SIDE), availH = Math.max(40, height - TOP - BOTTOM);
		scale = Math.min(availW / (x1 - x0), availH / (z1 - z0));
		mapW = (int) ((x1 - x0) * scale) / CELL * CELL;
		mapH = (int) ((z1 - z0) * scale) / CELL * CELL;
		mapX = (width - mapW) / 2;
		mapY = TOP + (availH - mapH) / 2;
		minWX = x0;
		minWZ = z0;
	}

	private static double[] bounds(ZoneShape shape) {
		return switch (shape) {
			case ZoneShape.Disc d -> new double[] {d.x() - d.r(), d.z() - d.r(), d.x() + d.r(), d.z() + d.r()};
			case ZoneShape.Ring r -> new double[] {r.x() - r.r(), r.z() - r.r(), r.x() + r.r(), r.z() + r.r()};
			case ZoneShape.Box b -> new double[] {b.minX(), b.minZ(), b.maxX(), b.maxZ()};
			case ZoneShape.All a -> null;
		};
	}

	/** Splits the map into horizontal runs of cells owned by the same zone (first match in priority order). */
	private void rasterise() {
		runs.clear();
		for (int py = 0; py < mapH; py += CELL) {
			int wz = (int) Math.floor(minWZ + (py + CELL / 2.0) / scale);
			int start = 0, current = -2;
			for (int px = 0; px < mapW; px += CELL) {
				int wx = (int) Math.floor(minWX + (px + CELL / 2.0) / scale);
				int zone = zoneAt(wx, wz);
				if (zone != current) {
					if (current != -2) {
						runs.add(new Run(py, start, px, current));
					}
					current = zone;
					start = px;
				}
			}
			runs.add(new Run(py, start, mapW, current));
		}
	}

	private int zoneAt(int x, int z) {
		for (int i = 0; i < zones.size(); i++) {
			if (zones.get(i).shape().contains(x, z)) {
				return i;
			}
		}
		return -1;
	}

	private int screenX(double worldX) {
		return mapX + (int) Math.round((worldX - minWX) * scale);
	}

	private int screenY(double worldZ) {
		return mapY + (int) Math.round((worldZ - minWZ) * scale);
	}

	private static int fill(MapZone zone) {
		if (zone.isDistrict()) {
			return GridColours.fill(GridClientState.state(zone.id()));
		}
		return switch (zone.kind()) {
			case "start" -> 0xFF26402A;
			case "ridge" -> 0xFF3A2B45;
			case "filler" -> 0xFF202328;
			case "outskirts" -> 0xFF19191C;
			default -> 0xFF111113;
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		Minecraft mc = Minecraft.getInstance();
		long now = mc.level == null ? 0 : mc.level.getGameTime();

		graphics.centeredText(font, Component.translatable("screen.ashfall.grid_map.title", GridClientState.percent())
				.withStyle(ChatFormatting.GOLD), width / 2, 9, 0xFFFFFFFF);
		graphics.fill(mapX - 2, mapY - 2, mapX + mapW + 2, mapY + mapH + 2, 0xFF000000);
		graphics.outline(mapX - 2, mapY - 2, mapW + 4, mapH + 4, 0xFF6A6458);
		for (Run run : runs) {
			int colour = run.zone() < 0 ? 0xFF111113 : fill(zones.get(run.zone()));
			graphics.fill(mapX + run.x0(), mapY + run.y(), mapX + run.x1(), mapY + run.y() + CELL, colour);
		}

		Component hovered = null;
		for (MapZone zone : zones) {
			if (zone.isDistrict() && zone.shape() instanceof ZoneShape.Disc disc) {
				int cx = screenX(disc.x()), cy = screenY(disc.z());
				DistrictState state = GridClientState.state(zone.id());
				Component name = Component.translatable("zone.ashfall." + zone.id());
				graphics.centeredText(font, name, cx, cy - font.lineHeight, 0xFFF2EEE4);
				graphics.centeredText(font, stateName(state), cx, cy + 1, GridColours.pip(state, now));
				int r = (int) Math.round(disc.r() * scale);
				if ((long) (mouseX - cx) * (mouseX - cx) + (long) (mouseY - cy) * (mouseY - cy) < (long) r * r) {
					hovered = name.copy().append(": ").append(stateName(state));
				}
			} else if ("ridge".equals(zone.kind()) && zone.shape() instanceof ZoneShape.Box box) {
				graphics.centeredText(font, Component.translatable("zone.ashfall." + zone.id()),
						screenX((box.minX() + box.maxX()) / 2.0), screenY(box.minZ()) + 4, 0xFFB9A6CC);
			}
		}

		if (mc.player != null) {
			for (long packed : RadioProgress.get(mc.player).relays()) {
				BlockPos pos = BlockPos.of(packed);
				int x = screenX(pos.getX()), y = screenY(pos.getZ());
				graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFF5FD7E8);
			}
		}

		for (MapMarker marker : GridClientState.markers()) {
			int x = screenX(marker.x()), y = screenY(marker.z());
			switch (marker.kind()) {
				case MapMarker.SUBSTATION -> {
					DistrictState state = GridClientState.state(marker.district().orElse(""));
					graphics.fill(x - 4, y - 4, x + 5, y + 5, 0xFF000000);
					graphics.fill(x - 3, y - 3, x + 4, y + 4, GridColours.pip(state, now));
					graphics.fill(x - 1, y - 3, x + 2, y + 4, 0xFF000000);
					// a reclaimed district's trader (survivor/Traders) stands at its substation: a gold pip on the corner
					boolean trader = state == DistrictState.RECLAIMED;
					if (trader) {
						graphics.fill(x + 3, y - 6, x + 7, y - 2, 0xFF000000);
						graphics.fill(x + 4, y - 5, x + 6, y - 3, 0xFFE8C547);
					}
					if (near(mouseX, mouseY, x, y)) {
						hovered = Component.translatable("screen.ashfall.grid_map.substation",
								Component.translatable("zone.ashfall." + marker.district().orElse(""))).append(": ").append(stateName(state));
						if (trader) {
							hovered = hovered.copy().append(". ").append(Component.translatable("screen.ashfall.grid_map.trader"));
						}
					}
				}
				case MapMarker.SAFEHOUSE -> {
					graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
					graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFF8FE08A);
					graphics.text(font, Component.translatable("screen.ashfall.grid_map.safehouse"), x + 6, y - 4, 0xFF8FE08A, true);
				}
				case MapMarker.HULL -> {
					graphics.fill(x - 4, y - 3, x + 5, y + 4, 0xFF000000);
					graphics.fill(x - 3, y - 2, x + 4, y + 3, 0xFFB57EDC);
					graphics.text(font, Component.translatable("screen.ashfall.grid_map.hull"), x + 7, y - 4, 0xFFB57EDC, true);
				}
				default -> graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
			}
		}

		// The player's Bunker Core ("how do I get back to my base?") and the current objective's target.
		MissionClientState.bunker().ifPresent(b -> {
			int x = screenX(b.x()), y = screenY(b.z());
			graphics.fill(x - 4, y - 4, x + 5, y + 5, 0xFF000000);
			graphics.fill(x - 3, y - 3, x + 4, y + 4, BUNKER_COLOUR);
			graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFF000000);
			graphics.text(font, Component.translatable("screen.ashfall.grid_map.bunker"), x + 7, y - 4, BUNKER_COLOUR, true);
		});
		Optional<MissionPos> target = MissionClientState.objectiveForMap().flatMap(Objective::target);
		if (target.isPresent()) {
			int x = Math.clamp(screenX(target.get().x()), mapX, mapX + mapW), y = Math.clamp(screenY(target.get().z()), mapY, mapY + mapH);
			int colour = (now / 10) % 2 == 0 ? OBJECTIVE_COLOUR : 0xFFFFFFFF;
			graphics.outline(x - 6, y - 6, 13, 13, 0xFF000000);
			graphics.outline(x - 5, y - 5, 11, 11, colour);
			if (near(mouseX, mouseY, x, y)) {
				hovered = Component.translatable("screen.ashfall.grid_map.objective").append(": ")
						.append(MissionClientState.objectiveForMap().map(Objective::text).orElse(Component.empty()));
			}
		}

		// The radio job under way: an aqua diamond, so it never reads as the objective's frame.
		Optional<RadioJobPayload.View> job = RadioJobClientState.job();
		if (job.isPresent()) {
			int x = Math.clamp(screenX(job.get().x()), mapX, mapX + mapW), y = Math.clamp(screenY(job.get().z()), mapY, mapY + mapH);
			for (int r = 0; r <= 5; r++) {
				graphics.fill(x - (5 - r), y - r, x + (5 - r) + 1, y - r + 1, 0xFF000000);
				graphics.fill(x - (5 - r), y + r, x + (5 - r) + 1, y + r + 1, 0xFF000000);
			}
			for (int r = 0; r <= 3; r++) {
				graphics.fill(x - (3 - r), y - r, x + (3 - r) + 1, y - r + 1, RADIO_JOB_COLOUR);
				graphics.fill(x - (3 - r), y + r, x + (3 - r) + 1, y + r + 1, RADIO_JOB_COLOUR);
			}
			if (near(mouseX, mouseY, x, y)) {
				hovered = Component.translatable("screen.ashfall.grid_map.radio_job").append(": ").append(RadioJobClientState.text(job.get()));
			}
		}

		// Graffiti marks: small sprayed symbols, under the player's own marker.
		for (GraffitiPayload.Mark mark : GraffitiClientState.marks()) {
			int x = screenX(mark.x()), y = screenY(mark.z());
			if (x < mapX || x > mapX + mapW || y < mapY || y > mapY + mapH) {
				continue;
			}
			extractGlyph(graphics, x, y, mark);
			if (near(mouseX, mouseY, x, y)) {
				Component text = Component.translatable("screen.ashfall.grid_map.graffiti", Component.translatable(mark.symbol().translationKey()));
				hovered = mark.state() == MarkState.FRESH ? text
						: text.copy().append(" (").append(Component.translatable("screen.ashfall.grid_map.graffiti." + mark.state().getSerializedName())).append(")");
			}
		}

		if (mc.player != null) {
			int x = Math.clamp(screenX(mc.player.getX()), mapX, mapX + mapW), y = Math.clamp(screenY(mc.player.getZ()), mapY, mapY + mapH);
			graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
			graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFFFFF);
			graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFFE53935);
		}

		extractLegend(graphics, now);
		Optional<String> stop = travelStop(mouseX, mouseY);
		if (stop.isPresent()) {
			Component hint = Component.translatable("screen.ashfall.grid_map.click_travel").withStyle(ChatFormatting.AQUA);
			hovered = hovered == null ? hint : hovered.copy().append(" ").append(hint);
		}
		if (hovered != null) {
			graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
		}
	}

	private void extractLegend(GuiGraphicsExtractor graphics, long now) {
		DistrictState[] shown = {DistrictState.DARK, DistrictState.REPAIRED, DistrictState.ENERGIZED, DistrictState.SIEGE, DistrictState.RECLAIMED};
		int total = 0;
		for (DistrictState state : shown) {
			total += 10 + font.width(stateName(state)) + 10;
		}
		Component relays = Component.translatable("screen.ashfall.grid_map.relays");
		total += 6 + font.width(relays);
		int x = (width - total) / 2, y = height - BOTTOM + 10;
		for (DistrictState state : shown) {
			graphics.fill(x, y, x + 7, y + 7, 0xFF000000);
			graphics.fill(x + 1, y + 1, x + 6, y + 6, GridColours.pip(state, now));
			Component name = stateName(state);
			graphics.text(font, name, x + 10, y, 0xFFD8D2C4, true);
			x += 10 + font.width(name) + 10;
		}
		graphics.fill(x, y + 2, x + 3, y + 5, 0xFF5FD7E8);
		graphics.text(font, relays, x + 6, y, 0xFFD8D2C4, true);
	}

	/**
	 * The transit stop under the cursor: the safehouse, the player's Bunker Core, or a reclaimed substation. The
	 * server decides whether the trip is allowed (are you at a stop, is it a horde night, can you pay).
	 */
	private Optional<String> travelStop(double mouseX, double mouseY) {
		int mx = (int) mouseX, my = (int) mouseY;
		Optional<MissionPos> bunker = MissionClientState.bunker();
		if (bunker.isPresent() && near(mx, my, screenX(bunker.get().x()), screenY(bunker.get().z()))) {
			return Optional.of(TravelNodes.BUNKER);
		}
		for (MapMarker marker : GridClientState.markers()) {
			if (!near(mx, my, screenX(marker.x()), screenY(marker.z()))) {
				continue;
			}
			if (marker.kind().equals(MapMarker.SAFEHOUSE)) {
				return Optional.of(TravelNodes.SAFEHOUSE);
			}
			if (marker.kind().equals(MapMarker.SUBSTATION) && marker.district().isPresent()
					&& GridClientState.state(marker.district().get()) == DistrictState.RECLAIMED) {
				return Optional.of(TravelNodes.DISTRICT_PREFIX + marker.district().get());
			}
		}
		return Optional.empty();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Optional<String> stop = travelStop(event.x(), event.y());
		Minecraft mc = Minecraft.getInstance();
		if (stop.isPresent() && mc.player != null) {
			mc.player.connection.sendCommand("ashfall travel \"" + stop.get() + "\"");
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** A Graffiti mark: its 5x5 glyph on a black square, in the paint colour (orange when warned, grey when stale). */
	private static void extractGlyph(GuiGraphicsExtractor graphics, int x, int y, GraffitiPayload.Mark mark) {
		int[] glyph = switch (mark.symbol()) {
			case CLEARED -> GLYPH_CLEARED;
			case DANGER -> GLYPH_DANGER;
			case LOOT -> GLYPH_LOOT;
			default -> GLYPH_SAFEHOUSE;
		};
		int colour = switch (mark.state()) {
			case WARN -> 0xFFF28A1E;
			case STALE -> 0xFF8A8A86;
			case FRESH -> graffitiColour(mark.symbol());
		};
		graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
		for (int row = 0; row < 5; row++) {
			for (int col = 0; col < 5; col++) {
				if ((glyph[row] & (0b10000 >> col)) != 0) {
					graphics.fill(x - 2 + col, y - 2 + row, x - 1 + col, y - 1 + row, colour);
				}
			}
		}
	}

	/** The paint colours of the wall textures: green cleared, red danger, yellow loot, blue safehouse. */
	private static int graffitiColour(GraffitiSymbol symbol) {
		return switch (symbol) {
			case CLEARED -> 0xFF5CBF4E;
			case DANGER -> 0xFFD93A2F;
			case LOOT -> 0xFFF2C230;
			case SAFEHOUSE -> 0xFF3F8FE0;
			default -> 0xFFD9D6CC;
		};
	}

	private static boolean near(int mouseX, int mouseY, int x, int y) {
		return Math.abs(mouseX - x) <= 5 && Math.abs(mouseY - y) <= 5;
	}

	private static Component stateName(DistrictState state) {
		return Component.translatable("district_state.ashfall." + state.id());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (GridClient.MAP_KEY.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
