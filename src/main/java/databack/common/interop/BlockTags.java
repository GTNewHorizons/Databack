package databack.common.interop;

import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;

public class BlockTags {

    private static BlockTagBackend backend;

    public static void init() {
        // when hogtags is done we'll add another backend
        backend = new ShimBackend();
    }

    public static Set<Block> getBlocks(String tag) {
        return backend.getBlocks(tag);
    }

    public interface BlockTagBackend {
        Set<Block> getBlocks(String tag);
    }

    private static class ShimBackend implements BlockTagBackend {

        private final SetMultimap<String, Block> tags = MultimapBuilder.hashKeys().hashSetValues().build();
        private final SetMultimap<Block, String> tagsRev = MultimapBuilder.hashKeys().hashSetValues().build();

        public ShimBackend() {
            register("minecraft:logs", Blocks.log);
            register("minecraft:logs", Blocks.log2);
            register("minecraft:planks", Blocks.planks);
        }

        private void register(String tag, Block block) {
            tags.put(tag, block);
            tagsRev.put(block, tag);
        }

        @Override
        public Set<Block> getBlocks(String tag) {
            return tags.get(tag);
        }
    }
}
