package databack.common.dto.worldgen.density_function;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.MathHelper;

import com.github.bsideup.jabel.Desugar;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.gtnewhorizon.gtnhlib.noise.NoiseSampler;
import databack.common.context.WorldContext;
import databack.common.dto.worldgen.density_function.DensityBuffer.ConstantBuffer;
import databack.common.dto.worldgen.density_function.DensityBuffer.CubeBuffer;
import databack.common.handlers.DatapackNoiseList;
import databack.common.handlers.DensityFunctionList;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;
import databack.common.util.DBDataUtils;

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

        densityFunctions.setFallback((json, typeOfT, context) -> {
            JsonPrimitive prim = (JsonPrimitive) json;

            if (prim.isString()) {
                return new DensityFunctionRef(prim.getAsString());
            } else {
                return new ConstantFunc(prim.getAsFloat());
            }
        });
    }

    @Desugar
    public record DensityFunctionRef(String name) implements IDensityFunctionFactory {

        public IDensityFunctionFactory dereference() {
            return DensityFunctionList.RT.getHandler().getDensityFunction(this.name);
        }

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            return dereference().instantiate(ctx);
        }
    }

    public static class AbsUnary extends UnaryDensityFunction {

        public AbsUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected float compute(float param) {
            return Math.abs(param);
        }
    }

    public static class BlendDensityUnary extends UnaryDensityFunction {

        public BlendDensityUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected float compute(float param) {
            return param;
        }
    }

    public static class CubeUnary extends UnaryDensityFunction {

        public CubeUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected float compute(float param) {
            return param * param * param;
        }
    }

    public static class HalfNegativeUnary extends UnaryDensityFunction {

        public HalfNegativeUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected float compute(float param) {
            return param < 0 ? param * 0.5f : param;
        }
    }

    public static class InvertUnary extends UnaryDensityFunction {

        public InvertUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected float compute(float param) {
            return 1f / param;
        }
    }

    public static class QuarterNegativeUnary extends UnaryDensityFunction {

        public QuarterNegativeUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected float compute(float param) {
            return param < 0 ? param * 0.25f : param;
        }
    }

    public static class SquareUnary extends UnaryDensityFunction {

        public SquareUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected float compute(float param) {
            return param * param;
        }
    }

    public static class SqueezeUnary extends UnaryDensityFunction {

        public SqueezeUnary(IDensityFunctionFactory argument) {
            super(argument);
        }

        @Override
        protected float compute(float param) {
            param = MathHelper.clamp_float(param, -1f, 1f);
            return param / 2 - param * param * param / 24;
        }
    }

    @Desugar
    public record CacheAllInCellUnary(IDensityFunctionFactory argument) implements IDensityFunctionFactory {

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            return argument.instantiate(ctx);
        }

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(argument);
        }
    }

    public static class AddBinary extends BinaryDensityFunction {

        public AddBinary(IDensityFunctionFactory argument1, IDensityFunctionFactory argument2) {
            super(argument1, argument2);
        }

        @Override
        protected float compute(float param1, float param2) {
            return param1 + param2;
        }
    }

    public static class MaxBinary extends BinaryDensityFunction {

        public MaxBinary(IDensityFunctionFactory argument1, IDensityFunctionFactory argument2) {
            super(argument1, argument2);
        }

        @Override
        protected float compute(float param1, float param2) {
            return Math.max(param1, param2);
        }
    }

    public static class MinBinary extends BinaryDensityFunction {

        public MinBinary(IDensityFunctionFactory argument1, IDensityFunctionFactory argument2) {
            super(argument1, argument2);
        }

        @Override
        protected float compute(float param1, float param2) {
            return Math.min(param1, param2);
        }
    }

    public static class MulBinary extends BinaryDensityFunction {

        public MulBinary(IDensityFunctionFactory argument1, IDensityFunctionFactory argument2) {
            super(argument1, argument2);
        }

        @Override
        protected float compute(float param1, float param2) {
            return param1 * param2;
        }
    }

    private static class FlatBuffer implements DensityBuffer {

        public final float[] data = new float[256];

        @Override
        public float get(int relX, int relY, int relZ) {
            return data[relZ << 4 | relX];
        }

        @Override
        public void discard() {

        }
    }

    @Desugar
    public record Cache2DFunc(IDensityFunctionFactory argument) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(argument);
        }

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction arg = argument.instantiate(ctx);

            return new IDensityFunction() {

                private int cacheX, cacheZ;
                private boolean initialized;

                private final DensityMask testMask = new DensityMask();
                private final DensityMask cacheMask = new DensityMask();
                private final FlatBuffer buffer = new FlatBuffer();

                @Override
                public boolean hasTrait(DensityFuncTrait trait) {
                    return trait == DensityFuncTrait.Flat || arg.hasTrait(trait);
                }

                @Override
                public DensityBuffer compute(int cubeX, int cubeY, int cubeZ, DensityMask mask) {
                    testMask.flatCopy(mask);

                    if (cubeX != cacheX || cubeZ != cacheZ || !initialized || !cacheMask.allSet(testMask)) {
                        if (cubeX != cacheX || cubeZ != cacheZ || !initialized) {
                            cacheMask.clear();
                        }

                        cacheX = cubeX;
                        cacheZ = cubeZ;
                        initialized = true;

                        testMask.removeAll(cacheMask);

                        DensityBuffer result = arg.compute(cubeX, 0, cubeZ, testMask);

                        cacheMask.or(testMask);

                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (testMask.isSet(x, 0, z)) {
                                    buffer.data[z << 4 | x] = result.get(x, 0, z);
                                }
                            }
                        }
                    }

                    return buffer;
                }
            };
        }
    }

    private static class FlatCacheBuffer implements DensityBuffer {

        public final float[] data = new float[16];

        @Override
        public float get(int relX, int relY, int relZ) {
            relX >>= 2;
            relZ >>= 2;

            return data[relZ << 2 | relX];
        }

        @Override
        public void discard() {

        }
    }

    @Desugar
    public record FlatCacheUnary(IDensityFunctionFactory argument) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(argument);
        }

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction arg = argument.instantiate(ctx);

            return new IDensityFunction() {

                private int cacheX, cacheZ;
                private boolean initialized;

                private final FlatCacheBuffer buffer = new FlatCacheBuffer();

                @Override
                public boolean hasTrait(DensityFuncTrait trait) {
                    return trait == DensityFuncTrait.Flat || arg.hasTrait(trait);
                }

                @Override
                public DensityBuffer compute(int cubeX, int cubeY, int cubeZ, DensityMask mask) {
                    if (cubeX != cacheX || cubeZ != cacheZ || !initialized) {
                        cacheX = cubeX;
                        cacheZ = cubeZ;
                        initialized = true;

                        DensityMask next = ctx.getMask();

                        for (int z = 0; z < 4; z++) {
                            for (int x = 0; x < 4; x++) {
                                next.set(x << 2, 0, z << 2);
                            }
                        }

                        DensityBuffer result = arg.compute(cubeX, 0, cubeZ, next);

                        ctx.releaseMask(next);

                        for (int z = 0; z < 4; z++) {
                            for (int x = 0; x < 4; x++) {
                                buffer.data[z << 2 | x] = result.get(x << 2, 0, z << 2);
                            }
                        }

                        result.discard();
                    }

                    return buffer;
                }
            };
        }
    }

    @Desugar
    public record CacheOnceUnary(IDensityFunctionFactory argument) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(argument);
        }

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction arg = argument.instantiate(ctx);

            return new IDensityFunction() {

                private int cacheX, cacheY, cacheZ;
                private boolean initialized;

                private final DensityMask cacheMask = new DensityMask();
                private final CubeBuffer buffer = new CubeBuffer(null);

                @Override
                public boolean hasTrait(DensityFuncTrait trait) {
                    return arg.hasTrait(trait);
                }

                @Override
                public DensityBuffer compute(int cubeX, int cubeY, int cubeZ, DensityMask mask) {
                    if (cubeX != cacheX
                        || cubeY != cacheY
                        || cubeZ != cacheZ
                        || !initialized
                        || !cacheMask.allSet(mask)) {
                        if (cubeX != cacheX || cubeY != cacheY || cubeZ != cacheZ || !initialized) {
                            cacheMask.clear();
                        }

                        cacheX = cubeX;
                        cacheY = cubeY;
                        cacheZ = cubeZ;
                        initialized = true;

                        DensityMask toCalculate = ctx.getMask().copy(mask).removeAll(cacheMask);

                        DensityBuffer result = arg.compute(cubeX, cubeY, cubeZ, toCalculate);

                        buffer.copyFrom(result, toCalculate);
                        cacheMask.or(toCalculate);

                        ctx.releaseMask(toCalculate);
                        result.discard();
                    }

                    return buffer;
                }
            };
        }
    }

    @Desugar
    public record InterpolatedFunc(IDensityFunctionFactory argument) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(argument);
        }

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction arg = argument.instantiate(ctx);

            // Static masks for the 8 sample regions of the 5x5x5 corner grid.
            // Position 16 on any axis is fetched from the adjacent cube at position 0.
            DensityMask innerMask = new DensityMask();
            for (int gz = 0; gz < 4; gz++) {
                for (int gy = 0; gy < 4; gy++) {
                    for (int gx = 0; gx < 4; gx++) {
                        innerMask.set(gx * 4, gy * 4, gz * 4);
                    }
                }
            }

            DensityMask xFaceMask = new DensityMask();
            for (int gz = 0; gz < 4; gz++) {
                for (int gy = 0; gy < 4; gy++) {
                    xFaceMask.set(0, gy * 4, gz * 4);
                }
            }

            DensityMask yFaceMask = new DensityMask();
            for (int gz = 0; gz < 4; gz++) {
                for (int gx = 0; gx < 4; gx++) {
                    yFaceMask.set(gx * 4, 0, gz * 4);
                }
            }

            DensityMask zFaceMask = new DensityMask();
            for (int gy = 0; gy < 4; gy++) {
                for (int gx = 0; gx < 4; gx++) {
                    zFaceMask.set(gx * 4, gy * 4, 0);
                }
            }

            DensityMask xyEdgeMask = new DensityMask();
            for (int gz = 0; gz < 4; gz++) {
                xyEdgeMask.set(0, 0, gz * 4);
            }

            DensityMask xzEdgeMask = new DensityMask();
            for (int gy = 0; gy < 4; gy++) {
                xzEdgeMask.set(0, gy * 4, 0);
            }

            DensityMask yzEdgeMask = new DensityMask();
            for (int gx = 0; gx < 4; gx++) {
                yzEdgeMask.set(gx * 4, 0, 0);
            }

            DensityMask cornerMask = new DensityMask();
            cornerMask.set(0, 0, 0);

            float[][][] corners = new float[5][5][5];

            return (cubeX, cubeY, cubeZ, mask) -> {
                DensityBuffer inner = arg.compute(cubeX, cubeY, cubeZ, innerMask);
                for (int gz = 0; gz < 4; gz++) {
                    for (int gy = 0; gy < 4; gy++) {
                        for (int gx = 0; gx < 4; gx++) {
                            corners[gx][gy][gz] = inner.get(gx * 4, gy * 4, gz * 4);
                        }
                    }
                }
                inner.discard();

                DensityBuffer xFace = arg.compute(cubeX + 1, cubeY, cubeZ, xFaceMask);
                for (int gz = 0; gz < 4; gz++) {
                    for (int gy = 0; gy < 4; gy++) {
                        corners[4][gy][gz] = xFace.get(0, gy * 4, gz * 4);
                    }
                }
                xFace.discard();

                DensityBuffer yFace = arg.compute(cubeX, cubeY + 1, cubeZ, yFaceMask);
                for (int gz = 0; gz < 4; gz++) {
                    for (int gx = 0; gx < 4; gx++) {
                        corners[gx][4][gz] = yFace.get(gx * 4, 0, gz * 4);
                    }
                }
                yFace.discard();

                DensityBuffer zFace = arg.compute(cubeX, cubeY, cubeZ + 1, zFaceMask);
                for (int gy = 0; gy < 4; gy++) {
                    for (int gx = 0; gx < 4; gx++) {
                        corners[gx][gy][4] = zFace.get(gx * 4, gy * 4, 0);
                    }
                }
                zFace.discard();

                DensityBuffer xyEdge = arg.compute(cubeX + 1, cubeY + 1, cubeZ, xyEdgeMask);
                for (int gz = 0; gz < 4; gz++) {
                    corners[4][4][gz] = xyEdge.get(0, 0, gz * 4);
                }
                xyEdge.discard();

                DensityBuffer xzEdge = arg.compute(cubeX + 1, cubeY, cubeZ + 1, xzEdgeMask);
                for (int gy = 0; gy < 4; gy++) {
                    corners[4][gy][4] = xzEdge.get(0, gy * 4, 0);
                }
                xzEdge.discard();

                DensityBuffer yzEdge = arg.compute(cubeX, cubeY + 1, cubeZ + 1, yzEdgeMask);
                for (int gx = 0; gx < 4; gx++) {
                    corners[gx][4][4] = yzEdge.get(gx * 4, 0, 0);
                }
                yzEdge.discard();

                DensityBuffer corner = arg.compute(cubeX + 1, cubeY + 1, cubeZ + 1, cornerMask);
                corners[4][4][4] = corner.get(0, 0, 0);
                corner.discard();

                CubeBuffer out = ctx.getCubeBuffer();
                for (int z = 0; z < 16; z++) {
                    int gz = z >> 2;
                    float kz = (z & 3) * 0.25f, kzi = 1f - kz;
                    for (int y = 0; y < 16; y++) {
                        int gy = y >> 2;
                        float ky = (y & 3) * 0.25f, kyi = 1f - ky;
                        for (int x = 0; x < 16; x++) {
                            if (!mask.isSet(x, y, z)) {
                                continue;
                            }
                            int gx = x >> 2;
                            float kx = (x & 3) * 0.25f, kxi = 1f - kx;
                            out.set(
                                x, y, z, corners[gx][gy][gz] * kxi * kyi * kzi
                                    + corners[gx + 1][gy][gz] * kx * kyi * kzi
                                    + corners[gx][gy + 1][gz] * kxi * ky * kzi
                                    + corners[gx + 1][gy + 1][gz] * kx * ky * kzi
                                    + corners[gx][gy][gz + 1] * kxi * kyi * kz
                                    + corners[gx + 1][gy][gz + 1] * kx * kyi * kz
                                    + corners[gx][gy + 1][gz + 1] * kxi * ky * kz
                                    + corners[gx + 1][gy + 1][gz + 1] * kx * ky * kz
                            );
                        }
                    }
                }
                return out;
            };
        }
    }

    @Desugar
    public record ClampFunc(IDensityFunctionFactory input, float min, float max) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(input);
        }

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction inputFn = input.instantiate(ctx);
            float lo = min, hi = max;

            return new IDensityFunction() {

                @Override
                public boolean hasTrait(DensityFuncTrait trait) {
                    return inputFn.hasTrait(trait);
                }

                @Override
                public DensityBuffer compute(int cubeX, int cubeY, int cubeZ, DensityMask mask) {
                    DensityBuffer input = inputFn.compute(cubeX, cubeY, cubeZ, mask);

                    CubeBuffer out = ctx.getCubeBuffer();

                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                if (mask.isSet(x, y, z)) {
                                    out.set(x, y, z, MathHelper.clamp_float(input.get(x, y, z), lo, hi));
                                }
                            }
                        }
                    }

                    input.discard();

                    return out;
                }
            };
        }
    }

    public static class ConstantDensityFunction implements IDensityFunction {

        public static final ConstantDensityFunction ZERO = new ConstantDensityFunction(0f);
        public static final ConstantDensityFunction ONE = new ConstantDensityFunction(1f);

        private final ConstantBuffer buf;

        public ConstantDensityFunction(float argument) {
            buf = new ConstantBuffer(argument);
        }

        @Override
        public boolean hasTrait(DensityFuncTrait trait) {
            return trait == DensityFuncTrait.Flat || trait == DensityFuncTrait.Constant;
        }

        @Override
        public DensityBuffer compute(int cubeX, int cubeY, int cubeZ, DensityMask mask) {
            return buf;
        }
    }

    @Desugar
    public record ConstantFunc(float argument) implements IDensityFunctionFactory {

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            return new ConstantDensityFunction(argument);
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
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction densityFn = density.instantiate(ctx);
            IDensityFunction upperBoundFn = upper_bound.instantiate(ctx);
            int lb = lower_bound, ch = cell_height;
            float chInv = 1f / ch;

            if (upperBoundFn.hasTrait(DensityFuncTrait.Flat)) {
                // Flat implementation: no 3d heightmaps

                int[] uppers = new int[256];
                float[] result = new float[256];

                return new IDensityFunction() {

                    @Override
                    public boolean hasTrait(DensityFuncTrait trait) {
                        return trait == DensityFuncTrait.Flat;
                    }

                    @Override
                    public DensityBuffer compute(int cubeX, int cubeY, int cubeZ, DensityMask mask) {
                        DensityMask flatMask = ctx.getMask().flatCopy(mask);
                        DensityBuffer upper = upperBoundFn.compute(cubeX, 0, cubeZ, flatMask);

                        int highestUpper = Integer.MIN_VALUE;

                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (flatMask.isSet(x, 0, z)) {
                                    int top = (int) (upper.get(x, 0, z) * chInv) * ch;
                                    if (top > highestUpper) {
                                        highestUpper = top;
                                    }
                                    uppers[z << 4 | x] = top;
                                    result[z << 4 | x] = lb;
                                }
                            }
                        }

                        upper.discard();

                        DensityMask sampleMask = ctx.getMask();

                        for (int y = highestUpper; y > lb; y -= ch) {
                            int relY = y & 15;

                            sampleMask.clear();
                            boolean any = false;

                            for (int z = 0; z < 16; z++) {
                                for (int x = 0; x < 16; x++) {
                                    if (flatMask.isSet(x, 0, z) && uppers[z << 4 | x] == y) {
                                        sampleMask.set(x, relY, z);
                                        any = true;
                                    }
                                }
                            }

                            if (!any) {
                                continue;
                            }

                            DensityBuffer densityBuf = densityFn.compute(cubeX, y >> 4, cubeZ, sampleMask);

                            for (int z = 0; z < 16; z++) {
                                for (int x = 0; x < 16; x++) {
                                    if (flatMask.isSet(x, 0, z) && uppers[z << 4 | x] == y) {
                                        if (densityBuf.get(x, relY, z) > 0) {
                                            result[z << 4 | x] = y;
                                            flatMask.remove(x, 0, z);
                                        } else {
                                            uppers[z << 4 | x] -= ch;
                                        }
                                    }
                                }
                            }

                            densityBuf.discard();
                        }

                        ctx.releaseMask(sampleMask);
                        ctx.releaseMask(flatMask);

                        CubeBuffer out = ctx.getCubeBuffer();
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 16; y++) {
                                for (int x = 0; x < 16; x++) {
                                    if (mask.isSet(x, y, z)) {
                                        out.set(x, y, z, result[z << 4 | x]);
                                    }
                                }
                            }
                        }
                        return out;
                    }
                };
            }

            throw new UnsupportedOperationException();
        }
    }

    @Desugar
    public record IntervalSelectFunc(
        IDensityFunctionFactory input, float[] thresholds, IDensityFunctionFactory[] functions
    ) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            List<IDensityFunctionFactory> result = new ArrayList<>(1 + functions.length);
            result.add(input);
            Collections.addAll(result, functions);
            return result;
        }

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction chooser = input.instantiate(ctx);

            IDensityFunction[] fns = DBDataUtils.mapToArray(functions, IDensityFunction[]::new, f -> f.instantiate(ctx));

            float[] thresh = thresholds;
            int len = thresh.length;

            DensityMask[] masks = new DensityMask[fns.length];

            for (int i = 0; i < masks.length; i++) {
                masks[i] = new DensityMask();
            }

            return new IDensityFunction() {

                @Override
                public boolean hasTrait(DensityFuncTrait trait) {
                    if (!chooser.hasTrait(trait)) {
                        return false;
                    }
                    for (IDensityFunction fn : fns) {
                        if (!fn.hasTrait(trait)) {
                            return false;
                        }
                    }
                    return true;
                }

                @Override
                public DensityBuffer compute(int cubeX, int cubeY, int cubeZ, DensityMask mask) {
                    DensityBuffer chooserData = chooser.compute(cubeX, cubeY, cubeZ, mask);

                    for (DensityMask densityMask : masks) {
                        densityMask.clear();
                    }

                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                if (mask.isSet(x, y, z)) {
                                    float value = chooserData.get(x, y, z);

                                    boolean found = false;

                                    for (int i = 0; i < len; i++) {
                                        if (value < thresh[i]) {
                                            masks[i].set(x, y, z);
                                            found = true;
                                            break;
                                        }
                                    }

                                    if (!found) {
                                        masks[len].set(x, y, z);
                                    }
                                }
                            }
                        }
                    }

                    chooserData.discard();

                    CubeBuffer out = ctx.getCubeBuffer();

                    for (int i = 0; i < masks.length; i++) {
                        DensityMask fnMask = masks[i];

                        if (fnMask.isEmpty()) {
                            continue;
                        }

                        DensityBuffer fnValues = fns[i].compute(cubeX, cubeY, cubeZ, mask);

                        out.copyFrom(fnValues, fnMask);

                        fnValues.discard();
                    }

                    return out;
                }
            };
        }
    }

    @Desugar
    public record RangeChoiceFunc(
        IDensityFunctionFactory input, float min_inclusive, float max_exclusive, IDensityFunctionFactory when_in_range,
        IDensityFunctionFactory when_out_of_range
    ) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Arrays.asList(input, when_in_range, when_out_of_range);
        }

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            IDensityFunction inputFn = input.instantiate(ctx);
            IDensityFunction inRange = when_in_range.instantiate(ctx);
            IDensityFunction outOfRange = when_out_of_range.instantiate(ctx);
            float lo = min_inclusive, hi = max_exclusive;

            DensityMask inMask = new DensityMask();
            DensityMask outMask = new DensityMask();

            return new IDensityFunction() {

                @Override
                public boolean hasTrait(DensityFuncTrait trait) {
                    return inputFn.hasTrait(trait) && inRange.hasTrait(trait) && outOfRange.hasTrait(trait);
                }

                @Override
                public DensityBuffer compute(int cubeX, int cubeY, int cubeZ, DensityMask mask) {
                    DensityBuffer inputBuf = inputFn.compute(cubeX, cubeY, cubeZ, mask);

                    inMask.clear();
                    outMask.clear();

                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                if (mask.isSet(x, y, z)) {
                                    float v = inputBuf.get(x, y, z);
                                    if (v >= lo && v < hi) {
                                        inMask.set(x, y, z);
                                    } else {
                                        outMask.set(x, y, z);
                                    }
                                }
                            }
                        }
                    }

                    inputBuf.discard();

                    CubeBuffer out = ctx.getCubeBuffer();

                    if (!inMask.isEmpty()) {
                        DensityBuffer inBuf = inRange.compute(cubeX, cubeY, cubeZ, inMask);
                        out.copyFrom(inBuf, inMask);
                        inBuf.discard();
                    }

                    if (!outMask.isEmpty()) {
                        DensityBuffer outBuf = outOfRange.compute(cubeX, cubeY, cubeZ, outMask);
                        out.copyFrom(outBuf, outMask);
                        outBuf.discard();
                    }

                    return out;
                }
            };
        }
    }

    @Desugar
    public record ShiftedNoiseFunc(
        String noise, float xz_scale, float y_scale, IDensityFunctionFactory shift_x, IDensityFunctionFactory shift_y,
        IDensityFunctionFactory shift_z
    ) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Arrays.asList(shift_x, shift_y, shift_z);
        }

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            NoiseSampler sampler = DatapackNoiseList.RT.getHandler().getSampler(ctx.getRandom(), noise);
            IDensityFunction sx = shift_x.instantiate(ctx);
            IDensityFunction sy = shift_y.instantiate(ctx);
            IDensityFunction sz = shift_z.instantiate(ctx);
            float xzs = xz_scale, ys = y_scale;

            double[] xcoord = new double[4096];
            double[] ycoord = new double[4096];
            double[] zcoord = new double[4096];
            double[] noiseout = new double[4096];

            return (cubeX, cubeY, cubeZ, mask) -> {
                DensityBuffer sxBuf = sx.compute(cubeX, cubeY, cubeZ, mask);
                DensityBuffer syBuf = sy.compute(cubeX, cubeY, cubeZ, mask);
                DensityBuffer szBuf = sz.compute(cubeX, cubeY, cubeZ, mask);

                int count = 0;
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            if (mask.isSet(x, y, z)) {
                                double bx = cubeX << 4 | x, by = cubeY << 4 | y, bz = cubeZ << 4 | z;
                                xcoord[count] = (bx + sxBuf.get(x, y, z)) * xzs;
                                ycoord[count] = (by + syBuf.get(x, y, z)) * ys;
                                zcoord[count] = (bz + szBuf.get(x, y, z)) * xzs;
                                count++;
                            }
                        }
                    }
                }

                sxBuf.discard();
                syBuf.discard();
                szBuf.discard();

                sampler.fill3D(xcoord, ycoord, zcoord, noiseout, count);

                count = 0;
                CubeBuffer out = ctx.getCubeBuffer();
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            if (mask.isSet(x, y, z)) {
                                out.set(x, y, z, (float) noiseout[count++]);
                            }
                        }
                    }
                }

                return out;
            };
        }
    }

    @Desugar
    public record ShiftFunc(String argument) implements IDensityFunctionFactory {

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            NoiseSampler sampler = DatapackNoiseList.RT.getHandler().getSampler(ctx.getRandom(), argument);

            double[] xcoord = new double[4096];
            double[] ycoord = new double[4096];
            double[] zcoord = new double[4096];
            double[] noiseout = new double[4096];

            return (cubeX, cubeY, cubeZ, mask) -> {
                int count = 0;
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            if (mask.isSet(x, y, z)) {
                                xcoord[count] = (cubeX << 4 | x) * 0.25;
                                ycoord[count] = (cubeY << 4 | y) * 0.25;
                                zcoord[count] = (cubeZ << 4 | z) * 0.25;
                                count++;
                            }
                        }
                    }
                }

                sampler.fill3D(xcoord, ycoord, zcoord, noiseout, count);

                count = 0;
                CubeBuffer out = ctx.getCubeBuffer();

                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            if (mask.isSet(x, y, z)) {
                                out.set(x, y, z, (float) (noiseout[count++] * 4));
                            }
                        }
                    }
                }

                return out;
            };
        }
    }

    @Desugar
    public record ShiftAFunc(String argument) implements IDensityFunctionFactory {

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            NoiseSampler sampler = DatapackNoiseList.RT.getHandler().getSampler(ctx.getRandom(), argument);

            double[] xcoord = new double[4096];
            double[] zcoord = new double[4096];
            double[] noiseout = new double[4096];

            FlatBuffer buffer = new FlatBuffer();

            return new IDensityFunction() {

                @Override
                public boolean hasTrait(DensityFuncTrait trait) {
                    return trait == DensityFuncTrait.Flat;
                }

                @Override
                public DensityBuffer compute(int cubeX, int cubeY, int cubeZ, DensityMask mask) {

                    DensityMask flat = ctx.getMask().flatCopy(mask);

                    int count = 0;
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (flat.isSet(x, 0, z)) {
                                xcoord[count] = (cubeX << 4 | x) * 0.25;
                                zcoord[count] = (cubeZ << 4 | z) * 0.25;
                                count++;
                            }
                        }
                    }

                    sampler.fill2D(xcoord, zcoord, noiseout, count);

                    Arrays.fill(buffer.data, 0f);

                    count = 0;
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (flat.isSet(x, 0, z)) {
                                float sample = (float) (noiseout[count++] * 4);

                                buffer.data[z << 4 | x] = sample;
                            }
                        }
                    }

                    ctx.releaseMask(flat);

                    return buffer;
                }
            };
        }
    }

    @Desugar
    public record ShiftBFunc(String argument) implements IDensityFunctionFactory {

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            NoiseSampler sampler = DatapackNoiseList.RT.getHandler().getSampler(ctx.getRandom(), argument);

            double[] xcoord = new double[4096]; // bz: sample(bz, bx, 0.0)
            double[] ycoord = new double[4096]; // bx
            double[] zcoord = new double[4096]; // stays zero
            double[] noiseout = new double[4096];

            return new IDensityFunction() {

                @Override
                public boolean hasTrait(DensityFuncTrait trait) {
                    return trait == DensityFuncTrait.Flat;
                }

                @Override
                public DensityBuffer compute(int cubeX, int cubeY, int cubeZ, DensityMask mask) {
                    int count = 0;
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                if (mask.isSet(x, y, z)) {
                                    xcoord[count] = (cubeZ << 4 | z) * 0.25;
                                    ycoord[count] = (cubeX << 4 | x) * 0.25;
                                    count++;
                                }
                            }
                        }
                    }

                    sampler.fill3D(xcoord, ycoord, zcoord, noiseout, count);

                    count = 0;
                    CubeBuffer out = ctx.getCubeBuffer();
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                if (mask.isSet(x, y, z)) {
                                    out.set(x, y, z, (float) (noiseout[count++] * 4));
                                }
                            }
                        }
                    }
                    return out;
                }
            };
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
        public IDensityFunction instantiate(WorldContext ctx) {
            NoiseSampler sampler = DatapackNoiseList.RT.getHandler().getSampler(ctx.getRandom(), noise);
            IDensityFunction inputFn = input.instantiate(ctx);
            RarityType rarityMapper = rarity_value_mapper;

            double[] xcoord = new double[4096];
            double[] ycoord = new double[4096];
            double[] zcoord = new double[4096];
            double[] noiseout = new double[4096];
            float[] rarityFactors = new float[4096];

            return (cubeX, cubeY, cubeZ, mask) -> {
                DensityBuffer inputBuf = inputFn.compute(cubeX, cubeY, cubeZ, mask);

                int count = 0;
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            if (!mask.isSet(x, y, z)) {
                                continue;
                            }

                            float value = inputBuf.get(x, y, z);
                            float rarity = 1f, rarityInv = 1f;

                            switch (rarityMapper) {
                                case type_1 -> {
                                    if (value < -0.75f) {
                                        rarity = 0.5f;
                                        rarityInv = 2f;
                                        break;
                                    }
                                    if (value < -0.5f) {
                                        rarity = 0.75f;
                                        rarityInv = 4f / 3f;
                                        break;
                                    }
                                    if (value < 0.5f) {
                                        break;
                                    }
                                    if (value < 0.75f) {
                                        rarity = 2f;
                                        rarityInv = 0.5f;
                                        break;
                                    }
                                    rarity = 3f;
                                    rarityInv = 1f / 3f;
                                }
                                case type_2 -> {
                                    if (value < -0.5f) {
                                        rarity = 0.75f;
                                        rarityInv = 4f / 3f;
                                        break;
                                    }
                                    if (value < 0f) {
                                        break;
                                    }
                                    if (value < 0.5f) {
                                        rarity = 1.5f;
                                        rarityInv = 2f / 3f;
                                        break;
                                    }
                                    rarity = 2f;
                                    rarityInv = 0.5f;
                                }
                            }

                            double bx = cubeX << 4 | x, by = cubeY << 4 | y, bz = cubeZ << 4 | z;
                            xcoord[count] = bx * rarityInv;
                            ycoord[count] = by * rarityInv;
                            zcoord[count] = bz * rarityInv;
                            rarityFactors[count] = rarity;
                            count++;
                        }
                    }
                }

                sampler.fill3D(xcoord, ycoord, zcoord, noiseout, count);

                count = 0;
                CubeBuffer out = ctx.getCubeBuffer();
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            if (!mask.isSet(x, y, z)) {
                                continue;
                            }
                            out.set(x, y, z, (float) (rarityFactors[count] * noiseout[count]));
                            count++;
                        }
                    }
                }

                inputBuf.discard();

                return out;
            };
        }
    }

    public enum RarityType {
        type_1,
        type_2
    }

    @Desugar
    public record YClampedGradientFunc(int from_y, int to_y, float from_value, float to_value)
        implements IDensityFunctionFactory {

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            float inMin = from_y, scale = (to_value - from_value) / (to_y - from_y), outMin = from_value;
            return (cubeX, cubeY, cubeZ, mask) -> {
                CubeBuffer out = ctx.getCubeBuffer();

                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        float val = (cubeY << 4 | y) - inMin;
                        val = val * scale + outMin;
                        for (int x = 0; x < 16; x++) {
                            if (mask.isSet(x, y, z)) {
                                out.set(x, y, z, val);
                            }
                        }
                    }
                }

                return out;
            };
        }
    }

    @Desugar
    public record NoiseFunc(String noise, float xz_scale, float y_scale) implements IDensityFunctionFactory {

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            NoiseSampler sampler = DatapackNoiseList.RT.getHandler().getSampler(ctx.getRandom(), noise);
            float xzs = xz_scale, ys = y_scale;

            double[] xcoord = new double[4096];
            double[] ycoord = new double[4096];
            double[] zcoord = new double[4096];
            double[] noiseout = new double[4096];

            return (cubeX, cubeY, cubeZ, mask) -> {
                int count = 0;

                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            if (mask.isSet(x, y, z)) {
                                double bx = cubeX << 4 | x;
                                double by = cubeY << 4 | y;
                                double bz = cubeZ << 4 | z;
                                xcoord[count] = bx * xzs;
                                ycoord[count] = by * ys;
                                zcoord[count] = bz * xzs;
                                count++;
                            }
                        }
                    }
                }

                sampler.fill3D(xcoord, ycoord, zcoord, noiseout, count);

                count = 0;

                CubeBuffer out = ctx.getCubeBuffer();

                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            if (mask.isSet(x, y, z)) {
                                out.set(x, y, z, (float) noiseout[count++]);
                            }
                        }
                    }
                }

                return out;
            };
        }
    }

    @Desugar
    public record OldBlendedNoiseFunc(
        float xz_scale, float y_scale, float xz_factor, float y_factor, float smear_scale_multiplier
    ) implements IDensityFunctionFactory {

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            return new OldBlendedNoise(ctx.getRandom(), xz_scale, y_scale, xz_factor, y_factor, smear_scale_multiplier);
        }
    }

    @Desugar
    public record SplineFunc(ISpline spline) implements IDensityFunctionFactory {

        @Override
        public List<IDensityFunctionFactory> children() {
            return Collections.singletonList(spline);
        }

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            return spline.instantiate(ctx);
        }
    }

    public interface ISpline extends IDensityFunctionFactory {

    }

    @Desugar
    public record SplineValue(float coordinate) implements ISpline {

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            return new ConstantDensityFunction(coordinate);
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
        public IDensityFunction instantiate(WorldContext ctx) {
            int N = points.length;

            if (N == 0) {
                return ConstantDensityFunction.ZERO;
            }

            IDensityFunction coord = coordinate.instantiate(ctx);
            IDensityFunction[] values = new IDensityFunction[N];
            float[] locs = new float[N];
            float[] derivs = new float[N];
            for (int i = 0; i < N; i++) {
                values[i] = points[i].value.instantiate(ctx);
                locs[i] = points[i].location;
                derivs[i] = points[i].derivative;
            }

            DensityMask[] segMasks = new DensityMask[N - 1];
            for (int i = 0; i < N - 1; i++) {
                segMasks[i] = new DensityMask();
            }
            DensityMask edgeLow = new DensityMask();
            DensityMask edgeHigh = new DensityMask();

            DensityBuffer[] valueBufs = new DensityBuffer[N];

            return new IDensityFunction() {

                @Override
                public boolean hasTrait(DensityFuncTrait trait) {
                    if (!coord.hasTrait(trait)) {
                        return false;
                    }
                    for (IDensityFunction v : values) {
                        if (!v.hasTrait(trait)) {
                            return false;
                        }
                    }
                    return true;
                }

                @Override
                public DensityBuffer compute(int cubeX, int cubeY, int cubeZ, DensityMask mask) {
                    DensityBuffer coordBuf = coord.compute(cubeX, cubeY, cubeZ, mask);

                    edgeLow.clear();
                    edgeHigh.clear();
                    for (DensityMask m : segMasks) {
                        m.clear();
                    }

                    float lastLoc = locs[N - 1];
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                if (!mask.isSet(x, y, z)) {
                                    continue;
                                }

                                float t = coordBuf.get(x, y, z);

                                if (t <= locs[0]) {
                                    edgeLow.set(x, y, z);
                                } else if (t >= lastLoc) {
                                    edgeHigh.set(x, y, z);
                                } else {
                                    int seg = 0;
                                    while (seg < N - 2 && locs[seg + 1] <= t) {
                                        seg++;
                                    }
                                    segMasks[seg].set(x, y, z);
                                }
                            }
                        }
                    }

                    DensityMask valMask = ctx.getMask();

                    for (int i = 0; i < N; i++) {
                        valMask.clear();
                        if (i == 0) {
                            valMask.or(edgeLow);
                        }
                        if (i > 0) {
                            valMask.or(segMasks[i - 1]);
                        }
                        if (i < N - 1) {
                            valMask.or(segMasks[i]);
                        }
                        if (i == N - 1) {
                            valMask.or(edgeHigh);
                        }
                        valueBufs[i] = values[i].compute(cubeX, cubeY, cubeZ, valMask);
                    }

                    ctx.releaseMask(valMask);

                    CubeBuffer out = ctx.getCubeBuffer();

                    out.copyFrom(valueBufs[0], edgeLow);
                    out.copyFrom(valueBufs[N - 1], edgeHigh);

                    for (int lo = 0; lo < N - 1; lo++) {
                        float dx = locs[lo + 1] - locs[lo];
                        float k0 = dx * derivs[lo], k1 = dx * derivs[lo + 1];
                        for (int bit = segMasks[lo].nextSetBit(0); bit != -1; bit = segMasks[lo].nextSetBit(bit + 1)) {
                            int x = bit & 0xf, y = bit >> 4 & 0xf, z = bit >> 8;
                            float t = coordBuf.get(x, y, z);
                            float f0 = valueBufs[lo].get(x, y, z);
                            float f1 = valueBufs[lo + 1].get(x, y, z);
                            float u = (t - locs[lo]) / dx;
                            float u2 = u * u, u3 = u2 * u;
                            out.set(
                                x, y, z, (2 * u3 - 3 * u2 + 1) * f0
                                    + (u3 - 2 * u2 + u) * k0
                                    + (-2 * u3 + 3 * u2) * f1
                                    + (u3 - u2) * k1
                            );
                        }
                    }

                    coordBuf.discard();
                    for (DensityBuffer vb : valueBufs) {
                        vb.discard();
                    }
                    return out;
                }
            };
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
        public IDensityFunction instantiate(WorldContext ctx) {
            return ConstantDensityFunction.ZERO; // TODO: this
        }
    }

    public static class BlendAlphaFunc implements IDensityFunctionFactory {

        @Override
        public IDensityFunction instantiate(WorldContext ctx) {
            return ConstantDensityFunction.ONE;
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
        public IDensityFunction instantiate(WorldContext ctx) {
            return ConstantDensityFunction.ZERO;
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
}
