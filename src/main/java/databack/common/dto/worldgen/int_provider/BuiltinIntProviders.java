package databack.common.dto.worldgen.int_provider;

import java.util.List;
import java.util.Random;

import com.google.gson.JsonParseException;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

public class BuiltinIntProviders {

    public static void init() {
        TaggedUnionLoader<IIntProvider> loader = DatapackSerialization.createTaggedUnionLoader("worldgen/int_provider", IIntProvider.class);

        loader.addVariant("minecraft:constant", ConstantInt.class);
        loader.addVariant("minecraft:uniform", UniformInt.class);
        loader.addVariant("minecraft:biased_to_bottom", BiasedToBottomInt.class);
        loader.addVariant("minecraft:clamped", ClampedInt.class);
        loader.addVariant("minecraft:clamped_normal", ClampedNormalInt.class);
        loader.addVariant("minecraft:trapezoid", TrapezoidInt.class);
        loader.addVariant("minecraft:weighted_list", WeightedListInt.class);

        loader.setFallback((json, typeOfT, context) -> {
            if (!json.isJsonPrimitive()) throw new JsonParseException("Expected int or typed IntProvider: " + json);
            int value = json.getAsInt();
            return random -> value;
        });
    }

    private static class ConstantInt implements IIntProvider {

        public int value;

        @Override
        public int get(Random random) {
            return value;
        }
    }

    private static class UniformInt implements IIntProvider {

        public int min_inclusive;
        public int max_inclusive;

        @Override
        public int get(Random random) {
            return min_inclusive + random.nextInt(max_inclusive - min_inclusive + 1);
        }
    }

    private static class BiasedToBottomInt implements IIntProvider {

        public int min_inclusive;
        public int max_inclusive;

        @Override
        public int get(Random random) {
            int range = max_inclusive - min_inclusive;
            return min_inclusive + random.nextInt(range + 1 - random.nextInt(range + 1));
        }
    }

    private static class ClampedInt implements IIntProvider {

        public int min_inclusive;
        public int max_inclusive;
        public IIntProvider source;

        @Override
        public int get(Random random) {
            return Math.max(min_inclusive, Math.min(max_inclusive, source.get(random)));
        }
    }

    private static class ClampedNormalInt implements IIntProvider {

        public int min_inclusive;
        public int max_inclusive;
        public float mean;
        public float deviation;

        @Override
        public int get(Random random) {
            int value = (int) Math.round(random.nextGaussian() * (double) deviation + (double) mean);
            return Math.max(min_inclusive, Math.min(max_inclusive, value));
        }
    }

    private static class WeightedEntry {
        public int weight;
        public IIntProvider data;
    }

    private static class WeightedListInt implements IIntProvider {

        public List<WeightedEntry> distribution;

        @Override
        public int get(Random random) {
            int totalWeight = 0;
            for (WeightedEntry entry : distribution) {
                totalWeight += entry.weight;
            }

            int roll = random.nextInt(totalWeight);
            for (WeightedEntry entry : distribution) {
                roll -= entry.weight;
                if (roll < 0) return entry.data.get(random);
            }

            return distribution.get(distribution.size() - 1).data.get(random);
        }
    }

    private static class TrapezoidInt implements IIntProvider {

        public int min;
        public int max;
        public int plateau;

        @Override
        public int get(Random random) {
            float f = (float)(max - min);
            float g = (float)plateau;
            float h = (f - g) / 2.0f;
            return min + (int)Math.floor(h + Math.abs(random.nextFloat() * f - h - g / 2.0f));
        }
    }
}
