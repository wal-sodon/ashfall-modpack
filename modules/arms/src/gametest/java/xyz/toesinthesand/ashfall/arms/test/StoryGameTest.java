package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.charger.ArmsBlocks;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;
import xyz.toesinthesand.ashfall.arms.grid.GridEvents;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.story.RadioProgress;
import xyz.toesinthesand.ashfall.arms.story.RelayLog;
import xyz.toesinthesand.ashfall.arms.story.RelayLogs;
import xyz.toesinthesand.ashfall.arms.story.Story;
import xyz.toesinthesand.ashfall.arms.story.StoryItems;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The story layer on a real server with the shipped data: Relay Logs (pool loaded, relay picks stable and spread,
 * one book per log per player), the "Relight" advancements (all load; district criteria fire from a fake
 * {@code DISTRICT_CHANGED} and from a real {@link GridState} update) and the story caches (the Field Radio cache rolls the radio; the site-document caches
 * roll their documents through {@code ashfall:relay_document}).
 */
public class StoryGameTest {
	private static final List<String> ADVANCEMENTS = List.of("root", "first_log", "logs_10", "logs_all", "grid_coupler",
			"grid_80", "asheville_relit", "boss_abomination", "boss_patient_zero", "boss_juggernaut",
			"river_arts_repaired", "river_arts_energized", "river_arts_reclaimed",
			"south_slope_repaired", "south_slope_energized", "south_slope_reclaimed",
			"downtown_repaired", "downtown_energized", "downtown_reclaimed",
			"mission_repaired", "mission_energized", "mission_reclaimed");

	private static AdvancementHolder advancement(MinecraftServer server, String name) {
		return server.getAdvancements().get(Identifier.fromNamespaceAndPath("ashfall", "relight/" + name));
	}

	private static boolean done(ServerPlayer player, String name) {
		AdvancementHolder holder = advancement(player.level().getServer(), name);
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	@GameTest
	public void relayLogPoolAndAdvancementsLoad(GameTestHelper helper) {
		helper.assertTrue(RelayLogs.pool().size() == 30, "expected 30 Relay Logs, got " + RelayLogs.pool().size());
		for (int i = 0; i < RelayLogs.pool().size(); i++) {
			helper.assertTrue(RelayLogs.pool().get(i).number() == i + 1, "Relay Logs are not numbered 1..30 without gaps");
		}
		for (String site : List.of("warehouse", "hospital", "hull")) {
			Optional<RelayLog> doc = RelayLogs.get(Identifier.fromNamespaceAndPath("ashfall", "site/" + site));
			helper.assertTrue(doc.isPresent() && doc.get().isSiteDocument(), "site document " + site + " missing");
		}
		MinecraftServer server = helper.getLevel().getServer();
		for (String name : ADVANCEMENTS) {
			helper.assertTrue(advancement(server, name) != null, "advancement ashfall:relight/" + name + " did not load");
		}
		helper.succeed();
	}

	@GameTest
	public void relayLogSelectionIsDeterministicAndSpread(GameTestHelper helper) {
		Set<Integer> seen = new HashSet<>();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		for (int i = 0; i < 300; i++) {
			// A street of relays a few blocks apart, the case a plain modulo would hand out in sequence.
			BlockPos pos = origin.offset(i * 7, 0, (i % 5) * 3);
			int first = RelayLogs.forRelay(pos).orElseThrow().number();
			int again = RelayLogs.forRelay(pos.immutable()).orElseThrow().number();
			helper.assertTrue(first == again, "relay at " + pos + " gave log " + first + " then " + again);
			seen.add(first);
		}
		helper.assertTrue(seen.size() >= 25, "300 relays reached only " + seen.size() + " of 30 logs");
		helper.succeed();
	}

	@GameTest
	public void relayLogOncePerPlayer(GameTestHelper helper) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, ArmsBlocks.POWER_RELAY);
		BlockPos relay = helper.absolutePos(rel);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().clearContent();
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(StoryItems.FIELD_RADIO));
		int expected = RelayLogs.forRelay(relay).orElseThrow().number();

		RelayLogs.Tuned first = Story.tuneRelay(player, relay);
		helper.assertTrue(first.result() == RelayLogs.Result.NEW, "first tune: " + first.result());
		helper.assertTrue(first.log().orElseThrow().number() == expected, "tuned log differs from the relay's pick");
		helper.assertTrue(books(player) == 1, "expected one book after the first tune, found " + books(player));
		ItemStack book = findBook(player);
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(content != null && content.title().raw().equals("Relay Log #" + expected), "book title: " + (content == null ? null : content.title().raw()));
		RadioProgress progress = RadioProgress.get(player);
		helper.assertTrue(progress.hasLog(expected) && progress.relays().contains(relay.asLong()) && progress.radio(), "progress not recorded: " + progress);
		helper.assertTrue(done(player, "first_log"), "first_log advancement not granted");

		RelayLogs.Tuned second = Story.tuneRelay(player, relay);
		helper.assertTrue(second.result() == RelayLogs.Result.ALREADY_HEARD, "second tune: " + second.result());
		helper.assertTrue(books(player) == 1, "the same log was given twice");

		// A second player still gets their own copy.
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		other.getInventory().clearContent();
		helper.assertTrue(Story.tuneRelay(other, relay).result() == RelayLogs.Result.NEW, "a second player was refused the log");
		helper.succeed();
	}

	private static int books(ServerPlayer player) {
		int n = 0;
		for (ItemStack stack : player.getInventory()) {
			if (stack.is(Items.WRITTEN_BOOK)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static ItemStack findBook(ServerPlayer player) {
		for (ItemStack stack : player.getInventory()) {
			if (stack.is(Items.WRITTEN_BOOK)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	@GameTest
	public void districtChangeFiresRelightCriteria(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(!done(player, "river_arts_repaired"), "fresh player already has river_arts_repaired");
		GridEvents.DISTRICT_CHANGED.invoker().onChange(level, "river_arts", DistrictState.DARK, DistrictState.ENERGIZED);
		helper.assertTrue(done(player, "river_arts_repaired"), "energizing River Arts did not grant river_arts_repaired");
		helper.assertTrue(done(player, "river_arts_energized"), "energizing River Arts did not grant river_arts_energized");
		helper.assertTrue(!done(player, "river_arts_reclaimed"), "energizing River Arts granted river_arts_reclaimed");
		helper.assertTrue(!done(player, "south_slope_repaired"), "a River Arts change granted a South Slope advancement");
		GridEvents.DISTRICT_CHANGED.invoker().onChange(level, "mission", DistrictState.SIEGE, DistrictState.RECLAIMED);
		helper.assertTrue(done(player, "mission_reclaimed") && done(player, "mission_repaired"), "reclaiming Mission did not grant its chain");
		helper.succeed();
	}

	/**
	 * End to end with the real grid: a district changing state through {@link GridState#update} (what the loop,
	 * the siege and {@code /ashfall grid set} all use) grants the Relight advancement, with no fake event.
	 */
	@GameTest
	public void realGridChangeGrantsRelightAdvancement(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		GridState grid = GridState.get(level.getServer());
		String id = "downtown";
		DistrictState before = grid.state(id);
		try {
			grid.update(level, id, r -> r.state = DistrictState.REPAIRED);
			helper.assertTrue(done(player, "downtown_repaired"), "a real Downtown repair did not grant downtown_repaired");
			helper.assertTrue(!done(player, "downtown_energized"), "a real Downtown repair granted downtown_energized");
		} finally {
			grid.update(level, id, r -> r.state = before);
		}
		helper.succeed();
	}

	@GameTest
	public void storyCachesRoll(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
				.create(LootContextParamSets.CHEST);
		for (long seed = 0; seed < 20; seed++) {
			helper.assertTrue(roll(level, "field_radio", params, seed).stream().anyMatch(s -> s.is(StoryItems.FIELD_RADIO)),
					"ashfall_cache:field_radio rolled no Field Radio (seed " + seed + ")");
		}
		// The radio cache also always carries the Survivor's Notes primer, so a new player's first chest explains the basics.
		Map<String, String> documents = Map.of("kiln_office", "Work Order 0417", "hospital_supply", "Incident Report: Deep Ward",
				"wreck_hull", "Inside the Hull", "field_radio", "Survivor's Notes");
		documents.forEach((table, title) -> {
			boolean found = roll(level, table, params, 7).stream().anyMatch(s -> {
				WrittenBookContent c = s.get(DataComponents.WRITTEN_BOOK_CONTENT);
				return s.is(Items.WRITTEN_BOOK) && c != null && c.title().raw().equals(title) && !c.pages().isEmpty();
			});
			helper.assertTrue(found, "ashfall_cache:" + table + " did not roll the document \"" + title + "\"");
		});
		helper.succeed();
	}

	private static List<ItemStack> roll(ServerLevel level, String table, LootParams params, long seed) {
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("ashfall_cache", table));
		LootTable loot = level.getServer().reloadableRegistries().lookup().get(key).orElseThrow().value();
		return loot.getRandomItems(params, seed);
	}
}
