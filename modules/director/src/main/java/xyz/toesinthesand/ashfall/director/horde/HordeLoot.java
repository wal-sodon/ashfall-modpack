package xyz.toesinthesand.ashfall.director.horde;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Each horde wave's own drop table (DeceasedCraft-style): a wave's zombies carry the wave's {@code loot_table}, and
 * it is rolled on death on top of their normal drops. An attachment, not the mob's own loot table field, because
 * vanilla gives no way to set that field from outside, and because extra drops keep the zombie's own (rotten flesh,
 * worn gear) instead of replacing them.
 */
public final class HordeLoot {
	/** Persistent, so a horde zombie in an unloaded chunk still drops its wave's loot after a restart. */
	public static final AttachmentType<Identifier> TABLE = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath("ashfall", "horde_loot"), Identifier.CODEC);

	private HordeLoot() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Identifier table = entity.getAttached(TABLE);
			if (table != null && entity.level() instanceof ServerLevel level) {
				entity.dropFromLootTable(level, source, source.getEntity() instanceof Player,
						ResourceKey.create(Registries.LOOT_TABLE, table));
			}
		});
	}
}
