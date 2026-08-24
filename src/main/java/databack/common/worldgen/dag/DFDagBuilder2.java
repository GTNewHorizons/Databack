package databack.common.worldgen.dag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.bsideup.jabel.Desugar;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.CacheAllInCellUnary;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.DensityFunctionRef;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.FindTopSurfaceFunc;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.InterpolatedFunc;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.SplineCurve;
import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;
import databack.common.worldgen.Expr;
import databack.common.worldgen.dag.codegen.CodeGenContext;
import databack.common.worldgen.dag.codegen.GeneratedKernel;
import databack.common.worldgen.dag.codegen.PerlinGlsl;
import databack.common.worldgen.dag.codegen.SplineEmitter;
import lombok.Data;
import mcgpu.core.hwaccel.buffer.BufferDataType;
import mcgpu.core.hwaccel.buffer.BufferLayout;
import mcgpu.core.hwaccel.buffer.OffsetBufferAccessor;
import mcgpu.core.hwaccel.shader.KernelBuilder;

/**
 * Builder2: pluggable GPGPU pipeline for density function code generation.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>{@link #convertTree} — converts the factory tree into {@link DAGNode}s, resolving refs
 *       and memoizing shared sub-trees by identity.</li>
 *   <li>{@link #partitionTree} — assigns each node to either an {@link InlinedDAGNode} or
 *       a {@link BarrierDAGNode} based on shape-aware generator classification. Compound barriers
 *       (Interpolated, Spline) are expanded into chains of linked barriers.</li>
 *   <li>{@link #build} — static entry point that runs all phases and emits
 *       one {@link GeneratedKernel} per barrier plus the terminal kernel.</li>
 * </ol>
 */
public class DFDagBuilder2 {

    private final CodeGenerationBackend backend;
    private final HashMap<IDensityFunctionFactory, DAGNode> nodes = new HashMap<>();
    private final HashMap<DAGNode, BarrierDAGNode> barrierNodes = new HashMap<>();

    int nextNodeId;
    int barrierCounter;

    private DFDagBuilder2(CodeGenerationBackend backend) {
        this.backend = backend;
    }

    // ---- Public entry points ----------------------------------------------------------------

    /**
     * Constructs and partitions the DAG without running code generation.
     * Useful for structural analysis and tests.
     */
    public static PartitionedDAGNode constructDAG(CodeGenerationBackend backend,
                                                   IDensityFunctionFactory root) {
        DFDagBuilder2 dag = new DFDagBuilder2(backend);

        if (!(root instanceof CacheAllInCellUnary)) {
            root = new CacheAllInCellUnary(root);
        }

        DAGNode rootNode = dag.convertTree(root);
        return dag.partitionTree(rootNode, CellSize.BLOCKS);
    }

    /**
     * Full pipeline: partitions the DAG, emits one kernel per barrier, emits the terminal kernel.
     * Returns the kernels in topological order (dependencies before consumers); the last element
     * is always the terminal (no output barrier).
     *
     * <p>Noise data upload is deferred: each {@link GeneratedKernel} records the noise slot IDs
     * in {@link GeneratedKernel#noiseSlotIds}. The caller must supply a
     * {@link java.util.function.Function Function&lt;String, int[]&gt;} noise provider to
     * {@link databack.common.worldgen.dag.DensityFunctionExecutor#setNoiseProvider} before
     * compiling, so the actual GPU upload happens on the compile thread.
     */
    public static List<GeneratedKernel> build(CodeGenerationBackend backend,
                                               IDensityFunctionFactory root) {
        DFDagBuilder2 dag = new DFDagBuilder2(backend);

        if (!(root instanceof CacheAllInCellUnary)) {
            root = new CacheAllInCellUnary(root);
        }

        DAGNode rootNode = dag.convertTree(root);
        PartitionedDAGNode rootPartitioned = dag.partitionTree(rootNode, CellSize.BLOCKS);

        List<BarrierDAGNode> barriers = dag.collectBarriers(rootPartitioned);

        List<GeneratedKernel> kernels = new ArrayList<>(barriers.size() + 1);
        for (BarrierDAGNode barrier : barriers) {
            kernels.add(buildBarrierKernel(barrier));
        }
        kernels.add(buildTerminalKernel(rootPartitioned));

        return kernels;
    }

    // ---- Phase 1: DAG construction ---------------------------------------------------------

    private DAGNode convertTree(IDensityFunctionFactory factory) {
        while (factory instanceof DensityFunctionRef) {
            factory = ((DensityFunctionRef) factory).dereference();
        }

        DAGNode interned = nodes.get(factory);
        if (interned != null) return interned;

        HashMap<IDensityFunctionFactory, DAGNode> children = new HashMap<>();
        for (IDensityFunctionFactory child : factory.children()) {
            children.put(child, convertTree(child));
        }

        DAGNode node = new DAGNode(nextNodeId++, factory, children, backend.getCodeGenerator(factory));
        nodes.put(factory, node);
        return node;
    }

    // ---- Phase 2: Partitioning (shape-aware) -----------------------------------------------

    PartitionedDAGNode partitionTree(DAGNode node, CellSize shapeHint) {
        IDensityFunctionFactory factory = node.factory();
        DFCodeGenerator gen = node.generator();

        // Compound barriers: intercepted by factory type before generator dispatch.
        // These are always handled regardless of what generator is registered.

        // InterpolatedFunc → SAMPLE (BLOCKS_REDUCED) + INTERP (BLOCKS) pair.
        if (factory instanceof InterpolatedFunc && shapeHint == CellSize.BLOCKS) {
            BarrierDAGNode existing = barrierNodes.get(node);
            if (existing != null) return existing;
            BarrierDAGNode interp = createInterpolatedChain(node);
            barrierNodes.put(node, interp);
            return interp;
        }

        // SplineCurve → single EVAL barrier (coordinate child handled inline by DagContext).
        if (factory instanceof SplineCurve && shapeHint == CellSize.BLOCKS) {
            BarrierDAGNode existing = barrierNodes.get(node);
            if (existing != null) return existing;
            BarrierDAGNode eval = createSplineEvalBarrier(node);
            barrierNodes.put(node, eval);
            return eval;
        }

        // Simple barrier: generator declares itself a barrier at this shape.
        if (gen instanceof BarrierDFCodeGenerator) {
            BarrierDFCodeGenerator<?> barrierGen = (BarrierDFCodeGenerator<?>) gen;
            CellSize outputShape = barrierGen.outputShape(shapeHint);
            if (outputShape != null) {
                BarrierDAGNode existing = barrierNodes.get(node);
                if (existing != null) return existing;

                HashMap<DAGNode, PartitionedDAGNode> inputs = new HashMap<>();
                for (Map.Entry<IDensityFunctionFactory, DAGNode> e : node.inputs().entrySet()) {
                    IDensityFunctionFactory childFactory = e.getKey();
                    CellSize childHint = barrierGen.childShapeHint(factory, shapeHint, childFactory);
                    inputs.put(e.getValue(), partitionTree(e.getValue(), childHint));
                }

                String id = factory.getClass().getSimpleName() + "_" + barrierCounter++;
                BarrierDAGNode barrier = new BarrierDAGNode(node, inputs, outputShape, id);
                barrierNodes.put(node, barrier);
                return barrier;
            }
            // outputShape == null → generator opts out at this shape; fall through to inline.
        }

        // Inline node: all children inherit the same shape hint.
        HashMap<DAGNode, PartitionedDAGNode> inputs = new HashMap<>();
        for (Map.Entry<IDensityFunctionFactory, DAGNode> e : node.inputs().entrySet()) {
            inputs.put(e.getValue(), partitionTree(e.getValue(), shapeHint));
        }
        return new InlinedDAGNode(node, inputs, shapeHint);
    }

    /**
     * InterpolatedFunc expands to two barriers:
     * <ol>
     *   <li>SAMPLE (BLOCKS_REDUCED) — evaluates the argument at 5×5×5 corner positions.</li>
     *   <li>INTERP (BLOCKS) — trilinearly interpolates the 125 corners to PER_VOXEL density.</li>
     * </ol>
     * The INTERP barrier is what external consumers reference. Its single input is the SAMPLE barrier.
     */
    private BarrierDAGNode createInterpolatedChain(DAGNode node) {
        InterpolatedFunc interp = (InterpolatedFunc) node.factory();

        // Build SAMPLE barrier: the argument evaluated at BLOCKS_REDUCED.
        DAGNode childDAGNode = node.inputs().get(interp.argument());
        PartitionedDAGNode childPartitioned = partitionTree(childDAGNode, CellSize.BLOCKS_REDUCED);

        HashMap<DAGNode, PartitionedDAGNode> sampleInputs = new HashMap<>();
        sampleInputs.put(childDAGNode, childPartitioned);

        // Synthetic DAGNode for SAMPLE uses InterpolatedSampleGenerator.
        DAGNode sampleNode = new DAGNode(nextNodeId++, node.factory(), node.inputs(),
            InterpolatedSampleGenerator.INSTANCE);
        String sampleId = "InterpolatedSample_" + barrierCounter++;
        BarrierDAGNode sampleBarrier = new BarrierDAGNode(sampleNode, sampleInputs,
            CellSize.BLOCKS_REDUCED, sampleId);

        // INTERP barrier: single input = SAMPLE barrier.
        HashMap<DAGNode, PartitionedDAGNode> interpInputs = new HashMap<>();
        interpInputs.put(sampleNode, sampleBarrier);

        HashMap<IDensityFunctionFactory, DAGNode> interpNodeInputs = new HashMap<>();
        interpNodeInputs.put(interp.argument(), sampleNode);
        DAGNode interpNode = new DAGNode(nextNodeId++, node.factory(), interpNodeInputs,
            InterpolatedInterpGenerator.INSTANCE);
        String interpId = "InterpolatedInterp_" + barrierCounter++;
        return new BarrierDAGNode(interpNode, interpInputs, CellSize.BLOCKS, interpId);
    }

    /**
     * SplineCurve creates a single EVAL barrier. The coordinate and point values are all computed
     * as inputs (inline or barrier) within the EVAL kernel, so no separate COORD barrier is needed.
     */
    private BarrierDAGNode createSplineEvalBarrier(DAGNode node) {
        SplineCurve sc = (SplineCurve) node.factory();

        HashMap<DAGNode, PartitionedDAGNode> inputs = new HashMap<>();
        // Partition all SplineCurve children (coord + point values) at BLOCKS shape.
        for (Map.Entry<IDensityFunctionFactory, DAGNode> e : node.inputs().entrySet()) {
            inputs.put(e.getValue(), partitionTree(e.getValue(), CellSize.BLOCKS));
        }

        // Synthetic DAGNode uses SplineEvalGenerator.
        DAGNode evalNode = new DAGNode(nextNodeId++, node.factory(), node.inputs(),
            SplineEvalGenerator.INSTANCE);
        String evalId = "SplineEval_" + barrierCounter++;
        return new BarrierDAGNode(evalNode, inputs, CellSize.BLOCKS, evalId);
    }

    // ---- Phase 3: Barrier collection -------------------------------------------------------

    private List<BarrierDAGNode> collectBarriers(PartitionedDAGNode root) {
        List<BarrierDAGNode> result = new ArrayList<>();
        Set<BarrierDAGNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        collectBarriers(root, result, seen);
        return result;
    }

    private void collectBarriers(PartitionedDAGNode node,
                                  List<BarrierDAGNode> out,
                                  Set<BarrierDAGNode> seen) {
        if (node instanceof BarrierDAGNode) {
            BarrierDAGNode barrier = (BarrierDAGNode) node;
            if (!seen.add(barrier)) return;
            // Recurse into inputs first (dependencies before self).
            for (PartitionedDAGNode child : barrier.inputs.values()) {
                collectBarriers(child, out, seen);
            }
            out.add(barrier);
        } else {
            // InlinedDAGNode: recurse into inputs, collect any barrier inputs.
            for (PartitionedDAGNode child : node.inputs().values()) {
                collectBarriers(child, out, seen);
            }
        }
    }

    // ---- Phase 3: Kernel construction ------------------------------------------------------

    private static GeneratedKernel buildBarrierKernel(BarrierDAGNode barrier) {
        CellSize shape = barrier.outputShape;
        int[] ls = localSizes(shape);
        // null constants: noise offsets are placeholders; real upload deferred to compile().
        KernelBuilder kb = new KernelBuilder(null);

        // Chunk coord push constants.
        kb.addParameter(BufferDataType.i32, "chunkX");
        if (!shape.isYInvariant()) {
            kb.addParameter(BufferDataType.i32, "chunkY");
        }
        kb.addParameter(BufferDataType.i32, "chunkZ");

        // Output buffer.
        BufferDataType outType = BufferDataType.f32;
        if (barrier.node.generator() instanceof BarrierDFCodeGenerator) {
            BarrierDFCodeGenerator<?> bg = (BarrierDFCodeGenerator<?>) barrier.node.generator();
            BufferLayout layout = bg.getBufferLayout();
            outType = layout.dataType();
            kb.addOutputBuffer(barrier.id, layout);
        }

        DagContext ctx = new DagContext(kb, shape);

        // Let the generator emit the main() body (coordinate preamble + expressions).
        BarrierDFCodeGenerator<?> gen = (BarrierDFCodeGenerator<?>) barrier.node.generator();
        gen.emitMain(ctx, barrier);

        String glsl = KernelBuilder.getStandardHeader(ls[0], ls[1], ls[2])
            + kb.preamble.toString()
            + kb.pushConstants.getPushConstantDefinition()
            + "void main() {\n" + kb.logic.toString() + "}\n";

        boolean isColumnReduce = barrier.node.factory() instanceof FindTopSurfaceFunc;
        boolean isYIndependent = shape.isYInvariant() && !isColumnReduce;

        return new GeneratedKernel(shape, glsl,
            new ArrayList<>(ctx.boundBarrierIds), barrier.id, new ArrayList<>(ctx.noiseSlotIds),
            isYIndependent, isColumnReduce,
            new HashMap<>(kb.inputs), new HashMap<>(kb.outputs));
    }

    private static GeneratedKernel buildTerminalKernel(PartitionedDAGNode root) {
        CellSize shape = CellSize.BLOCKS;
        int[] ls = localSizes(shape);
        KernelBuilder kb = new KernelBuilder(null);

        kb.addParameter(BufferDataType.i32, "chunkX");
        kb.addParameter(BufferDataType.i32, "chunkY");
        kb.addParameter(BufferDataType.i32, "chunkZ");
        kb.addOutputBuffer("output", new BufferLayout(BufferDataType.f32, 16, 16, 16));

        DagContext ctx = new DagContext(kb, shape);

        // Compute root node — emits all inline functions and configures barrier input bindings.
        Expr<Float> result = ctx.compute(root,
            Expr.of(Integer.class, "relX"),
            Expr.of(Integer.class, "relY"),
            Expr.of(Integer.class, "relZ"));

        appendCoordPreamble(kb.logic, shape);
        kb.logic.append("    float result = ").append(result).append(";\n");
        kb.logic.append("    SET_OUTPUT(threadIdx, result);\n");

        String glsl = KernelBuilder.getStandardHeader(ls[0], ls[1], ls[2])
            + kb.preamble.toString()
            + kb.pushConstants.getPushConstantDefinition()
            + "void main() {\n" + kb.logic.toString() + "}\n";

        return new GeneratedKernel(shape, glsl,
            new ArrayList<>(ctx.boundBarrierIds), null, new ArrayList<>(ctx.noiseSlotIds),
            false, false,
            new HashMap<>(kb.inputs), new HashMap<>(kb.outputs));
    }

    // ---- GLSL helpers ----------------------------------------------------------------------

    /** Emits the coordinate variable preamble for the given kernel shape into {@code logic}. */
    public static void appendCoordPreamble(StringBuilder logic, CellSize shape) {
        if (shape == CellSize.BLOCKS) {
            logic.append("    int relX = int(gl_GlobalInvocationID.x);\n");
            logic.append("    int relY = int(gl_GlobalInvocationID.y);\n");
            logic.append("    int relZ = int(gl_GlobalInvocationID.z);\n");
            logic.append("    int worldBlockX = GET_CHUNK_X * 16 + relX;\n");
            logic.append("    int worldBlockY = GET_CHUNK_Y * 16 + relY;\n");
            logic.append("    int worldBlockZ = GET_CHUNK_Z * 16 + relZ;\n");
            logic.append("    float wx = float(worldBlockX); float wy = float(worldBlockY); float wz = float(worldBlockZ);\n");
            logic.append("    int threadIdx = relZ * 256 + relY * 16 + relX;\n");
        } else if (shape == CellSize.COLUMNS) {
            logic.append("    int relX = int(gl_GlobalInvocationID.x);\n");
            logic.append("    int relZ = int(gl_GlobalInvocationID.z);\n");
            logic.append("    int worldBlockX = GET_CHUNK_X * 16 + relX;\n");
            logic.append("    int worldBlockZ = GET_CHUNK_Z * 16 + relZ;\n");
            logic.append("    float wx = float(worldBlockX); float wz = float(worldBlockZ);\n");
            logic.append("    int threadIdx = relZ * 16 + relX;\n");
        } else {
            // BLOCKS_REDUCED (PER_CORNER)
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

    /**
     * Returns the GLSL index expression for reading a barrier buffer from the consuming kernel.
     * The {@code x, y, z} strings are the GLSL variable names at the call site (e.g. "x", "relX",
     * "cornerX * 4"). At runtime they hold the consumer-shape-appropriate block coordinates.
     */
    public static String indexExpr(CellSize consumerShape, CellSize barrierShape,
                             String x, String y, String z) {
        if (barrierShape == CellSize.COLUMNS || barrierShape == CellSize.COLUMNS_REDUCED) {
            if (consumerShape == CellSize.BLOCKS || consumerShape == CellSize.COLUMNS) {
                // x and z are block coords (0-15); direct column index.
                return z + " * 16 + " + x;
            }
            // BLOCKS_REDUCED consumer: x,z may be cornerX*4 (0,4,8,12,16); clamp boundary corner.
            return "min(" + z + ", 15) * 16 + min(" + x + ", 15)";
        }
        // BLOCKS (3D) buffer: Z-outer, Y-middle, X-inner — matches threadIdx = z*256+y*16+x.
        return z + " * 256 + " + y + " * 16 + " + x;
    }

    private static int[] localSizes(CellSize shape) {
        if (shape == CellSize.BLOCKS)  return new int[]{16, 4, 16};
        if (shape == CellSize.COLUMNS) return new int[]{16, 1, 16};
        return new int[]{5, 5, 5}; // BLOCKS_REDUCED
    }

    // ---- Node types ------------------------------------------------------------------------

    @Desugar
    public record DAGNode(int id, IDensityFunctionFactory factory,
                          HashMap<IDensityFunctionFactory, DAGNode> inputs,
                          DFCodeGenerator generator) {

    }

    public interface PartitionedDAGNode {

        DAGNode node();

        HashMap<DAGNode, PartitionedDAGNode> inputs();

        CellSize outputShape();

        /** Looks up the partitioned node for a child factory of this node's factory. */
        default PartitionedDAGNode getInput(IDensityFunctionFactory func) {
            return inputs().get(node().inputs().get(func));
        }
    }

    @Data
    public static final class BarrierDAGNode implements PartitionedDAGNode {

        public final DAGNode node;
        public final HashMap<DAGNode, PartitionedDAGNode> inputs;
        public final CellSize outputShape;
        /** Unique identifier used for GPU buffer naming and plan wiring. */
        public final String id;

        public BarrierDAGNode(DAGNode node, HashMap<DAGNode, PartitionedDAGNode> inputs,
                              CellSize outputShape, String id) {
            this.node = node;
            this.inputs = inputs;
            this.outputShape = outputShape;
            this.id = id;
        }

        @Override
        public DAGNode node() {
            return node;
        }

        @Override
        public HashMap<DAGNode, PartitionedDAGNode> inputs() {
            return inputs;
        }

        @Override
        public CellSize outputShape() {
            return outputShape;
        }
    }

    @Desugar
    public record InlinedDAGNode(DAGNode node, HashMap<DAGNode, PartitionedDAGNode> inputs,
                                 CellSize outputShape)
        implements PartitionedDAGNode {

    }

    // ---- DagContext ------------------------------------------------------------------------

    /**
     * Per-kernel code generation context. Created fresh for each barrier or terminal kernel.
     * Caches generator state per node so that {@code emitFunction}/{@code configureConsumer}
     * is called exactly once per node per kernel.
     */
    public static class DagContext implements CodeGenContext {

        public final HashMap<PartitionedDAGNode, Object> kernelState = new HashMap<>();
        public final List<String> boundBarrierIds = new ArrayList<>();
        public final List<String> noiseSlotIds = new ArrayList<>();

        public final KernelBuilder kernel;
        private final CellSize kernelShape;

        // Per-kernel noise registration cache: noiseId → GLSL pc reference string.
        private final HashMap<String, String> noisePcCache = new HashMap<>();
        private boolean perlinInjected = false;

        public DagContext(KernelBuilder kernel, CellSize kernelShape) {
            this.kernel = kernel;
            this.kernelShape = kernelShape;
        }

        @Override
        public KernelBuilder getKernel() {
            return kernel;
        }

        @Override
        public CellSize getKernelShape() {
            return kernelShape;
        }

        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public Expr<Float> compute(PartitionedDAGNode node, Expr<Integer> x, Expr<Integer> y,
                                   Expr<Integer> z) {
            DFCodeGenerator gen = node.node().generator();

            Object state;
            if (!kernelState.containsKey(node)) {
                // Dispatch based on node type (not generator type), so optional-barrier
                // generators that become inline at certain shapes work correctly.
                if (node instanceof BarrierDAGNode) {
                    BarrierDFCodeGenerator barrier = (BarrierDFCodeGenerator) gen;
                    state = barrier.configureConsumer(this, (BarrierDAGNode) node);
                    boundBarrierIds.add(((BarrierDAGNode) node).id);
                } else {
                    InlineDFCodeGenerator inline = (InlineDFCodeGenerator) gen;
                    state = inline.emitFunction(this, (InlinedDAGNode) node);
                }
                kernelState.put(node, state);
            } else {
                state = kernelState.get(node);
            }

            if (node instanceof BarrierDAGNode) {
                BarrierDFCodeGenerator barrier = (BarrierDFCodeGenerator) gen;
                return barrier.emitGetter(this, state, x, y, z);
            } else {
                InlineDFCodeGenerator inline = (InlineDFCodeGenerator) gen;
                return inline.invokeFunction(this, state, x, y, z);
            }
        }

        @Override
        public String registerNoise(String noiseId) {
            String cached = noisePcCache.get(noiseId);
            if (cached != null) return cached;

            if (!perlinInjected) {
                kernel.preamble.append(PerlinGlsl.PERLIN_FUNCTION);
                perlinInjected = true;
            }

            OffsetBufferAccessor accessor = (OffsetBufferAccessor)
                kernel.pushConstants.addConstantOffset(BufferDataType.u32, 0, noiseId);
            String pcRef = "pc." + accessor.pcName;
            noisePcCache.put(noiseId, pcRef);
            if (!noiseSlotIds.contains(noiseId)) {
                noiseSlotIds.add(noiseId);
            }
            return pcRef;
        }
    }

    // ---- Compound barrier generators -------------------------------------------------------

    /**
     * INTERPOLATED_SAMPLE: evaluates the InterpolatedFunc's argument at the 5×5×5 corner grid
     * (BLOCKS_REDUCED dispatch). Passes scaled corner coords (cornerX*4, etc.) to child functions
     * so they compute correct world-space positions.
     */
    static final class InterpolatedSampleGenerator implements BarrierDFCodeGenerator<String> {

        static final InterpolatedSampleGenerator INSTANCE = new InterpolatedSampleGenerator();

        @Override
        public CellSize outputShape(CellSize inputShapeHint) {
            return CellSize.BLOCKS_REDUCED;
        }

        @Override
        public BufferLayout getBufferLayout() {
            return new BufferLayout(BufferDataType.f32, 5, 5, 5);
        }

        @Override
        public void emitMain(CodeGenContext context, BarrierDAGNode barrier) {
            InterpolatedFunc interp = (InterpolatedFunc) barrier.node.factory();
            PartitionedDAGNode child = barrier.getInput(interp.argument());

            // Pass scaled corner coords so child functions compute correct world positions.
            Expr<Float> childExpr = context.compute(child,
                Expr.of(Integer.class, "cornerX * 4"),
                Expr.of(Integer.class, "cornerY * 4"),
                Expr.of(Integer.class, "cornerZ * 4"));

            appendCoordPreamble(context.getKernel().logic, CellSize.BLOCKS_REDUCED);
            context.getKernel().logic
                .append("    float result = ").append(childExpr).append(";\n")
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
            // This barrier is only consumed by InterpolatedInterpGenerator.emitMain which does not
            // call emitGetter — it accesses the buffer directly by name.
            throw new UnsupportedOperationException("InterpolatedSample is read by InterpolatedInterp directly");
        }
    }

    /**
     * INTERPOLATED_INTERP: trilinearly interpolates from the 5×5×5 corner values
     * (BLOCKS_REDUCED buffer) to PER_VOXEL density values.
     */
    static final class InterpolatedInterpGenerator implements BarrierDFCodeGenerator<String> {

        static final InterpolatedInterpGenerator INSTANCE = new InterpolatedInterpGenerator();

        @Override
        public CellSize outputShape(CellSize inputShapeHint) {
            return CellSize.BLOCKS;
        }

        @Override
        public BufferLayout getBufferLayout() {
            return new BufferLayout(BufferDataType.f32, 16, 16, 16);
        }

        @Override
        public void emitMain(CodeGenContext context, BarrierDAGNode barrier) {
            // The single input is the SAMPLE barrier.
            BarrierDAGNode sampleBarrier = (BarrierDAGNode) barrier.inputs.values().iterator().next();
            String sampleName = InterpolatedSampleGenerator.INSTANCE.configureConsumer(context, sampleBarrier);
            String macro = KernelBuilder.toScreamingSnakeCase(sampleName);

            appendCoordPreamble(context.getKernel().logic, CellSize.BLOCKS);
            StringBuilder logic = context.getKernel().logic;

            logic.append("    int gx = relX >> 2; int gy = relY >> 2; int gz = relZ >> 2;\n");
            logic.append("    float kx = float(relX & 3) * 0.25f; float ky = float(relY & 3) * 0.25f; float kz = float(relZ & 3) * 0.25f;\n");

            logic.append("    float c000 = GET_").append(macro).append("(gz*25 + gy*5 + gx);\n");
            logic.append("    float c100 = GET_").append(macro).append("(gz*25 + gy*5 + gx + 1);\n");
            logic.append("    float c010 = GET_").append(macro).append("(gz*25 + (gy+1)*5 + gx);\n");
            logic.append("    float c110 = GET_").append(macro).append("(gz*25 + (gy+1)*5 + gx + 1);\n");
            logic.append("    float c001 = GET_").append(macro).append("((gz+1)*25 + gy*5 + gx);\n");
            logic.append("    float c101 = GET_").append(macro).append("((gz+1)*25 + gy*5 + gx + 1);\n");
            logic.append("    float c011 = GET_").append(macro).append("((gz+1)*25 + (gy+1)*5 + gx);\n");
            logic.append("    float c111 = GET_").append(macro).append("((gz+1)*25 + (gy+1)*5 + gx + 1);\n");

            logic.append("    float result = c000*(1.0f-kx)*(1.0f-ky)*(1.0f-kz) + c100*kx*(1.0f-ky)*(1.0f-kz)\n");
            logic.append("                 + c010*(1.0f-kx)*ky*(1.0f-kz)         + c110*kx*ky*(1.0f-kz)\n");
            logic.append("                 + c001*(1.0f-kx)*(1.0f-ky)*kz          + c101*kx*(1.0f-ky)*kz\n");
            logic.append("                 + c011*(1.0f-kx)*ky*kz                 + c111*kx*ky*kz;\n");
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
            CellSize consumerShape = consumingContext.getKernelShape();
            String idx = indexExpr(consumerShape, CellSize.BLOCKS, x.toString(), y.toString(), z.toString());
            return Expr.of(Float.class, "GET_" + KernelBuilder.toScreamingSnakeCase(bindingName) + "(" + idx + ")");
        }
    }

    /**
     * SPLINE_EVAL: evaluates a cubic-Hermite spline. The coordinate and all point values are
     * computed as inputs within this kernel (no separate COORD barrier). Uses {@link SplineEmitter}
     * to generate the segment-dispatch GLSL.
     */
    static final class SplineEvalGenerator implements BarrierDFCodeGenerator<String> {

        static final SplineEvalGenerator INSTANCE = new SplineEvalGenerator();

        @Override
        public CellSize outputShape(CellSize inputShapeHint) {
            return CellSize.BLOCKS;
        }

        @Override
        public BufferLayout getBufferLayout() {
            return new BufferLayout(BufferDataType.f32, 16, 16, 16);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public void emitMain(CodeGenContext context, BarrierDAGNode barrier) {
            SplineCurve sc = (SplineCurve) barrier.node.factory();

            Expr<Integer> relX = Expr.of(Integer.class, "relX");
            Expr<Integer> relY = Expr.of(Integer.class, "relY");
            Expr<Integer> relZ = Expr.of(Integer.class, "relZ");

            // Compute coordinate expression.
            PartitionedDAGNode coordInput = barrier.getInput(sc.coordinate());
            String coordArg = context.compute(coordInput, relX, relY, relZ).toString();

            // Compute each spline point's value expression.
            String[] args = new String[1 + sc.points().length];
            args[0] = coordArg;
            for (int i = 0; i < sc.points().length; i++) {
                PartitionedDAGNode pointInput = barrier.getInput(sc.points()[i].value());
                args[i + 1] = context.compute(pointInput, relX, relY, relZ).toString();
            }

            appendCoordPreamble(context.getKernel().logic, CellSize.BLOCKS);
            String splineExpr = SplineEmitter.emit(sc, args, context.getKernel(), 0);
            context.getKernel().logic.append("    SET_OUTPUT(threadIdx, ").append(splineExpr).append(");\n");
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
            CellSize consumerShape = consumingContext.getKernelShape();
            String idx = indexExpr(consumerShape, CellSize.BLOCKS, x.toString(), y.toString(), z.toString());
            return Expr.of(Float.class, "GET_" + KernelBuilder.toScreamingSnakeCase(bindingName) + "(" + idx + ")");
        }
    }
}
