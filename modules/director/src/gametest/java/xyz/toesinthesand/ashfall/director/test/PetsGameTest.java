package xyz.toesinthesand.ashfall.director.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;
import xyz.toesinthesand.ashfall.director.pets.Dogs;
import xyz.toesinthesand.ashfall.director.pets.Friendly;
import xyz.toesinthesand.ashfall.director.pets.StrayDogs;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.traps.SpikeStripBlock;

import java.util.UUID;

/**
 * Dogs on a real server: a tamed dog goes for a zombie near its owner with the small bite, makes no stimulus doing
 * it, and growls to its owner; friendly fire (the spike strip, {@link Friendly}) spares it; Rotten Flesh tames a
 * stray.
 */
public class PetsGameTest {
	private static final int PADDING = 24;

	private static void floor(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	@GameTest(maxTicks = 300, padding = PADDING)
	public void tamedDogAttacksZombieSilently(GameTestHelper helper) {
		floor(helper, 8);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = mockPlayer(helper, "dog-owner");
		Vec3 ownerAt = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
		owner.teleportTo(ownerAt.x, ownerAt.y, ownerAt.z);
		Wolf dog = helper.spawn(EntityTypes.WOLF, new Vec3(2.5, 1, 2.5));
		dog.tame(owner);
		helper.assertTrue(dog.getAttributeValue(Attributes.ATTACK_DAMAGE) == Dogs.BITE_DAMAGE,
				"a dog bites for " + Dogs.BITE_DAMAGE + ", got " + dog.getAttributeValue(Attributes.ATTACK_DAMAGE));
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(6.5, 1, 6.5));
		helper.assertTrue(Dogs.guards(dog, zombie), "a tamed dog guards against a zombie near its owner");
		helper.assertFalse(Dogs.guards(dog, owner), "a dog never guards against a player");
		helper.assertTrue(Dogs.growl(level, dog) == zombie, "the dog growls at the zombie for its owner");
		float before = zombie.getHealth();
		helper.succeedWhen(() -> {
			// by the dog, not the sun: a zombie in daylight burns on its own
			helper.assertTrue(zombie.getHealth() < before && zombie.getLastHurtByMob() == dog, "the dog has not bitten the zombie yet");
			helper.assertTrue(Stimuli.query(level, dog.position(), 1.0).stream().noneMatch(s -> s.sourceId() == dog.getId()),
					"a dog must not leave a stimulus");
			level.getServer().getPlayerList().remove(owner);
		});
	}

	@GameTest(padding = PADDING)
	public void friendlyFireSparesDogs(GameTestHelper helper) {
		floor(helper, 4);
		BlockPos stripPos = new BlockPos(1, 1, 1);
		helper.setBlock(stripPos, DirectorBlocks.SPIKE_STRIP);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(stripPos);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Wolf dog = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new Vec3(1.5, 1, 1.5));
		helper.assertFalse(Friendly.isFriendly(dog, player), "a wild dog is not a pet");
		dog.tame(player);
		helper.assertTrue(Friendly.isFriendly(dog, null), "a tamed dog is friendly to everyone");
		helper.assertTrue(Friendly.isFriendly(player, null), "players are friendly");
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 1, 2.5));
		helper.assertFalse(Friendly.isFriendly(zombie, player), "zombies are not friendly");
		float before = dog.getHealth();
		helper.assertFalse(SpikeStripBlock.stepOn(level.getBlockState(abs), level, abs, dog), "the strip must not hurt a dog");
		helper.assertTrue(dog.getHealth() == before, "dog health changed from " + before + " to " + dog.getHealth());
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void rottenFleshTamesStray(GameTestHelper helper) {
		floor(helper, 4);
		ServerLevel level = helper.getLevel();
		Wolf dog = StrayDogs.spawn(level, helper.absolutePos(new BlockPos(1, 1, 1)));
		helper.assertTrue(dog != null, "a stray spawns");
		helper.assertTrue(dog.getCustomName() != null, "a stray carries its name");
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ROTTEN_FLESH, 64));
		int fed = 0;
		// 1 in 3 per piece: 60 misses in a row is about 1 in 10^10
		while (!dog.isTame() && fed < 60) {
			dog.mobInteract(player, InteractionHand.MAIN_HAND);
			fed++;
		}
		helper.assertTrue(dog.isTame() && dog.isOwnedBy(player), "Rotten Flesh tamed the stray after " + fed + " pieces");
		helper.assertTrue(player.getMainHandItem().getCount() == 64 - fed, "each try uses one Rotten Flesh");
		helper.assertTrue(dog.isOrderedToSit(), "a freshly tamed dog sits, as with a bone");
		dog.discard();
		helper.succeed();
	}

	/** A survival mock in the level's player list (see {@code TrapsGameTest}); remove it when done. */
	private static ServerPlayer mockPlayer(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		return player;
	}
}
