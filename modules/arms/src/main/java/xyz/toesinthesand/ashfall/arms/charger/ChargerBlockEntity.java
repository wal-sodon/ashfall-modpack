package xyz.toesinthesand.ashfall.arms.charger;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.EnergyStorageUtil;
import team.reborn.energy.api.base.LimitingEnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyStorage;
import xyz.toesinthesand.ashfall.arms.grid.GridPower;
import xyz.toesinthesand.ashfall.arms.gun.ArmsNoise;

/**
 * Shared logic of the Field Charger and the Power Relay: a 960-unit buffer, four charge slots and one
 * Energy Cell slot. Each server tick it fills the buffer (relay generation, then the cell slot) and
 * pushes energy into every chargeable item at the kind's rate.
 * <p>
 * Items are charged through Team Reborn Energy's {@link EnergyStorage#ITEM} lookup on a
 * {@link ContainerItemContext} over the slot, inside a {@link Transaction}. That makes the charger work
 * with anything that exposes item energy (cell packs, Energy Cells, later Plasma Blades, other mods'
 * batteries) without knowing those items, and the transaction rewrites the slot's stack atomically.
 * The buffer is exposed to other mods through {@link EnergyStorage#SIDED} for insertion only, so a
 * Power Relay can't be tapped as a free generator.
 * <p>
 * Inside a reclaimed district ({@link GridPower#powered}) both kinds run without cells: the grid keeps the
 * buffer full, and a Field Charger no longer needs a claim there.
 */
public class ChargerBlockEntity extends BaseContainerBlockEntity {
	/** Items a Field Charger refuses: drained Energy Cells can only be recharged at a Power Relay. */
	public static final TagKey<Item> RELAY_CHARGE_ONLY = TagKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath("ashfall", "relay_charge_only"));
	/** Not registered here: the event is defined in arms' sounds.json, and a direct holder plays it by id. */
	public static final SoundEvent RELAY_HUM = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("ashfall", "relay.hum"));

	/** Menu data slots. */
	public static final int DATA_ENERGY = 0, DATA_STATUS = 1, DATA_COUNT = 2;
	/** Status values in {@link #DATA_STATUS}. */
	public static final int STATUS_IDLE = 0, STATUS_CHARGING = 1, STATUS_NO_CLAIM = 2;
	/** Ticks between claim lookups: claims change rarely and the lookup is not free. */
	private static final int CLAIM_RECHECK_TICKS = 20;

	private final ChargerKind kind;
	private NonNullList<ItemStack> items = NonNullList.withSize(ChargerKind.SLOTS, ItemStack.EMPTY);
	private final SimpleEnergyStorage buffer = new SimpleEnergyStorage(ChargerKind.BUFFER, ChargerKind.BUFFER, ChargerKind.BUFFER) {
		@Override
		protected void onFinalCommit() {
			setChanged();
		}
	};
	private final EnergyStorage external = new LimitingEnergyStorage(buffer, ChargerKind.EXTERNAL_INSERT_PER_TICK, 0);
	private int status = STATUS_IDLE;
	private int chargingTicks;
	/** Game time of the last claim lookup; -1 = never. */
	private long claimCheckedAt = -1;
	private boolean claimAllowed;
	/** Game time of the last grid lookup; -1 = never. */
	private long gridCheckedAt = -1;
	private boolean gridPowered;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_ENERGY -> (int) buffer.amount;
				case DATA_STATUS -> status;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			// Server-authoritative; the client copy lives in the menu's own data.
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public ChargerBlockEntity(BlockPos pos, BlockState state) {
		super(ArmsBlocks.CHARGER_ENTITY, pos, state);
		this.kind = state.getBlock() instanceof ChargerBlock block ? block.kind() : ChargerKind.FIELD_CHARGER;
	}

	public ChargerKind kind() {
		return kind;
	}

	/** The internal buffer (tests and tooling; other mods use {@link #externalStorage}). */
	public SimpleEnergyStorage buffer() {
		return buffer;
	}

	/** What {@link EnergyStorage#SIDED} hands out: insert-only, rate limited. */
	public EnergyStorage externalStorage() {
		return external;
	}

	public int status() {
		return status;
	}

	/** Item energy of a loose stack, or null if it has none. */
	static EnergyStorage energyOf(ItemStack stack) {
		return stack.isEmpty() ? null : ContainerItemContext.withConstant(stack).find(EnergyStorage.ITEM);
	}

	/** Whether {@code stack} may go in a charge slot of this kind of charger. */
	public static boolean canCharge(ChargerKind kind, ItemStack stack) {
		if (kind != ChargerKind.POWER_RELAY && stack.is(RELAY_CHARGE_ONLY)) {
			return false;
		}
		EnergyStorage energy = energyOf(stack);
		return energy != null && energy.supportsInsertion();
	}

	/** Whether {@code stack} may go in the cell slot: anything energy can be drawn from. */
	public static boolean canFuel(ItemStack stack) {
		EnergyStorage energy = energyOf(stack);
		return energy != null && energy.supportsExtraction();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, ChargerBlockEntity be) {
		if (level instanceof ServerLevel server) {
			be.tick(server, pos, state);
		}
	}

	private void tick(ServerLevel level, BlockPos pos, BlockState state) {
		boolean grid = gridPowered(level, pos);
		// On a reclaimed district's grid a Field Charger needs no claim: the grid is the power, not the base.
		if (kind.needsClaim() && !grid && !claimAllowed(level, pos)) {
			setStatus(level, pos, state, STATUS_NO_CLAIM);
			return;
		}

		if (grid && buffer.amount < buffer.capacity) {
			// Free charge: the buffer is topped up from the grid, so the cell slot is never drawn on.
			buffer.amount = buffer.capacity;
			setChanged();
		} else if (kind.generationPerTick() > 0 && buffer.amount < buffer.capacity) {
			buffer.amount = Math.min(buffer.capacity, buffer.amount + kind.generationPerTick());
			setChanged();
		}

		ContainerStorage slots = ContainerStorage.of(this, null);
		if (kind.shouldPullFromCell(buffer.amount)) {
			EnergyStorage cell = ContainerItemContext.ofSingleSlot(slots.getSlot(ChargerKind.CELL_SLOT)).find(EnergyStorage.ITEM);
			if (cell != null && cell.supportsExtraction()) {
				try (Transaction tx = Transaction.openOuter()) {
					EnergyStorageUtil.move(cell, buffer, ChargerKind.CELL_PULL_PER_TICK, tx);
					tx.commit();
				}
			}
		}

		boolean charging = false;
		long due = ChargeRate.unitsThisTick(level.getGameTime(), kind.quartersPerSlotTick());
		for (int slot = 0; slot < ChargerKind.CHARGE_SLOTS && buffer.amount > 0; slot++) {
			ItemStack stack = items.get(slot);
			if (stack.isEmpty() || (kind != ChargerKind.POWER_RELAY && stack.is(RELAY_CHARGE_ONLY))) {
				continue;
			}
			EnergyStorage target = ContainerItemContext.ofSingleSlot(slots.getSlot(slot)).find(EnergyStorage.ITEM);
			if (target == null || !target.supportsInsertion() || target.getAmount() >= target.getCapacity()) {
				continue;
			}
			charging = true; // even on a tick where the fractional rate moves nothing
			if (due > 0) {
				try (Transaction tx = Transaction.openOuter()) {
					EnergyStorageUtil.move(buffer, target, due, tx);
					tx.commit();
				}
			}
		}

		setStatus(level, pos, state, charging ? STATUS_CHARGING : STATUS_IDLE);
		if (charging) {
			if (kind.hums() && chargingTicks % ChargerKind.HUM_INTERVAL == 0) {
				level.playSound(null, pos, RELAY_HUM, SoundSource.BLOCKS, 1.0f, 1.0f);
				ArmsNoise.emit(level, pos, ChargerKind.HUM_NOISE_RADIUS);
			}
			chargingTicks++;
		} else {
			chargingTicks = 0;
		}
	}

	/** Whether a reclaimed district's grid powers this block (cached like the claim lookup). */
	private boolean gridPowered(ServerLevel level, BlockPos pos) {
		long now = level.getGameTime();
		if (gridCheckedAt < 0 || now < gridCheckedAt || now - gridCheckedAt >= CLAIM_RECHECK_TICKS) {
			gridCheckedAt = now;
			gridPowered = GridPower.powered(level, pos);
		}
		return gridPowered;
	}

	/** Whether the grid powers this block as of the last lookup (tests, tooling). */
	public boolean gridPowered() {
		return gridPowered;
	}

	private boolean claimAllowed(ServerLevel level, BlockPos pos) {
		long now = level.getGameTime();
		if (claimCheckedAt < 0 || now < claimCheckedAt || now - claimCheckedAt >= CLAIM_RECHECK_TICKS) {
			claimCheckedAt = now;
			claimAllowed = ChargerClaims.allowed(level, pos);
		}
		return claimAllowed;
	}

	private void setStatus(Level level, BlockPos pos, BlockState state, int newStatus) {
		status = newStatus;
		boolean active = newStatus == STATUS_CHARGING;
		if (newStatus != STATUS_CHARGING) {
			chargingTicks = 0;
		}
		if (state.getValue(ChargerBlock.ACTIVE) != active) {
			level.setBlock(pos, state.setValue(ChargerBlock.ACTIVE, active), Block.UPDATE_ALL);
		}
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.ashfall." + kind.id());
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return ChargerKind.SLOTS;
	}

	/** One item per slot: an energy item's charge is per stack, and a slot context can't split a stack. */
	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == ChargerKind.CELL_SLOT ? canFuel(stack) : canCharge(kind, stack);
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new ChargerMenu(containerId, inventory, this, data, kind);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		buffer.amount = Math.clamp(input.getLongOr("energy", 0L), 0L, buffer.capacity);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
		output.putLong("energy", buffer.amount);
	}
}
