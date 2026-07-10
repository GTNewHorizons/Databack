package databack.common.dto.worldgen.int_provider;

import java.util.Random;

import com.google.gson.JsonParseException;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

public class BuiltinIntProviders {

    public static void init() {
        TaggedUnionLoader<IIntProvider> loader = DatapackSerialization.getTaggedUnionLoader("worldgen/int_provider");

        loader.addVariant("constant", ConstantInt.class);
        loader.addVariant("uniform", UniformInt.class);
        loader.addVariant("biased_to_bottom", BiasedToBottomInt.class);
        loader.addVariant("clamped", ClampedInt.class);
        loader.addVariant("clamped_normal", ClampedNormalInt.class);
        loader.addVariant("trapezoid", TrapezoidInt.class);

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
        public double mean;
        public double deviation;

        @Override
        public int get(Random random) {
            int value = (int) Math.round(random.nextGaussian() * deviation + mean);
            return Math.max(min_inclusive, Math.min(max_inclusive, value));
        }
    }

    private static class TrapezoidInt implements IIntProvider {

        public int min;
        public int max;
        public int plateau;

        @Override
        public int get(Random random) {
            return 0; // TODO
        }
    }
}
