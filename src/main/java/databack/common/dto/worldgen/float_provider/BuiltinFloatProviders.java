package databack.common.dto.worldgen.float_provider;

import java.util.Random;

import com.google.gson.JsonParseException;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

public class BuiltinFloatProviders {

    public static void init() {
        TaggedUnionLoader<IFloatProvider> loader = DatapackSerialization.createTaggedUnionLoader("worldgen/float_provider", IFloatProvider.class);

        loader.addVariant("minecraft:constant", ConstantFloat.class);
        loader.addVariant("minecraft:uniform", UniformFloat.class);
        loader.addVariant("minecraft:clamped", ClampedFloat.class);
        loader.addVariant("minecraft:clamped_normal", ClampedNormalFloat.class);
        loader.addVariant("minecraft:trapezoid", TrapezoidFloat.class);

        loader.setFallback((json, typeOfT, context) -> {
            if (!json.isJsonPrimitive()) throw new JsonParseException("Expected number or typed FloatProvider: " + json);
            float value = json.getAsFloat();
            return random -> value;
        });
    }

    private static class ConstantFloat implements IFloatProvider {

        public float value;

        @Override
        public float get(Random random) {
            return value;
        }
    }

    private static class UniformFloat implements IFloatProvider {

        public float min_inclusive;
        public float max_exclusive;

        @Override
        public float get(Random random) {
            return min_inclusive + random.nextFloat() * (max_exclusive - min_inclusive);
        }
    }

    private static class ClampedFloat implements IFloatProvider {

        public float min_inclusive;
        public float max_exclusive;
        public IFloatProvider source;

        @Override
        public float get(Random random) {
            return Math.max(min_inclusive, Math.min(max_exclusive, source.get(random)));
        }
    }

    private static class ClampedNormalFloat implements IFloatProvider {

        public float min_inclusive;
        public float max_exclusive;
        public float mean;
        public float deviation;

        @Override
        public float get(Random random) {
            float value = (float) (random.nextGaussian() * (double) deviation + (double) mean);
            return Math.max(min_inclusive, Math.min(max_exclusive, value));
        }
    }

    private static class TrapezoidFloat implements IFloatProvider {

        public float min;
        public float max;
        public float plateau;

        @Override
        public float get(Random random) {
            float f = max - min;
            float g = plateau;
            float h = (f - g) / 2.0f;
            return min + h + Math.abs(random.nextFloat() * f - h - g / 2.0f);
        }
    }
}
