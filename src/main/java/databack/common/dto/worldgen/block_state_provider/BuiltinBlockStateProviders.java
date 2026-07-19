package databack.common.dto.worldgen.block_state_provider;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import databack.common.dto.worldgen.block_predicate.IBlockPredicate;
import databack.common.dto.worldgen.int_provider.IIntProvider;
import databack.common.dto.worldgen.noise.DatapackNoise;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

@SuppressWarnings("unused")
public class BuiltinBlockStateProviders {

    public static void init() {
        TaggedUnionLoader<IBlockStateProvider> loader = DatapackSerialization
            .createTaggedUnionLoader("worldgen/block_state_provider", IBlockStateProvider.class);

        loader.addVariant("minecraft:simple_state_provider", SimpleStateProvider.class);
        loader.addVariant("minecraft:weighted_state_provider", WeightedStateProvider.class);
        loader.addVariant("minecraft:noise_provider", NoiseProvider.class);
        loader.addVariant("minecraft:noise_threshold_provider", NoiseThresholdProvider.class);
        loader.addVariant("minecraft:dual_noise_provider", DualNoiseProvider.class);
        loader.addVariant("minecraft:rotated_block_provider", RotatedBlockProvider.class);
        loader.addVariant("minecraft:unstable_block_provider", UnstableBlockProvider.class);
        loader.addVariant("minecraft:rule_based_state_provider", RuleBasedStateProvider.class);
        loader.addVariant("minecraft:randomized_int_state_provider", RandomizedIntStateProvider.class);
    }

    // simple_state_provider
    private static class SimpleStateProvider implements IBlockStateProvider {
        public BlockState state;
    }

    // weighted_state_provider
    private static class WeightedEntry {
        public BlockState data;
        public int weight;
    }

    private static class WeightedStateProvider implements IBlockStateProvider {
        public List<WeightedEntry> entries;
    }

    // noise_provider
    private static class NoiseProvider implements IBlockStateProvider {
        public DatapackNoise noise;
        public float scale;
        public long seed;
        public BlockState[] states;
    }

    // noise_threshold_provider
    private static class NoiseThresholdProvider implements IBlockStateProvider {
        public DatapackNoise noise;
        public float scale;
        public long seed;
        public float threshold;
        public BlockState default_state;
        public float high_chance;
        public BlockState[] high_states;
        public BlockState[] low_states;
    }

    // dual_noise_provider — variety is [min, max] inclusive
    private static class DualNoiseProvider implements IBlockStateProvider {
        public DatapackNoise noise;
        public float scale;
        public long seed;
        public DatapackNoise slow_noise;
        public float slow_scale;
        public BlockState[] states;
        public int[] variety;
    }

    // rotated_block_provider
    private static class RotatedBlockProvider implements IBlockStateProvider {
        public BlockState state;
    }

    // unstable_block_provider
    private static class UnstableBlockProvider implements IBlockStateProvider {
        public BlockState state;
    }

    // rule_based_state_provider
    private static class Rule {
        public IBlockPredicate if_true;
        public IBlockStateProvider then;
    }

    private static class RuleBasedStateProvider implements IBlockStateProvider {
        @Nullable public IBlockStateProvider fallback;
        public List<Rule> rules;
    }

    // randomized_int_state_provider
    private static class RandomizedIntStateProvider implements IBlockStateProvider {
        public String property;
        public IBlockStateProvider source;
        public IIntProvider values;
    }
}
