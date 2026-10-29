package databack.common.dto.worldgen.block_predicate;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import databack.common.dto.BlockStateDTO;
import databack.common.dto.worldgen.BlockWhitelist;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;
import databack.common.worldgen.feature.FeatureBuildContext;
import databack.common.worldgen.vanilla.standin.VnDirection;

/// The block predicates, built as vanilla's own ([IBlockPredicate#instantiate]).
public class BuiltinBlockPredicates {

    private static final String PREDICATES = "blockpredicates/";
    private static final String FACTORIES = PREDICATES + "BlockPredicate";

    public static void init() {
        TaggedUnionLoader<IBlockPredicate> predicates = DatapackSerialization
            .createTaggedUnionLoader("builtin/block_predicates", IBlockPredicate.class);

        predicates.addVariant("minecraft:all_of", AllOfPredicate.class);
        predicates.addVariant("minecraft:any_of", AnyOfPredicate.class);
        predicates.addVariant("minecraft:has_sturdy_face", HasSturdyFacePredicate.class);
        predicates.addVariant("minecraft:inside_world_bounds", InsideWorldBoundsPredicate.class);
        predicates.addVariant("minecraft:matching_block_tag", MatchingBlockTagPredicate.class);
        predicates.addVariant("minecraft:matching_blocks", MatchingBlocksPredicate.class);
        predicates.addVariant("minecraft:matching_fluids", MatchingFluidsPredicate.class);
        predicates.addVariant("minecraft:not", NotPredicate.class);
        predicates.addVariant("minecraft:replaceable", ReplaceablePredicate.class);
        predicates.addVariant("minecraft:solid", SolidPredicate.class);
        predicates.addVariant("minecraft:unobstructed", UnobstructedPredicate.class);
        predicates.addVariant("minecraft:true", TruePredicate.class);
        predicates.addVariant("minecraft:would_survive", WouldSurvivePredicate.class);

        DatapackSerialization.getBuilder().registerTypeAdapter(BlockWhitelist.class, new BlockWhitelistAdapter());
    }

    /// Each of `predicates` built, or null if any cannot be.
    @Nullable
    static List<Object> instantiateAll(@NotNull IBlockPredicate @NotNull [] predicates,
        @NotNull FeatureBuildContext ctx) {
        List<Object> built = new ArrayList<>(predicates.length);
        for (IBlockPredicate predicate : predicates) {
            Object one = predicate.instantiate(ctx);
            if (one == null) return null;
            built.add(one);
        }
        return built;
    }

    /// `string | string[]`.
    private static class BlockWhitelistAdapter implements JsonDeserializer<BlockWhitelist> {

        @Override
        public BlockWhitelist deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            List<String> entries = new ArrayList<>();
            if (json.isJsonPrimitive()) {
                entries.add(json.getAsString());
            } else if (json.isJsonArray()) {
                JsonArray array = json.getAsJsonArray();
                for (JsonElement element : array) entries.add(element.getAsString());
            } else {
                throw new JsonParseException("Expected string or array for a block list: " + json);
            }
            return new BlockWhitelist(entries);
        }
    }

    private static class TruePredicate implements IBlockPredicate {

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(FACTORIES, "alwaysTrue");
        }
    }

    private static class AllOfPredicate implements IBlockPredicate {

        public IBlockPredicate[] predicates;

        @Nullable
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            List<Object> built = instantiateAll(predicates, ctx);
            return built == null ? null : ctx.vanilla.call(FACTORIES, "allOf", built);
        }
    }

    private static class AnyOfPredicate implements IBlockPredicate {

        public IBlockPredicate[] predicates;

        @Nullable
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            List<Object> built = instantiateAll(predicates, ctx);
            return built == null ? null : ctx.vanilla.call(FACTORIES, "anyOf", built);
        }
    }

    private static class HasSturdyFacePredicate implements IBlockPredicate {

        @Nullable
        public int[] offset;
        public String direction;

        @Nullable
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            VnDirection face = ctx.direction(direction);
            if (face == null) return ctx.unsupported("Sturdy face direction " + direction);
            return ctx.vanilla.call(FACTORIES, "hasSturdyFace", ctx.offset(offset), face);
        }
    }

    private static class InsideWorldBoundsPredicate implements IBlockPredicate {

        @Nullable
        public int[] offset;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(FACTORIES, "insideWorld", ctx.offset(offset));
        }
    }

    private static class MatchingBlockTagPredicate implements IBlockPredicate {

        @Nullable
        public int[] offset;
        public String tag;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(FACTORIES, "matchesTag", ctx.offset(offset), ctx.tag(tag));
        }
    }

    private static class MatchingBlocksPredicate implements IBlockPredicate {

        @Nullable
        public int[] offset;
        public BlockWhitelist blocks;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(PREDICATES + "MatchingBlocksPredicate", ctx.offset(offset),
                ctx.blocks(blocks.entries));
        }
    }

    private static class MatchingFluidsPredicate implements IBlockPredicate {

        @Nullable
        public int[] offset;
        public BlockWhitelist fluids;

        @Nullable
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            Object set = ctx.fluids(fluids.entries);
            return set == null ? null : ctx.vanilla.construct(PREDICATES + "MatchingFluidsPredicate",
                ctx.offset(offset), set);
        }
    }

    private static class NotPredicate implements IBlockPredicate {

        public IBlockPredicate predicate;

        @Nullable
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            Object built = predicate.instantiate(ctx);
            return built == null ? null : ctx.vanilla.call(FACTORIES, "not", built);
        }
    }

    private static class ReplaceablePredicate implements IBlockPredicate {

        @Nullable
        public int[] offset;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(FACTORIES, "replaceable", ctx.offset(offset));
        }
    }

    private static class SolidPredicate implements IBlockPredicate {

        @Nullable
        public int[] offset;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(FACTORIES, "solid", ctx.offset(offset));
        }
    }

    private static class UnobstructedPredicate implements IBlockPredicate {

        @Nullable
        public int[] offset;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(FACTORIES, "unobstructed", ctx.offset(offset));
        }
    }

    private static class WouldSurvivePredicate implements IBlockPredicate {

        @Nullable
        public int[] offset;
        public BlockStateDTO state;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(FACTORIES, "wouldSurvive", ctx.state(state), ctx.offset(offset));
        }
    }
}
