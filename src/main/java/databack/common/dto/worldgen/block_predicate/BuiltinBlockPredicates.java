package databack.common.dto.worldgen.block_predicate;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import databack.common.dto.worldgen.BlockWhitelist;
import databack.common.handlers.DatapackHandle;
import databack.common.interop.registry.ProxyBiomeRegistry;
import databack.common.interop.registry.ProxyBlockRegistry;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;
import databack.common.tags.BuiltinTagRegistries;
import databack.common.tags.ITag;

public class BuiltinBlockPredicates {

    public static void init() {
        TaggedUnionLoader<IBlockPredicate> predicates = DatapackSerialization
            .createTaggedUnionLoader("builtin/block_predicates", IBlockPredicate.class);

        predicates.addVariant("minecraft:all_of", AllOfPredicate.class);
        predicates.addVariant("minecraft:any_of", AnyOfPredicate.class);
        predicates.addVariant("minecraft:has_sturdy_face", HasSturdyFacePredicate.class);
        predicates.addVariant("minecraft:inside_world_bounds", InsideWorldBoundsPredicate.class);
        predicates.addVariant("minecraft:matching_block_tag", MatchingBlockTagPredicate.class);
        predicates.addVariant("minecraft:matching_biomes", MatchingBiomesPredicate.class);
        predicates.addVariant("minecraft:matching_blocks", MatchingBlocksPredicate.class);
        predicates.addVariant("minecraft:matching_fluids", MatchingFluidsPredicate.class);
        predicates.addVariant("minecraft:not", NotPredicate.class);
        predicates.addVariant("minecraft:replaceable", ReplaceablePredicate.class);
        predicates.addVariant("minecraft:solid", SolidPredicate.class);
        predicates.addVariant("minecraft:unobstructed", UnobstructedPredicate.class);
        predicates.addVariant("minecraft:true", TruePredicate.class);
        predicates.addVariant("minecraft:would_survive", WouldSurvivePredicate.class);

        predicates.setFallback((json, typeOfT, context) -> {
            BlockWhitelist whitelist = context.deserialize(json, BlockWhitelist.class);

            return (world, x, y, z) -> whitelist.contains(world.getBlock(x, y, z));
        });

        DatapackSerialization.getBuilder()
            .registerTypeAdapter(BlockWhitelist.class, new BlockWhitelistAdapter())
            .registerTypeAdapter(BiomeList.class, new BiomeListAdapter());
    }

    private static int offsetX(@Nullable int[] offset, int x) {
        return offset != null ? x + offset[0] : x;
    }

    private static int offsetY(@Nullable int[] offset, int y) {
        return offset != null ? y + offset[1] : y;
    }

    private static int offsetZ(@Nullable int[] offset, int z) {
        return offset != null ? z + offset[2] : z;
    }

    // Handles `string | string[]` block/fluid whitelists, with "#tag" for tag references.
    private static class BlockWhitelistAdapter implements JsonDeserializer<BlockWhitelist> {

        @Override
        public BlockWhitelist deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            if (json.isJsonPrimitive()) {
                return fromEntry(json.getAsString());
            }

            if (json.isJsonArray()) {
                JsonArray array = json.getAsJsonArray();
                List<BlockWhitelist> parts = new ArrayList<>(array.size());

                for (JsonElement element : array) {
                    parts.add(fromEntry(element.getAsString()));
                }

                return block -> {
                    for (BlockWhitelist part : parts) {
                        if (part.contains(block)) return true;
                    }
                    return false;
                };
            }

            throw new JsonParseException("Expected string or array for BlockWhitelist: " + json);
        }

        private static BlockWhitelist fromEntry(String entry) {
            if (entry.startsWith("#")) {
                String tagName = entry.substring(1);

                DatapackHandle<ITag<Block>> tag = new DatapackHandle<>(() -> BuiltinTagRegistries.blocks().getTag(new ResourceLocation(tagName)));

                return block -> tag.get().includes(block);
            } else {
                DatapackHandle<Block> block = new DatapackHandle<>(() -> ProxyBlockRegistry.INSTANCE.getObject(new ResourceLocation(entry)));

                return b -> b == block.get();
            }
        }
    }

    // Handles `string | string[]` biome ID lists.
    private static class BiomeList {

        ResourceLocation[] ids;

        boolean matches(BiomeGenBase biome) {
            ResourceLocation id = ProxyBiomeRegistry.INSTANCE.getIdForObject(biome);

            for (ResourceLocation expected : ids) {
                if (expected.equals(id)) return true;
            }

            return false;
        }
    }

    private static class BiomeListAdapter implements JsonDeserializer<BiomeList> {

        @Override
        public BiomeList deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            BiomeList list = new BiomeList();

            if (json.isJsonPrimitive()) {
                list.ids = new ResourceLocation[] { new ResourceLocation(json.getAsString()) };
            } else {
                list.ids = context.deserialize(json, ResourceLocation[].class);
            }

            return list;
        }
    }

    private enum Direction {
        down,
        up,
        north,
        south,
        west,
        east;

        ForgeDirection toForge() {
            switch (this) {
                case down: return ForgeDirection.DOWN;
                case up: return ForgeDirection.UP;
                case north: return ForgeDirection.NORTH;
                case south: return ForgeDirection.SOUTH;
                case west: return ForgeDirection.WEST;
                case east: return ForgeDirection.EAST;
                default: throw new IllegalStateException("Unexpected direction: " + this);
            }
        }
    }

    private static class TruePredicate implements IBlockPredicate {

        @Override
        public boolean test(World world, int x, int y, int z) {
            return true;
        }
    }

    private static class AllOfPredicate implements IBlockPredicate {

        public IBlockPredicate[] predicates;

        @Override
        public boolean test(World world, int x, int y, int z) {
            for (IBlockPredicate predicate : predicates) {
                if (!predicate.test(world, x, y, z)) return false;
            }
            return true;
        }
    }

    private static class AnyOfPredicate implements IBlockPredicate {

        public IBlockPredicate[] predicates;

        @Override
        public boolean test(World world, int x, int y, int z) {
            for (IBlockPredicate predicate : predicates) {
                if (predicate.test(world, x, y, z)) return true;
            }
            return false;
        }
    }

    private static class HasSturdyFacePredicate implements IBlockPredicate {

        @Nullable public int[] offset;
        public Direction direction;

        @Override
        public boolean test(World world, int x, int y, int z) {
            int tx = offsetX(offset, x);
            int ty = offsetY(offset, y);
            int tz = offsetZ(offset, z);

            return world.getBlock(tx, ty, tz).isSideSolid(world, tx, ty, tz, direction.toForge());
        }
    }

    private static class InsideWorldBoundsPredicate implements IBlockPredicate {

        @Nullable public int[] offset;

        @Override
        public boolean test(World world, int x, int y, int z) {
            int tx = offsetX(offset, x);
            int ty = offsetY(offset, y);
            int tz = offsetZ(offset, z);

            return ty >= 0 && ty < 256
                && tx >= -30000000 && tx <= 30000000
                && tz >= -30000000 && tz <= 30000000;
        }
    }

    private static class MatchingBlockTagPredicate implements IBlockPredicate {

        @Nullable public int[] offset;
        public String tag;

        private transient DatapackHandle<ITag<Block>> tagRef;

        @Override
        public boolean test(World world, int x, int y, int z) {
            if (tagRef == null) {
                tagRef = new DatapackHandle<>(() -> BuiltinTagRegistries.blocks().getTag(new ResourceLocation(tag)));
            }

            int tx = offsetX(offset, x);
            int ty = offsetY(offset, y);
            int tz = offsetZ(offset, z);

            return tagRef.get().includes(world.getBlock(tx, ty, tz));
        }
    }

    private static class MatchingBiomesPredicate implements IBlockPredicate {

        public BiomeList biomes;

        @Override
        public boolean test(World world, int x, int y, int z) {
            return biomes.matches(world.getBiomeGenForCoords(x, z));
        }
    }

    private static class MatchingBlocksPredicate implements IBlockPredicate {

        @Nullable public int[] offset;
        public BlockWhitelist blocks;

        @Override
        public boolean test(World world, int x, int y, int z) {
            int tx = offsetX(offset, x);
            int ty = offsetY(offset, y);
            int tz = offsetZ(offset, z);

            return blocks.contains(world.getBlock(tx, ty, tz));
        }
    }

    private static class MatchingFluidsPredicate implements IBlockPredicate {

        @Nullable public int[] offset;
        public BlockWhitelist fluids;

        @Override
        public boolean test(World world, int x, int y, int z) {
            int tx = offsetX(offset, x);
            int ty = offsetY(offset, y);
            int tz = offsetZ(offset, z);

            return fluids.contains(world.getBlock(tx, ty, tz));
        }
    }

    private static class NotPredicate implements IBlockPredicate {

        public IBlockPredicate predicate;

        @Override
        public boolean test(World world, int x, int y, int z) {
            return !predicate.test(world, x, y, z);
        }
    }

    private static class ReplaceablePredicate implements IBlockPredicate {

        @Nullable public int[] offset;

        @Override
        public boolean test(World world, int x, int y, int z) {
            int tx = offsetX(offset, x);
            int ty = offsetY(offset, y);
            int tz = offsetZ(offset, z);

            return world.getBlock(tx, ty, tz).isReplaceable(world, tx, ty, tz);
        }
    }

    private static class SolidPredicate implements IBlockPredicate {

        @Nullable public int[] offset;

        @Override
        public boolean test(World world, int x, int y, int z) {
            int tx = offsetX(offset, x);
            int ty = offsetY(offset, y);
            int tz = offsetZ(offset, z);

            return world.getBlock(tx, ty, tz).isSideSolid(world, tx, ty, tz, ForgeDirection.UP);
        }
    }

    private static class UnobstructedPredicate implements IBlockPredicate {

        @Nullable public int[] offset;

        @Override
        public boolean test(World world, int x, int y, int z) {
            int tx = offsetX(offset, x);
            int ty = offsetY(offset, y);
            int tz = offsetZ(offset, z);

            return world
                .getEntitiesWithinAABBExcludingEntity(
                    null,
                    AxisAlignedBB.getBoundingBox(tx, ty, tz, tx + 1, ty + 1, tz + 1))
                .isEmpty();
        }
    }

    private static class WouldSurvivePredicate implements IBlockPredicate {

        @Nullable public int[] offset;
        public BlockState state;

        @Override
        public boolean test(World world, int x, int y, int z) {
            return false; // TODO
        }
    }
}
