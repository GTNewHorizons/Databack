package databack.common.dto.worldgen.height_provider;

import java.util.Random;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonParseException;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

public class BuiltinHeightProviders {

    public static void init() {
        TaggedUnionLoader<IHeightProvider> loader = DatapackSerialization
            .getTaggedUnionLoader("worldgen/height_provider");

        loader.addVariant("constant", ConstantHeight.class);
        loader.addVariant("uniform", UniformHeight.class);
        loader.addVariant("biased_to_bottom", BiasedToBottomHeight.class);
        loader.addVariant("very_biased_to_bottom", VeryBiasedToBottomHeight.class);
        loader.addVariant("trapezoid", TrapezoidHeight.class);

        // Bare VerticalAnchor objects (no "type" field) resolve to a constant height.
        loader.setFallback((json, typeOfT, context) -> {
            if (!json.isJsonObject()) throw new JsonParseException("Expected object or typed HeightProvider: " + json);
            VerticalAnchor anchor = context.deserialize(json, VerticalAnchor.class);
            int y = anchor.resolve();
            return random -> y;
        });
    }

    static class VerticalAnchor {

        @Nullable Integer absolute;
        @Nullable Integer above_bottom;
        @Nullable Integer below_top;

        int resolve() {
            if (absolute != null) return absolute;
            if (above_bottom != null) return above_bottom;
            if (below_top != null) return 255 - below_top;
            throw new IllegalStateException("Empty VerticalAnchor");
        }
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

    private static class TrapezoidHeight implements IHeightProvider {

        public VerticalAnchor min_inclusive;
        public VerticalAnchor max_inclusive;
        @Nullable public Integer plateau;

        @Override
        public int get(Random random) {
            return 0; // TODO
        }
    }
}
