package xyz.toesinthesand.ashfall.arms.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;
import xyz.toesinthesand.ashfall.arms.grid.GridCommand;
import xyz.toesinthesand.ashfall.arms.grid.GridDistricts;
import xyz.toesinthesand.ashfall.arms.grid.GridSettings;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.grid.SiegeSettings;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.survivor.Survivor;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorBalance;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorMode;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRole;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRoster;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorTrade;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorUpkeep;
import xyz.toesinthesand.ashfall.arms.survivor.Survivors;
import xyz.toesinthesand.ashfall.arms.survivor.TraderRoster;
import xyz.toesinthesand.ashfall.arms.survivor.Traders;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * District traders (survivors plan, step 3): reclaiming a district brings its trader to the substation; a trade in the
 * vanilla trading screen takes the payment and gives the goods; an offer runs out and comes back after the restock time;
 * a trader who has sold out says how many days until the restock and opens no screen; a trader takes no orders or roles
 * and counts toward no cap; a killed trader is replaced after the delay. Each test has
 * its own district id with its own stock file in the gametest datapack ({@code survivor/traders/test_trader_*.json}:
 * 3 Canned Food for 2 Bandages twice, then 2 Scrap Metal and a Battery for an Energy Cell once), and passes game times
 * in explicitly (day-clock times, as the timers run on the overworld day clock) instead of waiting days.
 */
public class TraderGameTest {
	private static final int PADDING = 32;
	private static final BlockPos CORE = new BlockPos(3, 1, 3);

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/**
	 * A manual test district over this structure with its core recorded at {@link #CORE} (a stone block stands in for
	 * it), and no trader history: the gametest world is saved between runs.
	 */
	private static String district(GameTestHelper helper, String id) {
		floor(helper);
		helper.setBlock(CORE, Blocks.STONE);
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(7, 7, 7)));
		GridDistricts.registerTest(new GridDistricts.TestDistrict(id, box, Set.of(), GridSettings.DEFAULT, SiegeSettings.DEFAULT, 1, true));
		long core = helper.absolutePos(CORE).asLong();
		GridState.get(helper.getLevel().getServer()).update(helper.getLevel(), id, r -> {
			r.reset();
			r.corePos = core;
		});
		TraderRoster.get(helper.getLevel().getServer()).clear(id);
		return id;
	}

	private static void cleanup(GameTestHelper helper, String id) {
		ServerLevel level = helper.getLevel();
		TraderRoster roster = TraderRoster.get(level.getServer());
		roster.trader(id).map(level::getEntity).ifPresent(e -> e.discard());
		if (GridDistricts.isTest(id)) {
			GridCommand.force(level, id, DistrictState.DARK);
			GridDistricts.unregisterTest(id);
		}
		roster.clear(id);
	}

	private static Survivor traderOf(GameTestHelper helper, String id) {
		UUID uuid = TraderRoster.get(helper.getLevel().getServer()).trader(id).orElse(null);
		helper.assertTrue(uuid != null, "no trader on the roster for " + id);
		helper.assertTrue(helper.getLevel().getEntity(uuid) instanceof Survivor, "the roster's trader is not in the world");
		return (Survivor) helper.getLevel().getEntity(uuid);
	}

	/** A trader for {@code id} spawned straight at (2, 1, 2), no reclaim needed. */
	private static Survivor shop(GameTestHelper helper, String id) {
		floor(helper);
		TraderRoster.get(helper.getLevel().getServer()).clear(id);
		Survivor trader = Traders.spawn(helper.getLevel(), id, helper.absolutePos(new BlockPos(2, 1, 2)));
		helper.assertTrue(trader != null && trader.trade() != null, "trader spawned with a stock");
		return trader;
	}

	/** A survival mock player standing in the structure (vanilla's helper is creative). */
	private static ServerPlayer survivalPlayer(GameTestHelper helper, String name) {
		return survivalPlayer(helper, name, new ArrayList<>());
	}

	/** As above, keeping every chat line it is sent in {@code heard}. */
	private static ServerPlayer survivalPlayer(GameTestHelper helper, String name, List<Component> heard) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}

			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				heard.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.getAbilities().instabuild = false;
		player.getAbilities().invulnerable = false;
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		return player;
	}

	private static int count(Inventory inventory, Item item) {
		int n = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				n += inventory.getItem(i).getCount();
			}
		}
		return n;
	}

	/** Opens the trader's screen the way a right-click does and runs offer {@code index} as the client would (select, shift-click the result). */
	private static MerchantMenu buy(GameTestHelper helper, ServerPlayer player, Survivor trader, int index) {
		Traders.trade(player, trader);
		helper.assertTrue(player.containerMenu instanceof MerchantMenu, "no vanilla trading screen opened, got " + player.containerMenu);
		MerchantMenu menu = (MerchantMenu) player.containerMenu;
		menu.setSelectionHint(index);
		menu.tryMoveItems(index);
		menu.clicked(2, 0, ContainerInput.QUICK_MOVE, player);
		return menu;
	}

	@GameTest(padding = PADDING)
	public void reclaimingADistrictBringsItsTrader(GameTestHelper helper) {
		String id = district(helper, "test_trader_reclaim");
		ServerLevel level = helper.getLevel();
		try {
			GridCommand.force(level, id, DistrictState.RECLAIMED);
			Survivor trader = traderOf(helper, id);
			helper.assertTrue(trader.isTrader() && trader.role() == SurvivorRole.TRADER, "the newcomer is not a trader");
			helper.assertValueEqual(trader.trade().district(), id, "the trader's district");
			helper.assertTrue(!trader.survivorName().isEmpty(), "the trader has no name");
			BlockPos core = helper.absolutePos(CORE);
			helper.assertTrue(trader.blockPosition().closerThan(core, 6.0),
					"the trader stands at " + trader.blockPosition() + ", not by the core at " + core);
			helper.assertTrue(level.getBlockState(trader.blockPosition().below()).isSolidRender(), "the trader is not standing on a floor");
			helper.assertValueEqual(trader.trade().getOffers().size(), 2, "offers from the district's stock file");
			helper.assertTrue(Traders.check(level, id, level.getOverworldClockTime()) == null, "a second trader came to a district that has one");
		} finally {
			cleanup(helper, id);
		}
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void aTradeTakesThePaymentAndGivesTheGoods(GameTestHelper helper) {
		String id = "test_trader_trade";
		Survivor trader = shop(helper, id);
		ServerPlayer player = survivalPlayer(helper, "trader-buyer");
		try {
			player.getInventory().add(new ItemStack(SalvageItems.CANNED_FOOD, 3));
			MerchantMenu menu = buy(helper, player, trader, 0);
			helper.assertValueEqual(count(player.getInventory(), SalvageItems.CANNED_FOOD), 0, "canned food left after paying");
			helper.assertValueEqual(count(player.getInventory(), DirectorItems.BANDAGE), 2, "bandages bought");
			helper.assertValueEqual(menu.getOffers().get(0).getUses(), 1, "the offer's uses after one trade");
			helper.assertTrue(trader.trade().getTradingPlayer() == player, "the trader does not know who it is trading with");

			// too poor for the second offer: nothing changes hands, and closing the screen hands the payment slots back
			player.getInventory().add(new ItemStack(SalvageItems.SCRAP_METAL, 2));
			buy(helper, player, trader, 1);
			helper.assertValueEqual(trader.trade().getOffers().get(1).getUses(), 0, "an unpaid offer was used");
			player.closeContainer();
			helper.assertValueEqual(count(player.getInventory(), SalvageItems.SCRAP_METAL), 2, "scrap metal taken without the battery");
			helper.assertValueEqual(count(player.getInventory(), ArmsItems.ENERGY_CELL), 0, "an energy cell without the battery");
			helper.assertTrue(trader.trade().getTradingPlayer() == null, "closing the screen did not end the trade");
		} finally {
			player.level().getServer().getPlayerList().remove(player);
			cleanup(helper, id);
		}
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void anOfferRunsOutAndRestocks(GameTestHelper helper) {
		String id = "test_trader_restock";
		Survivor trader = shop(helper, id);
		ServerPlayer player = survivalPlayer(helper, "trader-restock");
		try {
			player.getInventory().add(new ItemStack(SalvageItems.CANNED_FOOD, 9));
			buy(helper, player, trader, 0); // shift-click trades until the offer runs out
			player.closeContainer();
			MerchantOffer offer = trader.trade().getOffers().get(0);
			helper.assertTrue(offer.isOutOfStock(), "the offer is not out of stock after " + offer.getUses() + " uses");
			helper.assertValueEqual(count(player.getInventory(), DirectorItems.BANDAGE), 4, "bandages from two trades");
			helper.assertValueEqual(count(player.getInventory(), SalvageItems.CANNED_FOOD), 3, "canned food left after two trades");

			SurvivorTrade trade = trader.trade();
			long restock = trade.restockedAt() + SurvivorBalance.get().trader().restockTicks();
			helper.assertTrue(!trade.restockIfDue(restock - 1), "restocked before the restock time");
			helper.assertTrue(trader.trade().getOffers().get(0).isOutOfStock(), "the offer came back early");
			helper.assertTrue(trade.restockIfDue(restock), "did not restock at the restock time");
			helper.assertTrue(!trader.trade().getOffers().get(0).isOutOfStock(), "the offer is still out of stock after the restock");
			helper.assertValueEqual(trader.trade().getOffers().get(0).getUses(), 0, "uses after the restock");
		} finally {
			player.level().getServer().getPlayerList().remove(player);
			cleanup(helper, id);
		}
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void aSoldOutTraderSaysWhenToComeBack(GameTestHelper helper) {
		String id = "test_trader_sold_out";
		Survivor trader = shop(helper, id);
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = survivalPlayer(helper, "trader-sold-out", heard);
		try {
			trader.trade().getOffers().forEach(MerchantOffer::setToOutOfStock);
			helper.assertTrue(trader.trade().soldOut(), "every offer used up is not sold out");
			Traders.trade(player, trader);
			helper.assertTrue(!(player.containerMenu instanceof MerchantMenu), "a sold-out trader opened the trading screen");
			helper.assertTrue(trader.trade().getTradingPlayer() == null, "a sold-out trader started a trade");
			long said = heard.stream().filter(c -> "message.ashfall.survivor.trader.sold_out".equals(lineKey(c))).count();
			helper.assertValueEqual(said, 1L, "sold-out lines heard");
			int days = trader.trade().daysToRestock(helper.getLevel().getOverworldClockTime());
			int full = (int) Math.ceil(SurvivorBalance.get().trader().restockTicks() / 24_000.0);
			helper.assertTrue(days >= 1 && days <= full, "days to the restock: " + days);
		} finally {
			player.level().getServer().getPlayerList().remove(player);
			cleanup(helper, id);
		}
		helper.succeed();
	}

	/** The line key inside a {@code Name: "line"} reply. */
	private static String lineKey(Component reply) {
		if (reply.getContents() instanceof TranslatableContents says && says.getArgs().length > 1
				&& says.getArgs()[1] instanceof Component line && line.getContents() instanceof TranslatableContents inner) {
			return inner.getKey();
		}
		return "";
	}

	@GameTest(padding = PADDING)
	public void tradersTakeNoOrdersAndCountTowardNoCap(GameTestHelper helper) {
		String id = "test_trader_orders";
		Survivor trader = shop(helper, id);
		ServerPlayer player = survivalPlayer(helper, "trader-orders");
		MinecraftServer server = helper.getLevel().getServer();
		try {
			helper.assertTrue(!SurvivorRole.TRADER.assignable(), "the trader role can be assigned");
			helper.assertTrue(!SurvivorRoster.get(server).contains(trader.getUUID()), "the trader is on the survivor roster (caps count it)");
			for (SurvivorRole role : SurvivorRole.values()) {
				helper.assertTrue(Survivors.roleChangeRefusal(trader, role).isPresent(), "a trader took the " + role.id() + " role");
			}
			server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "survivor " + trader.getUUID() + " follow");
			helper.assertTrue(trader.leader() == null, "a trader took a leader from an order");
			helper.assertValueEqual(trader.mode(), SurvivorMode.STAY, "a trader followed an order");
			helper.assertValueEqual(SurvivorRoster.get(server).groupOf(server, player.getUUID()), 0, "the trader counts toward the player's group");
			helper.assertValueEqual(SurvivorUpkeep.dailyMeal(helper.getLevel(), trader, 5), SurvivorUpkeep.Meal.NOT_SETTLED, "a trader eats");
			helper.assertTrue(trader.isWithinHome(helper.absolutePos(new BlockPos(2, 1, 9))), "the trader's post does not reach 7 blocks");
			helper.assertTrue(!trader.isWithinHome(helper.absolutePos(new BlockPos(2, 1, 12))), "the trader may stray 10 blocks from its post");
		} finally {
			player.level().getServer().getPlayerList().remove(player);
			cleanup(helper, id);
		}
		helper.succeed();
	}

	@GameTest(padding = PADDING, maxTicks = 100)
	public void aKilledTraderIsReplacedAfterTheDelay(GameTestHelper helper) {
		String id = district(helper, "test_trader_replace");
		ServerLevel level = helper.getLevel();
		GridCommand.force(level, id, DistrictState.RECLAIMED);
		Survivor first = traderOf(helper, id);
		UUID firstId = first.getUUID();
		first.kill(level);
		// the body is removed after the death animation
		helper.runAfterDelay(30, () -> {
			try {
				TraderRoster roster = TraderRoster.get(level.getServer());
				helper.assertTrue(roster.trader(id).isEmpty(), "the dead trader is still on the trader roster");
				long due = roster.dueAt(id);
				long now = level.getOverworldClockTime();
				helper.assertTrue(due >= now + SurvivorBalance.get().trader().replaceTicks() - 40,
						"the replacement is due too soon: " + due + " at day-clock " + now);
				helper.assertTrue(Traders.check(level, id, due - 1) == null, "a new trader came before the delay");
				Survivor second = Traders.check(level, id, due);
				helper.assertTrue(second != null, "no new trader after the delay");
				helper.assertTrue(!second.getUUID().equals(firstId), "the dead trader came back");
				helper.assertValueEqual(roster.trader(id).orElse(null), second.getUUID(), "the new trader on the roster");
			} finally {
				cleanup(helper, id);
			}
			helper.succeed();
		});
	}
}
