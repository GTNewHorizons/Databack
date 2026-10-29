package databack.common.dto.worldgen.block_state_provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.dto.BlockStateDTO;
import databack.common.dto.worldgen.block_predicate.IBlockPredicate;
import databack.common.dto.worldgen.int_provider.IIntProvider;
import databack.common.dto.worldgen.noise.DatapackNoise;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;
import databack.common.worldgen.feature.FeatureBuildContext;
import databack.common.worldgen.feature.standin.FtWeightedList;
import databack.common.worldgen.noise.NormalNoise;

/// The block state providers, built as vanilla's own ([IBlockStateProvider#instantiate]).
public class BuiltinBlockStateProviders {

    private static final String PROVIDERS = "feature/stateproviders/";

    public static void init() {
        TaggedUnionLoader<IBlockStateProvider> loader = DatapackSerialization
            .createTaggedUnionLoader("worldgen/block_state_provider", IBlockStateProvider.class);

        loader.addVariant("minecraft:simple_state_provider", SimpleStateProvider.class);
        loader.addVariant("minecraft:weighted_state_provider", WeightedStateProvider.class);
        loader.addVariant("minecraft:noise_provider", NoiseProvider.class);
        loader.addVariant("minecraft:noise_threshold_provider", NoiseThresholdProvider.class);
        loader.addVariant("minecraft:dual_noise_provider", DualNoiseProvider.class);
        loader.addVariant("minecraft:rotated_block_provider", RotatedBlockProvider.class);
        loader.addVariant("minecraft:rule_based_state_provider", RuleBasedStateProvider.class);
        loader.addVariant("minecraft:randomized_int_state_provider", RandomizedIntStateProvider.class);
    }

    private static NormalNoise.NoiseParameters noise(DatapackNoise noise) {
        return new NormalNoise.NoiseParameters(noise.firstOctave, noise.amplitudes);
    }

    private static class SimpleStateProvider implements IBlockStateProvider {

        public BlockStateDTO state;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(PROVIDERS + "BlockStateProvider", "simple", ctx.state(state));
        }
    }

    private static class WeightedEntry {

        public BlockStateDTO data;
        public int weight;
    }

    private static class WeightedStateProvider implements IBlockStateProvider {

        public List<WeightedEntry> entries;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            FtWeightedList.Builder list = FtWeightedList.builder();
            for (WeightedEntry entry : entries) list.add(ctx.state(entry.data), entry.weight);
            return ctx.vanilla.construct(PROVIDERS + "WeightedStateProvider", list.build());
        }
    }

    private static class NoiseProvider implements IBlockStateProvider {

        public DatapackNoise noise;
        public float scale;
        public long seed;
        public BlockStateDTO[] states;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(PROVIDERS + "NoiseProvider", seed, noise(noise), scale,
                ctx.states(Arrays.asList(states)));
        }
    }

    private static class NoiseThresholdProvider implements IBlockStateProvider {

        public DatapackNoise noise;
        public float scale;
        public long seed;
        public float threshold;
        public BlockStateDTO default_state;
        public float high_chance;
        public BlockStateDTO[] high_states;
        public BlockStateDTO[] low_states;

        /// Argument order checked by `FeatureConstructionTest`.
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(PROVIDERS + "NoiseThresholdProvider", seed, noise(noise), scale, threshold,
                high_chance, ctx.state(default_state), ctx.states(Arrays.asList(low_states)),
                ctx.states(Arrays.asList(high_states)));
        }
    }

    private static class DualNoiseProvider implements IBlockStateProvider {

        public DatapackNoise noise;
        public float scale;
        public long seed;
        public DatapackNoise slow_noise;
        public float slow_scale;
        public BlockStateDTO[] states;
        public int[] variety;

        /// Argument order checked by `FeatureConstructionTest`.
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(PROVIDERS + "DualNoiseProvider", ctx.intRange(variety), noise(slow_noise),
                slow_scale, seed, noise(noise), scale, ctx.states(Arrays.asList(states)));
        }
    }

    private static class RotatedBlockProvider implements IBlockStateProvider {

        public BlockStateDTO state;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(PROVIDERS + "RotatedBlockProvider", ctx.state(state).getBlock());
        }
    }

    private static class Rule {

        public IBlockPredicate if_true;
        public IBlockStateProvider then;
    }

    private static class RuleBasedStateProvider implements IBlockStateProvider {

        @Nullable
        public IBlockStateProvider fallback;
        public List<Rule> rules;

        @Nullable
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            Object builtFallback = null;
            if (fallback != null && (builtFallback = fallback.instantiate(ctx)) == null) return null;

            List<Object> builtRules = new ArrayList<>(rules.size());
            for (Rule rule : rules) {
                Object predicate = rule.if_true.instantiate(ctx), provider = rule.then.instantiate(ctx);
                if (predicate == null || provider == null) return null;
                builtRules.add(ctx.vanilla.construct(PROVIDERS + "RuleBasedStateProvider$Rule", predicate, provider));
            }
            return ctx.vanilla.construct(PROVIDERS + "RuleBasedStateProvider", builtFallback, builtRules);
        }
    }

    private static class RandomizedIntStateProvider implements IBlockStateProvider {

        public String property;
        public IBlockStateProvider source;
        public IIntProvider values;

        @Nullable
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            Object built = source.instantiate(ctx);
            return built == null ? null : ctx.vanilla.construct(PROVIDERS + "RandomizedIntStateProvider", built,
                property, ctx.intProvider(values));
        }
    }
}
