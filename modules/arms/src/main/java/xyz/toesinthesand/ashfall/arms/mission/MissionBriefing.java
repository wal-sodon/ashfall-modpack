package xyz.toesinthesand.ashfall.arms.mission;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import xyz.toesinthesand.ashfall.arms.finale.FinaleSettings;
import xyz.toesinthesand.ashfall.arms.grid.DistrictLoop;
import xyz.toesinthesand.ashfall.arms.grid.GridSettings;
import xyz.toesinthesand.ashfall.arms.story.StoryRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The first-join briefing: a title ("ASHFALL" / "Asheville has gone dark"), a short chat debrief with links, and the
 * Mission Briefing book in the inventory. Once per player per world ({@link MissionProgress#briefed}).
 * <p>
 * Three layers on purpose: the title sets the scene for a few seconds, the chat says the one thing to do now and stays
 * in the history, and the book is the full manual for later. The full text also opens as a dialog from the chat link,
 * so reading it never means digging the book out of a full inventory.
 * <p>
 * With {@code ashfall:objective} off (the pure sandbox) nobody is briefed; the flag stays unset, so turning the rule
 * on later still briefs everyone once. The Survivor's Notes in the radio cache stay as the in-world primer for that
 * sandbox case.
 */
public final class MissionBriefing {
	/** The book's title and author: plain strings, because a written book's title cannot be a translated text. */
	public static final String BOOK_TITLE = "Mission Briefing";
	public static final String BOOK_AUTHOR = "Montford relief crew";

	private MissionBriefing() {
	}

	/** Briefs {@code player} if the rule allows it and they have not been briefed in this world. @return whether it was delivered */
	public static boolean maybeBrief(ServerPlayer player) {
		if (!StoryRules.objective(player.level().getServer()) || MissionProgress.get(player).briefed()) {
			return false;
		}
		deliver(player);
		return true;
	}

	/** Title, chat and book, and marks the player briefed. */
	public static void deliver(ServerPlayer player) {
		MissionProgress.set(player, MissionProgress.get(player).withBriefed());
		MissionSettings settings = MissionSettings.get();
		player.connection.send(new ClientboundSetTitlesAnimationPacket(settings.titleFadeIn(), settings.titleStay(), settings.titleFadeOut()));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("briefing.ashfall.subtitle").withStyle(ChatFormatting.GRAY)));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("briefing.ashfall.title")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6f, 0.5f);

		Objective first = MissionResolver.current(MissionWorld.collect(player));
		player.sendSystemMessage(Component.translatable("briefing.ashfall.chat.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		player.sendSystemMessage(Component.translatable("briefing.ashfall.chat.situation").withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.translatable("briefing.ashfall.chat.mission").withStyle(ChatFormatting.WHITE));
		player.sendSystemMessage(Component.translatable("briefing.ashfall.chat.first", first.text().copy().withStyle(ChatFormatting.YELLOW))
				.withStyle(ChatFormatting.WHITE));
		player.sendSystemMessage(links());

		give(player, book());
	}

	/** {@code [Read the full briefing] [Show objectives]}, the links under the debrief. */
	public static Component links() {
		return link("briefing.ashfall.link.read", "briefing.ashfall.link.read.hover", "/ashfall mission briefing", ChatFormatting.AQUA)
				.append(Component.literal("  "))
				.append(link("briefing.ashfall.link.objectives", "briefing.ashfall.link.objectives.hover", "/ashfall mission", ChatFormatting.GREEN));
	}

	static MutableComponent link(String key, String hoverKey, String command, ChatFormatting colour) {
		return Component.literal("[").append(Component.translatable(key)).append("]").withStyle(Style.EMPTY.withColor(colour)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(hoverKey))));
	}

	/** The briefing's numbers from the loaded datapack. */
	public static BriefingPages.Numbers numbers() {
		GridSettings grid = GridSettings.get();
		FinaleSettings finale = FinaleSettings.get();
		int darknessCut = Math.round((1f - grid.reclaimed().darknessScale()) * 100f);
		return new BriefingPages.Numbers(grid.couplersRequired(), grid.cellsRequired(), grid.energizeSeconds(), grid.holdRadius(),
				grid.humRadius(), grid.sirenWarningSeconds(), DistrictLoop.gridPercent(4, 0), darknessCut, MissionSettings.get().crystalsNeeded(),
				finale.buffer().cellSeconds(), finale.breakers().holdSeconds());
	}

	/** Each page as a component: the heading in bold, a blank line, then the body. */
	public static List<Component> pageComponents() {
		List<Component> out = new ArrayList<>();
		for (BriefingPages.Page page : BriefingPages.pages(numbers())) {
			out.add(Component.empty()
					.append(Component.translatable(page.headingKey()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
					.append("\n\n")
					.append(Component.translatable(page.bodyKey(), page.args().toArray())));
		}
		return out;
	}

	/** A fresh Mission Briefing book. Generation 0 (an original), so a player may copy it for a friend. */
	public static ItemStack book() {
		List<Filterable<Component>> pages = new ArrayList<>();
		for (Component page : pageComponents()) {
			pages.add(Filterable.passThrough(page));
		}
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(BOOK_TITLE), BOOK_AUTHOR, 0, pages, true));
		return stack;
	}

	/** Whether {@code stack} is a Mission Briefing book. */
	public static boolean isBriefing(ItemStack stack) {
		WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		return stack.is(Items.WRITTEN_BOOK) && content != null && BOOK_TITLE.equals(content.title().raw()) && BOOK_AUTHOR.equals(content.author());
	}

	/** Opens the full briefing as a scrolling dialog: every page, one after another. */
	public static void openDialog(ServerPlayer player) {
		List<DialogBody> body = new ArrayList<>();
		for (Component page : pageComponents()) {
			body.add(new PlainMessage(page, 300));
		}
		CommonDialogData common = new CommonDialogData(Component.translatable("briefing.ashfall.dialog.title"), Optional.empty(), true, false,
				DialogAction.CLOSE, body, List.of());
		ActionButton done = new ActionButton(new CommonButtonData(Component.translatable("gui.done"), 150), Optional.empty());
		player.openDialog(Holder.direct((Dialog) new NoticeDialog(common, done)));
	}

	static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.spawnAtLocation(player.level(), stack);
		}
	}
}
