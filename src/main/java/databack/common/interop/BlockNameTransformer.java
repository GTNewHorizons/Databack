package databack.common.interop;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.util.ResourceLocation;

import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockStateImpl;
import databack.common.dto.BlockStateDTO;
import databack.common.interop.registry.ProxyBlockRegistry;

public class BlockNameTransformer {

    public interface BlockStateTransformer {

        void transform(BlockStateDTO state);
    }

    private static final HashMap<ResourceLocation, BlockStateTransformer> TRANSFORMERS = new HashMap<>();

    public static void transform(BlockStateDTO state) {
        var transformer = TRANSFORMERS.get(state.Name);

        if (transformer != null) {
            transformer.transform(state);
        }
    }

    public static void register(ResourceLocation blockId, BlockStateTransformer transformer) {
        TRANSFORMERS.put(blockId, transformer);
    }

    public static void register(String blockId, BlockStateTransformer transformer) {
        register(new ResourceLocation(blockId), transformer);
    }

    public static void convertBlockId(String srcBlockId, String dstBlockId) {
        register(srcBlockId, dto -> dto.Name = new ResourceLocation(dstBlockId));
    }

    public static void convertBlockId(String srcBlockId, String dstBlockId, Map<String, String> dstProperties) {
        register(srcBlockId, dto -> {
            dto.Name = new ResourceLocation(dstBlockId);

            if (dto.Properties == null) dto.Properties = new HashMap<>();
            dto.Properties.putAll(dstProperties);
        });
    }

    static {
        // Pure renames
        convertBlockId("minecraft:grass_block", "minecraft:grass");
        convertBlockId("minecraft:sugar_cane", "minecraft:reeds");
        convertBlockId("minecraft:melon", "minecraft:melon_block");
        convertBlockId("minecraft:jack_o_lantern", "minecraft:lit_pumpkin");
        convertBlockId("minecraft:dandelion", "minecraft:yellow_flower");
        convertBlockId("minecraft:cobweb", "minecraft:web");
        convertBlockId("minecraft:dead_bush", "minecraft:deadbush");
        convertBlockId("minecraft:lily_pad", "minecraft:waterlily");

        // Logs (axis property from modern state is preserved and handled by GTNHLib BlockRotatedPillar)
        convertBlockId("minecraft:oak_log",      "minecraft:log",  Collections.singletonMap("variant", "oak"));
        convertBlockId("minecraft:spruce_log",   "minecraft:log",  Collections.singletonMap("variant", "spruce"));
        convertBlockId("minecraft:birch_log",    "minecraft:log",  Collections.singletonMap("variant", "birch"));
        convertBlockId("minecraft:jungle_log",   "minecraft:log",  Collections.singletonMap("variant", "jungle"));
        convertBlockId("minecraft:acacia_log",   "minecraft:log2", Collections.singletonMap("variant", "acacia"));
        convertBlockId("minecraft:dark_oak_log", "minecraft:log2", Collections.singletonMap("variant", "dark_oak"));

        // Leaves
        convertBlockId("minecraft:oak_leaves",      "minecraft:leaves",  Collections.singletonMap("variant", "oak"));
        convertBlockId("minecraft:spruce_leaves",   "minecraft:leaves",  Collections.singletonMap("variant", "spruce"));
        convertBlockId("minecraft:birch_leaves",    "minecraft:leaves",  Collections.singletonMap("variant", "birch"));
        convertBlockId("minecraft:jungle_leaves",   "minecraft:leaves",  Collections.singletonMap("variant", "jungle"));
        convertBlockId("minecraft:acacia_leaves",   "minecraft:leaves2", Collections.singletonMap("variant", "acacia"));
        convertBlockId("minecraft:dark_oak_leaves", "minecraft:leaves2", Collections.singletonMap("variant", "dark_oak"));

        // Saplings
        convertBlockId("minecraft:oak_sapling",      "minecraft:sapling", Collections.singletonMap("type", "oak"));
        convertBlockId("minecraft:spruce_sapling",   "minecraft:sapling", Collections.singletonMap("type", "spruce"));
        convertBlockId("minecraft:birch_sapling",    "minecraft:sapling", Collections.singletonMap("type", "birch"));
        convertBlockId("minecraft:jungle_sapling",   "minecraft:sapling", Collections.singletonMap("type", "jungle"));
        convertBlockId("minecraft:acacia_sapling",   "minecraft:sapling", Collections.singletonMap("type", "acacia"));
        convertBlockId("minecraft:dark_oak_sapling", "minecraft:sapling", Collections.singletonMap("type", "dark_oak"));

        // Planks
        convertBlockId("minecraft:oak_planks",    "minecraft:planks", Collections.singletonMap("variant", "oak"));
        convertBlockId("minecraft:spruce_planks", "minecraft:planks", Collections.singletonMap("variant", "spruce"));
        convertBlockId("minecraft:acacia_planks", "minecraft:planks", Collections.singletonMap("variant", "acacia"));

        // Flowers
        convertBlockId("minecraft:poppy",        "minecraft:red_flower", Collections.singletonMap("type", "poppy"));
        convertBlockId("minecraft:blue_orchid",  "minecraft:red_flower", Collections.singletonMap("type", "blue_orchid"));
        convertBlockId("minecraft:allium",       "minecraft:red_flower", Collections.singletonMap("type", "allium"));
        convertBlockId("minecraft:azure_bluet",  "minecraft:red_flower", Collections.singletonMap("type", "azure_bluet"));
        convertBlockId("minecraft:red_tulip",    "minecraft:red_flower", Collections.singletonMap("type", "red_tulip"));
        convertBlockId("minecraft:orange_tulip", "minecraft:red_flower", Collections.singletonMap("type", "orange_tulip"));
        convertBlockId("minecraft:white_tulip",  "minecraft:red_flower", Collections.singletonMap("type", "white_tulip"));
        convertBlockId("minecraft:pink_tulip",   "minecraft:red_flower", Collections.singletonMap("type", "pink_tulip"));
        convertBlockId("minecraft:oxeye_daisy",  "minecraft:red_flower", Collections.singletonMap("type", "oxeye_daisy"));

        // Tall grass types (type "dead_bush" = meta 0, but dead_bush handled separately above)
        convertBlockId("minecraft:short_grass", "minecraft:tallgrass", Collections.singletonMap("type", "tall_grass"));
        convertBlockId("minecraft:fern",        "minecraft:tallgrass", Collections.singletonMap("type", "fern"));

        // Double plants
        convertBlockId("minecraft:sunflower",  "minecraft:double_plant", Collections.singletonMap("variant", "sunflower"));
        convertBlockId("minecraft:lilac",      "minecraft:double_plant", Collections.singletonMap("variant", "syringa"));
        convertBlockId("minecraft:tall_grass", "minecraft:double_plant", Collections.singletonMap("variant", "double_grass"));
        convertBlockId("minecraft:large_fern", "minecraft:double_plant", Collections.singletonMap("variant", "double_fern"));
        convertBlockId("minecraft:rose_bush",  "minecraft:double_plant", Collections.singletonMap("variant", "double_rose"));
        convertBlockId("minecraft:peony",      "minecraft:double_plant", Collections.singletonMap("variant", "paeonia"));

        // Dirt variants
        convertBlockId("minecraft:coarse_dirt", "minecraft:dirt", Collections.singletonMap("variant", "coarse_dirt"));
        convertBlockId("minecraft:podzol",      "minecraft:dirt", Collections.singletonMap("variant", "podzol"));
    }
}
