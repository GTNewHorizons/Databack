package databack.common.worldgen.density;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/// Copies of trees for sampling point by point at nearby positions, as biome lookups do: several roots at each
/// position, and the same columns at several heights. Each copy remembers ([MemoNode]):
///
/// - subtrees shared between roots, or used twice, so a position computes them once;
/// - subtrees that don't depend on y, where a parent does or at a root, so a column computes them once.
///
/// Markers are dropped first: single-point sampling ignores them ([DensityNode#sample]), and without them the y
/// analysis is exact. Cheap subtrees are recomputed. The copies give exactly what the originals give.
public final class DensityMemo {

    private DensityMemo() {}

    /// Memoizing copies of `roots`, sharing their memos. Not thread-safe: one set per thread.
    @NotNull
    @Contract("_ -> new")
    public static DensityNode[] memoize(DensityNode @NotNull [] roots) {
        DensityNode[] stripped = new DensityNode[roots.length];
        Map<DensityNode, DensityNode> unmarked = new IdentityHashMap<>();
        for (int i = 0; i < roots.length; i++) stripped[i] = roots[i].rewrite(DensityMemo::unmark, unmarked);

        Map<DensityNode, Boolean> independent = new IdentityHashMap<>();
        Set<DensityNode> targets = targets(stripped, independent);

        Map<DensityNode, DensityNode> copies = new IdentityHashMap<>();
        DensityNode[] memoized = new DensityNode[roots.length];
        for (int i = 0; i < roots.length; i++) memoized[i] = copy(stripped[i], targets, independent, copies);
        return memoized;
    }

    private static DensityNode unmark(DensityNode node) {
        return node instanceof MarkerNode marker ? marker.argument : node;
    }

    private static Set<DensityNode> targets(DensityNode[] roots, Map<DensityNode, Boolean> independent) {
        Map<DensityNode, Integer> references = new IdentityHashMap<>();
        Set<DensityNode> columnStarts = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<DensityNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        for (DensityNode root : roots) {
            references.merge(root, 1, Integer::sum);
            if (ChunkAnalysis.isYIndependent(root, independent)) columnStarts.add(root);
            walk(root, references, columnStarts, visited, independent);
        }

        Set<DensityNode> targets = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<DensityNode, Integer> e : references.entrySet()) {
            DensityNode node = e.getKey();
            if (ChunkAnalysis.isCheap(node)) continue;
            if (e.getValue() >= 2 || columnStarts.contains(node)) targets.add(node);
        }
        return targets;
    }

    private static void walk(
        DensityNode node, Map<DensityNode, Integer> references, Set<DensityNode> columnStarts, Set<DensityNode> visited,
        Map<DensityNode, Boolean> independent
    ) {
        if (!visited.add(node)) return;

        boolean dependent = !ChunkAnalysis.isYIndependent(node, independent);
        for (DensityNode child : children(node)) {
            references.merge(child, 1, Integer::sum);
            if (dependent && ChunkAnalysis.isYIndependent(child, independent)) columnStarts.add(child);
            walk(child, references, columnStarts, visited, independent);
        }
    }

    private static DensityNode[] children(DensityNode node) {
        return node instanceof FindTopSurfaceNode top ? new DensityNode[] { top.upperBound } : node.inputs();
    }

    private static DensityNode copy(
        DensityNode node, Set<DensityNode> targets, Map<DensityNode, Boolean> independent,
        Map<DensityNode, DensityNode> copies
    ) {
        DensityNode done = copies.get(node);
        if (done != null) return done;

        DensityNode[] inputs = node.inputs();
        DensityNode rebuilt = node;
        if (inputs.length > 0) {
            DensityNode[] next = new DensityNode[inputs.length];
            for (int i = 0; i < inputs.length; i++) next[i] = copy(inputs[i], targets, independent, copies);
            rebuilt = node.withInputs(next);
        }

        DensityNode result = targets.contains(node)
            ? new MemoNode(rebuilt, ChunkAnalysis.isYIndependent(node, independent)) : rebuilt;
        copies.put(node, result);
        return result;
    }
}
