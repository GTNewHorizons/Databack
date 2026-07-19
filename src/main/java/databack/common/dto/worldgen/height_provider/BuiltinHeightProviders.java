package databack.common.dto.worldgen.height_provider;

import java.util.List;
import java.util.Random;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonParseException;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

public class BuiltinHeightProviders {

    public static void init() {
        TaggedUnionLoader<IHeightProvider> loader = DatapackSerialization
            .createTaggedUnionLoader("worldgen/height_provider", IHeightProvider.class);

        loader.addVariant("minecraft:constant", ConstantHeight.class);
        loader.addVariant("minecraft:uniform", UniformHeight.class);
        loader.addVariant("minecraft:biased_to_bottom", BiasedToBottomHeight.class);
        loader.addVariant("minecraft:very_biased_to_bottom", VeryBiasedToBottomHeight.class);
        loader.addVariant("minecraft:trapezoid", TrapezoidHeight.class);
        loader.addVariant("minecraft:weighted_list", WeightedListHeight.class);

        // Bare VerticalAnchor (object or int) resolves to a constant height.
        loader.setFallback((json, typeOfT, context) -> {
            VerticalAnchor anchor;
            if (json.isJsonPrimitive()) {
                anchor = new VerticalAnchor();
                anchor.absolute = json.getAsInt();
            } else if (json.isJsonObject()) {
                anchor = context.deserialize(json, VerticalAnchor.class);
            } else {
                throw new JsonParseException("Expected int, VerticalAnchor object, or typed HeightProvider: " + json);
            }
            int y = anchor.resolve();
            return random -> y;
        });
    }

    private static class ConstantHeight implements IHeightProvider {

        public VerticalAnchor value;

        @Override
        public int get(Random random) {
            return value.resolve();
        }
    }

    private static class UniformHeight implements IHeightProvider {

        public VerticalAnchor min_inclusive;
        public VerticalAnchor max_inclusive;

        @Override
        public int get(Random random) {
            int min = min_inclusive.resolve();
            int max = max_inclusive.resolve();
            return min + random.nextInt(max - min + 1);
        }
    }

    private static class BiasedToBottomHeight implements IHeightProvider {

        public VerticalAnchor min_inclusive;
        public VerticalAnchor max_inclusive;
        @Nullable public Integer inner;

        @Override
        public int get(Random random) {
            int min = min_inclusive.resolve();
            int max = max_inclusive.resolve();
            int range = max - min;
            int samples = inner != null ? inner + 1 : 2;

            int result = max;
            for (int i = 0; i < samples; i++) {
                result = Math.min(result, min + random.nextInt(range + 1));
            }
            return result;
        }
    }

    private static class VeryBiasedToBottomHeight implements IHeightProvider {

        public VerticalAnchor min_inclusive;
        public VerticalAnchor max_inclusive;
        @Nullable public Integer inner;

        @Override
        public int get(Random random) {
            int min = min_inclusive.resolve();
            int max = max_inclusive.resolve();
            int range = max - min;
            int samples = inner != null ? inner + 2 : 3;

            int result = max;
            for (int i = 0; i < samples; i++) {
                result = Math.min(result, min + random.nextInt(range + 1));
            }
            return result;
        }
    }

    private static class WeightedEntry {
        public int weight;
        public IHeightProvider data;
    }

    private static class WeightedListHeight implements IHeightProvider {

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

    private static class TrapezoidHeight implements IHeightProvider {

        public VerticalAnchor min_inclusive;
        public VerticalAnchor max_inclusive;
        @Nullable public Integer plateau;

        @Override
        public int get(Random random) {
            int minY = min_inclusive.resolve();
            int maxY = max_inclusive.resolve();
            int plat = plateau != null ? plateau : 0;
            float f = (float)(maxY - minY);
            float g = (float)plat;
            float h = (f - g) / 2.0f;
            return minY + (int)Math.floor(h + Math.abs(random.nextFloat() * f - h - g / 2.0f));
        }
    }
}
