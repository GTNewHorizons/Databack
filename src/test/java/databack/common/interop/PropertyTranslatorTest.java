package databack.common.interop;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;

import org.junit.jupiter.api.Test;

class PropertyTranslatorTest {

    private static final Block PLAIN = new Block(Material.rock) {};

    private static final Block SLAB = new BlockSlab(false, Material.rock) {

        @Override
        public String func_150002_b(int meta) {
            return "slab";
        }
    };

    private static final Block STAIRS = new BlockStairs(PLAIN, 0) {};

    @Test
    void untranslatedPropertiesPassThrough() {
        assertArrayEquals(new String[] { "axis", "y" }, PropertyTranslator.translate(PLAIN, "axis", "y"));
        // Rules only apply to their block class
        assertArrayEquals(new String[] { "type", "top" }, PropertyTranslator.translate(PLAIN, "type", "top"));
    }

    @Test
    void slabTypeBecomesTopFlag() {
        assertArrayEquals(new String[] { "top", "false" }, PropertyTranslator.translate(SLAB, "type", "bottom"));
        assertArrayEquals(new String[] { "top", "true" }, PropertyTranslator.translate(SLAB, "type", "top"));
        // Double slabs are a separate block
        assertNull(PropertyTranslator.translate(SLAB, "type", "double"));
    }

    @Test
    void stairHalfBecomesUpDirection() {
        assertArrayEquals(new String[] { "up", "up" }, PropertyTranslator.translate(STAIRS, "half", "bottom"));
        assertArrayEquals(new String[] { "up", "down" }, PropertyTranslator.translate(STAIRS, "half", "top"));
        assertNull(PropertyTranslator.translate(STAIRS, "shape", "inner_left"));
        assertArrayEquals(new String[] { "facing", "east" }, PropertyTranslator.translate(STAIRS, "facing", "east"));
    }

    @Test
    void modernOnlyProperties() {
        assertTrue(PropertyTranslator.isModernOnly("waterlogged"));
        assertTrue(PropertyTranslator.isModernOnly("north"));
        assertFalse(PropertyTranslator.isModernOnly("facing"));
        assertFalse(PropertyTranslator.isModernOnly("age"));
    }
}
