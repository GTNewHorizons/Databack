package databack.common.dto.worldgen.block_predicate;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import databack.common.dto.worldgen.BlockState;
import databack.common.dto.worldgen.BlockWhitelist;
import databack.common.interop.BiomeIds;
import databack.common.interop.BlockTags;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

public class BuiltinBlockPredicates {

    public static void init() {
        TaggedUnionLoader<IBlockPredicate> predicates = DatapackSerialization
            .getTaggedUnionLoader("builtin/block_predicates");

        predicates.addVariant("all_of", AllOfPredicate.class);
        predicates.addVariant("any_of", AnyOfPredicate.class);
        predicates.addVariant("has_sturdy_face", HasSturdyFacePredicate.class);
        predicates.addVariant("inside_world_bounds", InsideWorldBoundsPredicate.class);
        predicates.addVariant("matching_block_tag", MatchingBlockTagPredicate.class);
        predicates.addVariant("matching_biomes", MatchingBiomesPredicate.class);
        predicates.addVariant("matching_blocks", MatchingBlocksPredicate.class);
        predicates.addVariant("matching_fluids", MatchingFluidsPredicate.class);
        predicates.addVariant("not", NotPredicate.class);
        predicates.addVariant("unobstructed", UnobstructedPredicate.class);
        predicates.addVariant("would_survive", WouldSurvivePredicate.class);

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
                String tag = entry.substring(1);
                return block -> BlockTags.getBlocks(tag).contains(block);
            }

            return block -> block == Block.blockRegistry.getObject(entry);
        }
    }

    // Handles `string | string[]` biome ID lists.
    private static class BiomeList {

        String[] ids;

        boolean matches(BiomeGenBase biome) {
            String id = BiomeIds.getBiomeId(biome);

            for (String expected : ids) {
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
                list.ids = new String[] { json.getAsString() };
            } else {
                list.ids = context.deserialize(json, String[].class);
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

        @Override
        public boolean test(World world, int x, int y, int z) {
            int tx = offsetX(offset, x);
            int ty = offsetY(offset, y);
            int tz = offsetZ(offset, z);

            return BlockTags.getBlocks(tag).contains(world.getBlock(tx, ty, tz));
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
