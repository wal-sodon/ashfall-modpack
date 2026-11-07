package xyz.toesinthesand.ashfall.arms.survivor;

import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A trader's side of the vanilla trading screen: the {@link Merchant} a {@link Survivor} with the {@link SurvivorRole#TRADER}
 * role delegates to, so the stock opens in the vanilla {@code MerchantMenu} and the client needs no screen of its own.
 * The menu itself is opened with the survivor as its merchant, not this object: vanilla's menu casts its merchant to an
 * entity to play the trade sound.
 * It holds the district whose stock it sells ({@link TraderStock}), the offers with their uses, and when it last restocked
 * (on the overworld day clock, like every other "days" rule, so nights slept through count); saved on the survivor under
 * {@code "trade"}. No XP, no levels, no price swings: the price in the stock file is the price.
 * One player trades at a time.
 */
public final class SurvivorTrade implements Merchant {
	private static final long DAY_TICKS = 24_000L;

	private final Survivor trader;
	private String district = "";
	private MerchantOffers offers = new MerchantOffers();
	/** Day-clock time ({@code getOverworldClockTime}) of the last restock. */
	private long restockedAt;
	private @Nullable Player customer;

	SurvivorTrade(Survivor trader) {
		this.trader = trader;
	}

	public String district() {
		return district;
	}

	public long restockedAt() {
		return restockedAt;
	}

	/** Takes up {@code district}'s stock, fresh, as of day-clock time {@code now}. */
	void start(String district, long now) {
		this.district = district;
		restock(now);
	}

	/**
	 * Restocks when {@link SurvivorBalance.Trader#restockTicks} of the day clock have passed since the last time (day-clock
	 * time {@code now} is passed in for gametests; a clock turned back to before the last restock restocks at once): the
	 * offers come fresh from the district's current stock file, so a datapack change shows up at the next restock. A
	 * district whose file is gone keeps its old offers, with their uses reset.
	 *
	 * @return whether it restocked
	 */
	public boolean restockIfDue(long now) {
		if (now >= restockedAt && now - restockedAt < SurvivorBalance.get().trader().restockTicks()) {
			return false;
		}
		restock(now);
		return true;
	}

	/** Every offer used up (a trader with no offers at all counts as sold out too). */
	public boolean soldOut() {
		return offers.stream().allMatch(MerchantOffer::isOutOfStock);
	}

	/** Whole days (at least 1) from day-clock time {@code now} until the next restock. */
	public int daysToRestock(long now) {
		long left = restockedAt + SurvivorBalance.get().trader().restockTicks() - now;
		return (int) Math.max(1L, (left + DAY_TICKS - 1) / DAY_TICKS);
	}

	private void restock(long now) {
		TraderStock stock = TraderStock.of(district).orElse(null);
		if (stock != null) {
			offers = stock.fresh();
		} else {
			offers.forEach(MerchantOffer::resetUses);
		}
		restockedAt = now;
	}

	/** Whether someone other than {@code player} has the trading screen open right now. */
	boolean busyWith(Player player) {
		return customer != null && customer != player && stillValid(customer) && customer.containerMenu instanceof MerchantMenu;
	}

	// ---- Merchant ----

	@Override
	public void setTradingPlayer(@Nullable Player player) {
		customer = player;
		if (player != null) {
			trader.getNavigation().stop();
		}
	}

	@Override
	public @Nullable Player getTradingPlayer() {
		return customer;
	}

	@Override
	public MerchantOffers getOffers() {
		return offers;
	}

	/** Client-side only in vanilla (the screen's copy); the server's offers are never replaced from outside. */
	@Override
	public void overrideOffers(MerchantOffers offers) {
	}

	@Override
	public void notifyTrade(MerchantOffer offer) {
		offer.increaseUses();
		trader.playSound(getNotifyTradeSound(), 1.0f, 1.0f);
	}

	@Override
	public void notifyTradeUpdated(ItemStack stack) {
	}

	@Override
	public int getVillagerXp() {
		return 0;
	}

	@Override
	public void overrideXp(int xp) {
	}

	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return SoundEvents.ITEM_PICKUP;
	}

	@Override
	public boolean isClientSide() {
		return false;
	}

	@Override
	public boolean stillValid(Player player) {
		return trader.isAlive() && !trader.isRemoved() && customer == player && player.distanceToSqr(trader) <= 64.0;
	}

	// ---- save ----

	void save(ValueOutput output) {
		output.putString("district", district);
		output.store("offers", MerchantOffers.CODEC, offers);
		output.putLong("restocked_clock", restockedAt);
	}

	void load(ValueInput input) {
		district = input.getStringOr("district", "");
		offers = input.read("offers", MerchantOffers.CODEC).orElseGet(MerchantOffers::new);
		restockedAt = input.read("restocked_clock", Codec.LONG).orElseGet(() -> legacyRestock(input.getLongOr("restocked_at", 0L)));
	}

	/**
	 * A save from before the day clock: {@code restocked_at} was game time. Carries the time already waited over onto the
	 * clock, so the restock is still due after the same wait (or at once if the level is not there to ask).
	 */
	private long legacyRestock(long gameTime) {
		if (trader.level() instanceof ServerLevel level) {
			return level.getOverworldClockTime() - Math.max(0L, level.getGameTime() - gameTime);
		}
		return Long.MIN_VALUE / 2;
	}
}
