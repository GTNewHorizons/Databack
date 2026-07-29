package databack.common.worldgen.dag.codegen;

import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.*;
import databack.common.worldgen.dag.BarrierNode;
import databack.common.worldgen.dag.DFDagNode;
import databack.common.worldgen.dag.DispatchShape;
import databack.common.worldgen.dag.InlineNode;
import databack.common.worldgen.dag.KernelGroup;
import mcgpu.core.hwaccel.buffer.BufferDataType;
import mcgpu.core.hwaccel.buffer.GPUBuffer;
import mcgpu.core.hwaccel.buffer.OffsetBufferAccessor;
import mcgpu.core.hwaccel.shader.KernelBuilder;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

/**
 * Emits GLSL float variable declarations for each {@link InlineNode} in a {@link KernelGroup}.
 * <p>
 * Each node is assigned a name {@code v_N} (where N is its index in {@code group.nodes()}).
 * BarrierNode inputs are accessed via the {@code GET_<MACRO>(idx)} macros added by
 * {@link KernelBuilder#addInputBuffer}.
 */
public final class ExprEmitter {

    private ExprEmitter() {}

    /**
     * Emits one {@code float v_N = <expr>;} line per node into {@code builder.logic}.
     *
     * @param group             the kernel group whose inline nodes to emit
     * @param builder           kernel builder (preamble + logic are mutated)
     * @param barrierMacroNames mapping from BarrierNode → screaming-snake-case macro name
     *                          (e.g. {@code "INTERPOLATED_SAMPLE_0"})
     * @return list of noise IDs in first-encounter order (for {@link GeneratedKernel#noiseSlotIds})
     */
    public static List<String> emitNodes(KernelGroup group, KernelBuilder builder,
                                         IdentityHashMap<BarrierNode, String> barrierMacroNames) {
        return emitNodes(group, builder, barrierMacroNames, null);
    }

    public static List<String> emitNodes(KernelGroup group, KernelBuilder builder,
                                         IdentityHashMap<BarrierNode, String> barrierMacroNames,
                                         Function<String, int[]> noiseDataProvider) {

        // name map: InlineNode → GLSL variable name "v_N"
        IdentityHashMap<InlineNode, String> nameMap = new IdentityHashMap<>();

        // read-slot map: BarrierNode → index in group.reads()
        IdentityHashMap<BarrierNode, Integer> readSlot = new IdentityHashMap<>();
        List<BarrierNode> reads = group.reads();
        for (int i = 0; i < reads.size(); i++) {
            readSlot.put(reads.get(i), i);
        }

        // per-kernel noise map: noiseId → constantOffset accessor
        LinkedHashMap<String, OffsetBufferAccessor> noiseMap = new LinkedHashMap<>();

        // track whether the Perlin GLSL function has been injected yet
        boolean[] perlinIncluded = {false};

        List<InlineNode> nodes = group.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            InlineNode node = nodes.get(i);

            // Resolve inputs to GLSL strings
            List<DFDagNode> inputs = node.inputs();
            String[] args = new String[inputs.size()];
            for (int j = 0; j < inputs.size(); j++) {
                DFDagNode input = inputs.get(j);
                if (input instanceof InlineNode) {
                    args[j] = nameMap.get((InlineNode) input);
                } else if (input instanceof BarrierNode b) {
                    String macroName = barrierMacroNames.get(b);
                    String idx = indexExpr(group.shape(), b.outputShape());
                    args[j] = "GET_" + macroName + "(" + idx + ")";
                } else {
                    args[j] = "0.0f /* unknown input type */";
                }
            }

            String expr = emitExpr(node.source(), args, builder, i, noiseMap, group.shape(), perlinIncluded, noiseDataProvider);
            builder.logic.append("    float v_").append(i).append(" = ").append(expr).append(";\n");
            nameMap.put(node, "v_" + i);
        }

        return new ArrayList<>(noiseMap.keySet());
    }

    // ---- Index expression ------------------------------------------------------------------

    /**
     * Returns the GLSL index expression used to read a barrier buffer.
     * When a PER_VOXEL kernel reads from a PER_COLUMN buffer the column index
     * (relZ * 16 + relX) is used instead of the thread's linear index.
     */
    static String indexExpr(DispatchShape kernelShape, DispatchShape barrierShape) {
        if (kernelShape == DispatchShape.PER_VOXEL && barrierShape == DispatchShape.PER_COLUMN) {
            return "relZ * 16 + relX";
        }
        if (kernelShape == DispatchShape.PER_CORNER && barrierShape == DispatchShape.PER_COLUMN) {
            // Corner (cornerX, cornerZ) maps to block (cornerX*4, cornerZ*4).
            // Clamp to 15 for the boundary corner (index 4 → block 16, which falls in the
            // adjacent chunk and has no entry in this chunk's 16×16 PER_COLUMN buffer).
            return "min(cornerZ * 4, 15) * 16 + min(cornerX * 4, 15)";
        }
        return "threadIdx";
    }

    // ---- Noise registration ----------------------------------------------------------------

    /**
     * Ensures a noise table is registered as a push-constant constantOffset in this kernel,
     * injects the Perlin function into the preamble on first use, and returns the GLSL
     * expression {@code "pc.<pcName>"} for the table base.
     */
    private static String registerNoise(String noiseId, KernelBuilder builder,
                                        LinkedHashMap<String, OffsetBufferAccessor> noiseMap,
                                        boolean[] perlinIncluded,
                                        Function<String, int[]> noiseDataProvider) {
        if (!noiseMap.containsKey(noiseId)) {
            int offset = 0;
            if (noiseDataProvider != null && builder.constants != null) {
                int[] gpuData = noiseDataProvider.apply(noiseId);
                GPUBuffer gpuBuf = builder.constants.addConstant(gpuData);
                offset = gpuBuf.getBufferOffset();
            }
            OffsetBufferAccessor accessor = (OffsetBufferAccessor)
                builder.pushConstants.addConstantOffset(BufferDataType.u32, offset, noiseId);
            noiseMap.put(noiseId, accessor);
            if (!perlinIncluded[0]) {
                builder.preamble.append(PerlinGlsl.PERLIN_FUNCTION);
                perlinIncluded[0] = true;
            }
        }
        return "pc." + noiseMap.get(noiseId).pcName;
    }

    // ---- Expression dispatch ---------------------------------------------------------------

    /**
     * Returns the GLSL Y-world-coordinate expression for the given kernel shape.
     * PER_COLUMN kernels have no Y dimension, so {@code 0.0f} is used as a safe constant.
     */
    private static String wy(DispatchShape kernelShape) {
        return kernelShape == DispatchShape.PER_COLUMN ? "0.0f" : "wy";
    }

    /**
     * Generates the GLSL expression string (right-hand side) for a single inline node.
     * May append auxiliary lines to {@code builder.logic} before returning (e.g. WeirdScaledSampler helpers).
     *
     * @param src           the density function factory for this node
     * @param args          resolved GLSL operand strings (one per child, in children() order)
     * @param builder       kernel builder
     * @param nodeIdx       index of this node (used for unique temporary names)
     * @param noiseMap      per-kernel noise ID → push constant accessor map
     * @param kernelShape   dispatch shape of the enclosing kernel
     * @param perlinIncluded single-element boolean array tracking Perlin preamble injection
     * @return GLSL expression string (without trailing semicolon)
     */
    static String emitExpr(Object src, String[] args, KernelBuilder builder, int nodeIdx,
                            LinkedHashMap<String, OffsetBufferAccessor> noiseMap,
                            DispatchShape kernelShape, boolean[] perlinIncluded,
                            Function<String, int[]> noiseDataProvider) {

        // ---- Constant ----
        if (src instanceof ConstantFunc c) {
            return c.argument + "f";
        }

        // ---- Unary arithmetic ----
        if (src instanceof AbsUnary) {
            return "abs(" + args[0] + ")";
        }
        if (src instanceof CubeUnary) {
            return "(" + args[0] + " * " + args[0] + " * " + args[0] + ")";
        }
        if (src instanceof SquareUnary) {
            return "(" + args[0] + " * " + args[0] + ")";
        }
        if (src instanceof HalfNegativeUnary) {
            return "(" + args[0] + " < 0.0f ? " + args[0] + " * 0.5f : " + args[0] + ")";
        }
        if (src instanceof QuarterNegativeUnary) {
            return "(" + args[0] + " < 0.0f ? " + args[0] + " * 0.25f : " + args[0] + ")";
        }
        if (src instanceof InvertUnary) {
            return "(1.0f / " + args[0] + ")";
        }
        if (src instanceof SqueezeUnary) {
            // Vanilla: clamp x first, then apply the polynomial using the clamped value for both terms.
            String clamped = "_sc_" + nodeIdx;
            builder.logic.append("    float ").append(clamped)
                .append(" = clamp(").append(args[0]).append(", -1.0f, 1.0f);\n");
            return "(" + clamped + " * 0.5f - " + clamped + " * " + clamped + " * " + clamped + " / 24.0f)";
        }
        // Pass-through unaries
        if (src instanceof BlendDensityUnary || src instanceof CacheAllInCellUnary) {
            return args[0];
        }
        // Caching markers inlined in non-PER_VOXEL contexts (they are barriers only in PER_VOXEL).
        if (src instanceof CacheOnceUnary) {
            return args[0];
        }
        // InterpolatedFunc inlined in non-PER_VOXEL contexts — trilinear interp is meaningless at
        // column/corner granularity, so pass through the argument as a best-effort approximation.
        if (src instanceof InterpolatedFunc) {
            return args[0];
        }

        // ---- Binary arithmetic ----
        if (src instanceof AddBinary) {
            return "(" + args[0] + " + " + args[1] + ")";
        }
        if (src instanceof MulBinary) {
            return "(" + args[0] + " * " + args[1] + ")";
        }
        if (src instanceof MaxBinary) {
            return "max(" + args[0] + ", " + args[1] + ")";
        }
        if (src instanceof MinBinary) {
            return "min(" + args[0] + ", " + args[1] + ")";
        }

        // ---- Clamp ----
        if (src instanceof ClampFunc c) {
            return "clamp(" + args[0] + ", " + c.min + "f, " + c.max + "f)";
        }

        // ---- Y Clamped Gradient ----
        if (src instanceof YClampedGradientFunc y) {
            if (kernelShape == DispatchShape.PER_COLUMN) {
                return "0.0f /* YClampedGradient: no Y in PER_COLUMN */";
            }
            int range = y.to_y - y.from_y;
            return "mix(" + y.from_value + "f, " + y.to_value + "f, clamp((float(worldBlockY) - "
                + y.from_y + ".0f) / " + range + ".0f, 0.0f, 1.0f))";
        }

        // ---- Range Choice ----
        if (src instanceof RangeChoiceFunc r) {
            return "(" + args[0] + " >= " + r.min_inclusive + "f && " + args[0] + " < " + r.max_exclusive + "f ? "
                + args[1] + " : " + args[2] + ")";
        }

        // ---- Interval Select ----
        if (src instanceof IntervalSelectFunc is) {
            float[] thresholds = is.thresholds;
            // args[0] = input, args[1..N] = functions[0..N-1], args[N+1] = functions[N] (last bucket)
            // Build nested ternary right-to-left.
            // The last branch (index thresholds.length) has no condition.
            String expr = args[thresholds.length + 1];
            for (int ti = thresholds.length - 1; ti >= 0; ti--) {
                expr = "(" + args[0] + " < " + thresholds[ti] + "f ? " + args[ti + 1] + " : " + expr + ")";
            }
            return expr;
        }

        // ---- Noise sampling ----
        if (src instanceof NoiseFunc n) {
            String pcRef = registerNoise(n.noise, builder, noiseMap, perlinIncluded, noiseDataProvider);
            String wy = wy(kernelShape);
            return "sampleNoise(" + pcRef + ", wx * " + n.xz_scale + "f, " + wy + " * " + n.y_scale + "f, wz * " + n.xz_scale + "f)";
        }

        if (src instanceof ShiftFunc s) {
            String pcRef = registerNoise(s.argument, builder, noiseMap, perlinIncluded, noiseDataProvider);
            String wy = wy(kernelShape);
            return "sampleNoise(" + pcRef + ", wx * 0.25f, " + wy + " * 0.25f, wz * 0.25f) * 4.0f";
        }

        if (src instanceof ShiftAFunc s) {
            String pcRef = registerNoise(s.argument, builder, noiseMap, perlinIncluded, noiseDataProvider);
            return "sampleNoise(" + pcRef + ", wx * 0.25f, 0.0f, wz * 0.25f) * 4.0f";
        }

        if (src instanceof ShiftBFunc s) {
            String pcRef = registerNoise(s.argument, builder, noiseMap, perlinIncluded, noiseDataProvider);
            return "sampleNoise(" + pcRef + ", wz * 0.25f, wx * 0.25f, 0.0f) * 4.0f";
        }

        if (src instanceof ShiftedNoiseFunc sn) {
            String pcRef = registerNoise(sn.noise, builder, noiseMap, perlinIncluded, noiseDataProvider);
            String wy = wy(kernelShape);
            return "sampleNoise(" + pcRef + ", (wx + " + args[0] + ") * " + sn.xz_scale + "f, (" + wy + " + " + args[1] + ") * " + sn.y_scale + "f, (wz + " + args[2] + ") * " + sn.xz_scale + "f)";
        }

        // ---- Weird Scaled Sampler ----
        if (src instanceof WeirdScaledSampler ws) {
            String pcRef = registerNoise(ws.noise, builder, noiseMap, perlinIncluded, noiseDataProvider);
            String rarityVar = "_rarity_" + nodeIdx;
            String rarityInvVar = "_rarityInv_" + nodeIdx;

            // Emit rarity helper variable before the main assignment.
            if (ws.rarity_value_mapper == RarityType.type_1) {
                builder.logic.append("    float ").append(rarityVar).append(" = (")
                    .append(args[0]).append(" < -0.75f ? 0.5f : ")
                    .append(args[0]).append(" < -0.5f ? 0.75f : ")
                    .append(args[0]).append(" < 0.5f ? 1.0f : ")
                    .append(args[0]).append(" < 0.75f ? 2.0f : 3.0f);\n");
            } else {
                // type_2
                builder.logic.append("    float ").append(rarityVar).append(" = (")
                    .append(args[0]).append(" < -0.5f ? 0.75f : ")
                    .append(args[0]).append(" < 0.0f ? 1.0f : ")
                    .append(args[0]).append(" < 0.5f ? 1.5f : 2.0f);\n");
            }
            builder.logic.append("    float ").append(rarityInvVar).append(" = 1.0f / ").append(rarityVar).append(";\n");

            String wy = wy(kernelShape);
            return rarityVar + " * sampleNoise(" + pcRef + ", wx * " + rarityInvVar + ", " + wy + " * " + rarityInvVar + ", wz * " + rarityInvVar + ")";
        }

        // ---- Old Blended Noise ----
        if (src instanceof OldBlendedNoiseFunc obn) {
            // Unique ID: parameters fully determine the seeded noise (combined with dimension seed at upload time).
            String noiseId = "old_blended_noise:" + obn.xz_scale + ":" + obn.y_scale + ":"
                + obn.xz_factor + ":" + obn.y_factor + ":" + obn.smear_scale_multiplier;
            if (!noiseMap.containsKey(noiseId)) {
                OffsetBufferAccessor accessor = (OffsetBufferAccessor)
                    builder.pushConstants.addConstantOffset(BufferDataType.u32, 0, noiseId);
                noiseMap.put(noiseId, accessor);
            }
            String pcRef = "pc." + noiseMap.get(noiseId).pcName;

            // Inject base noise preamble then OBN-specific functions
            if (!perlinIncluded[0]) {
                builder.preamble.append(PerlinGlsl.PERLIN_FUNCTION);
                perlinIncluded[0] = true;
            }
            if (!builder.preamble.toString().contains("sampleOldBlendedNoise")) {
                builder.preamble.append(PerlinGlsl.OBN_FUNCTION);
            }

            // Pre-compute parameters that match OldBlendedNoise constructor / compute()
            float xzMul      = (float) (684.412 * obn.xz_scale);
            float yMul       = (float) (684.412 * obn.y_scale);
            float limitSmear = yMul * obn.smear_scale_multiplier;
            float mainSmear  = limitSmear / obn.y_factor;
            String wy = wy(kernelShape);
            return "sampleOldBlendedNoise(" + pcRef + ", wx, " + wy + ", wz, "
                + xzMul + "f, " + yMul + "f, " + obn.xz_factor + "f, " + obn.y_factor + "f, "
                + limitSmear + "f, " + mainSmear + "f)";
        }

        // ---- Splines ----
        if (src instanceof SplineValue) {
            return ((SplineValue) src).coordinate + "f";
        }

        // SplineCurve is only a barrier in PER_VOXEL context. In PER_COLUMN/PER_CORNER contexts
        // it is inlined directly via SplineEmitter (same path as before spline barriers were added).
        if (src instanceof SplineCurve) {
            return SplineEmitter.emit((SplineCurve) src, args, builder, nodeIdx);
        }

        if (src instanceof SplineFunc) {
            // SplineFunc just passes its single spline child through.
            return args[0];
        }

        // ---- Blend / End islands (no-ops) ----
        if (src instanceof EndIslandsFunc) {
            return "0.0f";
        }
        if (src instanceof BlendAlphaFunc) {
            return "1.0f";
        }
        if (src instanceof BlendOffsetFunc) {
            return "0.0f";
        }

        // ---- Unknown ----
        System.err.println("[DFKernelCodegen] WARNING: Unknown density function type in GLSL codegen: "
            + src.getClass().getSimpleName());
        return "0.0f /* TODO: " + src.getClass().getSimpleName() + " */";
    }
}
