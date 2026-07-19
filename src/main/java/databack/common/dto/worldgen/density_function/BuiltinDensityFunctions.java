package databack.common.dto.worldgen.density_function;

import java.lang.reflect.Type;

import net.minecraft.util.MathHelper;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.gtnewhorizon.gtnhlib.noise.NoiseSampler;
import databack.common.context.CacheSlot;
import databack.common.context.StateSlot;
import databack.common.context.WorldContext;
import databack.common.handlers.DatapackNoiseList;
import databack.common.handlers.DensityFunctionList;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;
import databack.common.util.QuantizedFloatMap2D;
import databack.common.util.QuantizedFloatMap3D;

@SuppressWarnings("unused")
public class BuiltinDensityFunctions {

    public static void init() {
        TaggedUnionLoader<IDensityFunction> densityFunctions = DatapackSerialization.createTaggedUnionLoader("worldgen/density_function", IDensityFunction.class);

        densityFunctions.addVariant("minecraft:abs", AbsUnary.class);
        densityFunctions.addVariant("minecraft:blend_density", BlendDensityUnary.class);
        densityFunctions.addVariant("minecraft:cache_2d", Cache2DFunc.class);
        densityFunctions.addVariant("minecraft:cache_all_in_cell", CacheAllInCellUnary.class);
        densityFunctions.addVariant("minecraft:cache_once", CacheOnceUnary.class);
        densityFunctions.addVariant("minecraft:cube", CubeUnary.class);
        densityFunctions.addVariant("minecraft:flat_cache", FlatCacheUnary.class);
        densityFunctions.addVariant("minecraft:half_negative", HalfNegativeUnary.class);
        densityFunctions.addVariant("minecraft:interpolated", InterpolatedFunc.class);
        densityFunctions.addVariant("minecraft:invert", InvertUnary.class);
        densityFunctions.addVariant("minecraft:quarter_negative", QuarterNegativeUnary.class);
        densityFunctions.addVariant("minecraft:slide", SlideUnary.class);
        densityFunctions.addVariant("minecraft:square", SquareUnary.class);
        densityFunctions.addVariant("minecraft:squeeze", SqueezeUnary.class);

        densityFunctions.addVariant("minecraft:add", AddBinary.class);
        densityFunctions.addVariant("minecraft:max", MaxBinary.class);
        densityFunctions.addVariant("minecraft:min", MinBinary.class);
        densityFunctions.addVariant("minecraft:mul", MulBinary.class);

        densityFunctions.addVariant("minecraft:clamp", ClampFunc.class);
        densityFunctions.addVariant("minecraft:constant", ConstantFunc.class);
        densityFunctions.addVariant("minecraft:find_top_surface", FindTopSurfaceFunc.class);
        densityFunctions.addVariant("minecraft:interval_select", IntervalSelectFunc.class);
        densityFunctions.addVariant("minecraft:range_choice", RangeChoiceFunc.class);
        densityFunctions.addVariant("minecraft:shifted_noise", ShiftedNoiseFunc.class);
        densityFunctions.addVariant("minecraft:shift", ShiftFunc.class);
        densityFunctions.addVariant("minecraft:shift_a", ShiftAFunc.class);
        densityFunctions.addVariant("minecraft:shift_b", ShiftBFunc.class);
        densityFunctions.addVariant("minecraft:weird_scaled_sampler", WeirdScaledSampler.class);
        densityFunctions.addVariant("minecraft:y_clamped_gradient", YClampedGradientFunc.class);
        densityFunctions.addVariant("minecraft:noise", NoiseFunc.class);
        densityFunctions.addVariant("minecraft:old_blended_noise", OldBlendedNoise.class);
        densityFunctions.addVariant("minecraft:terrain_shaper_spline", TerrainShaperSpline.class);

        DatapackSerialization.getBuilder().registerTypeAdapter(ISpline.class, new SplineAdapter());
        densityFunctions.addVariant("minecraft:spline", SplineFunc.class);

        densityFunctions.addVariant("minecraft:end_islands", EndIslandsFunc.class);
        densityFunctions.addVariant("minecraft:blend_alpha", BlendAlphaFunc.class);
        densityFunctions.addVariant("minecraft:blend_offset", BlendOffsetFunc.class);

        densityFunctions.setFallback((json, typeOfT, context) -> {
            JsonPrimitive prim = (JsonPrimitive) json;

            if (prim.isString()) {
                var ref = new DensityFunctionRef();
                ref.name = prim.getAsString();

                return ref;
            } else {
                var ref = new ConstantFunc();
                ref.argument = prim.getAsFloat();

                return ref;
            }
        });
    }

    private static class DensityFunctionRef implements IDensityFunction {

        private String name;

        private transient volatile IDensityFunction cache;

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            if (this.cache == null) {
                synchronized (this) {
                    if (this.cache == null) {
                        this.cache = DensityFunctionList.RT.getHandler().getDensityFunction(this.name);
                    }
                }
            }

            return this.cache.compute(context, blockX, blockY, blockZ);
        }
    }

    private static class AbsUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return Math.abs(param);
        }
    }

    private static class BlendDensityUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return param;
        }
    }

    private static class Cache2DFunc implements IDensityFunction {

        public IDensityFunction argument;

        private transient volatile CacheSlot<QuantizedFloatMap2D> slot;

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            if (slot == null) {
                synchronized (this) {
                    if (slot == null) {
                        slot = context.createCacheSlot(QuantizedFloatMap2D::clear);
                    }
                }
            }

            QuantizedFloatMap2D cache = context.getCache(slot);

            if (cache == null) {
                cache = new QuantizedFloatMap2D(1024);
                cache.defaultReturnValue(Float.NaN);

                context.setCache(slot, cache);
            }

            float value = cache.get(blockX, blockZ);

            if (Float.isNaN(value)) {
                value = argument.compute(context, blockX, blockY, blockZ);
                cache.put(blockX, blockZ, value);
            }

            return value;
        }
    }

    private static class FlatCacheUnary implements IDensityFunction {

        public IDensityFunction argument;

        private transient volatile CacheSlot<QuantizedFloatMap2D> slot;

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            if (slot == null) {
                synchronized (this) {
                    if (slot == null) {
                        slot = context.createCacheSlot(QuantizedFloatMap2D::clear);
                    }
                }
            }

            QuantizedFloatMap2D cache = context.getCache(slot);

            if (cache == null) {
                cache = new QuantizedFloatMap2D(0.25f);
                cache.defaultReturnValue(Float.NaN);

                context.setCache(slot, cache);
            }

            float value = cache.get(blockX, blockZ);

            if (Float.isNaN(value)) {
                value = argument.compute(context, blockX, 0, blockZ);
                cache.put(blockX, blockZ, value);
            }

            return value;
        }
    }

    private static class CacheAllInCellUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return param;
        }
    }

    private static class CacheOnceUnary implements IDensityFunction {

        public IDensityFunction argument;

        private transient volatile CacheSlot<QuantizedFloatMap3D> slot;

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            if (slot == null) {
                synchronized (this) {
                    if (slot == null) {
                        slot = context.createCacheSlot(QuantizedFloatMap3D::clear);
                    }
                }
            }

            QuantizedFloatMap3D cache = context.getCache(slot);

            if (cache == null) {
                cache = new QuantizedFloatMap3D(1024);
                cache.defaultReturnValue(Float.NaN);

                context.setCache(slot, cache);
            }

            float value = cache.get(blockX, blockY, blockZ);

            if (Float.isNaN(value)) {
                value = argument.compute(context, blockX, blockY, blockZ);
                cache.put(blockX, blockY, blockZ, value);
            }

            return value;
        }
    }

    private static class CubeUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return param * param * param;
        }
    }

    private static class HalfNegativeUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return param < 0 ? param * 0.5f : param;
        }
    }

    private static class InterpolatedFunc implements IDensityFunction {

        public IDensityFunction argument;

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            int blockX2 = ((int) blockX) & ~0b11;
            int blockY2 = ((int) blockY) & ~0b11;
            int blockZ2 = ((int) blockZ) & ~0b11;

            float c000 = argument.compute(context, blockX2, blockY2, blockZ2);
            float c100 = argument.compute(context, blockX2 + 4, blockY2, blockZ2);
            float c010 = argument.compute(context, blockX2, blockY2 + 4, blockZ2);
            float c110 = argument.compute(context, blockX2 + 4, blockY2 + 4, blockZ2);
            float c001 = argument.compute(context, blockX2, blockY2, blockZ2 + 4);
            float c101 = argument.compute(context, blockX2 + 4, blockY2, blockZ2 + 4);
            float c011 = argument.compute(context, blockX2, blockY2 + 4, blockZ2 + 4);
            float c111 = argument.compute(context, blockX2 + 4, blockY2 + 4, blockZ2 + 4);

            float kx = (((int) blockX) & 0b11) * 0.25f;
            float ky = (((int) blockY) & 0b11) * 0.25f;
            float kz = (((int) blockZ) & 0b11) * 0.25f;

            float kxi = 1f - kx;
            float kyi = 1f - ky;
            float kzi = 1f - kz;

            return c000 * kxi * kyi * kzi
                + c100 * kx * kyi * kzi
                + c010 * kxi * ky * kzi
                + c110 * kx * ky * kzi
                + c001 * kxi * kyi * kz
                + c101 * kx * kyi * kz
                + c011 * kxi * ky * kz
                + c111 * kx * ky * kz;
        }
    }

    private static class InvertUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return 1f / param;
        }
    }

    private static class QuarterNegativeUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return param < 0 ? param * 0.25f : param;
        }
    }

    private static class SlideUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return 0;
        }
    }

    private static class SquareUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return param * param;
        }
    }

    private static class SqueezeUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            param = MathHelper.clamp_float(param, -1f, 1f);

            return param / 2 - param * param * param / 24;
        }
    }

    private static class AddBinary extends BinaryDensityFunction {

        @Override
        protected float compute(float param1, float param2) {
            return param1 + param2;
        }
    }

    private static class MaxBinary extends BinaryDensityFunction {

        @Override
        protected float compute(float param1, float param2) {
            return Math.max(param1, param2);
        }
    }

    private static class MinBinary extends BinaryDensityFunction {

        @Override
        protected float compute(float param1, float param2) {
            return Math.min(param1, param2);
        }
    }

    private static class MulBinary extends BinaryDensityFunction {

        @Override
        protected float compute(float param1, float param2) {
            return param1 * param2;
        }
    }

    private static class ClampFunc implements IDensityFunction {
        public IDensityFunction input;
        public float min, max;

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            return MathHelper.clamp_float(input.compute(context, blockX, blockY, blockZ), min, max);
        }
    }

    private static class ConstantFunc implements IDensityFunction {

        public float argument;

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            return argument;
        }
    }

    private static class FindTopSurfaceFunc implements IDensityFunction {
        public IDensityFunction density, upper_bound;
        public int lower_bound, cell_height;

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            for (int y = (int) upper_bound.compute(context, blockX, blockY, blockZ); y > lower_bound; y -= cell_height) {
                float value = density.compute(context, blockX, y, blockZ);

                if (value > 0) return y;
            }

            return lower_bound;
        }
    }

    private static class IntervalSelectFunc implements IDensityFunction {
        public IDensityFunction input;
        public float[] thresholds;
        public IDensityFunction[] functions;

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            float value = input.compute(context, blockX, blockY, blockZ);

            int len = thresholds.length;

            for (int i = 0; i < len; i++) {
                if (value < thresholds[i]) {
                    return functions[i].compute(context, blockX, blockY, blockZ);
                }
            }

            return functions[len].compute(context, blockX, blockY, blockZ);
        }
    }

    private static class RangeChoiceFunc implements IDensityFunction {
        public IDensityFunction input;
        public float min_inclusive, max_exclusive;
        public IDensityFunction when_in_range, when_out_of_range;

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            float value = input.compute(context, blockX, blockY, blockZ);

            return value >= min_inclusive && value < max_exclusive ? when_in_range.compute(context, blockX, blockY, blockZ) : when_out_of_range.compute(context, blockX, blockY, blockZ);
        }
    }

    private static class ShiftedNoiseFunc implements IDensityFunction {
        public String noise;
        public float xz_scale, y_scale;
        public IDensityFunction shift_x, shift_y, shift_z;

        private transient StateSlot<NoiseSampler> samplerSlot;

        private NoiseSampler getNoiseSampler(WorldContext context) {
            synchronized (this) {
                if (samplerSlot == null) {
                    samplerSlot = context.createStateSlot();
                }
            }

            NoiseSampler sampler = context.getState(samplerSlot);

            if (sampler == null) {
                sampler = DatapackNoiseList.RT.getHandler().getSampler(context.getDimensionSeed(), this.noise);
                context.setState(samplerSlot, sampler);
            }

            return sampler;
        }

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            int sx = (int) shift_x.compute(context, blockX, blockY, blockZ);
            int sy = (int) shift_y.compute(context, blockX, blockY, blockZ);
            int sz = (int) shift_z.compute(context, blockX, blockY, blockZ);

            return (float) getNoiseSampler(context).sample((blockX + sx) * xz_scale, (blockY + sy) * y_scale, (blockZ + sz) * xz_scale);
        }
    }

    private static class ShiftFunc implements IDensityFunction {
        public String argument;

        private transient StateSlot<NoiseSampler> samplerSlot;

        private NoiseSampler getNoiseSampler(WorldContext context) {
            synchronized (this) {
                if (samplerSlot == null) {
                    samplerSlot = context.createStateSlot();
                }
            }

            NoiseSampler sampler = context.getState(samplerSlot);

            if (sampler == null) {
                sampler = DatapackNoiseList.RT.getHandler().getSampler(context.getDimensionSeed(), this.argument);
                context.setState(samplerSlot, sampler);
            }

            return sampler;
        }

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            return (float) (getNoiseSampler(context).sample(blockX / 4, blockY / 4, blockZ / 4) * 4);
        }
    }

    private static class ShiftAFunc implements IDensityFunction {
        public String argument;

        private transient StateSlot<NoiseSampler> samplerSlot;

        private NoiseSampler getNoiseSampler(WorldContext context) {
            synchronized (this) {
                if (samplerSlot == null) {
                    samplerSlot = context.createStateSlot();
                }
            }

            NoiseSampler sampler = context.getState(samplerSlot);

            if (sampler == null) {
                sampler = DatapackNoiseList.RT.getHandler().getSampler(context.getDimensionSeed(), this.argument);
                context.setState(samplerSlot, sampler);
            }

            return sampler;
        }

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            return (float) (getNoiseSampler(context).sample(blockX / 4, 0, blockZ / 4) * 4);
        }
    }

    private static class ShiftBFunc implements IDensityFunction {
        public String argument;

        private transient StateSlot<NoiseSampler> samplerSlot;

        private NoiseSampler getNoiseSampler(WorldContext context) {
            synchronized (this) {
                if (samplerSlot == null) {
                    samplerSlot = context.createStateSlot();
                }
            }

            NoiseSampler sampler = context.getState(samplerSlot);

            if (sampler == null) {
                sampler = DatapackNoiseList.RT.getHandler().getSampler(context.getDimensionSeed(), this.argument);
                context.setState(samplerSlot, sampler);
            }

            return sampler;
        }

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            return (float) (getNoiseSampler(context).sample(blockZ / 4, blockX / 4, 0) * 4);
        }
    }

    private static class WeirdScaledSampler implements IDensityFunction {
        public RarityType rarity_value_mapper;
        public String noise;
        public IDensityFunction input;

        private transient StateSlot<NoiseSampler> samplerSlot;

        private NoiseSampler getNoiseSampler(WorldContext context) {
            synchronized (this) {
                if (samplerSlot == null) {
                    samplerSlot = context.createStateSlot();
                }
            }

            NoiseSampler sampler = context.getState(samplerSlot);

            if (sampler == null) {
                sampler = DatapackNoiseList.RT.getHandler().getSampler(context.getDimensionSeed(), this.noise);
                context.setState(samplerSlot, sampler);
            }

            return sampler;
        }

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            float value = input.compute(context, blockX, blockY, blockZ);

            float rarity = 1f, rarityInv = 1f;

            switch (rarity_value_mapper) {
                case type_1 -> {
                    if (value < -0.75f) {
                        rarity = 0.5f;
                        rarityInv = 1f / 0.5f;
                        break;
                    }

                    if (value < -0.5f) {
                        rarity = 0.75f;
                        rarityInv = 1f / 0.75f;
                        break;
                    }

                    if (value < 0.5f) {
                        break;
                    }

                    if (value < 0.75f) {
                        rarity = 2f;
                        rarityInv = 1f / 2f;
                        break;
                    }

                    rarity = 3f;
                    rarityInv = 1f / 3f;
                }
                case type_2 -> {
                    if (value < -0.5f) {
                        rarity = 0.75f;
                        rarityInv = 1f / 0.75f;
                        break;
                    }

                    if (value < 0f) {
                        break;
                    }

                    if (value < 0.5f) {
                        rarity = 1.5f;
                        rarityInv = 1f / 1.5f;
                        break;
                    }

                    rarity = 2f;
                    rarityInv = 1f / 2f;
                }
            }

            return (float) (rarity * getNoiseSampler(context).sample(blockX * rarityInv, blockY * rarityInv, blockZ * rarityInv));
        }
    }

    private enum RarityType {
        type_1,
        type_2;
    }

    private static class YClampedGradientFunc implements IDensityFunction {

        public int from_y, to_y;
        public float from_value, to_value;

        public static float map(float x, float in_min, float in_max, float out_min, float out_max) {
            return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
        }

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            //noinspection SuspiciousNameCombination
            return map(blockY, from_y, to_y, from_value, to_value);
        }
    }

    private static class NoiseFunc implements IDensityFunction {

        public String noise;
        public float xz_scale, y_scale;

        private transient StateSlot<NoiseSampler> samplerSlot;

        private NoiseSampler getNoiseSampler(WorldContext context) {
            synchronized (this) {
                if (samplerSlot == null) {
                    samplerSlot = context.createStateSlot();
                }
            }

            NoiseSampler sampler = context.getState(samplerSlot);

            if (sampler == null) {
                sampler = DatapackNoiseList.RT.getHandler().getSampler(context.getDimensionSeed(), this.noise);
                context.setState(samplerSlot, sampler);
            }

            return sampler;
        }

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            return (float) getNoiseSampler(context).sample(blockX * xz_scale, blockY * y_scale, blockZ * xz_scale);
        }
    }

    private static class OldBlendedNoise implements IDensityFunction {

        public float xz_scale, y_scale, xz_factor, y_factor, smear_scale_multiplier;

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            return 0; // TODO
        }
    }

    private static class TerrainShaperSpline implements IDensityFunction {

        public SplineType spline;
        public float min_value, max_value;
        public IDensityFunction continentalness, erosion, weirdness;

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            return 0; // TODO
        }

        private enum SplineType {
            offset,
            factor,
            jaggedness
        }
    }

    private static class SplineFunc implements IDensityFunction {
        public ISpline spline;

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            return spline.compute(context, blockX, blockY, blockZ);
        }
    }

    private interface ISpline extends IDensityFunction {

    }

    private static class SplineValue implements ISpline {
        public float coordinate;

        public SplineValue(float coordinate) {
            this.coordinate = coordinate;
        }

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            return coordinate;
        }
    }

    private static class SplineCurve implements ISpline {
        public IDensityFunction coordinate;
        public SplinePoint[] points;

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            return 0; // TODO: for claude
        }
    }

    private static class SplinePoint {
        public float location, derivative;
        public ISpline value;
    }

    private static class SplineAdapter implements JsonSerializer<ISpline>, JsonDeserializer<ISpline> {

        @Override
        public ISpline deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {

            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
                return new SplineValue(json.getAsFloat());
            }

            return context.deserialize(json, SplineCurve.class);
        }

        @Override
        public JsonElement serialize(ISpline src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(src, src.getClass());
        }
    }

    private static class EndIslandsFunc implements IDensityFunction {

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            return 0; // TODO: this
        }
    }

    private static class BlendAlphaFunc implements IDensityFunction {

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            return 0; // TODO: this
        }
    }

    private static class BlendOffsetFunc implements IDensityFunction {

        @Override
        public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
            return 0; // TODO: this
        }
    }
}
