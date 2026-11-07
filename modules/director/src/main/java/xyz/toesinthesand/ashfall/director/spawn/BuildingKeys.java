package xyz.toesinthesand.ashfall.director.spawn;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.lock.KeyData;
import xyz.toesinthesand.ashfall.salvage.lock.Strongboxes;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Director's half of building keys and safes: when a building's first population spawns, salvage may put a locked
 * Strongbox inside ({@link Strongboxes#tryPlace}), and if it does one of the new zombies carries the key. The key is
 * dropped from here on that zombie's death rather than from a loot table, because a loot table can't write the
 * building id into the key.
 */
public final class BuildingKeys {
	/** The key a building zombie carries. Persistent, so the carrier still drops it after a restart. */
	public static final AttachmentType<KeyData> CARRIER = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath("ashfall", "key_carrier"), KeyData.CODEC);

	private BuildingKeys() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register(BuildingKeys::onDeath);
	}

	/**
	 * Called once per building, right after its first population spawned: maybe a Strongbox, and if so a key carrier
	 * picked at random from {@code members}. With no members the box still goes in; the crowbar opens it.
	 *
	 * @return where the box went, if anywhere
	 */
	static Optional<BlockPos> firstPopulation(ServerLevel level, Building building, List<UUID> members) {
		Identifier profile = BuildingProfiles.classifyByTags(building);
		int tier = DirectorContexts.zoneOf(building).tier();
		Optional<BlockPos> box = Strongboxes.tryPlace(level, building, profile, tier, level.getRandom());
		if (box.isPresent() && !members.isEmpty()) {
			Entity carrier = level.getEntity(members.get(level.getRandom().nextInt(members.size())));
			if (carrier != null) {
				carrier.setAttached(CARRIER, new KeyData(building.id(), Strongboxes.keyName(building, profile)));
			}
		}
		return box;
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		KeyData key = entity.getAttached(CARRIER);
		if (key != null && entity.level() instanceof ServerLevel level) {
			entity.removeAttached(CARRIER);
			entity.spawnAtLocation(level, Strongboxes.key(key));
		}
	}
}
