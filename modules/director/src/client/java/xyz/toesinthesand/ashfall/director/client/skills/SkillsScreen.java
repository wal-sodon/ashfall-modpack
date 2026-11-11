package xyz.toesinthesand.ashfall.director.client.skills;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import xyz.toesinthesand.ashfall.director.skills.Skill;
import xyz.toesinthesand.ashfall.director.skills.SkillPayloads;
import xyz.toesinthesand.ashfall.director.skills.SkillRules;
import xyz.toesinthesand.ashfall.director.skills.Skills;
import xyz.toesinthesand.ashfall.director.skills.SurvivorClass;
import xyz.toesinthesand.ashfall.director.skills.XpMath;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The skill tree (K): the player's class, then every skill in two columns, the class's core skills first and marked
 * in gold. Each row shows the rank pips (core skills have more), what a rank does, the next rank's cost and a Train
 * button. Class and ranks come from the synced player attachment and XP from the synced XP bar, so the screen
 * updates as soon as the server confirms a purchase.
 */
public class SkillsScreen extends Screen {
	/** Plain zombies drop 5 XP; the cost line converts points into kills with it. */
	private static final int ZOMBIE_XP = 5;
	private static final int ROW = 30, HEADER = 44, GAP = 8;

	private final Map<Skill, Button> buttons = new EnumMap<>(Skill.class);
	private final List<Skill> order = new ArrayList<>();
	private SurvivorClass shownClass;
	private int top, left, colW, rows;

	public SkillsScreen() {
		super(Component.translatable("screen.ashfall.skills"));
	}

	@Override
	protected void init() {
		buttons.clear();
		order.clear();
		LocalPlayer player = minecraft.player;
		shownClass = player == null ? null : Skills.survivorClass(player);
		SkillRules rules = SkillsClient.rules();
		if (shownClass != null) {
			order.addAll(rules.coreSkills(shownClass));
		}
		for (Skill skill : Skill.values()) {
			if (!order.contains(skill)) {
				order.add(skill);
			}
		}
		rows = (order.size() + 1) / 2;
		colW = Math.min(230, (width - 16 - GAP) / 2);
		left = (width - (2 * colW + GAP)) / 2;
		top = Math.max(4, (height - (HEADER + rows * ROW)) / 2);
		for (int i = 0; i < order.size(); i++) {
			Skill skill = order.get(i);
			int x = left + (i / rows) * (colW + GAP), y = top + HEADER + (i % rows) * ROW;
			buttons.put(skill, addRenderableWidget(Button.builder(Component.translatable("screen.ashfall.skills.buy"),
					b -> ClientPlayNetworking.send(new SkillPayloads.Buy(skill.id()))).bounds(x + colW - 44, y + 4, 42, 18).build()));
		}
		addRenderableWidget(Button.builder(Component.translatable("screen.ashfall.skills.change"),
				b -> minecraft.gui.setScreen(new ClassPickerScreen())).bounds(left + 2 * colW + GAP - 90, top, 90, 18).build());
		refreshButtons();
	}

	@Override
	public void tick() {
		super.tick();
		LocalPlayer player = minecraft.player;
		if (player != null && Skills.survivorClass(player) != shownClass) {
			rebuildWidgets();
		}
		refreshButtons();
	}

	private static int points(LocalPlayer player) {
		return XpMath.points(player.experienceLevel, player.experienceProgress);
	}

	private void refreshButtons() {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		SkillRules rules = SkillsClient.rules();
		int held = points(player);
		buttons.forEach((skill, button) -> {
			int cost = rules.cost(shownClass, skill, Skills.rank(player, skill) + 1);
			button.active = cost >= 0 && held >= cost;
			button.visible = cost >= 0;
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		SkillRules rules = SkillsClient.rules();
		int right = left + 2 * colW + GAP;
		graphics.fill(left - 6, top - 6, right + 6, top + HEADER + rows * ROW + 2, 0xC0101010);
		graphics.text(font, title.copy().withStyle(ChatFormatting.GOLD), left, top, 0xFFFFFFFF, false);
		if (shownClass != null) {
			graphics.item(SkillIcons.of(shownClass), left + font.width(title) + 8, top - 4);
			graphics.text(font, Component.translatable("screen.ashfall.skills.class",
					Component.translatable("class.ashfall." + shownClass.id())), left + font.width(title) + 28, top, 0xFFE8C878, false);
		}
		graphics.text(font, Component.translatable("screen.ashfall.skills.xp", points(player), player.experienceLevel),
				left, top + 14, 0xFF80FF20, false);
		graphics.text(font, Component.translatable("screen.ashfall.skills.hint", rules.coreCosts().size(), rules.generalCosts().size()),
				left, top + 26, 0xFF9A948A, false);
		for (int i = 0; i < order.size(); i++) {
			Skill skill = order.get(i);
			int x = left + (i / rows) * (colW + GAP), y = top + HEADER + (i % rows) * ROW;
			boolean core = rules.isCore(shownClass, skill);
			int rank = Skills.rank(player, skill), max = rules.maxRank(shownClass, skill);
			graphics.fill(x, y, x + colW, y + ROW - 2, core ? 0x60605020 : 0x40303030);
			graphics.item(SkillIcons.of(skill), x + 2, y + 6);
			Component name = Component.translatable("skill.ashfall." + skill.id());
			graphics.text(font, name, x + 22, y + 3, core ? 0xFFE8C878 : 0xFFFFFFFF, false);
			StringBuilder pips = new StringBuilder();
			for (int r = 1; r <= max; r++) {
				pips.append(r <= rank ? '■' : '□');
			}
			graphics.text(font, pips.toString(), x + 26 + font.width(name), y + 3, core ? 0xFFE8C878 : 0xFFB8B2A4, false);
			int cost = rules.cost(shownClass, skill, rank + 1);
			Component sub = cost < 0
					? Component.translatable("screen.ashfall.skills.maxed").withStyle(ChatFormatting.GREEN)
					: Component.translatable("screen.ashfall.skills.cost", cost, (cost + ZOMBIE_XP - 1) / ZOMBIE_XP);
			graphics.text(font, sub, x + 22, y + 15, cost < 0 ? 0xFF80FF80 : 0xFFD8D2C4, false);
			if (mouseX >= x && mouseX < x + colW - 46 && mouseY >= y && mouseY < y + ROW - 2) {
				graphics.setTooltipForNextFrame(Component.translatable("skill.ashfall." + skill.id() + ".desc",
						SkillIcons.perRank(rules, skill)), mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
