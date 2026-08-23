package databack.common.worldgen.dag.codegen;

import com.github.bsideup.jabel.Desugar;
import databack.common.dto.worldgen.density_function.BinaryDensityFunction;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.*;
import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;
import databack.common.dto.worldgen.density_function.UnaryDensityFunction;
import databack.common.worldgen.Expr;
import databack.common.worldgen.dag.BarrierDFCodeGenerator;
import databack.common.worldgen.dag.CellSize;
import databack.common.worldgen.dag.CodeGenerationBackend;
import databack.common.worldgen.dag.DFDagBuilder2;
import databack.common.worldgen.dag.DFDagBuilder2.BarrierDAGNode;
import databack.common.worldgen.dag.DFDagBuilder2.InlinedDAGNode;
import databack.common.worldgen.dag.InlineDFCodeGenerator;
import mcgpu.core.hwaccel.shader.KernelBuilder;
import mcgpu.core.hwaccel.buffer.BufferDataType;
import mcgpu.core.hwaccel.buffer.BufferLayout;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class VulkanCodeGen {

    public static void init() {
        var gen = CodeGenerationBackend.VULKAN;

        // ---- Unary arithmetic ----
        gen.registerCodeGenerator(AbsUnary.class, new UnaryCodeGenerator(UnaryOp.Abs));
        gen.registerCodeGenerator(BlendDensityUnary.class, new UnaryCodeGenerator(UnaryOp.BlendDensity));
        gen.registerCodeGenerator(SquareUnary.class, new UnaryCodeGenerator(UnaryOp.Square));
        gen.registerCodeGenerator(CubeUnary.class, new UnaryCodeGenerator(UnaryOp.Cube));
        gen.registerCodeGenerator(HalfNegativeUnary.class, new UnaryCodeGenerator(UnaryOp.HalfNegative));
        gen.registerCodeGenerator(InvertUnary.class, new UnaryCodeGenerator(UnaryOp.Invert));
        gen.registerCodeGenerator(QuarterNegativeUnary.class, new UnaryCodeGenerator(UnaryOp.QuarterNegative));
        gen.registerCodeGenerator(SqueezeUnary.class, new UnaryCodeGenerator(UnaryOp.Squeeze));

        // ---- Binary arithmetic ----
        gen.registerCodeGenerator(AddBinary.class, new BinaryCodeGenerator(BinaryOp.Add));
        gen.registerCodeGenerator(MaxBinary.class, new BinaryCodeGenerator(BinaryOp.Max));
        gen.registerCodeGenerator(MinBinary.class, new BinaryCodeGenerator(BinaryOp.Min));
        gen.registerCodeGenerator(MulBinary.class, new BinaryCodeGenerator(BinaryOp.Mul));

        // ---- Simple inline ----
        gen.registerCodeGenerator(ConstantFunc.class, ConstantFuncCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(CacheAllInCellUnary.class, CacheAllInCellCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(ClampFunc.class, ClampFuncCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(YClampedGradientFunc.class, YClampedGradientCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(RangeChoiceFunc.class, RangeChoiceFuncCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(IntervalSelectFunc.class, IntervalSelectFuncCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(BlendAlphaFunc.class, ConstantExprCodeGenerator.ONE);
        gen.registerCodeGenerator(BlendOffsetFunc.class, ConstantExprCodeGenerator.ZERO);
        gen.registerCodeGenerator(EndIslandsFunc.class, ConstantExprCodeGenerator.ZERO);

        // ---- Spline inline ----
        gen.registerCodeGenerator(SplineValue.class, SplineValueCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(SplineFunc.class, SplineFuncCodeGenerator.INSTANCE);
        // SplineCurve and InterpolatedFunc: handled as compound barriers at BLOCKS by partitionTree;
        // register inline fallbacks for non-BLOCKS contexts.
        gen.registerCodeGenerator(SplineCurve.class, SplineCurveInlineCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(InterpolatedFunc.class, InterpolatedFuncInlineCodeGenerator.INSTANCE);

        // ---- Noise inline ----
        gen.registerCodeGenerator(NoiseFunc.class, NoiseFuncCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(ShiftFunc.class, ShiftFuncCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(ShiftAFunc.class, ShiftAFuncCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(ShiftBFunc.class, ShiftBFuncCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(ShiftedNoiseFunc.class, ShiftedNoiseFuncCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(WeirdScaledSampler.class, WeirdScaledSamplerCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(OldBlendedNoiseFunc.class, OldBlendedNoiseFuncCodeGenerator.INSTANCE);

        // ---- Barrier generators ----
        // CacheOnceCodeGenerator implements both interfaces; cast to BarrierDFCodeGenerator to resolve overload.
        gen.registerCodeGenerator(CacheOnceUnary.class, (BarrierDFCodeGenerator) CacheOnceCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(FlatCacheUnary.class, FlatCacheCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(Cache2DFunc.class, Cache2DCodeGenerator.INSTANCE);
        gen.registerCodeGenerator(FindTopSurfaceFunc.class, FindTopSurfaceCodeGenerator.INSTANCE);
    }

    // ========================================================================
    // Shared invoke helper (avoids boilerplate in every invokeFunction impl)
    // ========================================================================

    /** Generates {@code funcName(x, y, z)} call expression. */
    private static Expr<Float> callFunc(String funcName, Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
        return Expr.of(Float.class, funcName + "(" + x + ", " + y + ", " + z + ")");
    }

    // ========================================================================
    // Unary / Binary (existing patterns, kept as-is)
    // ========================================================================

    private enum UnaryOp {
        Abs, BlendDensity, Square, Cube, HalfNegative, Invert, QuarterNegative, Squeeze
    }

    @Desugar
    private record UnaryCodeGenerator(UnaryOp op) implements InlineDFCodeGenerator<String> {

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            String funcName = context.getKernel().createName("unary_" + op);
            UnaryDensityFunction unary = (UnaryDensityFunction) dagNode.node().factory();
            Expr<Float> next = context.compute(dagNode.getInput(unary.argument), Expr.X, Expr.Y, Expr.Z);

            String body = switch (op) {
                case Abs -> "return abs(value);";
                case BlendDensity -> "return value;";
                case Square -> "return value * value;";
                case Cube -> "return value * value * value;";
                case HalfNegative -> "return value < 0.0f ? value * 0.5f : value;";
                case Invert -> "return 1.0f / value;";
                case QuarterNegative -> "return value < 0.0f ? value * 0.25f : value;";
                case Squeeze -> "float c = clamp(value, -1.0f, 1.0f); return c * 0.5f - c * c * c / 24.0f;";
            };

            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) {\n" +
                "    float value = " + next + ";\n" +
                "    " + body + "\n}\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    private enum BinaryOp { Add, Max, Min, Mul }

    @Desugar
    private record BinaryCodeGenerator(BinaryOp op) implements InlineDFCodeGenerator<String> {

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            String funcName = context.getKernel().createName("binary_" + op);
            BinaryDensityFunction binary = (BinaryDensityFunction) dagNode.node().factory();
            Expr<Float> a = context.compute(dagNode.getInput(binary.argument1), Expr.X, Expr.Y, Expr.Z);
            Expr<Float> b = context.compute(dagNode.getInput(binary.argument2), Expr.X, Expr.Y, Expr.Z);

            String expr = switch (op) {
                case Add -> a + " + " + b;
                case Max -> "max(" + a + ", " + b + ")";
                case Min -> "min(" + a + ", " + b + ")";
                case Mul -> a + " * " + b;
            };

            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) {\n" +
                "    return " + expr + ";\n}\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    // ========================================================================
    // Simple inline generators
    // ========================================================================

    /** Returns a literal float expression baked from the factory. State = GLSL literal string. */
    private static final class ConstantFuncCodeGenerator implements InlineDFCodeGenerator<String> {

        static final ConstantFuncCodeGenerator INSTANCE = new ConstantFuncCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            ConstantFunc c = (ConstantFunc) dagNode.node().factory();
            return c.argument() + "f";
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String literal,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return Expr.of(Float.class, literal);
        }
    }

    /** Constant expression generator — always returns the same GLSL literal. State = literal. */
    private static final class ConstantExprCodeGenerator implements InlineDFCodeGenerator<String> {

        static final ConstantExprCodeGenerator ZERO = new ConstantExprCodeGenerator("0.0f");
        static final ConstantExprCodeGenerator ONE  = new ConstantExprCodeGenerator("1.0f");

        private final String literal;

        ConstantExprCodeGenerator(String literal) { this.literal = literal; }

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) { return literal; }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String lit,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return Expr.of(Float.class, lit);
        }
    }

    /** CacheAllInCell: pass-through wrapper. */
    private static final class CacheAllInCellCodeGenerator implements InlineDFCodeGenerator<String> {

        static final CacheAllInCellCodeGenerator INSTANCE = new CacheAllInCellCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            CacheAllInCellUnary f = (CacheAllInCellUnary) dagNode.node().factory();
            String funcName = context.getKernel().createName("cacheAllInCell");
            Expr<Float> child = context.compute(dagNode.getInput(f.argument()), Expr.X, Expr.Y, Expr.Z);
            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) { return " + child + "; }\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    /** clamp(input, min, max) with baked min/max literals. */
    private static final class ClampFuncCodeGenerator implements InlineDFCodeGenerator<String> {

        static final ClampFuncCodeGenerator INSTANCE = new ClampFuncCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            ClampFunc c = (ClampFunc) dagNode.node().factory();
            String funcName = context.getKernel().createName("clamp");
            Expr<Float> input = context.compute(dagNode.getInput(c.input()), Expr.X, Expr.Y, Expr.Z);
            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) {\n" +
                "    return clamp(" + input + ", " + c.min() + "f, " + c.max() + "f);\n}\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    /**
     * mix(from_value, to_value, clamp((worldY - from_y) / (to_y - from_y), 0, 1)).
     * Returns 0.0f in Y-invariant kernels.
     */
    private static final class YClampedGradientCodeGenerator implements InlineDFCodeGenerator<String> {

        static final YClampedGradientCodeGenerator INSTANCE = new YClampedGradientCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            YClampedGradientFunc y = (YClampedGradientFunc) dagNode.node().factory();
            String funcName = context.getKernel().createName("yClampedGradient");
            if (context.getKernelShape().isYInvariant()) {
                context.getKernel().preamble.append(
                    "float " + funcName + "(int x, int y, int z) { return 0.0f; }\n");
                return funcName;
            }
            int range = y.to_y() - y.from_y();
            // GET_CHUNK_Y is a push-constant macro available globally in GLSL.
            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int yCoord, int z) {\n" +
                "    int worldY = GET_CHUNK_Y * 16 + yCoord;\n" +
                "    return mix(" + y.from_value() + "f, " + y.to_value() + "f,\n" +
                "        clamp((float(worldY) - " + y.from_y() + ".0f) / " + range + ".0f, 0.0f, 1.0f));\n}\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    /** Conditional: input in [min, max) → when_in_range, else when_out_of_range. */
    private static final class RangeChoiceFuncCodeGenerator implements InlineDFCodeGenerator<String> {

        static final RangeChoiceFuncCodeGenerator INSTANCE = new RangeChoiceFuncCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            RangeChoiceFunc r = (RangeChoiceFunc) dagNode.node().factory();
            String funcName = context.getKernel().createName("rangeChoice");
            Expr<Float> input   = context.compute(dagNode.getInput(r.input()), Expr.X, Expr.Y, Expr.Z);
            Expr<Float> inRange = context.compute(dagNode.getInput(r.when_in_range()), Expr.X, Expr.Y, Expr.Z);
            Expr<Float> outRange = context.compute(dagNode.getInput(r.when_out_of_range()), Expr.X, Expr.Y, Expr.Z);
            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) {\n" +
                "    float v = " + input + ";\n" +
                "    return (v >= " + r.min_inclusive() + "f && v < " + r.max_exclusive() + "f) ? "
                    + inRange + " : " + outRange + ";\n}\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    /** Nested ternary built right-to-left over threshold array. */
    private static final class IntervalSelectFuncCodeGenerator implements InlineDFCodeGenerator<String> {

        static final IntervalSelectFuncCodeGenerator INSTANCE = new IntervalSelectFuncCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            IntervalSelectFunc is = (IntervalSelectFunc) dagNode.node().factory();
            String funcName = context.getKernel().createName("intervalSelect");
            Expr<Float> inputExpr = context.compute(dagNode.getInput(is.input()), Expr.X, Expr.Y, Expr.Z);
            IDensityFunctionFactory[] funcs = is.functions();
            String[] funcExprs = new String[funcs.length];
            for (int i = 0; i < funcs.length; i++) {
                funcExprs[i] = context.compute(dagNode.getInput(funcs[i]), Expr.X, Expr.Y, Expr.Z).toString();
            }
            // Build nested ternary right-to-left.
            float[] thresholds = is.thresholds();
            String expr = funcExprs[thresholds.length]; // last bucket (no condition)
            for (int ti = thresholds.length - 1; ti >= 0; ti--) {
                expr = "(" + inputExpr + " < " + thresholds[ti] + "f ? " + funcExprs[ti] + " : " + expr + ")";
            }
            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) { return " + expr + "; }\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    // ========================================================================
    // Spline inline generators
    // ========================================================================

    /** SplineValue: returns its float literal as a constant expression. */
    private static final class SplineValueCodeGenerator implements InlineDFCodeGenerator<String> {

        static final SplineValueCodeGenerator INSTANCE = new SplineValueCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            SplineValue sv = (SplineValue) dagNode.node().factory();
            return sv.coordinate() + "f";
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String literal,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return Expr.of(Float.class, literal);
        }
    }

    /** SplineFunc: pass-through to its single ISpline child. */
    private static final class SplineFuncCodeGenerator implements InlineDFCodeGenerator<String> {

        static final SplineFuncCodeGenerator INSTANCE = new SplineFuncCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            SplineFunc sf = (SplineFunc) dagNode.node().factory();
            String funcName = context.getKernel().createName("splineFunc");
            Expr<Float> child = context.compute(dagNode.getInput(sf.spline()), Expr.X, Expr.Y, Expr.Z);
            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) { return " + child + "; }\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    /**
     * SplineCurve inline fallback (non-BLOCKS shapes).
     * Uses SplineEmitter into a temporary KernelBuilder so the spline logic can be embedded
     * as a preamble function body rather than being emitted into main().
     */
    private static final class SplineCurveInlineCodeGenerator implements InlineDFCodeGenerator<String> {

        static final SplineCurveInlineCodeGenerator INSTANCE = new SplineCurveInlineCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            SplineCurve sc = (SplineCurve) dagNode.node().factory();
            String funcName = context.getKernel().createName("splineCurve");

            Expr<Float> coordExpr = context.compute(dagNode.getInput(sc.coordinate()), Expr.X, Expr.Y, Expr.Z);
            SplinePoint[] points = sc.points();
            String[] args = new String[1 + points.length];
            args[0] = coordExpr.toString();
            for (int i = 0; i < points.length; i++) {
                args[i + 1] = context.compute(dagNode.getInput(points[i].value()), Expr.X, Expr.Y, Expr.Z).toString();
            }

            // Emit spline logic into a temporary builder, then embed it in a preamble function.
            KernelBuilder tempKb = new KernelBuilder(context.getKernel().constants);
            String resultVar = SplineEmitter.emit(sc, args, tempKb, 0);

            context.getKernel().preamble
                .append("float ").append(funcName).append("(int x, int y, int z) {\n")
                .append(tempKb.logic)         // local variable declarations + block braces
                .append("    return ").append(resultVar).append(";\n}\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    /**
     * InterpolatedFunc inline fallback (non-BLOCKS shapes).
     * Trilinear interpolation is meaningless at column/corner granularity; pass through the argument.
     */
    private static final class InterpolatedFuncInlineCodeGenerator implements InlineDFCodeGenerator<String> {

        static final InterpolatedFuncInlineCodeGenerator INSTANCE = new InterpolatedFuncInlineCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            InterpolatedFunc f = (InterpolatedFunc) dagNode.node().factory();
            String funcName = context.getKernel().createName("interpolatedInline");
            Expr<Float> child = context.compute(dagNode.getInput(f.argument()), Expr.X, Expr.Y, Expr.Z);
            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) { return " + child + "; }\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    // ========================================================================
    // Noise inline generators
    // ========================================================================

    /**
     * Emits a preamble function that computes world coords from (x,y,z) params and calls sampleNoise.
     * All noise generators follow this same pattern.
     */
    private static String wyParam(boolean yInvariant) {
        return yInvariant ? "0.0f" : "float(GET_CHUNK_Y * 16 + y)";
    }

    private static String noiseCoordPreamble(boolean yInvariant) {
        StringBuilder sb = new StringBuilder();
        sb.append("    float wx = float(GET_CHUNK_X * 16 + x);\n");
        if (!yInvariant) sb.append("    float wy = float(GET_CHUNK_Y * 16 + y);\n");
        sb.append("    float wz = float(GET_CHUNK_Z * 16 + z);\n");
        return sb.toString();
    }

    private static final class NoiseFuncCodeGenerator implements InlineDFCodeGenerator<String> {

        static final NoiseFuncCodeGenerator INSTANCE = new NoiseFuncCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            NoiseFunc n = (NoiseFunc) dagNode.node().factory();
            String pcRef = context.registerNoise(n.noise());
            String funcName = context.getKernel().createName("noise");
            boolean yi = context.getKernelShape().isYInvariant();
            String wy = yi ? "0.0f" : "wy";
            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) {\n" +
                noiseCoordPreamble(yi) +
                "    return sampleNoise(" + pcRef + ", wx * " + n.xz_scale() + "f, "
                    + wy + " * " + n.y_scale() + "f, wz * " + n.xz_scale() + "f);\n}\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    /** ShiftFunc: sampleNoise(pcRef, wx*0.25, wy*0.25, wz*0.25) * 4.0 */
    private static final class ShiftFuncCodeGenerator implements InlineDFCodeGenerator<String> {

        static final ShiftFuncCodeGenerator INSTANCE = new ShiftFuncCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            ShiftFunc s = (ShiftFunc) dagNode.node().factory();
            String pcRef = context.registerNoise(s.argument());
            String funcName = context.getKernel().createName("shiftNoise");
            boolean yi = context.getKernelShape().isYInvariant();
            String wy = yi ? "0.0f" : "wy";
            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) {\n" +
                noiseCoordPreamble(yi) +
                "    return sampleNoise(" + pcRef + ", wx * 0.25f, " + wy + " * 0.25f, wz * 0.25f) * 4.0f;\n}\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    /** ShiftAFunc: sampleNoise(pcRef, wx*0.25, 0, wz*0.25) * 4.0 */
    private static final class ShiftAFuncCodeGenerator implements InlineDFCodeGenerator<String> {

        static final ShiftAFuncCodeGenerator INSTANCE = new ShiftAFuncCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            ShiftAFunc s = (ShiftAFunc) dagNode.node().factory();
            String pcRef = context.registerNoise(s.argument());
            String funcName = context.getKernel().createName("shiftANoise");
            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) {\n" +
                "    float wx = float(GET_CHUNK_X * 16 + x);\n" +
                "    float wz = float(GET_CHUNK_Z * 16 + z);\n" +
                "    return sampleNoise(" + pcRef + ", wx * 0.25f, 0.0f, wz * 0.25f) * 4.0f;\n}\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    /** ShiftBFunc: sampleNoise(pcRef, wz*0.25, wx*0.25, 0) * 4.0 */
    private static final class ShiftBFuncCodeGenerator implements InlineDFCodeGenerator<String> {

        static final ShiftBFuncCodeGenerator INSTANCE = new ShiftBFuncCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            ShiftBFunc s = (ShiftBFunc) dagNode.node().factory();
            String pcRef = context.registerNoise(s.argument());
            String funcName = context.getKernel().createName("shiftBNoise");
            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) {\n" +
                "    float wx = float(GET_CHUNK_X * 16 + x);\n" +
                "    float wz = float(GET_CHUNK_Z * 16 + z);\n" +
                "    return sampleNoise(" + pcRef + ", wz * 0.25f, wx * 0.25f, 0.0f) * 4.0f;\n}\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    /** ShiftedNoiseFunc: sampleNoise(pcRef, (wx+shiftX)*xzScale, (wy+shiftY)*yScale, (wz+shiftZ)*xzScale) */
    private static final class ShiftedNoiseFuncCodeGenerator implements InlineDFCodeGenerator<String> {

        static final ShiftedNoiseFuncCodeGenerator INSTANCE = new ShiftedNoiseFuncCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            ShiftedNoiseFunc sn = (ShiftedNoiseFunc) dagNode.node().factory();
            String pcRef = context.registerNoise(sn.noise());
            String funcName = context.getKernel().createName("shiftedNoise");
            boolean yi = context.getKernelShape().isYInvariant();
            // Shift children are evaluated at (x,y,z) — function parameters.
            Expr<Float> shiftX = context.compute(dagNode.getInput(sn.shift_x()), Expr.X, Expr.Y, Expr.Z);
            Expr<Float> shiftY = context.compute(dagNode.getInput(sn.shift_y()), Expr.X, Expr.Y, Expr.Z);
            Expr<Float> shiftZ = context.compute(dagNode.getInput(sn.shift_z()), Expr.X, Expr.Y, Expr.Z);
            String wy = yi ? "0.0f" : "wy";
            String wyShifted = yi ? "0.0f + (" + shiftY + ")" : "(wy + (" + shiftY + "))";
            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) {\n" +
                noiseCoordPreamble(yi) +
                "    return sampleNoise(" + pcRef + ",\n" +
                "        (wx + (" + shiftX + ")) * " + sn.xz_scale() + "f,\n" +
                "        " + wyShifted + " * " + sn.y_scale() + "f,\n" +
                "        (wz + (" + shiftZ + ")) * " + sn.xz_scale() + "f);\n}\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    /** WeirdScaledSampler: rarity lookup table → scaled noise. */
    private static final class WeirdScaledSamplerCodeGenerator implements InlineDFCodeGenerator<String> {

        static final WeirdScaledSamplerCodeGenerator INSTANCE = new WeirdScaledSamplerCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            WeirdScaledSampler ws = (WeirdScaledSampler) dagNode.node().factory();
            String pcRef = context.registerNoise(ws.noise());
            String funcName = context.getKernel().createName("weirdScaled");
            boolean yi = context.getKernelShape().isYInvariant();
            Expr<Float> input = context.compute(dagNode.getInput(ws.input()), Expr.X, Expr.Y, Expr.Z);
            String wy = yi ? "0.0f" : "wy";

            String rarityExpr;
            if (ws.rarity_value_mapper() == RarityType.type_1) {
                rarityExpr =
                    "(inp < -0.75f ? 0.5f : inp < -0.5f ? 0.75f : inp < 0.5f ? 1.0f : inp < 0.75f ? 2.0f : 3.0f)";
            } else {
                rarityExpr = "(inp < -0.5f ? 0.75f : inp < 0.0f ? 1.0f : inp < 0.5f ? 1.5f : 2.0f)";
            }

            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) {\n" +
                noiseCoordPreamble(yi) +
                "    float inp = " + input + ";\n" +
                "    float rarity = " + rarityExpr + ";\n" +
                "    float rarityInv = 1.0f / rarity;\n" +
                "    return rarity * sampleNoise(" + pcRef + ", wx * rarityInv, " + wy + " * rarityInv, wz * rarityInv);\n}\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    /** OldBlendedNoise: 8-octave main + 16-octave min/max blend. */
    private static final class OldBlendedNoiseFuncCodeGenerator implements InlineDFCodeGenerator<String> {

        static final OldBlendedNoiseFuncCodeGenerator INSTANCE = new OldBlendedNoiseFuncCodeGenerator();

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            OldBlendedNoiseFunc obn = (OldBlendedNoiseFunc) dagNode.node().factory();
            // Use parameters as the noise ID (same as ExprEmitter).
            String noiseId = "old_blended_noise:" + obn.xz_scale() + ":" + obn.y_scale() + ":"
                + obn.xz_factor() + ":" + obn.y_factor() + ":" + obn.smear_scale_multiplier();
            String pcRef = context.registerNoise(noiseId);
            // Inject OBN function if not already present (registerNoise injects PERLIN first).
            if (!context.getKernel().preamble.toString().contains("sampleOldBlendedNoise")) {
                context.getKernel().preamble.append(PerlinGlsl.OBN_FUNCTION);
            }

            float xzMul     = (float) (684.412 * obn.xz_scale());
            float yMul      = (float) (684.412 * obn.y_scale());
            float limitSmear = yMul * obn.smear_scale_multiplier();
            float mainSmear  = limitSmear / obn.y_factor();

            String funcName = context.getKernel().createName("oldBlendedNoise");
            boolean yi = context.getKernelShape().isYInvariant();
            String wy = yi ? "0.0f" : "wy";
            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) {\n" +
                noiseCoordPreamble(yi) +
                "    return sampleOldBlendedNoise(" + pcRef + ", wx, " + wy + ", wz,\n" +
                "        " + xzMul + "f, " + yMul + "f, " + obn.xz_factor() + "f, " + obn.y_factor() + "f,\n" +
                "        " + limitSmear + "f, " + mainSmear + "f);\n}\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    // ========================================================================
    // Barrier generators
    // ========================================================================

    /**
     * CacheOnceUnary barrier: materializes a 16×16×16 BLOCKS buffer.
     * Returns null for non-BLOCKS shapes (becomes inline pass-through).
     * Implements both BarrierDFCodeGenerator (for BLOCKS) and InlineDFCodeGenerator
     * (for COLUMNS/BLOCKS_REDUCED contexts where it falls through to inline).
     */
    private static final class CacheOnceCodeGenerator
        implements BarrierDFCodeGenerator<String>, InlineDFCodeGenerator<String> {

        static final CacheOnceCodeGenerator INSTANCE = new CacheOnceCodeGenerator();

        // ---- BarrierDFCodeGenerator (BLOCKS shape) ----

        @Override
        public CellSize outputShape(CellSize inputShapeHint) {
            return inputShapeHint == CellSize.BLOCKS ? CellSize.BLOCKS : null;
        }

        @Override
        public BufferLayout getBufferLayout() {
            return new BufferLayout(BufferDataType.f32, 16, 16, 16);
        }

        @Override
        public void emitMain(CodeGenContext context, BarrierDAGNode barrier) {
            CacheOnceUnary f = (CacheOnceUnary) barrier.node.factory();
            Expr<Float> child = context.compute(barrier.getInput(f.argument()),
                Expr.of(Integer.class, "relX"), Expr.of(Integer.class, "relY"),
                Expr.of(Integer.class, "relZ"));
            DFDagBuilder2.appendCoordPreamble(context.getKernel().logic, CellSize.BLOCKS);
            context.getKernel().logic
                .append("    float result = ").append(child).append(";\n")
                .append("    SET_OUTPUT(threadIdx, result);\n");
        }

        @Override
        public String configureConsumer(CodeGenContext consumingContext, BarrierDAGNode barrierNode) {
            String name = barrierNode.id;
            consumingContext.getKernel().addInputBuffer(name, getBufferLayout());
            return name;
        }

        @Override
        public Expr<Float> emitGetter(CodeGenContext consumingContext, String bindingName,
                                      Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            CellSize cs = consumingContext.getKernelShape();
            String idx = DFDagBuilder2.indexExpr(cs, CellSize.BLOCKS, x.toString(), y.toString(), z.toString());
            return Expr.of(Float.class,
                "GET_" + toMacro(bindingName) + "(" + idx + ")");
        }

        // ---- InlineDFCodeGenerator (non-BLOCKS inline fallback) ----

        @Override
        public String emitFunction(CodeGenContext context, InlinedDAGNode dagNode) {
            CacheOnceUnary f = (CacheOnceUnary) dagNode.node().factory();
            String funcName = context.getKernel().createName("cacheOnceInline");
            Expr<Float> child = context.compute(dagNode.getInput(f.argument()), Expr.X, Expr.Y, Expr.Z);
            context.getKernel().preamble.append(
                "float " + funcName + "(int x, int y, int z) { return " + child + "; }\n");
            return funcName;
        }

        @Override
        public Expr<Float> invokeFunction(CodeGenContext context, String funcName,
                                          Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            return callFunc(funcName, x, y, z);
        }
    }

    /**
     * FlatCacheUnary barrier: evaluates child at y=0, produces a 16×1×16 COLUMNS buffer.
     */
    private static final class FlatCacheCodeGenerator implements BarrierDFCodeGenerator<String> {

        static final FlatCacheCodeGenerator INSTANCE = new FlatCacheCodeGenerator();

        @Override
        public CellSize outputShape(CellSize inputShapeHint) { return CellSize.COLUMNS; }

        @Override
        public CellSize childShapeHint(IDensityFunctionFactory parentFactory, CellSize outputHint,
                                       IDensityFunctionFactory child) {
            return CellSize.COLUMNS; // child is always evaluated at COLUMNS (y=0)
        }

        @Override
        public BufferLayout getBufferLayout() {
            return new BufferLayout(BufferDataType.f32, 16, 1, 16);
        }

        @Override
        public void emitMain(CodeGenContext context, BarrierDAGNode barrier) {
            FlatCacheUnary f = (FlatCacheUnary) barrier.node.factory();
            // Evaluate child at y=0 (COLUMNS kernel has no Y dispatch).
            Expr<Float> child = context.compute(barrier.getInput(f.argument()),
                Expr.of(Integer.class, "relX"), Expr.of(Integer.class, "0"),
                Expr.of(Integer.class, "relZ"));
            DFDagBuilder2.appendCoordPreamble(context.getKernel().logic, CellSize.COLUMNS);
            context.getKernel().logic
                .append("    float result = ").append(child).append(";\n")
                .append("    SET_OUTPUT(threadIdx, result);\n");
        }

        @Override
        public String configureConsumer(CodeGenContext consumingContext, BarrierDAGNode barrierNode) {
            String name = barrierNode.id;
            consumingContext.getKernel().addInputBuffer(name, getBufferLayout());
            return name;
        }

        @Override
        public Expr<Float> emitGetter(CodeGenContext consumingContext, String bindingName,
                                      Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            CellSize cs = consumingContext.getKernelShape();
            String idx = DFDagBuilder2.indexExpr(cs, CellSize.COLUMNS, x.toString(), y.toString(), z.toString());
            return Expr.of(Float.class, "GET_" + toMacro(bindingName) + "(" + idx + ")");
        }
    }

    /**
     * Cache2DFunc barrier: same GPU behavior as FlatCache (evaluate at y=0, COLUMNS buffer).
     */
    private static final class Cache2DCodeGenerator implements BarrierDFCodeGenerator<String> {

        static final Cache2DCodeGenerator INSTANCE = new Cache2DCodeGenerator();

        @Override
        public CellSize outputShape(CellSize inputShapeHint) { return CellSize.COLUMNS; }

        @Override
        public CellSize childShapeHint(IDensityFunctionFactory parentFactory, CellSize outputHint,
                                       IDensityFunctionFactory child) {
            return CellSize.COLUMNS; // child is always evaluated at y=0 (COLUMNS)
        }

        @Override
        public BufferLayout getBufferLayout() {
            return new BufferLayout(BufferDataType.f32, 16, 1, 16);
        }

        @Override
        public void emitMain(CodeGenContext context, BarrierDAGNode barrier) {
            Cache2DFunc f = (Cache2DFunc) barrier.node.factory();
            Expr<Float> child = context.compute(barrier.getInput(f.argument()),
                Expr.of(Integer.class, "relX"), Expr.of(Integer.class, "0"),
                Expr.of(Integer.class, "relZ"));
            DFDagBuilder2.appendCoordPreamble(context.getKernel().logic, CellSize.COLUMNS);
            context.getKernel().logic
                .append("    float result = ").append(child).append(";\n")
                .append("    SET_OUTPUT(threadIdx, result);\n");
        }

        @Override
        public String configureConsumer(CodeGenContext consumingContext, BarrierDAGNode barrierNode) {
            String name = barrierNode.id;
            consumingContext.getKernel().addInputBuffer(name, getBufferLayout());
            return name;
        }

        @Override
        public Expr<Float> emitGetter(CodeGenContext consumingContext, String bindingName,
                                      Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            CellSize cs = consumingContext.getKernelShape();
            String idx = DFDagBuilder2.indexExpr(cs, CellSize.COLUMNS, x.toString(), y.toString(), z.toString());
            return Expr.of(Float.class, "GET_" + toMacro(bindingName) + "(" + idx + ")");
        }
    }

    /**
     * FindTopSurface barrier: Y-scan loop, produces a 16×1×16 COLUMNS buffer of float Y values.
     * Child shape hints: density → BLOCKS, upper_bound → COLUMNS.
     */
    private static final class FindTopSurfaceCodeGenerator implements BarrierDFCodeGenerator<String> {

        static final FindTopSurfaceCodeGenerator INSTANCE = new FindTopSurfaceCodeGenerator();

        @Override
        public CellSize outputShape(CellSize inputShapeHint) { return CellSize.COLUMNS; }

        @Override
        public BufferLayout getBufferLayout() {
            return new BufferLayout(BufferDataType.f32, 16, 1, 16);
        }

        @Override
        public CellSize childShapeHint(IDensityFunctionFactory parentFactory, CellSize outputHint,
                                       IDensityFunctionFactory child) {
            FindTopSurfaceFunc fts = (FindTopSurfaceFunc) parentFactory;
            // density child needs full BLOCKS resolution for the Y-scan.
            return (child == fts.density()) ? CellSize.BLOCKS : CellSize.COLUMNS;
        }

        @Override
        public void emitMain(CodeGenContext context, BarrierDAGNode barrier) {
            FindTopSurfaceFunc fts = (FindTopSurfaceFunc) barrier.node.factory();

            // upper_bound: COLUMNS barrier → read with (relX, 0, relZ)
            Expr<Float> upperExpr = context.compute(barrier.getInput(fts.upper_bound()),
                Expr.of(Integer.class, "relX"), Expr.of(Integer.class, "0"),
                Expr.of(Integer.class, "relZ"));

            // density: BLOCKS barrier → read with (relX, relY, relZ) inside the Y-loop.
            // configureConsumer is called here to set up the buffer binding.
            Expr<Float> densityExpr = context.compute(barrier.getInput(fts.density()),
                Expr.of(Integer.class, "relX"), Expr.of(Integer.class, "relY"),
                Expr.of(Integer.class, "relZ"));

            DFDagBuilder2.appendCoordPreamble(context.getKernel().logic, CellSize.COLUMNS);
            StringBuilder logic = context.getKernel().logic;

            logic.append("    float upperBoundF = ").append(upperExpr).append(";\n");
            logic.append("    int upperY = (int(upperBoundF) / ")
                 .append(fts.cell_height()).append(") * ").append(fts.cell_height()).append(";\n");
            logic.append("    float result = ").append((float) fts.lower_bound()).append("f;\n");
            logic.append("    for (int y = upperY; y > ").append(fts.lower_bound())
                 .append("; y -= ").append(fts.cell_height()).append(") {\n");
            logic.append("        int relY = y - GET_CHUNK_Y * 16;\n");
            logic.append("        if (relY < 0 || relY >= 16) continue;\n");
            logic.append("        float density = ").append(densityExpr).append(";\n");
            logic.append("        if (density > 0.0f) { result = float(y); break; }\n");
            logic.append("    }\n");
            logic.append("    SET_OUTPUT(threadIdx, result);\n");
        }

        @Override
        public String configureConsumer(CodeGenContext consumingContext, BarrierDAGNode barrierNode) {
            String name = barrierNode.id;
            consumingContext.getKernel().addInputBuffer(name, getBufferLayout());
            return name;
        }

        @Override
        public Expr<Float> emitGetter(CodeGenContext consumingContext, String bindingName,
                                      Expr<Integer> x, Expr<Integer> y, Expr<Integer> z) {
            CellSize cs = consumingContext.getKernelShape();
            String idx = DFDagBuilder2.indexExpr(cs, CellSize.COLUMNS, x.toString(), y.toString(), z.toString());
            return Expr.of(Float.class, "GET_" + toMacro(bindingName) + "(" + idx + ")");
        }
    }

    // ========================================================================
    // Shared helpers
    // ========================================================================

    private static String toMacro(String name) {
        return KernelBuilder.toScreamingSnakeCase(name);
    }
}
