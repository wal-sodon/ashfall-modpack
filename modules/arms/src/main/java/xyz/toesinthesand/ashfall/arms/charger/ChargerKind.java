package xyz.toesinthesand.ashfall.arms.charger;

/**
 * The charger flavours and their balance numbers (spec B, "Chargers"). One block entity class runs
 * both; everything that differs between them is a field here, so tuning never touches the logic.
 * <p>
 * Rates are in quarter energy units per tick because the Field Charger's 0.25/tick per slot is below
 * one whole energy unit and Team Reborn Energy only moves whole units; {@link ChargeRate} spreads the
 * quarters over ticks exactly.
 */
public enum ChargerKind {
	/**
	 * Player-craftable and slow, and only inside a claim: bases get a trickle charger, the open world
	 * does not. Has no power of its own: it runs on the Energy Cell in its cell slot (or energy piped in).
	 */
	FIELD_CHARGER("field_charger", 1, 0, true, false),
	/**
	 * The Field Charger rebuilt with alloy and spare electronics: three times the rate, same claim rule and same
	 * Energy Cell power (so it only empties the cell faster, it makes no free energy). A late-game use for scrap.
	 */
	RAPID_CHARGER("rapid_charger", 3, 0, true, false),
	/**
	 * A world fixture on the old grid: fast, works anywhere, powers itself, and hums loudly enough to draw
	 * the horde, which is the price of using one. Generation covers all four slots at full rate, so its
	 * cell slot is only drawn on if generation ever falls short (see {@link #shouldPullFromCell}).
	 */
	POWER_RELAY("power_relay", 8, 8, false, true);

	/** Energy the block can hold (both kinds). */
	public static final long BUFFER = 960;
	/** Charge slots, then the cell slot. */
	public static final int CHARGE_SLOTS = 4;
	public static final int CELL_SLOT = CHARGE_SLOTS;
	public static final int SLOTS = CHARGE_SLOTS + 1;
	/** Energy pulled from the cell slot into the buffer per tick, enough to keep every slot busy. */
	public static final long CELL_PULL_PER_TICK = 4;
	/** Energy other mods may push in per tick through {@code EnergyStorage.SIDED}; nothing may be pulled out. */
	public static final long EXTERNAL_INSERT_PER_TICK = 32;
	/** Ticks between relay hums (and noise events) while charging. */
	public static final int HUM_INTERVAL = 40;
	/** Noise radius of a humming relay, in blocks. */
	public static final int HUM_NOISE_RADIUS = 48;

	private final String id;
	private final int quartersPerSlotTick;
	private final long generationPerTick;
	private final boolean needsClaim;
	private final boolean hums;

	ChargerKind(String id, int quartersPerSlotTick, long generationPerTick, boolean needsClaim, boolean hums) {
		this.id = id;
		this.quartersPerSlotTick = quartersPerSlotTick;
		this.generationPerTick = generationPerTick;
		this.needsClaim = needsClaim;
		this.hums = hums;
	}

	/** Registry path, {@code ashfall:<id>}. */
	public String id() {
		return id;
	}

	/** Charge rate per slot, in quarter energy units per tick. */
	public int quartersPerSlotTick() {
		return quartersPerSlotTick;
	}

	/** Energy the block produces on its own each tick. */
	public long generationPerTick() {
		return generationPerTick;
	}

	/** Whether the block only works inside a claim (when a claims mod is present). */
	public boolean needsClaim() {
		return needsClaim;
	}

	/** Whether charging plays the hum and makes noise. */
	public boolean hums() {
		return hums;
	}

	/** Most energy one tick of charging can take out of the buffer (every slot at full rate, rounded up). */
	public long maxDrainPerTick() {
		return (CHARGE_SLOTS * (long) quartersPerSlotTick + 3) / 4;
	}

	/**
	 * Whether the Energy Cell slot should be drawn on this tick, given the buffer after generation.
	 * <p>
	 * A kind without power of its own fills its buffer from the cell. A self-powered kind only touches
	 * the cell when the buffer can't cover a full tick of charging: otherwise a cell left in a Power
	 * Relay's cell slot would be emptied into a buffer the relay would have filled for free anyway.
	 */
	public boolean shouldPullFromCell(long buffered) {
		if (generationPerTick <= 0) {
			return buffered < BUFFER;
		}
		return buffered < maxDrainPerTick();
	}
}
