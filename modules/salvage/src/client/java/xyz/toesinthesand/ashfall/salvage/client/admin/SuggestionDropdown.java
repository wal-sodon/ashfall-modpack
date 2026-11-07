package xyz.toesinthesand.ashfall.salvage.client.admin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import xyz.toesinthesand.ashfall.salvage.admin.IdFilter;

import java.util.List;

/**
 * Autocomplete under an {@link EditBox} for the admin screens: a short list of matching ids (loot tables, items,
 * entities) the server sent, picked with the mouse, Tab or the arrow keys.
 * <p>
 * Vanilla's command suggestions are tied to the chat box and Brigadier, so the admin screens use this small
 * drop-down instead. The owning screen forwards clicks and keys first and draws it last, so it sits over the
 * widgets below the box.
 */
public final class SuggestionDropdown {
	private static final int ROW = 11;

	private final EditBox box;
	private final int maxRows;
	private List<String> source = List.of();
	private List<String> matches = List.of();
	private int selected;
	/** Set after a pick so the box's own responder does not reopen the list for the value just chosen. */
	private boolean suppress;

	public SuggestionDropdown(EditBox box, int maxRows) {
		this.box = box;
		this.maxRows = maxRows;
	}

	public void setSource(List<String> ids) {
		source = ids;
		update();
	}

	/** Recomputes the matches; call from the box's responder. */
	public void update() {
		if (suppress) {
			suppress = false;
			matches = List.of();
		} else {
			matches = IdFilter.filter(source, box.getValue(), maxRows);
		}
		selected = 0;
		String value = box.getValue();
		box.setSuggestion(!matches.isEmpty() && matches.getFirst().startsWith(value) && !value.isEmpty()
				? matches.getFirst().substring(value.length()) : null);
	}

	public boolean visible() {
		return box.visible && box.isFocused() && !matches.isEmpty();
	}

	private void accept(int index) {
		suppress = true;
		box.setValue(matches.get(index));
		box.moveCursorToEnd(false);
		matches = List.of();
		box.setSuggestion(null);
	}

	public void extract(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		if (!visible()) {
			return;
		}
		int x = box.getX(), y = box.getBottom() + 1, w = Math.max(box.getWidth(), widest(font) + 6);
		graphics.fill(x - 1, y - 1, x + w + 1, y + matches.size() * ROW + 1, 0xFF6A6458);
		for (int i = 0; i < matches.size(); i++) {
			int top = y + i * ROW;
			boolean hover = mouseX >= x && mouseX < x + w && mouseY >= top && mouseY < top + ROW;
			graphics.fill(x, top, x + w, top + ROW, i == selected || hover ? 0xFF3A3530 : 0xF0101010);
			graphics.text(font, matches.get(i), x + 3, top + 2, i == selected ? 0xFFFFE08A : 0xFFD8D2C4, false);
		}
	}

	private int widest(Font font) {
		int w = 0;
		for (String m : matches) {
			w = Math.max(w, font.width(m));
		}
		return w;
	}

	/** @return true if the click picked a suggestion (the screen should stop handling it) */
	public boolean mouseClicked(Font font, double mouseX, double mouseY) {
		if (!visible()) {
			return false;
		}
		int x = box.getX(), y = box.getBottom() + 1, w = Math.max(box.getWidth(), widest(font) + 6);
		if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + matches.size() * ROW) {
			return false;
		}
		accept((int) ((mouseY - y) / ROW));
		return true;
	}

	/** Tab accepts, Up/Down move the highlight. */
	public boolean keyPressed(KeyEvent event) {
		if (!visible()) {
			return false;
		}
		switch (event.key()) {
			case InputConstants.KEY_TAB -> accept(selected);
			case InputConstants.KEY_DOWN -> selected = (selected + 1) % matches.size();
			case InputConstants.KEY_UP -> selected = (selected + matches.size() - 1) % matches.size();
			default -> {
				return false;
			}
		}
		return true;
	}
}
