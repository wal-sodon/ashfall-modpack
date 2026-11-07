package xyz.toesinthesand.ashfall.arms.story;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.Optional;

/**
 * Loot function {@code ashfall:relay_document}: turns the rolled item into the written book of one Relay Log
 * or site document, by id ({@code "log": "ashfall:site/warehouse"}).
 * <p>
 * The site documents (Kiln &amp; Co. work order, the hospital incident report, the hull log) live in the same
 * {@code relay_logs} data as the radio fragments, so every story text is in one place and a writer never has to
 * copy prose into a loot table. The landmark caches just name the document.
 */
public final class RelayDocumentFunction extends LootItemConditionalFunction {
	public static final MapCodec<RelayDocumentFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
			.and(Identifier.CODEC.fieldOf("log").forGetter(f -> f.log))
			.apply(i, RelayDocumentFunction::new));

	private final Identifier log;

	private RelayDocumentFunction(Optional<Holder<LootItemCondition>> condition, Identifier log) {
		super(condition);
		this.log = log;
	}

	public static void register() {
		Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("ashfall", "relay_document"), CODEC);
	}

	@Override
	public MapCodec<RelayDocumentFunction> codec() {
		return CODEC;
	}

	@Override
	protected ItemStack run(ItemStack stack, LootContext context) {
		Optional<RelayLog> document = RelayLogs.get(log);
		if (document.isEmpty()) {
			AshfallArms.LOGGER.warn("Loot names Relay Log {} but no such log is loaded", log);
			return stack;
		}
		return RelayLogs.book(document.get()).copyWithCount(stack.getCount());
	}
}
