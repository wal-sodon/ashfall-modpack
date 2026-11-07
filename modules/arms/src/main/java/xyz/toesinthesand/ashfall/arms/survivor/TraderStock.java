package xyz.toesinthesand.ashfall.arms.survivor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.arms.item.CellPackItem;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * What each district's trader sells, from {@code data/<ns>/ashfall/survivor/traders/<district>.json} (the file name is
 * the district id; the {@code ashfall} file wins). A district without a file gets no trader. Each offer takes one or two
 * stacks of salvage and gives one stack, at most {@code max_uses} times per restock ({@link SurvivorBalance.Trader}).
 * Energy items are sold full, the way loot and drops hand them out.
 *
 * @param offers in the order the trading screen lists them
 */
public record TraderStock(List<Offer> offers) {
	/**
	 * A payment: {@code count} of {@code id}. Kept as plain data and turned into a vanilla {@link ItemCost} only when the
	 * offers are made, because an {@code ItemCost} builds an item stack and the reload parses files before item
	 * components are bound.
	 */
	public record Cost(Item item, int count) {
		public static final Codec<Cost> CODEC = RecordCodecBuilder.create(i -> i.group(
				BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(Cost::item),
				Codec.intRange(1, 99).optionalFieldOf("count", 1).forGetter(Cost::count)
		).apply(i, Cost::new));

		ItemCost toItemCost() {
			return new ItemCost(item, count);
		}
	}

	/**
	 * @param buy     the first payment
	 * @param buyB    an optional second payment
	 * @param sell    what the player gets
	 * @param maxUses trades before it is out of stock until the next restock
	 */
	public record Offer(Cost buy, Optional<Cost> buyB, ItemStackTemplate sell, int maxUses) {
		public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
				Cost.CODEC.fieldOf("buy").forGetter(Offer::buy),
				Cost.CODEC.optionalFieldOf("buy_b").forGetter(Offer::buyB),
				ItemStackTemplate.CODEC.fieldOf("sell").forGetter(Offer::sell),
				Codec.intRange(1, 64).optionalFieldOf("max_uses", 4).forGetter(Offer::maxUses)
		).apply(i, Offer::new));

		/** A fresh vanilla offer: no uses yet, no XP, no price swings. */
		MerchantOffer toMerchantOffer() {
			ItemStack result = sell.create();
			if (result.getItem() instanceof CellPackItem pack && result.getCount() == 1) {
				pack.setStoredEnergy(result, pack.getEnergyCapacity(result));
			}
			return new MerchantOffer(buy.toItemCost(), buyB.map(Cost::toItemCost), result, maxUses, 0, 0f);
		}
	}

	public static final Codec<TraderStock> CODEC = RecordCodecBuilder.create(i -> i.group(
			Offer.CODEC.listOf().fieldOf("offers").forGetter(TraderStock::offers)
	).apply(i, TraderStock::new));

	private static volatile Map<String, TraderStock> stocks = Map.of();

	/** The stock of {@code district}, if it has a trader at all. */
	public static Optional<TraderStock> of(String district) {
		return Optional.ofNullable(stocks.get(district));
	}

	/** Every district with a stock file. */
	public static Set<String> districts() {
		return stocks.keySet();
	}

	/** A full set of fresh offers. */
	public MerchantOffers fresh() {
		MerchantOffers out = new MerchantOffers();
		for (Offer offer : offers) {
			out.add(offer.toMerchantOffer());
		}
		return out;
	}

	public static final class Loader extends SimpleJsonResourceReloadListener<TraderStock> {
		public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "survivor_traders");

		public Loader() {
			super(CODEC, FileToIdConverter.json("ashfall/survivor/traders"));
		}

		@Override
		protected void apply(Map<Identifier, TraderStock> loaded, ResourceManager manager, ProfilerFiller profiler) {
			Map<String, TraderStock> next = new TreeMap<>();
			loaded.forEach((id, stock) -> {
				if (!next.containsKey(id.getPath()) || "ashfall".equals(id.getNamespace())) {
					next.put(id.getPath(), stock);
				}
			});
			stocks = Map.copyOf(next);
			AshfallArms.LOGGER.info("Loaded trader stock for {} districts", next.size());
		}
	}
}
