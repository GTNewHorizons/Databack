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
import databack.common.context.WorldContext;
import databack.common.context.WorldContextImpl;
import databack.common.handlers.DatapackNoiseList;
import databack.common.handlers.DensityFunctionList;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;
import databack.common.util.QuantizedFloatMap2D;
import databack.common.util.QuantizedFloatMap3D;

@SuppressWarnings("unused")
public class BuiltinDensityFunctions {

    public static void init() {
        TaggedUnionLoader<IDensityFunctionFactory> densityFunctions = DatapackSerialization.createTaggedUnionLoader("worldgen/density_function", IDensityFunctionFactory.class);

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

    public static class DensityFunctionRef implements IDensityFunctionFactory {

        private String name;

        private transient volatile IDensityFunctionFactory cache;

        public IDensityFunctionFactory getFactory() {
            if (this.cache == null) {
                synchronized (this) {
                    if (this.cache == null) {
                        this.cache = DensityFunctionList.RT.getHandler().getDensityFunction(this.name);
                    }
                }
            }
            return this.cache;
        }

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            return getFactory().instantiate(ctx);
        }
    }

    public static class AbsUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return Math.abs(param);
        }
    }

    public static class BlendDensityUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return param;
        }
    }

    public static class Cache2DFunc implements IDensityFunctionFactory {

        public IDensityFunctionFactory argument;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction arg = argument.instantiate(ctx);
            CacheSlot<QuantizedFloatMap2D> slot = WorldContextImpl.allocateCacheSlot(QuantizedFloatMap2D::clear);
            return (context, blockX, blockY, blockZ) -> {
                QuantizedFloatMap2D map = context.getCache(slot);
                if (map == null) {
                    map = new QuantizedFloatMap2D(1024);
                    map.defaultReturnValue(Float.NaN);
                    context.setCache(slot, map);
                }
                float value = map.get(blockX, blockZ);
                if (Float.isNaN(value)) {
                    value = arg.compute(context, blockX, blockY, blockZ);
                    map.put(blockX, blockZ, value);
                }
                return value;
            };
        }
    }

    public static class FlatCacheUnary implements IDensityFunctionFactory {

        public IDensityFunctionFactory argument;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction arg = argument.instantiate(ctx);
            CacheSlot<QuantizedFloatMap2D> slot = WorldContextImpl.allocateCacheSlot(QuantizedFloatMap2D::clear);
            return (context, blockX, blockY, blockZ) -> {
                QuantizedFloatMap2D map = context.getCache(slot);
                if (map == null) {
                    map = new QuantizedFloatMap2D(0.25f);
                    map.defaultReturnValue(Float.NaN);
                    context.setCache(slot, map);
                }
                float value = map.get(blockX, blockZ);
                if (Float.isNaN(value)) {
                    value = arg.compute(context, blockX, 0, blockZ);
                    map.put(blockX, blockZ, value);
                }
                return value;
            };
        }
    }

    public static class CacheAllInCellUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return param;
        }
    }

    public static class CacheOnceUnary implements IDensityFunctionFactory {

        public IDensityFunctionFactory argument;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction arg = argument.instantiate(ctx);
            CacheSlot<QuantizedFloatMap3D> slot = WorldContextImpl.allocateCacheSlot(QuantizedFloatMap3D::clear);
            return (context, blockX, blockY, blockZ) -> {
                QuantizedFloatMap3D map = context.getCache(slot);
                if (map == null) {
                    map = new QuantizedFloatMap3D(1024);
                    map.defaultReturnValue(Float.NaN);
                    context.setCache(slot, map);
                }
                float value = map.get(blockX, blockY, blockZ);
                if (Float.isNaN(value)) {
                    value = arg.compute(context, blockX, blockY, blockZ);
                    map.put(blockX, blockY, blockZ, value);
                }
                return value;
            };
        }
    }

    public static class CubeUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return param * param * param;
        }
    }

    public static class HalfNegativeUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return param < 0 ? param * 0.5f : param;
        }
    }

    public static class InterpolatedFunc implements IDensityFunctionFactory {

        public IDensityFunctionFactory argument;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction arg = argument.instantiate(ctx);
            return (context, blockX, blockY, blockZ) -> {
                int bx2 = ((int) blockX) & ~0b11;
                int by2 = ((int) blockY) & ~0b11;
                int bz2 = ((int) blockZ) & ~0b11;

                float c000 = arg.compute(context, bx2, by2, bz2);
                float c100 = arg.compute(context, bx2 + 4, by2, bz2);
                float c010 = arg.compute(context, bx2, by2 + 4, bz2);
                float c110 = arg.compute(context, bx2 + 4, by2 + 4, bz2);
                float c001 = arg.compute(context, bx2, by2, bz2 + 4);
                float c101 = arg.compute(context, bx2 + 4, by2, bz2 + 4);
                float c011 = arg.compute(context, bx2, by2 + 4, bz2 + 4);
                float c111 = arg.compute(context, bx2 + 4, by2 + 4, bz2 + 4);

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
            };
        }
    }

    public static class InvertUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return 1f / param;
        }
    }

    public static class QuarterNegativeUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return param < 0 ? param * 0.25f : param;
        }
    }

    public static class SlideUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return 0;
        }
    }

    public static class SquareUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            return param * param;
        }
    }

    public static class SqueezeUnary extends UnaryDensityFunction {

        @Override
        protected float compute(float param) {
            param = MathHelper.clamp_float(param, -1f, 1f);
            return param / 2 - param * param * param / 24;
        }
    }

    public static class AddBinary extends BinaryDensityFunction {

        @Override
        protected float compute(float param1, float param2) {
            return param1 + param2;
        }
    }

    public static class MaxBinary extends BinaryDensityFunction {

        @Override
        protected float compute(float param1, float param2) {
            return Math.max(param1, param2);
        }
    }

    public static class MinBinary extends BinaryDensityFunction {

        @Override
        protected float compute(float param1, float param2) {
            return Math.min(param1, param2);
        }
    }

    public static class MulBinary extends BinaryDensityFunction {

        @Override
        protected float compute(float param1, float param2) {
            return param1 * param2;
        }
    }

    public static class ClampFunc implements IDensityFunctionFactory {

        public IDensityFunctionFactory input;
        public float min, max;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction inputFn = input.instantiate(ctx);
            float lo = min, hi = max;
            return (context, blockX, blockY, blockZ) ->
                MathHelper.clamp_float(inputFn.compute(context, blockX, blockY, blockZ), lo, hi);
        }
    }

    public static class ConstantFunc implements IDensityFunctionFactory {

        public float argument;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            float v = argument;
            return (context, blockX, blockY, blockZ) -> v;
        }
    }

    public static class FindTopSurfaceFunc implements IDensityFunctionFactory {

        public IDensityFunctionFactory density, upper_bound;
        public int lower_bound, cell_height;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction densityFn = density.instantiate(ctx);
            IDensityFunction upperBoundFn = upper_bound.instantiate(ctx);
            int lb = lower_bound, ch = cell_height;
            return (context, blockX, blockY, blockZ) -> {
                for (int y = (int) upperBoundFn.compute(context, blockX, blockY, blockZ); y > lb; y -= ch) {
                    if (densityFn.compute(context, blockX, y, blockZ) > 0) return y;
                }
                return lb;
            };
        }
    }

    public static class IntervalSelectFunc implements IDensityFunctionFactory {

        public IDensityFunctionFactory input;
        public float[] thresholds;
        public IDensityFunctionFactory[] functions;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction inputFn = input.instantiate(ctx);
            IDensityFunction[] fns = new IDensityFunction[functions.length];
            for (int i = 0; i < functions.length; i++) fns[i] = functions[i].instantiate(ctx);
            float[] thresh = thresholds;
            return (context, blockX, blockY, blockZ) -> {
                float value = inputFn.compute(context, blockX, blockY, blockZ);
                int len = thresh.length;
                for (int i = 0; i < len; i++) {
                    if (value < thresh[i]) return fns[i].compute(context, blockX, blockY, blockZ);
                }
                return fns[len].compute(context, blockX, blockY, blockZ);
            };
        }
    }

    public static class RangeChoiceFunc implements IDensityFunctionFactory {

        public IDensityFunctionFactory input;
        public float min_inclusive, max_exclusive;
        public IDensityFunctionFactory when_in_range, when_out_of_range;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction inputFn = input.instantiate(ctx);
            IDensityFunction inRange = when_in_range.instantiate(ctx);
            IDensityFunction outOfRange = when_out_of_range.instantiate(ctx);
            float lo = min_inclusive, hi = max_exclusive;
            return (context, blockX, blockY, blockZ) -> {
                float value = inputFn.compute(context, blockX, blockY, blockZ);
                return value >= lo && value < hi
                    ? inRange.compute(context, blockX, blockY, blockZ)
                    : outOfRange.compute(context, blockX, blockY, blockZ);
            };
        }
    }

    public static class ShiftedNoiseFunc implements IDensityFunctionFactory {

        public String noise;
        public float xz_scale, y_scale;
        public IDensityFunctionFactory shift_x, shift_y, shift_z;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            NoiseSampler sampler = DatapackNoiseList.RT.getHandler().getSampler(ctx.getDimensionSeed(), noise);
            IDensityFunction sx = shift_x.instantiate(ctx);
            IDensityFunction sy = shift_y.instantiate(ctx);
            IDensityFunction sz = shift_z.instantiate(ctx);
            float xzs = xz_scale, ys = y_scale;
            return (context, blockX, blockY, blockZ) -> {
                int dx = (int) sx.compute(context, blockX, blockY, blockZ);
                int dy = (int) sy.compute(context, blockX, blockY, blockZ);
                int dz = (int) sz.compute(context, blockX, blockY, blockZ);
                return (float) sampler.sample((blockX + dx) * xzs, (blockY + dy) * ys, (blockZ + dz) * xzs);
            };
        }
    }

    public static class ShiftFunc implements IDensityFunctionFactory {

        public String argument;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            NoiseSampler sampler = DatapackNoiseList.RT.getHandler().getSampler(ctx.getDimensionSeed(), argument);
            return (context, blockX, blockY, blockZ) ->
                (float) (sampler.sample(blockX / 4, blockY / 4, blockZ / 4) * 4);
        }
    }

    public static class ShiftAFunc implements IDensityFunctionFactory {

        public String argument;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            NoiseSampler sampler = DatapackNoiseList.RT.getHandler().getSampler(ctx.getDimensionSeed(), argument);
            return (context, blockX, blockY, blockZ) ->
                (float) (sampler.sample(blockX / 4, 0, blockZ / 4) * 4);
        }
    }

    public static class ShiftBFunc implements IDensityFunctionFactory {

        public String argument;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            NoiseSampler sampler = DatapackNoiseList.RT.getHandler().getSampler(ctx.getDimensionSeed(), argument);
            return (context, blockX, blockY, blockZ) ->
                (float) (sampler.sample(blockZ / 4, blockX / 4, 0) * 4);
        }
    }

    public static class WeirdScaledSampler implements IDensityFunctionFactory {

        public RarityType rarity_value_mapper;
        public String noise;
        public IDensityFunctionFactory input;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            NoiseSampler sampler = DatapackNoiseList.RT.getHandler().getSampler(ctx.getDimensionSeed(), noise);
            IDensityFunction inputFn = input.instantiate(ctx);
            RarityType rarityMapper = rarity_value_mapper;
            return (context, blockX, blockY, blockZ) -> {
                float value = inputFn.compute(context, blockX, blockY, blockZ);
                float rarity = 1f, rarityInv = 1f;
                switch (rarityMapper) {
                    case type_1 -> {
                        if (value < -0.75f) { rarity = 0.5f; rarityInv = 2f; break; }
                        if (value < -0.5f)  { rarity = 0.75f; rarityInv = 4f / 3f; break; }
                        if (value < 0.5f)   { break; }
                        if (value < 0.75f)  { rarity = 2f; rarityInv = 0.5f; break; }
                        rarity = 3f; rarityInv = 1f / 3f;
                    }
                    case type_2 -> {
                        if (value < -0.5f) { rarity = 0.75f; rarityInv = 4f / 3f; break; }
                        if (value < 0f)    { break; }
                        if (value < 0.5f)  { rarity = 1.5f; rarityInv = 2f / 3f; break; }
                        rarity = 2f; rarityInv = 0.5f;
                    }
                }
                return (float) (rarity * sampler.sample(blockX * rarityInv, blockY * rarityInv, blockZ * rarityInv));
            };
        }
    }

    public enum RarityType {
        type_1,
        type_2;
    }

    public static class YClampedGradientFunc implements IDensityFunctionFactory {

        public int from_y, to_y;
        public float from_value, to_value;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            float inMin = from_y, inMax = to_y, outMin = from_value, outMax = to_value;
            return (context, blockX, blockY, blockZ) ->
                (blockY - inMin) * (outMax - outMin) / (inMax - inMin) + outMin;
        }
    }

    public static class NoiseFunc implements IDensityFunctionFactory {

        public String noise;
        public float xz_scale, y_scale;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            NoiseSampler sampler = DatapackNoiseList.RT.getHandler().getSampler(ctx.getDimensionSeed(), noise);
            float xzs = xz_scale, ys = y_scale;
            return (context, blockX, blockY, blockZ) ->
                (float) sampler.sample(blockX * xzs, blockY * ys, blockZ * xzs);
        }
    }

    public static class OldBlendedNoise implements IDensityFunctionFactory {

        public float xz_scale, y_scale, xz_factor, y_factor, smear_scale_multiplier;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            return (context, blockX, blockY, blockZ) -> 0; // TODO
        }
    }

    public enum SplineType {
        offset,
        factor,
        jaggedness
    }

    public static class SplineFunc implements IDensityFunctionFactory {

        public ISpline spline;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            return spline.instantiate(ctx);
        }
    }

    public interface ISpline extends IDensityFunctionFactory {

    }

    public static class SplineValue implements ISpline {

        public float coordinate;

        public SplineValue(float coordinate) {
            this.coordinate = coordinate;
        }

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            float v = coordinate;
            return (context, blockX, blockY, blockZ) -> v;
        }
    }

    public static class SplineCurve implements ISpline {

        public IDensityFunctionFactory coordinate;
        public SplinePoint[] points;

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction coord = coordinate.instantiate(ctx);
            int N = points.length;
            IDensityFunction[] values = new IDensityFunction[N];
            float[] locations = new float[N];
            float[] derivatives = new float[N];
            for (int i = 0; i < N; i++) {
                values[i] = points[i].value.instantiate(ctx);
                locations[i] = points[i].location;
                derivatives[i] = points[i].derivative;
            }
            return (context, blockX, blockY, blockZ) -> {
                float t = coord.compute(context, blockX, blockY, blockZ);

                if (N == 0) return 0;
                if (t <= locations[0]) return values[0].compute(context, blockX, blockY, blockZ);
                if (t >= locations[N - 1]) return values[N - 1].compute(context, blockX, blockY, blockZ);

                int lo = 0, hi = N - 1;
                while (hi - lo > 1) {
                    int mid = (lo + hi) >>> 1;
                    if (locations[mid] <= t) lo = mid; else hi = mid;
                }

                float f0 = values[lo].compute(context, blockX, blockY, blockZ);
                float f1 = values[hi].compute(context, blockX, blockY, blockZ);
                float dx = locations[hi] - locations[lo];
                float u = (t - locations[lo]) / dx;
                float u2 = u * u;
                float u3 = u2 * u;

                float h00 = 2 * u3 - 3 * u2 + 1;
                float h10 = u3 - 2 * u2 + u;
                float h01 = -2 * u3 + 3 * u2;
                float h11 = u3 - u2;

                return h00 * f0 + h10 * dx * derivatives[lo] + h01 * f1 + h11 * dx * derivatives[hi];
            };
        }
    }

    public static class SplinePoint {
        public float location, derivative;
        public ISpline value;
    }

    public static class SplineAdapter implements JsonSerializer<ISpline>, JsonDeserializer<ISpline> {

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

    public static class EndIslandsFunc implements IDensityFunctionFactory {

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            return (context, blockX, blockY, blockZ) -> 0; // TODO: this
        }
    }

    public static class BlendAlphaFunc implements IDensityFunctionFactory {

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            return (context, blockX, blockY, blockZ) -> 1f;
        }
    }

    public static class BlendOffsetFunc implements IDensityFunctionFactory {

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            return (context, blockX, blockY, blockZ) -> 0;
        }
    }
}
