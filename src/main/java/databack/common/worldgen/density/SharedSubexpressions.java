package databack.common.worldgen.density;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/// Finds subtrees that are evaluated more than once per corner, so [PureChunkEvaluator] can give them a one-entry
/// memo keyed on the block position.
///
/// The overworld's `final_density` references `overworld/sloped_cheese` three times, so without a memo every corner
/// would evaluate it (and the `old_blended_noise` inside it) up to three times. In the pure regime every function is
/// a pure function of the block position, so reusing the last result for the same position returns exactly what
/// evaluating it again would.
///
/// Only interpolator arguments are considered: that's where evaluation happens point by point. At cell level whole
/// cells are evaluated array-at-a-time, which a per-point memo would only get in the way of.
final class SharedSubexpressions {

    private SharedSubexpressions() {}

    /// The nodes below `roots` (an interned DAG) that have at least two distinct parents (or one parent using them
    /// twice), excluding markers and cheap nodes, which are cheaper to recompute than to look up. Single-point
    /// contexts (`flat_cache` arguments, `find_top_surface` densities) are not entered: they are evaluated separately.
    static Set<DensityNode> memoTargets(Collection<DensityNode> roots) {
        Map<DensityNode, Integer> references = new IdentityHashMap<>();
        Set<DensityNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DensityNode root : roots) {
            // An interpolator argument shared by two interpolators counts as referenced twice.
            references.merge(root, 1, Integer::sum);
            countReferences(root, references, visited);
        }

        Set<DensityNode> targets = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<DensityNode, Integer> e : references.entrySet()) {
            DensityNode f = e.getKey();
            if (e.getValue() >= 2 && !(f instanceof MarkerNode) && !ChunkAnalysis.isCheap(f)) targets.add(f);
        }
        return targets;
    }

    private static void countReferences(DensityNode f, Map<DensityNode, Integer> references, Set<DensityNode> visited) {
        if (!visited.add(f)) return;
        if (f instanceof MarkerNode m && m.kind == MarkerNode.Kind.FLAT_CACHE) return;
        DensityNode[] in = f instanceof FindTopSurfaceNode t ? new DensityNode[] { t.upperBound } : f.inputs();
        for (DensityNode child : in) {
            references.merge(child, 1, Integer::sum);
            countReferences(child, references, visited);
        }
    }
}
