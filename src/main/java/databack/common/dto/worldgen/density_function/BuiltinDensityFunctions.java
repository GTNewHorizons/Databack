package databack.common.dto.worldgen.density_function;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;
import databack.common.worldgen.density.Density;
import databack.common.worldgen.density.DensityBuildContext;
import databack.common.worldgen.density.DensityNode;
import databack.common.worldgen.density.OldBlendedNoise;
import databack.common.worldgen.density.PlaceholderNode;
import databack.common.worldgen.density.Spline;
import databack.common.worldgen.density.TransformNode;
import databack.common.worldgen.density.WeirdScaledNode;

/// DTOs for every 26.1 density function type. Each one knows how to build its runtime counterpart (see
/// [databack.common.worldgen.density.Density]) via [IDensityFunctionFactory#instantiate].
///
/// Numeric fields are `double`, except spline locations, derivatives and values, which the format defines as
/// `float`s.
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
        densityFunctions.addVariant("minecraft:old_blended_noise", OldBlendedNoiseFunc.class);

        DatapackSerialization.getBuilder().registerTypeAdapter(ISpline.class, new SplineAdapter());
        densityFunctions.addVariant("minecraft:spline", SplineFunc.class);

        densityFunctions.addVariant("minecraft:end_islands", EndIslandsFunc.class);
        densityFunctions.addVariant("minecraft:blend_alpha", BlendAlphaFunc.class);
        densityFunctions.addVariant("minecraft:blend_offset", BlendOffsetFunc.class);
        densityFunctions.addVariant("minecraft:beardifier", BeardifierFunc.class);

        densityFunctions.setFallback((json, typeOfT, context) -> {
            JsonPrimitive prim = (JsonPrimitive) json;

            if (prim.isString()) {
                return new DensityFunctionRef(prim.getAsString());
            } else {
                return new ConstantFunc(prim.getAsDouble());
            }
        });
    }

    @Desugar
    public record DensityFunctionRef(String name) implements IDensityFunctionFactory {

        public IDensityFunctionFactory dereference() {
            return databack.common.handlers.DensityFunctionList.RT.getHandler().getDensityFunction(this.name);
        }

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            IDensityFunctionFactory target = ctx.lookupFunction(name);

            if (target == null) {
                DensityBuildContext.LOGGER.warn("Unknown density function '{}'; using 0", name);
                return Density.ZERO;
            }

            return ctx.build(target);
        }
    }

    // <editor-fold desc="Single-input transforms">

    public static class AbsUnary extends UnaryDensityFunction {

        public AbsUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected TransformNode.Kind kind() {
            return TransformNode.Kind.ABS;
        }
    }

    public static class CubeUnary extends UnaryDensityFunction {

        public CubeUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected TransformNode.Kind kind() {
            return TransformNode.Kind.CUBE;
        }
    }

    public static class HalfNegativeUnary extends UnaryDensityFunction {

        public HalfNegativeUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected TransformNode.Kind kind() {
            return TransformNode.Kind.HALF_NEGATIVE;
        }
    }

    public static class InvertUnary extends UnaryDensityFunction {

        public InvertUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected TransformNode.Kind kind() {
            return TransformNode.Kind.INVERT;
        }
    }

    public static class QuarterNegativeUnary extends UnaryDensityFunction {

        public QuarterNegativeUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected TransformNode.Kind kind() {
            return TransformNode.Kind.QUARTER_NEGATIVE;
        }
    }

    public static class SquareUnary extends UnaryDensityFunction {

        public SquareUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected TransformNode.Kind kind() {
            return TransformNode.Kind.SQUARE;
        }
    }

    public static class SqueezeUnary extends UnaryDensityFunction {

        public SqueezeUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected TransformNode.Kind kind() {
            return TransformNode.Kind.SQUEEZE;
        }
    }

    public static class BlendDensityUnary extends UnaryDensityFunction {

        public BlendDensityUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected TransformNode.Kind kind() {
            return null;
        }

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.blendDensity(ctx.build(argument));
        }
    }

    // </editor-fold>

    // <editor-fold desc="Binary functions">

    public static class AddBinary extends BinaryDensityFunction {

        public AddBinary(IDensityFunctionFactory argument1, IDensityFunctionFactory argument2) {
            super(argument1, argument2);
        }

        @Override
        protected DensityNode combine(DensityNode a, DensityNode b) {
            return Density.add(a, b);
        }
    }

    public static class MaxBinary extends BinaryDensityFunction {

        public MaxBinary(IDensityFunctionFactory argument1, IDensityFunctionFactory argument2) {
            super(argument1, argument2);
        }

        @Override
        protected DensityNode combine(DensityNode a, DensityNode b) {
            return Density.max(a, b);
        }
    }

    public static class MinBinary extends BinaryDensityFunction {

        public MinBinary(IDensityFunctionFactory argument1, IDensityFunctionFactory argument2) {
            super(argument1, argument2);
        }

        @Override
        protected DensityNode combine(DensityNode a, DensityNode b) {
            return Density.min(a, b);
        }
    }

    public static class MulBinary extends BinaryDensityFunction {

        public MulBinary(IDensityFunctionFactory argument1, IDensityFunctionFactory argument2) {
            super(argument1, argument2);
        }

        @Override
        protected DensityNode combine(DensityNode a, DensityNode b) {
            return Density.mul(a, b);
        }
    }

    // </editor-fold>

    // <editor-fold desc="Markers">

    @Desugar
    public record InterpolatedFunc(IDensityFunctionFactory argument) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(argument);
        }

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.interpolated(ctx.build(argument));
        }
    }

    @Desugar
    public record FlatCacheUnary(IDensityFunctionFactory argument) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(argument);
        }

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.flatCache(ctx.build(argument));
        }
    }

    @Desugar
    public record Cache2DFunc(IDensityFunctionFactory argument) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(argument);
        }

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.cache2d(ctx.build(argument));
        }
    }

    @Desugar
    public record CacheOnceUnary(IDensityFunctionFactory argument) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(argument);
        }

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.cacheOnce(ctx.build(argument));
        }
    }

    @Desugar
    public record CacheAllInCellUnary(IDensityFunctionFactory argument) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(argument);
        }

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.cacheAllInCell(ctx.build(argument));
        }
    }

    // </editor-fold>

    // <editor-fold desc="Everything else">

    @Desugar
    public record ClampFunc(IDensityFunctionFactory input, double min, double max) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(input);
        }

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.clamp(ctx.build(input), min, max);
        }
    }

    @Desugar
    public record ConstantFunc(double argument) implements IDensityFunctionFactory {

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.constant(argument);
        }
    }

    @Desugar
    public record FindTopSurfaceFunc(
        IDensityFunctionFactory density, IDensityFunctionFactory upper_bound, int lower_bound, int cell_height
    ) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Arrays.asList(density, upper_bound);
        }

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.findTopSurface(ctx.build(density), ctx.build(upper_bound), lower_bound, cell_height);
        }
    }

    /// Not part of 26.1 (added in 26.2). Parsed so that packs using it still load; it evaluates to 0 with a warning.
    @Desugar
    public record IntervalSelectFunc(
        IDensityFunctionFactory input, double[] thresholds, IDensityFunctionFactory[] functions
    ) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            List<IDensityFunctionFactory> result = new ArrayList<>(1 + (functions == null ? 0 : functions.length));
            result.add(input);
            if (functions != null) Collections.addAll(result, functions);
            return result;
        }

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            DensityBuildContext.LOGGER.warn("minecraft:interval_select is not a 26.1 density function type; using 0");
            return Density.ZERO;
        }
    }

    @Desugar
    public record RangeChoiceFunc(
        IDensityFunctionFactory input, double min_inclusive, double max_exclusive, IDensityFunctionFactory when_in_range,
        IDensityFunctionFactory when_out_of_range
    ) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Arrays.asList(input, when_in_range, when_out_of_range);
        }

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.rangeChoice(
                ctx.build(input), min_inclusive, max_exclusive, ctx.build(when_in_range), ctx.build(when_out_of_range));
        }
    }

    @Desugar
    public record ShiftedNoiseFunc(
        String noise, double xz_scale, double y_scale, IDensityFunctionFactory shift_x, IDensityFunctionFactory shift_y,
        IDensityFunctionFactory shift_z
    ) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Arrays.asList(shift_x, shift_y, shift_z);
        }

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.shiftedNoise(
                ctx.build(shift_x), ctx.build(shift_y), ctx.build(shift_z), xz_scale, y_scale, ctx.noise(noise));
        }
    }

    @Desugar
    public record ShiftFunc(String argument) implements IDensityFunctionFactory {

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.shift(ctx.noise(argument));
        }
    }

    @Desugar
    public record ShiftAFunc(String argument) implements IDensityFunctionFactory {

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.shiftA(ctx.noise(argument));
        }
    }

    @Desugar
    public record ShiftBFunc(String argument) implements IDensityFunctionFactory {

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.shiftB(ctx.noise(argument));
        }
    }

    @Desugar
    public record WeirdScaledSampler(RarityType rarity_value_mapper, String noise, IDensityFunctionFactory input)
        implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(input);
        }

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            WeirdScaledNode.Rarity rarity = switch (rarity_value_mapper) {
                case type_1 -> WeirdScaledNode.Rarity.TYPE_1;
                case type_2 -> WeirdScaledNode.Rarity.TYPE_2;
            };

            return Density.weirdScaled(ctx.build(input), ctx.noise(noise), rarity);
        }
    }

    public enum RarityType {
        type_1,
        type_2
    }

    @Desugar
    public record YClampedGradientFunc(int from_y, int to_y, double from_value, double to_value)
        implements IDensityFunctionFactory {

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.yGradient(from_y, to_y, from_value, to_value);
        }
    }

    @Desugar
    public record NoiseFunc(String noise, double xz_scale, double y_scale) implements IDensityFunctionFactory {

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.noise(ctx.noise(noise), xz_scale, y_scale);
        }
    }

    @Desugar
    public record OldBlendedNoiseFunc(
        double xz_scale, double y_scale, double xz_factor, double y_factor, double smear_scale_multiplier
    ) implements IDensityFunctionFactory {

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return new OldBlendedNoise(ctx.terrainRandom(), xz_scale, y_scale, xz_factor, y_factor, smear_scale_multiplier);
        }
    }

    @Desugar
    public record SplineFunc(ISpline spline) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(spline);
        }

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return Density.spline(spline.instantiateSpline(ctx));
        }
    }

    public interface ISpline extends IDensityFunctionFactory {

        Spline instantiateSpline(DensityBuildContext ctx);

        @Override
        default DensityNode instantiate(DensityBuildContext ctx) {
            return Density.spline(instantiateSpline(ctx));
        }
    }

    @Desugar
    public record SplineValue(float coordinate) implements ISpline {

        @Override
        public Spline instantiateSpline(DensityBuildContext ctx) {
            return Spline.fixed(coordinate);
        }
    }

    @Desugar
    public record SplineCurve(IDensityFunctionFactory coordinate, SplinePoint[] points) implements ISpline {

        @Override
        public List<IDensityFunctionFactory> children() {
            List<IDensityFunctionFactory> result = new ArrayList<>(1 + points.length);
            result.add(coordinate);
            for (SplinePoint p : points) {
                result.add(p.value);
            }
            return result;
        }

        @Override
        public Spline instantiateSpline(DensityBuildContext ctx) {
            int n = points.length;
            float[] locations = new float[n];
            float[] derivatives = new float[n];
            Spline[] values = new Spline[n];

            for (int i = 0; i < n; i++) {
                locations[i] = points[i].location;
                derivatives[i] = points[i].derivative;
                values[i] = points[i].value.instantiateSpline(ctx);
            }

            return Spline.curve(ctx.build(coordinate), locations, values, derivatives);
        }
    }

    @Desugar
    public record SplinePoint(float location, float derivative, ISpline value) {

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
        public DensityNode instantiate(DensityBuildContext ctx) {
            return ctx.endIslands();
        }

        @Override
        public int hashCode() {
            return 789;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof EndIslandsFunc;
        }
    }

    public static class BlendAlphaFunc implements IDensityFunctionFactory {

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return PlaceholderNode.BLEND_ALPHA;
        }

        @Override
        public int hashCode() {
            return 123;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof BlendAlphaFunc;
        }
    }

    public static class BlendOffsetFunc implements IDensityFunctionFactory {

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return PlaceholderNode.BLEND_OFFSET;
        }

        @Override
        public int hashCode() {
            return 456;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof BlendOffsetFunc;
        }
    }

    /// Structure terrain adaptation. Structures aren't generated yet, so this is always 0.
    public static class BeardifierFunc implements IDensityFunctionFactory {

        @Override
        public DensityNode instantiate(DensityBuildContext ctx) {
            return PlaceholderNode.BEARDIFIER;
        }

        @Override
        public int hashCode() {
            return 1011;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof BeardifierFunc;
        }
    }

    // </editor-fold>
}
