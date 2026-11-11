package xyz.toesinthesand.ashfall.director.client.skills;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import xyz.toesinthesand.ashfall.director.skills.Skill;
import xyz.toesinthesand.ashfall.director.skills.SkillPayloads;
import xyz.toesinthesand.ashfall.director.skills.SkillRules;
import xyz.toesinthesand.ashfall.director.skills.Skills;
import xyz.toesinthesand.ashfall.director.skills.SurvivorClass;

import java.util.List;

/**
 * The class picker: six cards, each with the class's description and its core skills. A player without a class
 * gets it on joining and can't close it until they choose; with a class, it is the "Change class" page of the skill
 * screen (the server checks home and the cooldown).
 */
public class ClassPickerScreen extends Screen {
	private static final int CARD_H = 112, GAP = 6;

	private final SurvivorClass startedAs;
	private int cardW, left, top;

	public ClassPickerScreen() {
		super(Component.translatable("screen.ashfall.class"));
		LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		startedAs = player == null ? null : Skills.survivorClass(player);
	}

	private boolean forced() {
		return startedAs == null;
	}

	@Override
	protected void init() {
		SurvivorClass[] classes = SurvivorClass.values();
		cardW = Math.min(136, (width - 16 - 2 * GAP) / 3);
		left = (width - (3 * cardW + 2 * GAP)) / 2;
		top = Math.max(30, (height - (2 * CARD_H + GAP) - 24) / 2 + 12);
		for (int i = 0; i < classes.length; i++) {
			SurvivorClass c = classes[i];
			int x = left + (i % 3) * (cardW + GAP), y = top + (i / 3) * (CARD_H + GAP);
			Button button = addRenderableWidget(Button.builder(Component.translatable(c == startedAs
							? "screen.ashfall.class.current" : "screen.ashfall.class.pick"),
					b -> ClientPlayNetworking.send(new SkillPayloads.Choose(c.id()))).bounds(x + 4, y + CARD_H - 22, cardW - 8, 18).build());
			button.active = c != startedAs;
		}
		if (!forced()) {
			addRenderableWidget(Button.builder(Component.translatable("screen.ashfall.class.back"),
					b -> minecraft.gui.setScreen(new SkillsScreen())).bounds(width / 2 - 40, top + 2 * CARD_H + GAP + 4, 80, 18).build());
		}
	}

	@Override
	public void tick() {
		super.tick();
		LocalPlayer player = minecraft.player;
		if (player != null && Skills.survivorClass(player) != null && Skills.survivorClass(player) != startedAs) {
			minecraft.gui.setScreen(new SkillsScreen());
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !forced();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		SkillRules rules = SkillsClient.rules();
		graphics.centeredText(font, title.copy().withStyle(ChatFormatting.GOLD), width / 2, top - 28, 0xFFFFFFFF);
		Component sub = forced() ? Component.translatable("screen.ashfall.class.first")
				: Component.translatable("screen.ashfall.class.change", Math.round(rules.classChangeRefund() * 100), rules.classChangeDays());
		List<FormattedCharSequence> lines = font.split(sub, Math.min(width - 20, 3 * cardW + 2 * GAP));
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			graphics.centeredText(font, lines.get(i), width / 2, top - 16 + i * 9, 0xFFB8B2A4);
		}
		SurvivorClass[] classes = SurvivorClass.values();
		for (int i = 0; i < classes.length; i++) {
			SurvivorClass c = classes[i];
			int x = left + (i % 3) * (cardW + GAP), y = top + (i / 3) * (CARD_H + GAP);
			boolean hover = mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + CARD_H;
			graphics.fill(x, y, x + cardW, y + CARD_H, c == startedAs ? 0xD0303818 : hover ? 0xD0282828 : 0xD0141414);
			graphics.item(SkillIcons.of(c), x + 4, y + 4);
			graphics.text(font, Component.translatable("class.ashfall." + c.id()).withStyle(ChatFormatting.GOLD), x + 24, y + 8, 0xFFFFFFFF, false);
			int ty = y + 24;
			for (FormattedCharSequence line : font.split(Component.translatable("class.ashfall." + c.id() + ".desc"), cardW - 8)) {
				if (ty > y + 42) {
					break;
				}
				graphics.text(font, line, x + 4, ty, 0xFFB8B2A4, false);
				ty += 9;
			}
			int sy = y + 46;
			for (Skill skill : rules.coreSkills(c)) {
				graphics.item(SkillIcons.of(skill), x + 4, sy - 4);
				graphics.text(font, Component.translatable("skill.ashfall." + skill.id()), x + 22, sy, 0xFFE8C878, false);
				if (mouseX >= x && mouseX < x + cardW && mouseY >= sy - 4 && mouseY < sy + 12) {
					graphics.setTooltipForNextFrame(Component.translatable("skill.ashfall." + skill.id() + ".desc",
							SkillIcons.perRank(rules, skill)), mouseX, mouseY);
				}
				sy += 15;
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
