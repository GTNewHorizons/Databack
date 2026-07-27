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
 * </ul>
 *
 * <h3>BarrierNode sharing</h3>
 * BarrierNodes are memoized by source factory instance identity. Multiple references to the
 * same named density function (resolved via {@link DensityFunctionRef}) share one BarrierNode,
 * so the GPU buffer is produced once and read many times. InlineNodes are not memoized —
 * re-evaluating a cheap expression inline is always better than a buffer round-trip.
 */
public class DFDagBuilder {

    private int barrierCounter;
    private final IdentityHashMap<IDensityFunctionFactory, BarrierNode> barrierMemo = new IdentityHashMap<>();

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

        BarrierKind kind = classifyBarrier(factory);
        if (kind != null) {
            return barrierMemo.computeIfAbsent(factory, f -> createBarrier(f, kind));
        }

        List<IDensityFunctionFactory> childFactories = factory.children();
        List<DFDagNode> childNodes = new ArrayList<>(childFactories.size());
        for (IDensityFunctionFactory child : childFactories) {
            childNodes.add(buildNode(child, shapeHint));
        }
        return new InlineNode(factory, shapeHint, childNodes);
    }

    private BarrierNode createBarrier(IDensityFunctionFactory factory, BarrierKind kind) {
        if (kind == BarrierKind.CACHE_ONCE)
            return buildSimpleBarrier(factory, kind, DispatchShape.PER_VOXEL, DispatchShape.PER_VOXEL);
        if (kind == BarrierKind.FLAT_CACHE)
            return buildSimpleBarrier(factory, kind, DispatchShape.PER_COLUMN, DispatchShape.PER_COLUMN);
        if (kind == BarrierKind.COLUMN_REDUCE)
            return buildSimpleBarrier(factory, kind, DispatchShape.PER_COLUMN, DispatchShape.PER_COLUMN);
        if (kind == BarrierKind.INTERPOLATED_INTERP)
            // Expands to a (SAMPLE, INTERP) pair; returns the INTERP node that consumers see.
            return createInterpolatedPair(factory);
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

    private static BarrierKind classifyBarrier(IDensityFunctionFactory factory) {
        if (factory instanceof CacheOnceUnary)     return BarrierKind.CACHE_ONCE;
        if (factory instanceof Cache2DFunc)        return BarrierKind.FLAT_CACHE;
        if (factory instanceof FlatCacheUnary)     return BarrierKind.FLAT_CACHE;
        if (factory instanceof FindTopSurfaceFunc) return BarrierKind.COLUMN_REDUCE;
        // INTERPOLATED_INTERP is the marker; createBarrier expands it to the (SAMPLE, INTERP) pair.
        if (factory instanceof InterpolatedFunc)   return BarrierKind.INTERPOLATED_INTERP;
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
