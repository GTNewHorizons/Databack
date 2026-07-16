package databack.common.interop;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import databack.common.tags.TagRegistry;

public class BlockTags {

    private static BlockTagBackend backend;

    public static void init() {
        // Register vanilla shim entries so block tags work even without a datapack
        TagRegistry.INSTANCE.registerStatic(
            "blocks", "minecraft:logs",
            Arrays.asList("minecraft:log", "minecraft:log2"));
        TagRegistry.INSTANCE.registerStatic(
            "blocks", "minecraft:planks",
            Arrays.asList("minecraft:planks"));

        // when hogtags is done we'll add another backend
        backend = new TagRegistryBackend();
    }

    public static Set<Block> getBlocks(String tag) {
        return backend.getBlocks(tag);
    }

    public interface BlockTagBackend {

        Set<Block> getBlocks(String tag);
    }

    private static class TagRegistryBackend implements BlockTagBackend {

        @Override
        public Set<Block> getBlocks(String tag) {
            Set<String> ids = TagRegistry.INSTANCE.getEntries("blocks", tag);
            Set<Block> result = new HashSet<>();
            for (String id : ids) {
                Block block = (Block) Block.blockRegistry.getObject(id);
                if (block != null && block != Blocks.air) {
                    result.add(block);
                }
            }
            return result;
        }
    }
}
