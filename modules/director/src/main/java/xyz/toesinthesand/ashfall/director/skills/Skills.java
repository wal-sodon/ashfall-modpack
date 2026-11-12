package xyz.toesinthesand.ashfall.director.skills;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import xyz.toesinthesand.ashfall.director.haven.Havens;
import xyz.toesinthesand.ashfall.salvage.loot.LootRefill;

import java.util.function.Predicate;

/**
 * Survivor Skills: each player picks a {@link SurvivorClass}, then spends XP points on ranks of {@link Skill}s, with
 * the class's core skills going further and costing less. Class and ranks live in a player attachment that is saved,
 * synced to the player's own client for the screens, and copied on death (skills are kept). Attribute skills (Hardy,
 * Fleet Foot, Brawler, Bulwark) are attribute modifiers re-applied whenever ranks or rules change; the rest are read
 * by the system they change through {@link #bonus} and {@link #reduction}.
 */
public final class Skills {
	public static final AttachmentType<SkillData> SKILLS = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("ashfall", "skills"), builder -> builder
					.persistent(SkillData.CODEC)
					.initializer(() -> SkillData.EMPTY)
					.copyOnDeath()
					.syncWith(SkillData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	static final Identifier HARDY_ID = Identifier.fromNamespaceAndPath("ashfall", "skill_hardy");
	static final Identifier FLEET_FOOT_ID = Identifier.fromNamespaceAndPath("ashfall", "skill_fleet_foot");
	static final Identifier BRAWLER_ID = Identifier.fromNamespaceAndPath("ashfall", "skill_brawler");
	static final Identifier BULWARK_ID = Identifier.fromNamespaceAndPath("ashfall", "skill_bulwark");

	/** Why a purchase did or did not go through. */
	public enum BuyResult {
		BOUGHT, NO_CLASS, MAX_RANK, NOT_ENOUGH_XP
	}

	/** Why a class choice did or did not go through. */
	public enum ChooseResult {
		/** The first pick: free, anywhere. */
		CHOSEN,
		/** A change: skills reset, part of the XP refunded. */
		CHANGED,
		SAME_CLASS,
		NOT_AT_HOME,
		TOO_SOON
	}

	/** Where a class may be changed: a Bunker Core's area. Replaceable so tests need no real core. */
	private static volatile Predicate<ServerPlayer> atHome = player -> Havens.inBunker(player.level(), player.blockPosition());

	private Skills() {
	}

	/** Test hook: replaces the "at home" check for class changes; null restores the Bunker Core check. */
	public static void setHomeCheckForTesting(Predicate<ServerPlayer> check) {
		atHome = check == null ? player -> Havens.inBunker(player.level(), player.blockPosition()) : check;
	}

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(SkillRules.ID, new SkillRules.Loader());
		PayloadTypeRegistry.serverboundPlay().register(SkillPayloads.Buy.TYPE, SkillPayloads.Buy.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SkillPayloads.Choose.TYPE, SkillPayloads.Choose.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SkillPayloads.Rules.TYPE, SkillPayloads.Rules.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SkillPayloads.PickClass.TYPE, SkillPayloads.PickClass.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SkillPayloads.Buy.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> buyAndReport(player, Skill.byId(payload.skill())));
		});
		ServerPlayNetworking.registerGlobalReceiver(SkillPayloads.Choose.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> chooseAndReport(player, SurvivorClass.byId(payload.survivorClass())));
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			applyAttributes(player, false);
			sendRules(player);
			if (get(player).chosen() == null && ServerPlayNetworking.canSend(player, SkillPayloads.PickClass.TYPE)) {
				ServerPlayNetworking.send(player, SkillPayloads.PickClass.INSTANCE);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			// Fabric copies the attachment in its own AFTER_RESPAWN listener, which may run after this one
			if (!newPlayer.hasAttached(SKILLS) && oldPlayer.hasAttached(SKILLS)) {
				newPlayer.setAttached(SKILLS, get(oldPlayer));
			}
			applyAttributes(newPlayer, !alive);
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				applyAttributes(player, false);
				sendRules(player);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> SkillsCommand.register(dispatcher));
		LootRefill.setExtraRollChance(player -> bonus(player, Skill.PACK_RAT));
	}

	public static SkillData get(Player player) {
		return player.getAttachedOrElse(SKILLS, SkillData.EMPTY);
	}

	public static void set(ServerPlayer player, SkillData data) {
		player.setAttached(SKILLS, data);
		applyAttributes(player, false);
	}

	/** The player's class, or null before they have picked one. */
	public static SurvivorClass survivorClass(Player player) {
		return get(player).chosen();
	}

	/** The rank that counts: what was bought, capped by what the player's class allows now. */
	public static int rank(Player player, Skill skill) {
		SkillData data = get(player);
		return Math.min(data.rank(skill), SkillRules.get().maxRank(data.chosen(), skill));
	}

	/** What the player's ranks add: {@code rank * perRank} (0 for anyone without the skill). */
	public static double bonus(Player player, Skill skill) {
		return rank(player, skill) * SkillRules.get().perRank(skill);
	}

	/** What the player's ranks leave of something bad: {@code 1 - rank * perRank}, never below 0. */
	public static float reduction(Player player, Skill skill) {
		return (float) Math.max(0.0, 1.0 - bonus(player, skill));
	}

	/** XP points the player holds right now. */
	public static int points(ServerPlayer player) {
		return XpMath.points(player.experienceLevel, player.experienceProgress);
	}

	/** Buys the next rank of {@code skill} if the player has a class and can afford it. */
	public static BuyResult buy(ServerPlayer player, Skill skill) {
		SkillData data = get(player);
		if (data.chosen() == null) {
			return BuyResult.NO_CLASS;
		}
		int next = rank(player, skill) + 1;
		int cost = SkillRules.get().cost(data.chosen(), skill, next);
		if (cost < 0) {
			return BuyResult.MAX_RANK;
		}
		int held = points(player);
		if (held < cost) {
			return BuyResult.NOT_ENOUGH_XP;
		}
		setPoints(player, held - cost);
		set(player, data.with(skill, next).withSpent(data.spent() + cost));
		return BuyResult.BOUGHT;
	}

	/**
	 * Picks or changes the player's class. The first pick is free and works anywhere. A change only works inside a
	 * Bunker Core's area, once per {@link SkillRules#classChangeDays} in-game days; it clears every rank and gives back
	 * {@link SkillRules#classChangeRefund} of the XP spent on them.
	 */
	public static ChooseResult choose(ServerPlayer player, SurvivorClass chosen) {
		SkillData data = get(player);
		long now = player.level().getServer().overworld().getGameTime();
		if (data.chosen() == null) {
			set(player, data.asClass(chosen, data.classChangedAt()));
			return ChooseResult.CHOSEN;
		}
		if (data.chosen() == chosen) {
			return ChooseResult.SAME_CLASS;
		}
		if (!atHome.test(player)) {
			return ChooseResult.NOT_AT_HOME;
		}
		SkillRules rules = SkillRules.get();
		if (data.classChangedAt() >= 0 && now - data.classChangedAt() < rules.classChangeTicks()) {
			return ChooseResult.TOO_SOON;
		}
		int refund = (int) Math.floor(data.spent() * Math.clamp(rules.classChangeRefund(), 0.0, 1.0));
		setPoints(player, points(player) + refund);
		set(player, data.asClass(chosen, now));
		return ChooseResult.CHANGED;
	}

	/** In-game days until the player may change class again (0 if they may now). */
	public static int daysUntilChange(ServerPlayer player) {
		SkillData data = get(player);
		if (data.classChangedAt() < 0) {
			return 0;
		}
		long left = data.classChangedAt() + SkillRules.get().classChangeTicks() - player.level().getServer().overworld().getGameTime();
		return left <= 0 ? 0 : (int) Math.ceil(left / 24000.0);
	}

	/**
	 * Sets the player's XP to exactly {@code points}, as level plus progress. Vanilla's {@code totalExperience} (the
	 * death-screen score) is left alone, as enchanting does.
	 */
	static void setPoints(ServerPlayer player, int points) {
		int level = XpMath.levelFor(Math.max(0, points));
		player.setExperienceLevels(level);
		player.setExperiencePoints(Math.max(0, points) - XpMath.pointsAtLevel(level));
	}

	private static Component skillName(Skill skill) {
		return Component.translatable("skill.ashfall." + skill.id());
	}

	static Component className(SurvivorClass chosen) {
		return Component.translatable("class.ashfall." + chosen.id());
	}

	private static void buyAndReport(ServerPlayer player, Skill skill) {
		if (skill == null || !player.isAlive()) {
			return;
		}
		BuyResult result = buy(player, skill);
		Component message = switch (result) {
			case BOUGHT -> Component.translatable("message.ashfall.skills.bought", skillName(skill), rank(player, skill));
			case NO_CLASS -> Component.translatable("message.ashfall.skills.no_class");
			case MAX_RANK -> Component.translatable("message.ashfall.skills.max", skillName(skill));
			case NOT_ENOUGH_XP -> Component.translatable("message.ashfall.skills.short",
					SkillRules.get().cost(survivorClass(player), skill, rank(player, skill) + 1), points(player));
		};
		player.sendSystemMessage(message, true);
	}

	private static void chooseAndReport(ServerPlayer player, SurvivorClass chosen) {
		if (chosen == null) {
			return;
		}
		ChooseResult result = choose(player, chosen);
		Component message = switch (result) {
			case CHOSEN -> Component.translatable("message.ashfall.class.chosen", className(chosen));
			case CHANGED -> Component.translatable("message.ashfall.class.changed", className(chosen));
			case SAME_CLASS -> Component.translatable("message.ashfall.class.same", className(chosen));
			case NOT_AT_HOME -> Component.translatable("message.ashfall.class.not_home");
			case TOO_SOON -> Component.translatable("message.ashfall.class.too_soon", daysUntilChange(player));
		};
		player.sendSystemMessage(message, true);
	}

	static void sendRules(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, SkillPayloads.Rules.TYPE)) {
			ServerPlayNetworking.send(player, new SkillPayloads.Rules(SkillRules.get()));
		}
	}

	/**
	 * Puts the attribute skills' modifiers in line with the player's ranks. Permanent modifiers, so max health is
	 * already raised when the saved health loads on the next join; replaced by id, so they never stack.
	 *
	 * @param heal top the player up to the new max health (after a respawn, which starts at 20)
	 */
	public static void applyAttributes(ServerPlayer player, boolean heal) {
		set(player, Attributes.MAX_HEALTH, HARDY_ID, bonus(player, Skill.HARDY), AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.MOVEMENT_SPEED, FLEET_FOOT_ID, bonus(player, Skill.FLEET_FOOT), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(player, Attributes.ATTACK_DAMAGE, BRAWLER_ID, bonus(player, Skill.BRAWLER), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.ARMOR, BULWARK_ID, bonus(player, Skill.BULWARK), AttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		} else if (heal && player.isAlive()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount,
							AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (current != null && current.amount() == amount) {
			return;
		}
		instance.removeModifier(id);
		if (amount != 0.0) {
			instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
		}
	}
}
