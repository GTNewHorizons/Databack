package databack.common.worldgen.feature.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.init.Blocks;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import databack.common.worldgen.feature.standin.FtBlock;
import databack.common.worldgen.feature.standin.FtBlockState;
import databack.common.worldgen.rng.StandardRandom;
import databack.common.worldgen.vanilla.standin.VnBlockPos;
import databack.common.worldgen.vanilla.standin.VnDirection;

/// [PopulationLevel], [LegacyStates] and [LegacyBlockFacts] over real 1.7 blocks in a fake world.
class PopulationLevelTest {

    private final LegacyStates states = new LegacyStates();
    private final FakeLegacyWorld world = new FakeLegacyWorld(Blocks.air);
    private final PopulationLevel level = new PopulationLevel(world, states, ColumnArea.ofChunks(0, 0, 1, 1),
        (x, y, z) -> y < 0 ? "minecraft:deep_dark" : "minecraft:plains", new StandardRandom(1));

    private static FtBlockState state(String id) {
        return FtBlock.of(id).defaultBlockState();
    }

    @Test
    void statesConvertBothWays() {
        ImmutableBlockMeta stone = states.toLegacy(state("minecraft:stone"));
        assertSame(Blocks.stone, stone.getBlock());
        assertEquals(0, stone.getBlockMeta());

        assertSame(state("minecraft:stone"), states.toModern(Blocks.stone, 0));
        assertSame(state("minecraft:air"), states.toModern(Blocks.air, 0));
        assertNull(states.toLegacy(state("databack:no_such_block")));
    }

    private FtBlockState roundTrip(FtBlockState state) {
        ImmutableBlockMeta legacy = states.toLegacy(state);
        return states.toModern(legacy.getBlock(), legacy.getBlockMeta());
    }

    @Test
    void propertiesSurviveTheRoundTrip() {
        FtBlockState stairs = FtBlockState.of(FtBlock.of("minecraft:oak_stairs"), map("facing", "east", "half", "top"));
        assertEquals("east", roundTrip(stairs).properties().get("facing"));
        assertEquals("top", roundTrip(stairs).properties().get("half"));

        FtBlockState log = FtBlockState.of(FtBlock.of("minecraft:oak_log"), map("axis", "x"));
        assertEquals("x", roundTrip(log).properties().get("axis"));
    }

    @Test
    void stateFormsGiveBackTheirProperty() {
        assertEquals("true", states.toModern(Blocks.lit_furnace, 2).properties().get("lit"));
        assertEquals("minecraft:furnace", states.toModern(Blocks.lit_furnace, 2).getBlock().id);
        assertEquals("double", states.toModern(Blocks.double_stone_slab, 0).properties().get("type"));
    }

    private static java.util.Map<String, String> map(String... entries) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        for (int i = 0; i < entries.length; i += 2) map.put(entries[i], entries[i + 1]);
        return map;
    }

    @Test
    void writesStayInTheWritableArea() {
        assertTrue(level.setBlock(new VnBlockPos(31, 10, 0), state("minecraft:stone"), 3));
        assertSame(Blocks.stone, world.getBlock(31, 10, 0));

        assertFalse(level.setBlock(new VnBlockPos(32, 10, 0), state("minecraft:stone"), 3));
        assertFalse(level.setBlock(new VnBlockPos(-1, 10, 0), state("minecraft:stone"), 3));
        assertFalse(level.setBlock(new VnBlockPos(0, 256, 0), state("minecraft:stone"), 3));
        assertSame(Blocks.air, world.getBlock(32, 10, 0));
    }

    @Test
    void blocksWithoutA17CounterpartAreNotWritten() {
        assertFalse(level.setBlock(new VnBlockPos(1, 1, 1), state("databack:no_such_block"), 3));
        assertSame(Blocks.air, world.getBlock(1, 1, 1));
        assertFalse(level.canSurvive(state("databack:no_such_block"), new VnBlockPos(1, 1, 1)));
    }

    @Test
    void writingWhatIsThereChangesNothing() {
        assertTrue(level.setBlock(new VnBlockPos(1, 1, 1), state("minecraft:stone"), 3));
        assertFalse(level.setBlock(new VnBlockPos(1, 1, 1), state("minecraft:stone"), 3));
    }

    @Test
    void readsSeeTheWorldOnlyInTheArea() {
        world.setBlock(40, 5, 40, Blocks.stone, 0);

        world.setBlock(20, 5, 20, Blocks.stone, 0);

        assertSame(state("minecraft:stone"), level.getBlockState(new VnBlockPos(20, 5, 20)));
        assertSame(state("minecraft:air"), level.getBlockState(new VnBlockPos(40, 5, 40)));
        assertSame(state("minecraft:air"), level.getBlockState(new VnBlockPos(40, -1, 40)));
        assertTrue(level.isEmptyBlock(new VnBlockPos(0, 0, 0)));
    }

    @Test
    void biomesComeFromTheLookup() {
        assertEquals("minecraft:deep_dark", level.getBiome(new VnBlockPos(0, -10, 0)).id);
        assertEquals("minecraft:plains", level.getBiome(new VnBlockPos(0, 70, 0)).value());
    }

    @Test
    void facesAskTheMatchingSide() {
        level.isFaceSturdy(state("minecraft:stone"), new VnBlockPos(1, 2, 3), VnDirection.WEST);
        assertEquals(ForgeDirection.WEST, world.lastSide);
        level.isFaceSturdy(state("minecraft:stone"), new VnBlockPos(1, 2, 3), VnDirection.DOWN);
        assertEquals(ForgeDirection.DOWN, world.lastSide);
    }

    @Test
    void factsComeFromMaterials() {
        LegacyBlockFacts facts = new LegacyBlockFacts(states);

        assertTrue(facts.isReplaceable(state("minecraft:air")));
        assertFalse(facts.isReplaceable(state("minecraft:stone")));
        assertTrue(facts.isSolid(state("minecraft:stone")));
        assertFalse(facts.isSolid(state("minecraft:air")));
        assertFalse(facts.isInTag("minecraft:stone", "databack:no_such_tag"));
    }
}
