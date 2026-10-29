package databack.common.interop.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.Test;

import databack.common.interop.modern_block.BlockIdentity;

class BlockIdentityValidatorTest {

    private static final Block BLOCK = new Block(Material.rock) {};

    private static BlockIdentity identity(String id) {
        return new BlockIdentity(new ResourceLocation(id), BLOCK, null);
    }

    @Test
    void reportsUnknownAndUnmappedBlocks() {
        Set<String> modern = new HashSet<>(Arrays.asList("minecraft:stone", "minecraft:furnace", "minecraft:snow_block"));

        List<BlockIdentity> identities = Arrays.asList(
            identity("minecraft:stone"),
            identity("minecraft:furnace"),
            // A second form of the same block
            identity("minecraft:furnace"),
            // A 1.7 name that was never mapped
            identity("minecraft:lit_furnace"),
            // Mod namespaces aren't checked
            identity("etfuturum:netherite_stairs"));

        var result = BlockIdentityValidator.check(identities, modern);

        assertEquals(1, result.unknown.size());
        assertEquals(new ResourceLocation("minecraft:lit_furnace"), result.unknown.get(0).identityId);
        assertEquals(new TreeSet<>(Arrays.asList("minecraft:snow_block")), result.unmapped);
    }
}
