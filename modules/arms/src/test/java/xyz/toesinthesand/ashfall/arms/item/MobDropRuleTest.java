package xyz.toesinthesand.ashfall.arms.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped mob drop rules parse, keep the designed rates, and the codec refuses impossible drops. */
class MobDropRuleTest {
	private static MobDropRule rule(String entity) throws IOException {
		Identifier id = Identifier.parse(entity);
		String path = "/data/" + MobDrops.filePath(id);
		try (InputStream in = MobDropRuleTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "missing resource " + path);
			JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			MobDropRule rule = MobDropRule.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
			assertEquals(id, rule.entity(), "file " + path + " names another entity");
			return rule;
		}
	}

	@Test
	void shippedRulesKeepTheEconomy() throws IOException {
		MobDropRule zombie = rule("minecraft:zombie");
		assertEquals(List.of(new MobDropRule.Drop(Identifier.parse("ashfall:scrap_electronics"), 0.03f, true, 1, 1),
				new MobDropRule.Drop(Identifier.parse("ashfall:bandage"), 0.02f, true, 1, 1)), zombie.drops());
		assertEquals(zombie.drops(), rule("minecraft:husk").drops());
		// balance-v1: sieges and horde nights pay back part of their cells through the specials in them
		assertEquals(0.5f, rule("ashfall:brute").drops().getFirst().chance(), 1e-6);
		assertEquals(0.3f, rule("ashfall:screamer").drops().getFirst().chance(), 1e-6);
		assertEquals(0.08f, rule("ashfall:bloated").drops().getLast().chance(), 1e-6);
		assertEquals(0.35f, rule("mutantmonsters:mutant_zombie").drops().getFirst().chance(), 1e-6);
		for (String e : List.of("minecraft:zombie", "minecraft:husk", "mutantmonsters:mutant_zombie", "ashfall:brute", "ashfall:screamer",
				"ashfall:spitter", "ashfall:stalker", "ashfall:bloated", "ashfall:crawler")) {
			for (MobDropRule.Drop d : rule(e).drops()) {
				assertTrue(d.killedByPlayer(), e + " pays out on environment kills");
				if (d.item().getPath().equals("energy_cell")) {
					assertTrue(!e.startsWith("minecraft:"), "plain " + e + "s must not drop Energy Cells");
				}
			}
		}
	}

	@Test
	void codecRefusesImpossibleDrops() {
		String base = "{\"entity\":\"minecraft:pig\",\"drops\":[{\"item\":\"minecraft:stick\",\"chance\":%s,\"min\":%d,\"max\":%d}]}";
		assertTrue(MobDropRule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(base.formatted("1.5", 1, 1))).isError(), "chance above 1");
		assertTrue(MobDropRule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(base.formatted("0.5", 3, 2))).isError(), "min above max");
		MobDropRule ok = MobDropRule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(base.formatted("0.5", 1, 3))).getOrThrow();
		assertTrue(ok.drops().getFirst().killedByPlayer(), "killed_by_player defaults to true");
	}

	@Test
	void pathsFollowTheEntityId() {
		Identifier zombie = Identifier.parse("minecraft:zombie");
		assertEquals("ashfall/ashfall/mob_drops/minecraft/zombie.json", MobDrops.filePath(zombie));
		assertEquals("minecraft:entities/zombie", MobDrops.tableFor(zombie).identifier().toString());
	}
}
