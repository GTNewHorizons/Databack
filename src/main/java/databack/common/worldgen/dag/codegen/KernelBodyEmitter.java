package databack.common.worldgen.dag.codegen;

import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.FindTopSurfaceFunc;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.SplineCurve;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.SplineValue;
import databack.common.worldgen.dag.BarrierKind;
import databack.common.worldgen.dag.BarrierNode;
import databack.common.worldgen.dag.DFDagNode;
import databack.common.worldgen.dag.DispatchShape;
import databack.common.worldgen.dag.InlineNode;
import databack.common.worldgen.dag.KernelGroup;
import mcgpu.core.hwaccel.buffer.BufferDataType;
import mcgpu.core.hwaccel.buffer.BufferLayout;
import mcgpu.core.hwaccel.shader.KernelBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Function;

/**
 * Compiles a single {@link KernelGroup} into a {@link GeneratedKernel} containing
 * complete GLSL source and associated metadata.
 */
public final class KernelBodyEmitter {

    private KernelBodyEmitter() {}

    /**
     * Emits GLSL source for the given kernel group.
     *
     * @param group the kernel group to compile
     * @return a {@link GeneratedKernel} with full GLSL source and metadata
     */
    public static GeneratedKernel emit(KernelGroup group) {
        return emit(group, new KernelBuilder(null));
    }

    /**
     * Emits GLSL source for the given kernel group using a caller-supplied {@link KernelBuilder}.
     * <p>
     * Pass a builder backed by a real {@link mcgpu.core.hwaccel.buffer.ConstantBuffer} during
     * executor compilation so that noise tables and other constants can be uploaded and their
     * push-constant offsets recorded in {@link KernelBuilder#pushConstants}.
     *
     * @param group   the kernel group to compile
     * @param builder the builder to populate with buffer macros, push constants, and GLSL logic
     * @return a {@link GeneratedKernel} with full GLSL source and metadata
     */
    public static GeneratedKernel emit(KernelGroup group, KernelBuilder builder) {
        return emit(group, builder, null);
    }

    public static GeneratedKernel emit(KernelGroup group, KernelBuilder builder,
                                       Function<String, int[]> noiseDataProvider) {

        // ---- 1. Local sizes ----
        int[] localSize = localSizes(group.shape());

        // ---- 3. Register read buffers ----
        IdentityHashMap<BarrierNode, String> barrierMacroNames = new IdentityHashMap<>();
        for (BarrierNode read : group.reads()) {
            builder.addInputBuffer(read.id(),
                new BufferLayout(BufferDataType.f32, read.outputShape().elementCount));
            barrierMacroNames.put(read, KernelBuilder.toScreamingSnakeCase(read.id()));
        }

        // ---- 4. Register output buffer ----
        // COLUMN_REDUCE writes integer Y values; everything else writes float densities.
        if (!group.isTerminal()) {
            BarrierKind kind = group.output().kind();
            BufferDataType outputType = (kind == BarrierKind.COLUMN_REDUCE)
                ? BufferDataType.u32
                : BufferDataType.f32;
            builder.addOutputBuffer("output",
                new BufferLayout(outputType, group.output().outputShape().elementCount));
        } else {
            // Terminal kernel still writes its result for readback.
            builder.addOutputBuffer("output",
                new BufferLayout(BufferDataType.f32, group.shape().elementCount));
        }

        // ---- 5. Chunk coordinate parameters ----
        builder.addParameter(BufferDataType.i32, "chunkX");
        builder.addParameter(BufferDataType.i32, "chunkY");
        builder.addParameter(BufferDataType.i32, "chunkZ");

        // ---- 6. Coordinate preamble ----
        appendCoordinatePreamble(builder, group.shape());

        // ---- 7. Body dispatch ----
        List<String> noiseSlotIds;

        if (!group.isTerminal() && group.output().kind() == BarrierKind.INTERPOLATED_INTERP) {
            // INTERPOLATED_INTERP: trilinear interpolation from 5x5x5 corner buffer.
            noiseSlotIds = emitInterpolatedInterp(group, builder, barrierMacroNames);
        } else if (!group.isTerminal() && group.output().kind() == BarrierKind.COLUMN_REDUCE) {
            // COLUMN_REDUCE: Y-scan to find first solid voxel.
            noiseSlotIds = emitColumnReduce(group, builder, barrierMacroNames);
        } else if (!group.isTerminal() && group.output().kind() == BarrierKind.SPLINE_EVAL) {
            // SPLINE_EVAL: read coordinate from buffer, evaluate cubic-Hermite spline.
            noiseSlotIds = emitSplineEval(group, builder, barrierMacroNames, noiseDataProvider);
        } else {
            // Standard body: emit inline nodes and optionally write result to output.
            noiseSlotIds = ExprEmitter.emitNodes(group, builder, barrierMacroNames, noiseDataProvider);
            if (!group.nodes().isEmpty()) {
                String lastVar = "v_" + (group.nodes().size() - 1);
                builder.logic.append("    SET_OUTPUT(threadIdx, ").append(lastVar).append(");\n");
            } else if (!group.reads().isEmpty()) {
                // Degenerate pass-through: no inline nodes — copy single barrier directly to output.
                BarrierNode read = group.reads().get(0);
                String macro = barrierMacroNames.get(read);
                String idx = ExprEmitter.indexExpr(group.shape(), read.outputShape());
                builder.logic.append("    SET_OUTPUT(threadIdx, GET_").append(macro)
                    .append("(").append(idx).append("));\n");
            }
        }

        // ---- 8. Assemble GLSL ----
        String glsl = KernelBuilder.getStandardHeader(localSize[0], localSize[1], localSize[2])
            + builder.preamble.toString()
            + builder.pushConstants.getPushConstantDefinition()
            + "void main() {\n"
            + builder.logic.toString()
            + "}\n";

        // ---- 9. Collect barrier IDs and return ----
        List<String> inputIds = new ArrayList<>();
        for (BarrierNode r : group.reads()) {
            inputIds.add(r.id());
        }
        String outputId = group.isTerminal() ? null : group.output().id();

        return new GeneratedKernel(group.shape(), glsl, inputIds, outputId, noiseSlotIds);
    }

    // ---- Coordinate preamble ---------------------------------------------------------------

    private static void appendCoordinatePreamble(KernelBuilder builder, DispatchShape shape) {
        StringBuilder logic = builder.logic;
        if (shape == DispatchShape.PER_VOXEL) {
            logic.append("    int relX = int(gl_GlobalInvocationID.x);\n");
            logic.append("    int relY = int(gl_GlobalInvocationID.y);\n");
            logic.append("    int relZ = int(gl_GlobalInvocationID.z);\n");
            logic.append("    int worldBlockX = GET_CHUNK_X * 16 + relX;\n");
            logic.append("    int worldBlockY = GET_CHUNK_Y * 16 + relY;\n");
            logic.append("    int worldBlockZ = GET_CHUNK_Z * 16 + relZ;\n");
            logic.append("    float wx = float(worldBlockX); float wy = float(worldBlockY); float wz = float(worldBlockZ);\n");
            logic.append("    int threadIdx = relZ * 256 + relY * 16 + relX;\n");
        } else if (shape == DispatchShape.PER_COLUMN) {
            logic.append("    int relX = int(gl_GlobalInvocationID.x);\n");
            logic.append("    int relZ = int(gl_GlobalInvocationID.z);\n");
            logic.append("    int worldBlockX = GET_CHUNK_X * 16 + relX;\n");
            logic.append("    int worldBlockZ = GET_CHUNK_Z * 16 + relZ;\n");
            logic.append("    float wx = float(worldBlockX); float wz = float(worldBlockZ);\n");
            logic.append("    int threadIdx = relZ * 16 + relX;\n");
        } else {
            // PER_CORNER
            logic.append("    int cornerX = int(gl_GlobalInvocationID.x);\n");
            logic.append("    int cornerY = int(gl_GlobalInvocationID.y);\n");
            logic.append("    int cornerZ = int(gl_GlobalInvocationID.z);\n");
            logic.append("    int worldBlockX = GET_CHUNK_X * 16 + cornerX * 4;\n");
            logic.append("    int worldBlockY = GET_CHUNK_Y * 16 + cornerY * 4;\n");
            logic.append("    int worldBlockZ = GET_CHUNK_Z * 16 + cornerZ * 4;\n");
            logic.append("    float wx = float(worldBlockX); float wy = float(worldBlockY); float wz = float(worldBlockZ);\n");
            logic.append("    int threadIdx = cornerZ * 25 + cornerY * 5 + cornerX;\n");
        }
    }

    // ---- Special body: INTERPOLATED_INTERP -------------------------------------------------

    /**
     * Emits trilinear interpolation from the paired INTERPOLATED_SAMPLE buffer
     * (125 PER_CORNER values) into the PER_VOXEL output buffer.
     */
    private static List<String> emitInterpolatedInterp(KernelGroup group, KernelBuilder builder,
                                                       IdentityHashMap<BarrierNode, String> barrierMacroNames) {
        // The single read is the INTERPOLATED_SAMPLE barrier.
        BarrierNode sampleBarrier = group.reads().get(0);
        String sampleMacro = barrierMacroNames.get(sampleBarrier);

        StringBuilder logic = builder.logic;
        logic.append("    int gx = relX >> 2; int gy = relY >> 2; int gz = relZ >> 2;\n");
        logic.append("    float kx = float(relX & 3) * 0.25f; float ky = float(relY & 3) * 0.25f; float kz = float(relZ & 3) * 0.25f;\n");

        // Sample all 8 corners of the enclosing 4x4x4 cell.
        logic.append("    float c000 = GET_").append(sampleMacro).append("(gz*25 + gy*5 + gx);\n");
        logic.append("    float c100 = GET_").append(sampleMacro).append("(gz*25 + gy*5 + gx + 1);\n");
        logic.append("    float c010 = GET_").append(sampleMacro).append("(gz*25 + (gy+1)*5 + gx);\n");
        logic.append("    float c110 = GET_").append(sampleMacro).append("(gz*25 + (gy+1)*5 + gx + 1);\n");
        logic.append("    float c001 = GET_").append(sampleMacro).append("((gz+1)*25 + gy*5 + gx);\n");
        logic.append("    float c101 = GET_").append(sampleMacro).append("((gz+1)*25 + gy*5 + gx + 1);\n");
        logic.append("    float c011 = GET_").append(sampleMacro).append("((gz+1)*25 + (gy+1)*5 + gx);\n");
        logic.append("    float c111 = GET_").append(sampleMacro).append("((gz+1)*25 + (gy+1)*5 + gx + 1);\n");

        // Trilinear interpolation.
        logic.append("    float result = c000*(1.0f-kx)*(1.0f-ky)*(1.0f-kz) + c100*kx*(1.0f-ky)*(1.0f-kz)\n");
        logic.append("                 + c010*(1.0f-kx)*ky*(1.0f-kz)         + c110*kx*ky*(1.0f-kz)\n");
        logic.append("                 + c001*(1.0f-kx)*(1.0f-ky)*kz          + c101*kx*(1.0f-ky)*kz\n");
        logic.append("                 + c011*(1.0f-kx)*ky*kz                 + c111*kx*ky*kz;\n");
        logic.append("    SET_OUTPUT(threadIdx, result);\n");

        return Collections.emptyList();
    }

    // ---- Special body: COLUMN_REDUCE -------------------------------------------------------

    /**
     * Emits a Y-scan loop that finds the highest voxel with positive density,
     * matching the Java {@code FindTopSurfaceFunc} reference implementation.
     * <p>
     * reads[0] = density (PER_VOXEL buffer), reads[1] = upper_bound (PER_COLUMN buffer).
     */
    private static List<String> emitColumnReduce(KernelGroup group, KernelBuilder builder,
                                                  IdentityHashMap<BarrierNode, String> barrierMacroNames) {
        FindTopSurfaceFunc fts = (FindTopSurfaceFunc) group.output().source();

        BarrierNode densityBarrier = group.reads().get(0);
        BarrierNode upperBoundBarrier = group.reads().get(1);
        String densityMacro = barrierMacroNames.get(densityBarrier);
        String upperBoundMacro = barrierMacroNames.get(upperBoundBarrier);

        int lowerBound = fts.lower_bound;
        int cellHeight = fts.cell_height;

        StringBuilder logic = builder.logic;
        logic.append("    float upperBoundF = GET_").append(upperBoundMacro).append("(threadIdx);\n");
        logic.append("    int upperY = (int(upperBoundF) / ").append(cellHeight).append(") * ").append(cellHeight).append(";\n");
        logic.append("    int result = ").append(lowerBound).append(";\n");
        logic.append("    for (int y = upperY; y > ").append(lowerBound).append("; y -= ").append(cellHeight).append(") {\n");
        logic.append("        int relY = y - GET_CHUNK_Y * 16;\n");
        logic.append("        if (relY < 0 || relY >= 16) continue;\n");
        logic.append("        float density = GET_").append(densityMacro).append("(relY * 256 + relZ * 16 + relX);\n");
        logic.append("        if (density > 0.0f) { result = y; break; }\n");
        logic.append("    }\n");
        logic.append("    SET_OUTPUT(threadIdx, uint(result));\n");

        return Collections.emptyList();
    }

    // ---- Special body: SPLINE_EVAL ---------------------------------------------------------

    /**
     * Emits cubic-Hermite spline evaluation for a {@link BarrierKind#SPLINE_EVAL} kernel.
     * <p>
     * The eval barrier's inputs are laid out as:
     * <ul>
     *   <li>inputs[0] — the coordinate: either a BarrierNode (read via GET macro) or an
     *       InlineNode (inlined directly when the coord is barrier-free)</li>
     *   <li>inputs[1..N] — spline point values: either a nested-spline BarrierNode,
     *       a {@link SplineValue} InlineNode (float literal), or a general InlineNode</li>
     * </ul>
     * InlineNode variables are emitted first via {@link ExprEmitter#emitNodes} so their
     * variable names ({@code v_N}) are available when building the args array.
     */
    private static List<String> emitSplineEval(KernelGroup group, KernelBuilder builder,
                                                IdentityHashMap<BarrierNode, String> barrierMacroNames,
                                                Function<String, int[]> noiseDataProvider) {
        BarrierNode evalBarrier = group.output();
        SplineCurve sc = (SplineCurve) evalBarrier.source();
        List<DFDagNode> evalInputs = evalBarrier.inputs();

        // Emit InlineNode variables first (handles barrier-free coord subtrees).
        List<String> noiseSlotIds = ExprEmitter.emitNodes(group, builder, barrierMacroNames, noiseDataProvider);

        List<InlineNode> nodes = group.nodes();
        String[] args = new String[evalInputs.size()];
        for (int i = 0; i < evalInputs.size(); i++) {
            DFDagNode inp = evalInputs.get(i);
            if (inp instanceof BarrierNode) {
                BarrierNode b = (BarrierNode) inp;
                args[i] = "GET_" + barrierMacroNames.get(b) + "(threadIdx)";
            } else if (inp instanceof InlineNode) {
                InlineNode in = (InlineNode) inp;
                if (in.source() instanceof SplineValue) {
                    // Constant spline point value — inline as float literal.
                    args[i] = ((SplineValue) in.source()).coordinate + "f";
                } else {
                    // Barrier-free coord subtree — find the variable emitted by ExprEmitter.
                    int idx = identityIndexOf(nodes, in);
                    args[i] = idx >= 0 ? "v_" + idx : "0.0f /* coord node not in kernel */";
                }
            } else {
                args[i] = "0.0f /* unknown spline eval input type */";
            }
        }

        String splineExpr = SplineEmitter.emit(sc, args, builder, 0);
        builder.logic.append("    SET_OUTPUT(threadIdx, ").append(splineExpr).append(");\n");
        return noiseSlotIds;
    }

    /** Finds the index of {@code target} in {@code nodes} using reference equality. */
    private static int identityIndexOf(List<InlineNode> nodes, InlineNode target) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i) == target) return i;
        }
        return -1;
    }

    // ---- Local size helper -----------------------------------------------------------------

    private static int[] localSizes(DispatchShape shape) {
        // PER_VOXEL uses 16×4×16 = 1024 threads per workgroup (Vulkan minimum guaranteed limit).
        // Four workgroups in Y cover the full 16 voxel height: dispatch(1, 4, 1).
        if (shape == DispatchShape.PER_VOXEL)  return new int[]{16, 4, 16};
        if (shape == DispatchShape.PER_COLUMN) return new int[]{16, 1, 16};
        // PER_CORNER
        return new int[]{5, 5, 5};
    }
}
