package databack.common.worldgen.density;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/// Everything about chunk evaluation that is the same for every chunk and thread: the (interned) tree, the cell
/// geometry, the evaluated Y range, and which regime the tree needs. Immutable; evaluators are built from it per
/// thread.
public final class ChunkPlan {

    /// `final_density`, interned so that structurally equal subtrees are the same object (spec 5.1: identical subtrees
    /// share one marker instance).
    public final DensityNode root;

    public final int cellWidth, cellHeight;
    /// Cells per horizontal axis of a chunk.
    public final int cellsXZ;
    /// Lowest evaluated block, a multiple of `cellHeight`.
    public final int minY;
    /// Evaluated cells per column.
    public final int cellsY;

    public final boolean pure;
    /// Why the tree needs the stateful regime, or null.
    public final String impurity;

    /// `interpolated` markers in children-before-parent order of first appearance (spec 5.1), as seen from the
    /// cell level. Single-point contexts (`flat_cache` arguments, `find_top_surface` densities) are not entered.
    public final List<MarkerNode> interpolators;
    /// `cache_all_in_cell` markers in the same order, not including the implicit outermost one.
    public final List<MarkerNode> cellCaches;

    /// @param minY lowest block to evaluate; rounded down to a cell boundary
    /// @param maxY one above the highest block to evaluate; rounded up to a cell boundary
    public ChunkPlan(DensityNode finalDensity, int cellWidth, int cellHeight, int minY, int maxY, boolean forceStateful) {
        if (cellWidth <= 0 || 16 % cellWidth != 0) throw new IllegalArgumentException("cell width " + cellWidth);
        if (cellHeight <= 0) throw new IllegalArgumentException("cell height " + cellHeight);

        this.root = intern(forChunk(finalDensity));
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.cellsXZ = 16 / cellWidth;
        this.minY = Math.floorDiv(minY, cellHeight) * cellHeight;
        int top = Math.floorDiv(maxY + cellHeight - 1, cellHeight) * cellHeight;
        this.cellsY = (top - this.minY) / cellHeight;

        String why = ChunkAnalysis.impurity(root);
        if (why == null && forceStateful) why = "stateful evaluation requested";
        this.impurity = why;
        this.pure = why == null;

        List<MarkerNode> interps = new ArrayList<>(), caches = new ArrayList<>();
        collectMarkers(root, new IdentityHashMap<>(), interps, caches);
        this.interpolators = interps;
        this.cellCaches = caches;
    }

    /// Height of the evaluated range, in blocks.
    public int height() {
        return cellsY * cellHeight;
    }

    public int cellVolume() {
        return cellWidth * cellWidth * cellHeight;
    }

    /// The tree as evaluated inside a chunk: every `mul(0, x)` computes `x * 0` ([AffineNode#zeroFirst]), including
    /// at the single points of `flat_cache` arguments and `find_top_surface` densities (cases `constant_bare_zero*`).
    static DensityNode forChunk(DensityNode root) {
        return forChunk(root, new IdentityHashMap<>());
    }

    private static DensityNode forChunk(DensityNode n, Map<DensityNode, DensityNode> done) {
        DensityNode result = done.get(n);
        if (result != null) return result;
        DensityNode[] in = n.inputs();
        DensityNode[] next = in.clone();
        boolean changed = false;
        for (int i = 0; i < in.length; i++) {
            next[i] = forChunk(in[i], done);
            changed |= next[i] != in[i];
        }
        result = changed ? n.withInputs(next) : n;
        if (result instanceof AffineNode a) result = a.inChunk();
        done.put(n, result);
        return result;
    }

    /// Rebuilds `root` so that structurally equal subtrees are one object.
    static DensityNode intern(DensityNode root) {
        Map<DensityNode, DensityNode> canonical = new HashMap<>();
        return root.rewrite(n -> {
            DensityNode existing = canonical.putIfAbsent(n, n);
            return existing != null ? existing : n;
        });
    }

    private static void collectMarkers(
        DensityNode n, Map<DensityNode, Boolean> seen, List<MarkerNode> interps, List<MarkerNode> caches
    ) {
        if (seen.put(n, Boolean.TRUE) != null) return;
        if (n instanceof MarkerNode m && m.kind == MarkerNode.Kind.FLAT_CACHE) return;

        DensityNode[] in = n instanceof FindTopSurfaceNode f ? new DensityNode[] { f.upperBound } : n.inputs();
        for (DensityNode child : in) collectMarkers(child, seen, interps, caches);

        if (n instanceof MarkerNode m) {
            if (m.kind == MarkerNode.Kind.INTERPOLATED) interps.add(m);
            else if (m.kind == MarkerNode.Kind.CACHE_ALL_IN_CELL) caches.add(m);
        }
    }
}
