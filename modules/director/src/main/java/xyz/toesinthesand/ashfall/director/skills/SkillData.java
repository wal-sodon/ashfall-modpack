package xyz.toesinthesand.ashfall.director.skills;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;

/**
 * A player's class, skill ranks (keyed by {@link Skill#id()}), the XP points spent on them (for the half refund when
 * the class changes) and when the class last changed (overworld game time, -1 never). Immutable so the attachment
 * only syncs real changes. Unknown skill ids are kept and ignored.
 *
 * @param survivorClass the class id, or "" before the player has chosen
 */
public record SkillData(String survivorClass, Map<String, Integer> ranks, int spent, long classChangedAt) {
	public static final SkillData EMPTY = new SkillData("", Map.of(), 0, -1L);

	public static final Codec<SkillData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("class", "").forGetter(SkillData::survivorClass),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("ranks", Map.of()).forGetter(SkillData::ranks),
			Codec.INT.optionalFieldOf("spent", 0).forGetter(SkillData::spent),
			Codec.LONG.optionalFieldOf("class_changed_at", -1L).forGetter(SkillData::classChangedAt)
	).apply(i, SkillData::new));

	public static final StreamCodec<ByteBuf, SkillData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SkillData::survivorClass,
			ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
			SkillData::ranks,
			ByteBufCodecs.VAR_INT, SkillData::spent,
			ByteBufCodecs.VAR_LONG, SkillData::classChangedAt,
			SkillData::new);

	public SkillData {
		survivorClass = survivorClass == null ? "" : survivorClass;
		Map<String, Integer> clean = new HashMap<>();
		ranks.forEach((id, rank) -> {
			if (rank != null && rank > 0) {
				clean.put(id, rank);
			}
		});
		ranks = Map.copyOf(clean);
		spent = Math.max(0, spent);
	}

	/** The chosen class, or null before the picker. */
	public SurvivorClass chosen() {
		return SurvivorClass.byId(survivorClass);
	}

	public int rank(Skill skill) {
		return ranks.getOrDefault(skill.id(), 0);
	}

	public SkillData with(Skill skill, int rank) {
		Map<String, Integer> next = new HashMap<>(ranks);
		next.put(skill.id(), Math.max(0, rank));
		return new SkillData(survivorClass, next, spent, classChangedAt);
	}

	public SkillData withSpent(int points) {
		return new SkillData(survivorClass, ranks, points, classChangedAt);
	}

	/** A fresh start in {@code chosen}: no ranks, nothing spent. */
	public SkillData asClass(SurvivorClass chosen, long changedAt) {
		return new SkillData(chosen.id(), Map.of(), 0, changedAt);
	}

	/** Ranks bought in all skills together. */
	public int totalRanks() {
		int total = 0;
		for (Skill skill : Skill.values()) {
			total += rank(skill);
		}
		return total;
	}
}
