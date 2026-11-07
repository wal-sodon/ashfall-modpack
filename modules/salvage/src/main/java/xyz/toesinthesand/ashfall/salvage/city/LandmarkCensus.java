package xyz.toesinthesand.ashfall.salvage.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.TreeMap;

/**
 * Counts what a generated landmark contains, so tests can check the assets came out as authored
 * (stairs instead of ladders, no light sources, the right loot tables, the Substation Core marker)
 * without comparing block by block. Reads loaded or generatable chunks; call on the server thread.
 *
 * @param lootTables containers that roll a loot table, by table id
 * @param lights     blocks that emit light (Ashfall's cities are dark: should be 0)
 * @param deadLamps  unlit redstone lamps, the relight markers
 * @param cores      Substation Cores: the assets' placeholder (heavy core) or the real core arms swaps in, both
 *                   listed in the {@code ashfall:landmark/substation_core} block tag
 * @param lightBlocks each light-emitting block found and where, so a failure names its culprit
 */
public record LandmarkCensus(int stairs, int ladders, int doors, int beds, int lights, int deadLamps, int cores,
		int emptyContainers, Map<String, Integer> lootTables, Map<String, Integer> lightBlocks) {

	/** The Substation Core marker tag: salvage lists the placeholder, arms adds its real core. */
	public static final TagKey<Block> SUBSTATION_CORE = TagKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath("ashfall", "landmark/substation_core"));

	/** Scans the box from (x0, y0, z0) to (x1, y1, z1), inclusive. */
	public static LandmarkCensus scan(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1) {
		int stairs = 0, ladders = 0, doors = 0, beds = 0, lights = 0, lamps = 0, cores = 0, empty = 0;
		Map<String, Integer> loot = new TreeMap<>();
		Map<String, Integer> lit = new TreeMap<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					BlockState st = level.getBlockState(p.set(x, y, z));
					if (st.isAir()) {
						continue;
					}
					if (st.is(BlockTags.STAIRS)) {
						stairs++;
					} else if (st.is(Blocks.LADDER)) {
						ladders++;
					} else if (st.is(BlockTags.DOORS)) {
						doors++;
					} else if (st.is(BlockTags.BEDS)) {
						beds++;
					} else if (st.is(Blocks.REDSTONE_LAMP)) {
						lamps++;
					} else if (st.is(SUBSTATION_CORE)) {
						cores++;
					}
					if (st.getLightEmission() > 0) {
						lights++;
						lit.merge(st.getBlock() + " at " + p.toShortString(), 1, Integer::sum);
					}
					if (st.hasBlockEntity()) {
						BlockEntity be = level.getBlockEntity(p);
						if (be instanceof RandomizableContainer rc) {
							if (rc.getLootTable() != null) {
								loot.merge(rc.getLootTable().identifier().toString(), 1, Integer::sum);
							} else {
								empty++;
							}
						}
					}
				}
			}
		}
		return new LandmarkCensus(stairs, ladders, doors, beds, lights, lamps, cores, empty, loot, lit);
	}
}
