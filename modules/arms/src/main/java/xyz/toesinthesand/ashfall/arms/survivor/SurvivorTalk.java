package xyz.toesinthesand.ashfall.arms.survivor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.arms.blade.PlasmaBladeItem;
import xyz.toesinthesand.ashfall.arms.gun.EnergyGunItem;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreRecord;

/**
 * What a survivor says, in chat. Right-clicking one gets a line and a row of clickable choices, the way the Shortwave
 * Set offers radio jobs: [Follow] [Stay] [Home], [Settle here] inside its leader's bunker, and a button per role it could
 * switch to (the base roles only once it has a home). A bitten survivor says so first, and one with a base role says
 * how its work stands (out until when, out of bandages; {@link SurvivorWork#status}), then whether it is hungry
 * ({@link SurvivorUpkeep}) or still new to its job. Each runs
 * {@code /survivor <uuid> ...} ({@link SurvivorCommand}), so the same orders can be typed. Lines are lang keys under
 * {@code message.ashfall.survivor.}, said as {@code Name: "line"}.
 */
public final class SurvivorTalk {
	private SurvivorTalk() {
	}

	/** {@code Name: "<line>"} for the lang key {@code message.ashfall.survivor.<key>}. */
	public static MutableComponent line(Survivor survivor, String key, Object... args) {
		return Component.translatable("message.ashfall.survivor.says", survivor.survivorName(),
				Component.translatable("message.ashfall.survivor." + key, args)).withStyle(ChatFormatting.WHITE);
	}

	/** The greeting and the choices; someone else's survivor only says whose lead it takes. */
	public static void talk(ServerPlayer player, Survivor survivor) {
		if (!Survivors.mayOrder(player, survivor)) {
			Player leader = survivor.leaderPlayer();
			Component name = leader != null ? leader.getDisplayName() : Component.translatable("message.ashfall.survivor.someone");
			player.sendSystemMessage(line(survivor, "not_yours", name));
			return;
		}
		String greeting = survivor.leader() == null ? "greet.stranger" : "greet." + survivor.mode().id();
		if (survivor.infected()) {
			player.sendSystemMessage(line(survivor, "greet.bitten").withStyle(ChatFormatting.RED));
		}
		player.sendSystemMessage(line(survivor, greeting));
		Component status = SurvivorWork.status(player.level(), survivor);
		if (status != null) {
			player.sendSystemMessage(status);
		}
		if (survivor.hungry()) {
			String hungry = SurvivorWork.atHome(player.level(), survivor) ? "hungry" : "hungry_away";
			player.sendSystemMessage(line(survivor, hungry, survivor.hungryDays()).withStyle(ChatFormatting.GOLD));
		}
		if (SurvivorWork.newInRole(survivor)) {
			player.sendSystemMessage(line(survivor, "new_role").withStyle(ChatFormatting.GRAY));
		}
		String base = "/survivor " + survivor.getUUID() + " ";
		MutableComponent row = Component.empty()
				.append(button("follow", base + "follow", ChatFormatting.GREEN)).append(" ")
				.append(button("stay", base + "stay", ChatFormatting.YELLOW));
		if (survivor.home() != null) {
			row.append(" ").append(button("home", base + "home", ChatFormatting.AQUA));
		}
		BlockPos here = survivor.settleableCore(player.level()).map(BunkerCoreRecord::pos).orElse(null);
		if (here != null && !here.equals(survivor.home())) {
			row.append(" ").append(button("settle", base + "settle", ChatFormatting.LIGHT_PURPLE));
		}
		for (SurvivorRole role : SurvivorRole.values()) {
			if (role.assignable() && role != survivor.role() && (!role.needsHome() || survivor.home() != null)) {
				row.append(" ").append(roleButton(role, base + "role " + role.id()));
			}
		}
		player.sendSystemMessage(row);
	}

	private static MutableComponent button(String key, String command, ChatFormatting colour) {
		return Component.translatable("message.ashfall.survivor.button." + key).withStyle(s -> s
				.withColor(colour).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.ashfall.survivor.hover." + key))));
	}

	private static MutableComponent roleButton(SurvivorRole role, String command) {
		return Component.translatable("message.ashfall.survivor.button.role", role.displayName()).withStyle(s -> s
				.withColor(ChatFormatting.GOLD).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.ashfall.survivor.hover.role." + role.id()))));
	}

	/**
	 * Sneak-right-click: hands a Guard the held weapon (giving back the one it had), or with an empty hand takes the
	 * weapon back. Non-guards and non-weapons are refused with a line.
	 */
	public static void handOver(ServerPlayer player, Survivor survivor, InteractionHand hand) {
		if (!Survivors.mayOrder(player, survivor)) {
			talk(player, survivor);
			return;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			ItemStack weapon = survivor.getMainHandItem();
			if (!weapon.isEmpty()) {
				survivor.equipWeapon(ItemStack.EMPTY);
				give(player, weapon);
				player.sendSystemMessage(line(survivor, "weapon.returned"));
			}
			return;
		}
		if (survivor.role() != SurvivorRole.GUARD) {
			player.sendSystemMessage(line(survivor, "weapon.not_guard"));
			return;
		}
		if (!Survivors.isMeleeWeapon(held)) {
			player.sendSystemMessage(line(survivor, held.getItem() instanceof EnergyGunItem ? "weapon.blaster"
					: held.getItem() instanceof PlasmaBladeItem ? "weapon.plasma_blade" : "weapon.not_weapon"));
			return;
		}
		ItemStack given = held.split(1);
		ItemStack old = survivor.equipWeapon(given);
		if (!old.isEmpty()) {
			give(player, old);
		}
		player.sendSystemMessage(line(survivor, "weapon.thanks"));
	}

	/** Into the player's inventory, or dropped at their feet when it is full. */
	private static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}

	/** The reply to an order: the line for it, or the refusal. */
	static Component ordered(Survivor survivor, SurvivorMode mode) {
		if (mode == SurvivorMode.HOME && survivor.home() == null) {
			return line(survivor, "no_home");
		}
		return line(survivor, "order." + mode.id());
	}

	/** The reply to a role change, saying where an old guard's weapon went. */
	static Component roleChanged(Survivor survivor, SurvivorRole role, SupplyLockers.@Nullable Stored weapon) {
		MutableComponent reply = line(survivor, "role." + role.id());
		if (weapon != null) {
			reply.append(" ").append(Component.translatable("message.ashfall.survivor.weapon."
					+ (weapon == SupplyLockers.Stored.LOCKER ? "stored" : "dropped")).withStyle(ChatFormatting.GRAY));
		}
		return reply;
	}

	/** The reply to [Settle here] (or [Stay] inside the leader's bunker). */
	static void settled(ServerPlayer player, Survivor survivor, Survivor.Settle result) {
		switch (result) {
			case SETTLED -> {
				player.sendSystemMessage(Component.translatable("message.ashfall.survivor.settled_note", survivor.survivorName())
						.withStyle(ChatFormatting.GREEN));
				player.sendSystemMessage(line(survivor, "settled"));
			}
			case ALREADY -> player.sendSystemMessage(line(survivor, "order.home"));
			case NOT_LEADERS_BUNKER -> player.sendSystemMessage(line(survivor, "settle.not_here"));
			case FULL -> player.sendSystemMessage(line(survivor, "settle.full", SurvivorBalance.get().caps().base()));
		}
	}

	/**
	 * Antibiotics used on a survivor: one is used up and a bitten survivor is cured; a healthy one hands them back
	 * (nothing is used). Anyone may treat anyone's survivor.
	 */
	static void treat(ServerPlayer player, Survivor survivor, InteractionHand hand) {
		if (!SurvivorInfection.cure(survivor)) {
			player.sendSystemMessage(line(survivor, "treat.healthy"));
			return;
		}
		player.getItemInHand(hand).consume(1, player);
		player.sendSystemMessage(line(survivor, "treat.cured").withStyle(ChatFormatting.GREEN));
	}

	static void died(ServerPlayer leader, Survivor survivor) {
		leader.sendSystemMessage(Component.translatable("message.ashfall.survivor.died", survivor.survivorName())
				.withStyle(ChatFormatting.RED));
	}
}
