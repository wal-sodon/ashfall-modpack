package xyz.toesinthesand.ashfall.arms.bench;

import net.minecraft.world.item.ItemStack;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentItem;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentStats;
import xyz.toesinthesand.ashfall.arms.gun.EnergyGunItem;
import xyz.toesinthesand.ashfall.arms.gun.GunAttachments;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What the Weaponsmith Bench allows. Kept apart from the menu so gametests check the rules (slots,
 * mutual exclusion, district gating) without opening a screen, and so the gate lives in one place.
 * <p>
 * Gating is by district rather than by recipe unlocks: the endgame design ties the Heat Sink and the
 * Cell Regulator to South Slope's reclaimed grid, and an attachment found in loot before then must still
 * wait for that, so the bench refuses to fit it instead of the crafting table refusing to make it.
 */
public final class BenchRules {
	/** The bench's verdict for the current inputs; the ordinal is the menu's synced status. */
	public enum Status {
		EMPTY, READY, NOT_A_GUN, NOT_AN_ATTACHMENT, ALREADY_FITTED, LOCKED;

		public static Status byOrdinal(int ordinal) {
			Status[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : EMPTY;
		}
	}

	/** A fitted gun, and the attachments it pushed out of the same slot (handed back to the player). */
	public record Fit(ItemStack gun, List<String> replaced) {
	}

	private BenchRules() {
	}

	/**
	 * Whether {@code attachment} may be fitted to {@code gun}. {@code lookup} gives an attachment's stats
	 * (null for an unknown id); {@code reclaimed} says whether a district's grid is back.
	 */
	public static Status check(ItemStack gun, ItemStack attachment, Function<String, AttachmentStats> lookup,
							   Predicate<String> reclaimed) {
		if (gun.isEmpty() || attachment.isEmpty()) {
			return Status.EMPTY;
		}
		if (!(gun.getItem() instanceof EnergyGunItem)) {
			return Status.NOT_A_GUN;
		}
		String id = AttachmentItem.attachmentId(attachment);
		AttachmentStats stats = id == null ? null : lookup.apply(id);
		if (stats == null) {
			return Status.NOT_AN_ATTACHMENT;
		}
		if (gun.getOrDefault(ArmsComponents.ATTACHMENTS, GunAttachments.NONE).has(id)) {
			return Status.ALREADY_FITTED;
		}
		if (stats.requiresDistrict().isPresent() && !reclaimed.test(stats.requiresDistrict().get())) {
			return Status.LOCKED;
		}
		return Status.READY;
	}

	/**
	 * {@code gun} (one item, every other component kept) with attachment {@code id} fitted. Anything already in
	 * the same slot is taken off, which is what makes the Cell Regulator and the Overcharger exclusive.
	 */
	public static Fit fit(ItemStack gun, String id, Function<String, AttachmentStats> lookup) {
		AttachmentStats stats = lookup.apply(id);
		GunAttachments attachments = gun.getOrDefault(ArmsComponents.ATTACHMENTS, GunAttachments.NONE);
		List<String> replaced = new ArrayList<>();
		if (stats != null) {
			for (String other : attachments.ids()) {
				AttachmentStats otherStats = lookup.apply(other);
				if (!other.equals(id) && otherStats != null && otherStats.slot().equals(stats.slot())) {
					replaced.add(other);
				}
			}
		}
		for (String other : replaced) {
			attachments = attachments.without(other);
		}
		ItemStack result = gun.copyWithCount(1);
		result.set(ArmsComponents.ATTACHMENTS, attachments.with(id));
		return new Fit(result, List.copyOf(replaced));
	}
}
