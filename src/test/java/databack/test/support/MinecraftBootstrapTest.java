package databack.test.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.Test;

import cpw.mods.fml.common.Loader;

import databack.common.interop.modern_block.BlockIdentity;
import databack.common.interop.registry.BlockIdentityRegistry;

class MinecraftBootstrapTest {

    @Test
    void blocksAndItemsAreRegistered() {
        assertNotNull(Blocks.stone);
        assertSame(Blocks.stone, Block.getBlockFromName("minecraft:stone"));
        assertEquals(1, Block.getIdFromBlock(Blocks.stone));
        assertNotNull(Items.diamond);
    }

    @Test
    void vanillaIdentitiesAreRegistered() {
        BlockIdentity stone = BlockIdentityRegistry.INSTANCE.getBlockIdentity(Blocks.stone, 0);
        assertNotNull(stone);
        assertEquals(new ResourceLocation("minecraft:stone"), stone.identityId);
    }

    @Test
    void efrBlocksAreRegisteredWithIdentities() {
        Block deepslate = Block.getBlockFromName("etfuturum:deepslate");
        assertNotNull(deepslate);
        assertEquals(new ResourceLocation("minecraft:deepslate"),
            BlockIdentityRegistry.INSTANCE.getBlockIdentity(deepslate, 0).identityId);
        assertSame(deepslate, BlockIdentityRegistry.INSTANCE.getObject(new ResourceLocation("minecraft:deepslate")).block);
    }

    @Test
    void noModsAreLoaded() {
        assertFalse(Loader.isModLoaded("etfuturum"));
    }
}
