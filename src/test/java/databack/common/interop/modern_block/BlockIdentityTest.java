package databack.common.interop.modern_block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.Test;

class BlockIdentityTest {

    private static final Block FURNACE = new Block(Material.rock) {};
    private static final Block LIT_FURNACE = new Block(Material.rock) {};

    @Test
    void formsOfOneModernBlockAreEqual() {
        var unlit = new BlockIdentity(new ResourceLocation("test:furnace"), FURNACE, null);
        var lit = new BlockIdentity(new ResourceLocation("test:furnace"), LIT_FURNACE, null);

        assertEquals(unlit, lit);
        assertEquals(unlit.hashCode(), lit.hashCode());

        Set<BlockIdentity> set = new HashSet<>();
        set.add(unlit);
        set.add(lit);

        assertEquals(1, set.size());
    }

    @Test
    void differentIdsAreNotEqual() {
        var furnace = new BlockIdentity(new ResourceLocation("test:furnace2"), FURNACE, null);
        var smoker = new BlockIdentity(new ResourceLocation("test:smoker"), FURNACE, null);

        assertNotEquals(furnace, smoker);
    }

    @Test
    void formsShareTags() {
        var unlit = new BlockIdentity(new ResourceLocation("test:ore"), FURNACE, null);
        var lit = new BlockIdentity(new ResourceLocation("test:ore"), LIT_FURNACE, null);
        var other = new BlockIdentity(new ResourceLocation("test:other_ore"), FURNACE, null);

        assertSame(unlit.db$getTagBitSet(), lit.db$getTagBitSet());

        unlit.db$getTagBitSet().set(3);

        assertTrue(lit.db$getTagBitSet().get(3));
        assertFalse(other.db$getTagBitSet().get(3));

        lit.db$getTagBitSet().clear();

        assertFalse(unlit.db$getTagBitSet().get(3));
    }
}
