package xyz.toesinthesand.ashfall.arms.bench;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.story.StoryEffects;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentItem;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentStatsManager;
import xyz.toesinthesand.ashfall.arms.gun.EnergyGunItem;

import java.util.function.Predicate;

/**
 * Weaponsmith Bench menu: a blaster slot, an attachment slot and a result slot, in the anvil's layout.
 * Built on {@link ItemCombinerMenu} (the anvil's and smithing table's base), which already does the
 * block-distance check, the shift-click routing and handing the inputs back on close.
 * <p>
 * The result is only ever computed on the server: the district gate reads {@link GridState}, which the
 * client does not have, so a client-side guess could flash a result the server then refuses. The
 * verdict is synced in one data slot so the screen can say why nothing is offered.
 */
public class WeaponsmithBenchMenu extends ItemCombinerMenu {
	public static final int GUN_SLOT = 0, ATTACHMENT_SLOT = 1;
	public static final int GUN_X = 27, ATTACHMENT_X = 76, RESULT_X = 134, SLOT_Y = 35;

	private final DataSlot status = DataSlot.standalone();

	/** Client side: the mirror filled by the server's slot and data updates. */
	public WeaponsmithBenchMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, ContainerLevelAccess.NULL);
	}

	public WeaponsmithBenchMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(ArmsBench.MENU, containerId, inventory, access, ItemCombinerMenuSlotDefinition.create()
				.withSlot(GUN_SLOT, GUN_X, SLOT_Y, stack -> stack.getItem() instanceof EnergyGunItem)
				.withSlot(ATTACHMENT_SLOT, ATTACHMENT_X, SLOT_Y, stack -> stack.getItem() instanceof AttachmentItem)
				.withResultSlot(2, RESULT_X, SLOT_Y)
				.build());
		addDataSlot(status);
	}

	public BenchRules.Status status() {
		return BenchRules.Status.byOrdinal(status.get());
	}

	@Override
	protected boolean isValidBlock(BlockState state) {
		return state.is(ArmsBench.WEAPONSMITH_BENCH);
	}

	@Override
	protected boolean mayPickup(Player player, boolean hasItem) {
		return hasItem && status() == BenchRules.Status.READY;
	}

	/** Whether a district is reclaimed, as the server sees it (the client never decides). */
	private Predicate<String> reclaimed() {
		// The Bind ending unlocks the Overcharge mods without district gating (StoryEffects#unlockAttachments).
		return district -> player.level() instanceof ServerLevel level
				&& (StoryEffects.unlockAttachments(level.getServer()) || GridState.get(level.getServer()).isReclaimed(district));
	}

	@Override
	public void createResult() {
		if (player.level().isClientSide()) {
			return;
		}
		ItemStack gun = inputSlots.getItem(GUN_SLOT);
		ItemStack attachment = inputSlots.getItem(ATTACHMENT_SLOT);
		BenchRules.Status verdict = BenchRules.check(gun, attachment, id -> AttachmentStatsManager.get(false, id), reclaimed());
		status.set(verdict.ordinal());
		if (verdict == BenchRules.Status.READY) {
			resultSlots.setItem(0, BenchRules.fit(gun, AttachmentItem.attachmentId(attachment),
					id -> AttachmentStatsManager.get(false, id)).gun());
		} else {
			resultSlots.setItem(0, ItemStack.EMPTY);
		}
		broadcastChanges();
	}

	/** Uses up the gun and one attachment, and hands back whatever the new attachment pushed out of its slot. */
	@Override
	protected void onTake(Player player, ItemStack result) {
		ItemStack gun = inputSlots.getItem(GUN_SLOT);
		ItemStack attachment = inputSlots.getItem(ATTACHMENT_SLOT);
		String id = AttachmentItem.attachmentId(attachment);
		if (id != null && !gun.isEmpty()) {
			for (String replaced : BenchRules.fit(gun, id, other -> AttachmentStatsManager.get(false, other)).replaced()) {
				Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("ashfall", replaced));
				if (item instanceof AttachmentItem) {
					player.getInventory().placeItemBackInInventory(new ItemStack(item), Prediction.SERVER_ONLY);
				}
			}
		}
		inputSlots.setItem(GUN_SLOT, ItemStack.EMPTY);
		attachment.shrink(1);
		inputSlots.setItem(ATTACHMENT_SLOT, attachment);
		access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0f, 1.1f));
	}
}
