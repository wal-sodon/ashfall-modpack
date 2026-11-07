package xyz.toesinthesand.ashfall.arms.client.drops;

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
import xyz.toesinthesand.ashfall.arms.drops.DropForm;
import xyz.toesinthesand.ashfall.arms.drops.MobDropView;
import xyz.toesinthesand.ashfall.arms.drops.MobDropsActionPayload;
import xyz.toesinthesand.ashfall.arms.drops.MobDropsActionPayload.Action;
import xyz.toesinthesand.ashfall.arms.drops.MobDropsSyncPayload;
import xyz.toesinthesand.ashfall.arms.client.admin.SuggestionDropdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The op-only Mob Drops screen: entities with Ashfall drop rules on the left, the selected entity's extra drops on
 * the right (item, chance in percent, player-kill-only, count range).
 * <p>
 * Save sends the whole list; the server writes it into the world override datapack and reloads, then answers.
 * Rows are kept as the raw text typed so adding or removing a row never throws away a half-typed value.
 */
public class MobDropsScreen extends Screen {
	private static final int ROW = 22, TOP = 24;

	/** One drop row as typed. */
	private static final class RowState {
		String item, chance, min, max;
		boolean playerKill;

		RowState(String item, String chance, boolean playerKill, String min, String max) {
			this.item = item;
			this.chance = chance;
			this.playerKill = playerKill;
			this.min = min;
			this.max = max;
		}

		static RowState of(DropForm d) {
			return new RowState(d.item(), percent(d.chance()), d.killedByPlayer(), Integer.toString(d.min()), Integer.toString(d.max()));
		}
	}

	private record RowWidgets(EditBox item, EditBox chance, CycleButton<Boolean> playerKill, EditBox min, EditBox max) {
	}

	private List<MobDropView> rules;
	private List<String> entities;
	private List<String> items;
	private String selected;
	private final List<RowState> rows = new ArrayList<>();
	private final List<RowWidgets> rowWidgets = new ArrayList<>();
	private final List<SuggestionDropdown> dropdowns = new ArrayList<>();
	private boolean reloadRows = true;
	private boolean waiting;
	private Component status = Component.empty();
	private int statusColour = 0xFFD8D2C4;

	private EntityList list;
	private EditBox entityBox;
	private Button saveButton, resetButton, addButton;
	private int maxRows;

	public MobDropsScreen(MobDropsSyncPayload payload) {
		super(Component.translatable("screen.ashfall.mob_drops"));
		rules = payload.rules();
		entities = payload.entities();
		items = payload.items();
		selected = rules.isEmpty() ? null : rules.getFirst().entity();
		setStatus(payload);
		loadRows();
	}

	private static Component t(String key, Object... args) {
		return Component.translatable("screen.ashfall.mob_drops." + key, args);
	}

	/** Chance as a percentage without float noise: 0.035 -> "3.5". */
	static String percent(float chance) {
		String s = String.format(Locale.ROOT, "%.3f", chance * 100.0);
		s = s.replaceAll("0+$", "");
		return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
	}

	@Override
	protected void init() {
		dropdowns.clear();
		rowWidgets.clear();
		int listW = Math.clamp(width / 4, 110, 170);
		list = addRenderableWidget(new EntityList(minecraft, listW, height - TOP - 58, TOP));
		list.updateSizeAndPosition(listW, height - TOP - 58, 6, TOP);
		entityBox = addRenderableWidget(new EditBox(font, 6, height - 52, listW, 16, t("entity")));
		entityBox.setMaxLength(128);
		entityBox.setHint(t("entity.hint"));
		SuggestionDropdown entityDrop = new SuggestionDropdown(entityBox, 5);
		entityDrop.setSource(entities);
		entityBox.setResponder(s -> entityDrop.update());
		dropdowns.add(entityDrop);
		addRenderableWidget(Button.builder(t("add_entity"), b -> addEntity()).bounds(6, height - 30, listW, 20).build());

		int x0 = listW + 14, w = width - x0 - 8;
		maxRows = Math.clamp((height - TOP - 18 - 60) / ROW, 1, MobDropsActionPayload.MAX_DROPS);
		int chanceW = 42, killW = 72, countW = 26, removeW = 16, gap = 4;
		int itemW = Math.max(80, w - chanceW - killW - 2 * countW - removeW - 5 * gap);
		int y = TOP + 16;
		for (int i = 0; i < Math.min(rows.size(), maxRows); i++) {
			RowState row = rows.get(i);
			int x = x0;
			EditBox item = addRenderableWidget(new EditBox(font, x, y, itemW, 16, t("item")));
			item.setMaxLength(128);
			item.setValue(row.item);
			item.moveCursorToStart(false);
			SuggestionDropdown itemDrop = new SuggestionDropdown(item, 6);
			itemDrop.setSource(items);
			item.setResponder(s -> {
				row.item = s;
				itemDrop.update();
			});
			dropdowns.add(itemDrop);
			x += itemW + gap;
			EditBox chance = numberBox(x, y, chanceW, row.chance, "chance", s -> row.chance = s);
			x += chanceW + gap;
			CycleButton<Boolean> kill = addRenderableWidget(CycleButton.booleanBuilder(t("player_kill"), t("any_kill"), row.playerKill)
					.displayOnlyValue().withTooltip(v -> Tooltip.create(t("kill.tooltip")))
					.create(x, y - 1, killW, 18, t("kill"), (b, v) -> row.playerKill = v));
			x += killW + gap;
			EditBox min = numberBox(x, y, countW, row.min, "min", s -> row.min = s);
			x += countW + gap;
			EditBox max = numberBox(x, y, countW, row.max, "max", s -> row.max = s);
			x += countW + gap;
			int index = i;
			addRenderableWidget(Button.builder(Component.literal("x"), b -> removeRow(index))
					.tooltip(Tooltip.create(t("remove"))).bounds(x, y - 1, removeW, 18).build());
			rowWidgets.add(new RowWidgets(item, chance, kill, min, max));
			y += ROW;
		}
		int by = height - 30, bw = (w - 3 * 4) / 4;
		addButton = addRenderableWidget(Button.builder(t("add_drop"), b -> addRow()).bounds(x0, by, bw, 20).build());
		saveButton = addRenderableWidget(Button.builder(t("save"), b -> save()).bounds(x0 + bw + 4, by, bw, 20).build());
		resetButton = addRenderableWidget(Button.builder(t("reset"), b -> reset())
				.tooltip(Tooltip.create(t("reset.tooltip"))).bounds(x0 + 2 * (bw + 4), by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x0 + 3 * (bw + 4), by, bw, 20).build());
		list.refresh();
		updateButtons();
	}

	private EditBox numberBox(int x, int y, int w, String value, String key, java.util.function.Consumer<String> onChange) {
		EditBox box = addRenderableWidget(new EditBox(font, x, y, w, 16, t(key)));
		box.setMaxLength(8);
		box.setValue(value);
		box.setResponder(onChange);
		box.setTooltip(Tooltip.create(t(key + ".tooltip")));
		return box;
	}

	private void updateButtons() {
		MobDropView view = view(selected);
		boolean has = selected != null;
		saveButton.active = has && !waiting;
		addButton.active = has && rows.size() < maxRows;
		resetButton.active = has && !waiting && view != null && view.overridden();
	}

	// ---- server sync ---------------------------------------------------------------------------

	public void accept(MobDropsSyncPayload payload) {
		waiting = false;
		rules = payload.rules();
		if (!payload.entities().isEmpty()) {
			entities = payload.entities();
		}
		if (!payload.items().isEmpty()) {
			items = payload.items();
		}
		setStatus(payload);
		if (reloadRows && !payload.error()) {
			loadRows();
		}
		reloadRows = false;
		rebuildWidgets();
	}

	private void setStatus(MobDropsSyncPayload payload) {
		if (!payload.message().isEmpty()) {
			status = Component.literal(payload.message());
			statusColour = payload.error() ? 0xFFFF7A6A : 0xFF8FE08A;
		}
	}

	private void localError(Component message) {
		status = message;
		statusColour = 0xFFFF7A6A;
	}

	private MobDropView view(String entity) {
		for (MobDropView v : rules) {
			if (v.entity().equals(entity)) {
				return v;
			}
		}
		return null;
	}

	private void loadRows() {
		rows.clear();
		MobDropView view = view(selected);
		if (view != null) {
			view.drops().forEach(d -> rows.add(RowState.of(d)));
		}
	}

	// ---- actions ---------------------------------------------------------------------------------

	private void select(String entity) {
		selected = entity;
		loadRows();
		rebuildWidgets();
	}

	private void addEntity() {
		String id = entityBox.getValue().trim();
		if (id.isEmpty()) {
			return;
		}
		if (!id.contains(":")) {
			id = "minecraft:" + id;
		}
		if (!entities.contains(id) && view(id) == null) {
			localError(t("unknown_entity", id));
			return;
		}
		selected = id;
		loadRows();
		if (rows.isEmpty()) {
			rows.add(new RowState("", "5", true, "1", "1"));
		}
		entityBox.setValue("");
		status = t("unsaved", id);
		statusColour = 0xFFE8C878;
		rebuildWidgets();
	}

	private void addRow() {
		rows.add(new RowState("", "5", true, "1", "1"));
		rebuildWidgets();
	}

	private void removeRow(int index) {
		if (index < rows.size()) {
			rows.remove(index);
			rebuildWidgets();
		}
	}

	private void save() {
		if (selected == null) {
			return;
		}
		if (rows.size() > maxRows) {
			localError(t("too_many"));
			return;
		}
		List<DropForm> forms = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			RowState r = rows.get(i);
			try {
				float chance = Float.parseFloat(r.chance.trim()) / 100f;
				forms.add(new DropForm(r.item.trim(), chance, r.playerKill, Integer.parseInt(r.min.trim()), Integer.parseInt(r.max.trim())));
			} catch (NumberFormatException e) {
				localError(t("bad_row", i + 1));
				return;
			}
		}
		waiting = true;
		reloadRows = true;
		status = t("saving");
		statusColour = 0xFFE8C878;
		updateButtons();
		ClientPlayNetworking.send(new MobDropsActionPayload(Action.SAVE, selected, forms));
	}

	private void reset() {
		if (selected == null) {
			return;
		}
		waiting = true;
		reloadRows = true;
		updateButtons();
		ClientPlayNetworking.send(MobDropsActionPayload.of(Action.RESET, selected));
	}

	// ---- input and drawing -----------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (SuggestionDropdown d : dropdowns) {
			if (d.mouseClicked(font, event.x(), event.y())) {
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		for (SuggestionDropdown d : dropdowns) {
			if (d.keyPressed(event)) {
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title.copy().withStyle(ChatFormatting.GOLD), width / 2, 8, 0xFFFFFFFF);
		int x0 = list.getRight() + 8;
		if (selected == null) {
			graphics.text(font, t("pick"), x0, TOP + 4, 0xFF8A847A, false);
		} else {
			MobDropView view = view(selected);
			Component head = Component.literal(selected).withStyle(ChatFormatting.WHITE);
			if (view != null && view.overridden()) {
				head = head.copy().append(Component.literal(" ").append(t("override")).withStyle(ChatFormatting.YELLOW));
			}
			graphics.text(font, head, x0, TOP - 10 + 2, 0xFFFFFFFF, false);
			if (!rowWidgets.isEmpty()) {
				RowWidgets first = rowWidgets.getFirst();
				int hy = TOP + 5;
				graphics.text(font, t("item"), first.item().getX(), hy, 0xFFB8B2A4, false);
				graphics.text(font, t("chance"), first.chance().getX(), hy, 0xFFB8B2A4, false);
				graphics.text(font, t("kill"), first.playerKill().getX(), hy, 0xFFB8B2A4, false);
				graphics.text(font, t("min"), first.min().getX(), hy, 0xFFB8B2A4, false);
				graphics.text(font, t("max"), first.max().getX(), hy, 0xFFB8B2A4, false);
			} else {
				graphics.text(font, t("no_drops"), x0, TOP + 16, 0xFF8A847A, false);
			}
			if (rows.size() > maxRows) {
				graphics.text(font, t("too_many"), x0, height - 58, 0xFFFF7A6A, false);
			}
		}
		graphics.text(font, status, x0, height - 44, statusColour, false);
		for (SuggestionDropdown d : dropdowns) {
			d.extract(graphics, font, mouseX, mouseY);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Entities with rules (plus one just added and not yet saved). */
	final class EntityList extends ObjectSelectionList<EntityList.Entry> {
		EntityList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, 22);
		}

		void refresh() {
			List<Entry> entries = new ArrayList<>();
			Entry chosen = null;
			boolean selectedListed = false;
			for (MobDropView v : rules) {
				Entry e = new Entry(v.entity(), v.drops().size(), v.overridden());
				entries.add(e);
				if (v.entity().equals(selected)) {
					chosen = e;
					selectedListed = true;
				}
			}
			if (selected != null && !selectedListed) {
				chosen = new Entry(selected, -1, false);
				entries.add(chosen);
			}
			replaceEntries(entries);
			setSelected(chosen);
		}

		@Override
		public int getRowWidth() {
			return width - 10;
		}

		final class Entry extends ObjectSelectionList.Entry<Entry> {
			private final String entity;
			private final int drops;
			private final boolean overridden;

			Entry(String entity, int drops, boolean overridden) {
				this.entity = entity;
				this.drops = drops;
				this.overridden = overridden;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
				int x = getContentX() + 2, y = getContentY() + 1;
				String path = entity.substring(entity.indexOf(':') + 1);
				graphics.text(font, font.plainSubstrByWidth(path, getContentWidth() - 4), x, y, 0xFFFFFFFF, false);
				Component sub = drops < 0 ? t("new_entity") : overridden ? t("drops_override", drops) : t("drops", drops);
				graphics.text(font, sub, x, y + 10, overridden || drops < 0 ? 0xFFE8C878 : 0xFF9A948A, false);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				EntityList.this.setSelected(this);
				select(entity);
				return true;
			}

			@Override
			public Component getNarration() {
				return Component.literal(entity);
			}
		}
	}
}
