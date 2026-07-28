package databack.common.worldgen.dag;

import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.*;
import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;

import java.util.*;

/**
 * Builds a {@link DFKernelPlan} from a density function factory tree.
 *
 * <h3>Splitting rules</h3>
 * <ul>
 *   <li>{@link CacheOnceUnary} → {@link BarrierKind#CACHE_ONCE}: author-marked expensive
 *       shared sub-expression; materialize to a PER_VOXEL buffer.</li>
 *   <li>{@link Cache2DFunc} / {@link FlatCacheUnary} → {@link BarrierKind#FLAT_CACHE}:
 *       flat (Y-independent) sub-expression; materialize to a PER_COLUMN buffer.</li>
 *   <li>{@link FindTopSurfaceFunc} → {@link BarrierKind#COLUMN_REDUCE}: different dispatch
 *       shape (16×16 column threads, Y-scan loop inside); PER_COLUMN output.</li>
 *   <li>{@link InterpolatedFunc} → two linked barriers: {@link BarrierKind#INTERPOLATED_SAMPLE}
 *       (PER_CORNER, evaluates argument at 5×5×5 corners) and {@link BarrierKind#INTERPOLATED_INTERP}
 *       (PER_VOXEL, trilinear interpolation). Consumers see only the INTERP node.</li>
 *   <li>{@link SplineCurve} → two linked barriers: {@link BarrierKind#SPLINE_COORD}
 *       (PER_VOXEL, evaluates the coordinate expression) and {@link BarrierKind#SPLINE_EVAL}
 *       (PER_VOXEL, reads coord buffer and evaluates the cubic-Hermite spline).
 *       Consumers see only the EVAL node.</li>
 * </ul>
 *
 * <h3>BarrierNode sharing</h3>
 * BarrierNodes are memoized by source factory instance identity. Multiple references to the
 * same named density function (resolved via {@link DensityFunctionRef}) share one BarrierNode,
 * so the GPU buffer is produced once and read many times.
 *
 * <h3>InlineNode CSE</h3>
 * InlineNodes are also memoized by (factory identity × dispatch shape). When the same factory
 * instance appears at multiple positions in the tree, a single InlineNode is reused, producing
 * one GLSL variable instead of duplicating the expression.
 */
public class DFDagBuilder {

    private int barrierCounter;
    private final IdentityHashMap<IDensityFunctionFactory, BarrierNode> barrierMemo = new IdentityHashMap<>();
    // CSE: memoize InlineNodes by (factory identity × dispatch shape) to avoid duplicate variables
    // when the same factory instance appears more than once in the tree.
    private final Map<DispatchShape, IdentityHashMap<IDensityFunctionFactory, InlineNode>> inlineMemos
        = new EnumMap<>(DispatchShape.class);

    public static DFKernelPlan build(IDensityFunctionFactory root) {
        DFDagBuilder builder = new DFDagBuilder();
        DFDagNode rootNode = builder.buildNode(root, DispatchShape.PER_VOXEL);
        return builder.buildPlan(rootNode);
    }

    // ---- DAG construction ----

    private DFDagNode buildNode(IDensityFunctionFactory factory, DispatchShape shapeHint) {
        // Resolve DensityFunctionRef transparently. The resolved instance is the memo key,
        // so multiple refs to the same function share one BarrierNode.
        while (factory instanceof DensityFunctionRef ref) {
            factory = ref.getFactory();
        }

        BarrierKind kind = classifyBarrier(factory, shapeHint);
        if (kind != null) {
            return barrierMemo.computeIfAbsent(factory, f -> createBarrier(f, kind));
        }

        // CSE: return a cached InlineNode if this factory was already inlined at this shape.
        IdentityHashMap<IDensityFunctionFactory, InlineNode> shapeMemo =
            inlineMemos.computeIfAbsent(shapeHint, k -> new IdentityHashMap<>());
        InlineNode cached = shapeMemo.get(factory);
        if (cached != null) return cached;

        List<IDensityFunctionFactory> childFactories = factory.children();
        List<DFDagNode> childNodes = new ArrayList<>(childFactories.size());
        for (IDensityFunctionFactory child : childFactories) {
            childNodes.add(buildNode(child, shapeHint));
        }
        InlineNode node = new InlineNode(factory, shapeHint, childNodes);
        shapeMemo.put(factory, node);
        return node;
    }

    private BarrierNode createBarrier(IDensityFunctionFactory factory, BarrierKind kind) {
        if (kind == BarrierKind.CACHE_ONCE)
            return buildSimpleBarrier(factory, kind, DispatchShape.PER_VOXEL, DispatchShape.PER_VOXEL);
        if (kind == BarrierKind.FLAT_CACHE)
            return buildSimpleBarrier(factory, kind, DispatchShape.PER_COLUMN, DispatchShape.PER_COLUMN);
        if (kind == BarrierKind.COLUMN_REDUCE)
            return createColumnReduce(factory);
        if (kind == BarrierKind.INTERPOLATED_INTERP)
            // Expands to a (SAMPLE, INTERP) pair; returns the INTERP node that consumers see.
            return createInterpolatedPair(factory);
        if (kind == BarrierKind.SPLINE_EVAL)
            // Expands to a (COORD, EVAL) pair; returns the EVAL node that consumers see.
            return createSplinePair(factory);
        throw new AssertionError("unexpected barrier kind: " + kind);
    }

    /**
     * InterpolatedFunc expands to two linked BarrierNodes:
     * <ol>
     *   <li>SAMPLE (PER_CORNER): evaluates argument at the 5×5×5 corner grid positions.</li>
     *   <li>INTERP (PER_VOXEL): trilinearly interpolates from the 125 corner values.</li>
     * </ol>
     * The INTERP node is what consumers see; SAMPLE is an internal dependency.
     */
    private BarrierNode createInterpolatedPair(IDensityFunctionFactory factory) {
        List<IDensityFunctionFactory> childFactories = factory.children();
        List<DFDagNode> sampleInputs = new ArrayList<>(childFactories.size());
        for (IDensityFunctionFactory child : childFactories) {
            sampleInputs.add(buildNode(child, DispatchShape.PER_CORNER));
        }
        String sampleId = "InterpolatedSample_" + barrierCounter++;
        BarrierNode sampleBarrier = new BarrierNode(factory, BarrierKind.INTERPOLATED_SAMPLE,
            DispatchShape.PER_CORNER, sampleInputs, sampleId);

        String interpId = "InterpolatedInterp_" + barrierCounter++;
        List<DFDagNode> interpInputs = new ArrayList<>(1);
        interpInputs.add(sampleBarrier);
        return new BarrierNode(factory, BarrierKind.INTERPOLATED_INTERP,
            DispatchShape.PER_VOXEL, interpInputs, interpId);
    }

    /**
     * SplineCurve expands to a SPLINE_EVAL barrier, optionally preceded by a SPLINE_COORD barrier.
     * <p>
     * SPLINE_COORD is skipped when the coordinate expression is "barrier-free" (no buffer reads —
     * e.g. constants, arithmetic, noise). In that case the coordinate InlineNode is passed
     * directly as inputs[0] of the SPLINE_EVAL barrier and inlined into that kernel.
     * SPLINE_COORD is only created when the coordinate itself reads from a barrier buffer
     * (e.g. a nested CacheOnce), to avoid re-reading it inside every SPLINE_EVAL invocation.
     * The EVAL node is what consumers see.
     */
    private BarrierNode createSplinePair(IDensityFunctionFactory factory) {
        SplineCurve sc = (SplineCurve) factory;

        DFDagNode coordNode = buildNode(sc.coordinate, DispatchShape.PER_VOXEL);

        // Decide how to supply the coordinate to SPLINE_EVAL:
        //  - already a BarrierNode → use it directly (e.g. coord is itself a spline/cache)
        //  - barrier-free InlineNode → inline into SPLINE_EVAL, no SPLINE_COORD kernel needed
        //  - InlineNode with barrier reads → materialize via SPLINE_COORD to avoid redundant reads
        DFDagNode coordInput;
        if (coordNode instanceof BarrierNode || isBarrierFree(coordNode)) {
            coordInput = coordNode;
        } else {
            String coordId = "SplineCoord_" + barrierCounter++;
            coordInput = new BarrierNode(factory, BarrierKind.SPLINE_COORD,
                DispatchShape.PER_VOXEL, Collections.singletonList(coordNode), coordId);
        }

        List<DFDagNode> evalInputs = new ArrayList<>(1 + sc.points.length);
        evalInputs.add(coordInput);
        for (SplinePoint p : sc.points) {
            evalInputs.add(buildNode(p.value, DispatchShape.PER_VOXEL));
        }

        String evalId = "SplineEval_" + barrierCounter++;
        return new BarrierNode(factory, BarrierKind.SPLINE_EVAL,
            DispatchShape.PER_VOXEL, evalInputs, evalId);
    }

    /**
     * FindTopSurfaceFunc has two children with different dispatch shapes:
     * <ol>
     *   <li>density (children[0]): indexed as PER_VOXEL inside the Y-scan loop — must be PER_VOXEL.</li>
     *   <li>upper_bound (children[1]): flat/column-scoped — built as PER_COLUMN.</li>
     * </ol>
     * Building both children with PER_COLUMN (as buildSimpleBarrier would) causes PER_VOXEL-output
     * barriers like CacheOnce to be skipped (shape guard in classifyBarrier), losing the barrier read
     * that emitColumnReduce requires at reads[0].
     */
    private BarrierNode createColumnReduce(IDensityFunctionFactory factory) {
        FindTopSurfaceFunc fts = (FindTopSurfaceFunc) factory;
        List<DFDagNode> inputs = new ArrayList<>(2);
        inputs.add(buildNode(fts.density, DispatchShape.PER_VOXEL));
        inputs.add(buildNode(fts.upper_bound, DispatchShape.PER_COLUMN));
        String id = "FindTopSurfaceFunc_" + barrierCounter++;
        return new BarrierNode(factory, BarrierKind.COLUMN_REDUCE, DispatchShape.PER_COLUMN, inputs, id);
    }

    /**
     * Returns true if {@code node} and all its transitive inputs are InlineNodes (no BarrierNode reads).
     * Used to decide whether a spline coordinate can be inlined directly into the SPLINE_EVAL kernel.
     */
    private static boolean isBarrierFree(DFDagNode node) {
        if (node instanceof BarrierNode) return false;
        for (DFDagNode input : node.inputs()) {
            if (!isBarrierFree(input)) return false;
        }
        return true;
    }

    private BarrierNode buildSimpleBarrier(IDensityFunctionFactory factory, BarrierKind kind,
                                            DispatchShape innerShape, DispatchShape outputShape) {
        List<IDensityFunctionFactory> childFactories = factory.children();
        List<DFDagNode> inputs = new ArrayList<>(childFactories.size());
        for (IDensityFunctionFactory child : childFactories) {
            inputs.add(buildNode(child, innerShape));
        }
        String id = factory.getClass().getSimpleName() + "_" + barrierCounter++;
        return new BarrierNode(factory, kind, outputShape, inputs, id);
    }

    private static BarrierKind classifyBarrier(IDensityFunctionFactory factory, DispatchShape shapeHint) {
        // FLAT_CACHE and COLUMN_REDUCE produce PER_COLUMN output and are valid in any context
        // (PER_VOXEL kernels read from them via a cross-shape column index).
        if (factory instanceof Cache2DFunc)        return BarrierKind.FLAT_CACHE;
        if (factory instanceof FlatCacheUnary)     return BarrierKind.FLAT_CACHE;
        if (factory instanceof FindTopSurfaceFunc) return BarrierKind.COLUMN_REDUCE;

        // The following barrier kinds all produce PER_VOXEL output and are only valid as barriers
        // in PER_VOXEL context. Inside a PER_COLUMN kernel (e.g. FlatCache wrapping a CacheOnce
        // or a SplineCurve) they must be inlined instead to avoid Y-dependent buffer reads in
        // Y-independent kernel groups, which DFPlanBuilder cannot resolve.
        if (shapeHint != DispatchShape.PER_VOXEL) return null;

        if (factory instanceof CacheOnceUnary)   return BarrierKind.CACHE_ONCE;
        // INTERPOLATED_INTERP is the marker; createBarrier expands it to the (SAMPLE, INTERP) pair.
        if (factory instanceof InterpolatedFunc) return BarrierKind.INTERPOLATED_INTERP;
        // SPLINE_EVAL is the marker; createBarrier expands it to the (COORD, EVAL) pair.
        if (factory instanceof SplineCurve)      return BarrierKind.SPLINE_EVAL;
        return null;
    }

    // ---- Plan construction ----

    private DFKernelPlan buildPlan(DFDagNode rootNode) {
        List<BarrierNode> barriers = new ArrayList<>();
        Set<BarrierNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        collectBarriers(rootNode, barriers, seen);

        List<KernelGroup> groups = new ArrayList<>(barriers.size() + 1);
        for (BarrierNode barrier : barriers) {
            groups.add(buildKernelGroup(barrier));
        }
        groups.add(buildTerminalGroup(rootNode));

        return new DFKernelPlan(groups);
    }

    /** DFS post-order — deposits barriers with all their dependencies before themselves. */
    private void collectBarriers(DFDagNode node, List<BarrierNode> out, Set<BarrierNode> seen) {
        if (node instanceof BarrierNode barrier) {
            if (!seen.add(barrier)) return;
            for (DFDagNode input : barrier.inputs()) {
                collectBarriers(input, out, seen);
            }
            out.add(barrier);
        } else {
            for (DFDagNode input : node.inputs()) {
                collectBarriers(input, out, seen);
            }
        }
    }

    private KernelGroup buildKernelGroup(BarrierNode barrier) {
        List<InlineNode> nodes = new ArrayList<>();
        LinkedHashSet<BarrierNode> reads = new LinkedHashSet<>();
        Set<InlineNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        for (DFDagNode input : barrier.inputs()) {
            collectInlines(input, nodes, reads, visited);
        }

        return new KernelGroup(barrier.outputShape(), new ArrayList<>(reads), nodes, barrier);
    }

    private KernelGroup buildTerminalGroup(DFDagNode rootNode) {
        List<InlineNode> nodes = new ArrayList<>();
        LinkedHashSet<BarrierNode> reads = new LinkedHashSet<>();
        Set<InlineNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        collectInlines(rootNode, nodes, reads, visited);

        // Degenerate case: entire tree is a single barrier (e.g. top-level CacheOnce).
        DispatchShape shape = nodes.isEmpty() ? rootNode.outputShape() : DispatchShape.PER_VOXEL;
        return new KernelGroup(shape, new ArrayList<>(reads), nodes, null);
    }

    /**
     * DFS post-order within one kernel's scope.
     * Stops at BarrierNodes, recording them as buffer reads rather than recursing.
     */
    private void collectInlines(DFDagNode node, List<InlineNode> out,
                                 Set<BarrierNode> reads, Set<InlineNode> visited) {
        if (node instanceof BarrierNode) {
            reads.add((BarrierNode) node);
        } else {
            InlineNode inline = (InlineNode) node;
            if (!visited.add(inline)) return;
            for (DFDagNode input : inline.inputs()) {
                collectInlines(input, out, reads, visited);
            }
            out.add(inline);
        }
    }
}
