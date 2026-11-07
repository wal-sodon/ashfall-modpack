package xyz.toesinthesand.ashfall.salvage.client.loot;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import xyz.toesinthesand.ashfall.salvage.client.admin.SuggestionDropdown;
import xyz.toesinthesand.ashfall.salvage.loot.LootZone;
import xyz.toesinthesand.ashfall.salvage.loot.LootZoneActionPayload;
import xyz.toesinthesand.ashfall.salvage.loot.LootZoneActionPayload.Action;
import xyz.toesinthesand.ashfall.salvage.loot.LootZoneForm;
import xyz.toesinthesand.ashfall.salvage.loot.LootZoneSyncPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The op-only Loot Zone screen: the zone list on the left, the selected zone's form on the right, and buttons for
 * every {@code /lootzone} action.
 * <p>
 * The screen holds no authority. Every button sends a {@link LootZoneActionPayload}; the server validates it,
 * applies it exactly like the command, and answers with the new zone list and a status line. Only number parsing
 * happens here, so a typo is caught before a round trip.
 */
public class LootZoneScreen extends Screen {
	/** Container presets the cycle button steps through; anything else is typed (a block id or #tag). */
	private static final List<String> TYPE_PRESETS = List.of(LootZone.ANY, "minecraft:chest", "minecraft:barrel",
			"minecraft:trapped_chest", "#c:chests", "#c:barrels");
	private static final int ROW = 22, LABEL_W = 70, TOP = 24;

	private List<LootZoneForm> zones;
	private List<String> tables;
	private String selectedId;
	/** Set when we sent an action whose answer should reload the form (save, import, open). */
	private boolean reloadForm = true;
	private String pendingCreate;
	private boolean confirmDelete;
	private Component status = Component.empty();
	private int statusColour = 0xFFD8D2C4;
	private String dimension = "";

	private ZoneList list;
	private EditBox nameBox, xBox, yBox, zBox, radiusBox, typeBox, tableBox, delayBox, quietBox, noPlayerBox;
	private CycleButton<Boolean> regenButton;
	private Button saveButton, scanButton, refillButton, deleteButton, tpButton;
	private SuggestionDropdown tableSuggestions;

	public LootZoneScreen(LootZoneSyncPayload payload) {
		super(Component.translatable("screen.ashfall.loot_zones"));
		this.zones = payload.zones();
		this.tables = payload.tables();
		this.selectedId = zones.isEmpty() ? null : zones.getFirst().id();
		setStatus(payload);
	}

	private static Component t(String key, Object... args) {
		return Component.translatable("screen.ashfall.loot_zones." + key, args);
	}

	@Override
	protected void init() {
		int listW = Math.clamp(width / 4, 100, 160);
		list = addRenderableWidget(new ZoneList(minecraft, listW, height - TOP - 58, TOP));
		list.updateSizeAndPosition(listW, height - TOP - 58, 6, TOP);
		addRenderableWidget(Button.builder(t("new"), b -> selectNew()).bounds(6, height - 30, listW, 20).build());

		int fx = listW + 14 + LABEL_W, fw = Math.max(150, width - fx - 8);
		int y = TOP;
		nameBox = box(fx, y, Math.min(fw, 160), "name", 64);
		y += ROW;
		int third = (fw - 46) / 3;
		xBox = box(fx, y, third - 2, "x", 9);
		yBox = box(fx + third, y, third - 2, "y", 9);
		zBox = box(fx + 2 * third, y, third - 2, "z", 9);
		addRenderableWidget(Button.builder(t("here"), b -> useMyPosition()).bounds(fx + 3 * third, y - 1, 44, 18)
				.tooltip(Tooltip.create(t("here.tooltip"))).build());
		y += ROW;
		radiusBox = box(fx, y, 40, "radius", 4);
		int typeX = fx + 40 + 6 + 30;
		typeBox = box(typeX, y, Math.max(60, fx + fw - typeX - 24), "type", 128);
		typeBox.setTooltip(Tooltip.create(t("type.tooltip")));
		addRenderableWidget(Button.builder(Component.literal(">"), b -> cycleType()).bounds(fx + fw - 20, y - 1, 20, 18)
				.tooltip(Tooltip.create(t("type.cycle"))).build());
		y += ROW;
		tableBox = box(fx, y, fw, "table", 256);
		tableSuggestions = new SuggestionDropdown(tableBox, 6);
		tableSuggestions.setSource(tables);
		tableBox.setResponder(s -> tableSuggestions.update());
		y += ROW;
		regenButton = addRenderableWidget(CycleButton.onOffBuilder(true).create(fx, y - 1, 80, 18, t("regen")));
		int delayX = fx + 80 + 6 + 64;
		delayBox = box(delayX, y, Math.max(50, fx + fw - delayX), "delay", 12);
		delayBox.setTooltip(Tooltip.create(t("ticks.tooltip")));
		y += ROW;
		quietBox = box(fx, y, 70, "quiet", 12);
		quietBox.setTooltip(Tooltip.create(t("quiet.tooltip")));
		int npX = fx + 70 + 6 + 64;
		noPlayerBox = box(npX, y, Math.max(36, Math.min(50, fx + fw - npX)), "no_player", 3);
		noPlayerBox.setTooltip(Tooltip.create(t("no_player.tooltip")));
		y += ROW + 4;

		int bx = listW + 14, bw = (width - bx - 8 - 4 * 4) / 5;
		saveButton = addRenderableWidget(Button.builder(t("save"), b -> save()).bounds(bx, y, bw, 20).build());
		scanButton = addRenderableWidget(Button.builder(t("scan"), b -> send(Action.SCAN))
				.tooltip(Tooltip.create(t("scan.tooltip"))).bounds(bx + (bw + 4), y, bw, 20).build());
		refillButton = addRenderableWidget(Button.builder(t("refill"), b -> send(Action.REFILL))
				.tooltip(Tooltip.create(t("refill.tooltip"))).bounds(bx + 2 * (bw + 4), y, bw, 20).build());
		deleteButton = addRenderableWidget(Button.builder(t("delete"), b -> delete()).bounds(bx + 3 * (bw + 4), y, bw, 20).build());
		tpButton = addRenderableWidget(Button.builder(t("teleport"), b -> send(Action.TELEPORT)).bounds(bx + 4 * (bw + 4), y, bw, 20).build());
		y += 24;
		int bw4 = (width - bx - 8 - 3 * 4) / 4;
		addRenderableWidget(Button.builder(t("export"), b -> send(Action.EXPORT))
				.tooltip(Tooltip.create(t("export.tooltip"))).bounds(bx, y, bw4, 20).build());
		addRenderableWidget(Button.builder(t("import_clipboard"), b -> importClipboard())
				.tooltip(Tooltip.create(t("import_clipboard.tooltip"))).bounds(bx + (bw4 + 4), y, bw4, 20).build());
		addRenderableWidget(Button.builder(t("import_file"), b -> send(Action.IMPORT_FILE))
				.tooltip(Tooltip.create(t("import_file.tooltip"))).bounds(bx + 2 * (bw4 + 4), y, bw4, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(bx + 3 * (bw4 + 4), y, bw4, 20).build());

		list.refresh();
		loadSelection();
		updateButtons();
	}

	private EditBox box(int x, int y, int w, String key, int max) {
		EditBox box = new EditBox(font, x, y, w, 16, t(key));
		box.setMaxLength(max);
		return addRenderableWidget(box);
	}

	// ---- server sync ---------------------------------------------------------------------------

	/** A newer sync arrived while the screen is open. */
	public void accept(LootZoneSyncPayload payload) {
		zones = payload.zones();
		if (!payload.tables().isEmpty()) {
			tables = payload.tables();
			if (tableSuggestions != null) {
				tableSuggestions.setSource(tables);
			}
		}
		if (pendingCreate != null && !payload.error() && zone(pendingCreate) != null) {
			selectedId = pendingCreate;
		}
		pendingCreate = null;
		if (selectedId != null && zone(selectedId) == null) {
			selectedId = null;
			reloadForm = true;
		}
		setStatus(payload);
		if (list != null) {
			list.refresh();
			if (reloadForm && !payload.error()) {
				loadSelection();
			}
			updateButtons();
		}
		reloadForm = false;
	}

	private void setStatus(LootZoneSyncPayload payload) {
		if (!payload.message().isEmpty()) {
			status = Component.literal(payload.message());
			statusColour = payload.error() ? 0xFFFF7A6A : 0xFF8FE08A;
		}
	}

	private void localError(Component message) {
		status = message;
		statusColour = 0xFFFF7A6A;
	}

	private LootZoneForm zone(String id) {
		for (LootZoneForm z : zones) {
			if (z.id().equals(id)) {
				return z;
			}
		}
		return null;
	}

	private void send(Action action) {
		if (selectedId == null && action != Action.EXPORT && action != Action.IMPORT_FILE) {
			return;
		}
		confirmDelete = false;
		reloadForm = action == Action.IMPORT_FILE;
		ClientPlayNetworking.send(LootZoneActionPayload.of(action, selectedId == null ? "" : selectedId));
	}

	// ---- form ----------------------------------------------------------------------------------

	private void select(String id) {
		selectedId = id;
		confirmDelete = false;
		loadSelection();
		updateButtons();
	}

	private void selectNew() {
		selectedId = null;
		confirmDelete = false;
		list.setSelected(null);
		loadSelection();
		updateButtons();
	}

	private void loadSelection() {
		LootZoneForm form = selectedId == null ? null : zone(selectedId);
		if (form == null) {
			form = LootZoneForm.BLANK;
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				var pos = mc.player.blockPosition();
				form = new LootZoneForm("", "", pos.getX(), pos.getY(), pos.getZ(), form.radius(), form.containerType(),
						tables.contains("ashfall:buildings/generic") ? "ashfall:buildings/generic" : "", form.regen(),
						form.delayTicks(), form.quietTicks(), form.noPlayerRadius());
			}
		}
		fill(form);
	}

	private void fill(LootZoneForm form) {
		nameBox.setValue(form.id());
		nameBox.setEditable(selectedId == null);
		dimension = form.dimension();
		xBox.setValue(Integer.toString(form.x()));
		yBox.setValue(Integer.toString(form.y()));
		zBox.setValue(Integer.toString(form.z()));
		radiusBox.setValue(Integer.toString(form.radius()));
		typeBox.setValue(form.containerType());
		tableBox.setValue(form.table());
		tableBox.moveCursorToStart(false);
		regenButton.setValue(form.regen());
		delayBox.setValue(Long.toString(form.delayTicks()));
		quietBox.setValue(Long.toString(form.quietTicks()));
		noPlayerBox.setValue(Integer.toString(form.noPlayerRadius()));
	}

	private void updateButtons() {
		boolean existing = selectedId != null;
		scanButton.active = refillButton.active = deleteButton.active = tpButton.active = existing;
		saveButton.setMessage(existing ? t("save") : t("create"));
		deleteButton.setMessage(confirmDelete ? t("delete.confirm").copy().withStyle(ChatFormatting.RED) : t("delete"));
	}

	private void useMyPosition() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		var pos = mc.player.blockPosition();
		xBox.setValue(Integer.toString(pos.getX()));
		yBox.setValue(Integer.toString(pos.getY()));
		zBox.setValue(Integer.toString(pos.getZ()));
		dimension = mc.level.dimension().identifier().toString();
	}

	private void cycleType() {
		int i = TYPE_PRESETS.indexOf(typeBox.getValue().trim());
		typeBox.setValue(TYPE_PRESETS.get((i + 1) % TYPE_PRESETS.size()));
	}

	/** Reads the form; null (with the status line set) if a number does not parse. */
	private LootZoneForm readForm() {
		try {
			return new LootZoneForm(nameBox.getValue().trim(), dimension,
					parseInt(xBox, "x"), parseInt(yBox, "y"), parseInt(zBox, "z"), parseInt(radiusBox, "radius"),
					typeBox.getValue().trim(), tableBox.getValue().trim(), regenButton.getValue(),
					parseLong(delayBox, "delay"), parseLong(quietBox, "quiet"), parseInt(noPlayerBox, "no_player"));
		} catch (BadNumber e) {
			localError(t("bad_number", t(e.getMessage())));
			return null;
		}
	}

	private static final class BadNumber extends Exception {
		BadNumber(String field) {
			super(field, null, false, false);
		}
	}

	private static int parseInt(EditBox box, String field) throws BadNumber {
		try {
			return Integer.parseInt(box.getValue().trim());
		} catch (NumberFormatException e) {
			throw new BadNumber(field);
		}
	}

	private static long parseLong(EditBox box, String field) throws BadNumber {
		try {
			return Long.parseLong(box.getValue().trim());
		} catch (NumberFormatException e) {
			throw new BadNumber(field);
		}
	}

	private void save() {
		LootZoneForm form = readForm();
		if (form == null) {
			return;
		}
		confirmDelete = false;
		reloadForm = true;
		if (selectedId == null) {
			pendingCreate = form.id();
		}
		ClientPlayNetworking.send(new LootZoneActionPayload(Action.SAVE, selectedId == null ? "" : selectedId, form, ""));
	}

	private void delete() {
		if (!confirmDelete) {
			confirmDelete = true;
			updateButtons();
			return;
		}
		send(Action.DELETE);
	}

	private void importClipboard() {
		String text = Minecraft.getInstance().keyboardHandler.getClipboard();
		if (text == null || text.isBlank()) {
			localError(t("clipboard_empty"));
			return;
		}
		if (text.length() > LootZoneActionPayload.MAX_TEXT) {
			localError(t("clipboard_too_long"));
			return;
		}
		reloadForm = true;
		ClientPlayNetworking.send(new LootZoneActionPayload(Action.IMPORT_TEXT, "", LootZoneForm.BLANK, text));
	}

	// ---- input and drawing -----------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (tableSuggestions.mouseClicked(font, event.x(), event.y())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (tableSuggestions.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	/** A resize rebuilds every widget: put back what was being typed. */
	@Override
	public void resize(int width, int height) {
		LootZoneForm form = readFormQuietly();
		super.resize(width, height);
		if (form != null) {
			fill(form);
		}
	}

	private LootZoneForm readFormQuietly() {
		Component keep = status;
		int keepColour = statusColour;
		LootZoneForm form = readForm();
		status = keep;
		statusColour = keepColour;
		return form;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title.copy().withStyle(ChatFormatting.GOLD), width / 2, 8, 0xFFFFFFFF);
		int lx = list.getRight() + 8, y = TOP + 4;
		int fx = lx + LABEL_W;
		label(graphics, "name", lx, y);
		if (!dimension.isEmpty()) {
			graphics.text(font, dimension, nameBox.getRight() + 6, y, 0xFF8A847A, false);
		}
		y += ROW;
		label(graphics, "centre", lx, y);
		y += ROW;
		label(graphics, "radius", lx, y);
		graphics.text(font, t("type"), fx + 46, y, 0xFFB8B2A4, false);
		y += ROW;
		label(graphics, "table", lx, y);
		y += ROW;
		label(graphics, "refill", lx, y);
		graphics.text(font, t("delay"), regenButton.getRight() + 6, y, 0xFFB8B2A4, false);
		y += ROW;
		label(graphics, "quiet", lx, y);
		graphics.text(font, t("no_player"), quietBox.getRight() + 6, y, 0xFFB8B2A4, false);
		graphics.text(font, status, lx, height - 14, statusColour, false);
		if (zones.isEmpty()) {
			graphics.centeredText(font, t("empty"), list.getX() + list.getWidth() / 2, TOP + 8, 0xFF8A847A);
		}
		tableSuggestions.extract(graphics, font, mouseX, mouseY);
	}

	private void label(GuiGraphicsExtractor graphics, String key, int x, int y) {
		graphics.text(font, t(key), x, y, 0xFFB8B2A4, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The zone list: name on the first line, radius and table on the second. */
	final class ZoneList extends ObjectSelectionList<ZoneList.Entry> {
		ZoneList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, 22);
		}

		void refresh() {
			List<Entry> entries = new ArrayList<>();
			Entry chosen = null;
			for (LootZoneForm z : zones) {
				Entry e = new Entry(z);
				entries.add(e);
				if (z.id().equals(selectedId)) {
					chosen = e;
				}
			}
			replaceEntries(entries);
			setSelected(chosen);
		}

		@Override
		public int getRowWidth() {
			return width - 10;
		}

		final class Entry extends ObjectSelectionList.Entry<Entry> {
			private final LootZoneForm zone;

			Entry(LootZoneForm zone) {
				this.zone = zone;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
				int x = getContentX() + 2, y = getContentY() + 1;
				graphics.text(font, font.plainSubstrByWidth(zone.id(), getContentWidth() - 4), x, y, 0xFFFFFFFF, false);
				String table = zone.table().substring(zone.table().indexOf(':') + 1);
				graphics.text(font, font.plainSubstrByWidth("r" + zone.radius() + " " + table, getContentWidth() - 4),
						x, y + 10, zone.regen() ? 0xFF9A948A : 0xFF7A6A6A, false);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				ZoneList.this.setSelected(this);
				select(zone.id());
				return true;
			}

			@Override
			public Component getNarration() {
				return Component.literal(zone.id());
			}
		}
	}
}
