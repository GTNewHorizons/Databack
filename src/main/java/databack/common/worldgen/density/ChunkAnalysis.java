package databack.common.worldgen.density;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/// Static facts about a tree that decide how a chunk can be evaluated.
///
/// The central question is whether marker state is **observable** (spec 4). When it is not, every function inside a
/// chunk is a pure function of the block position (with `interpolated` and `flat_cache` replaced by their chunk
/// values), and [PureChunkEvaluator] may evaluate anything in any order, cache across chunks, and skip work. When it
/// is, [StatefulChunkEvaluator] replays the exact evaluation order of spec 5.
///
/// The walk tracks, for each node, where it is evaluated and how it is read:
///
/// - **cell level** (outside any `interpolated`) or **corner level** (inside an `interpolated` argument, evaluated at
///   cell corners). Arguments of `flat_cache` and the density of `find_top_surface` are evaluated at single points,
///   where every marker is transparent, so the walk does not enter them.
/// - **batch** or **point** reads, following the forwarding table of spec 5.4.
///
/// State is observable when:
/// - an `interpolated` or `cache_all_in_cell` is evaluated at corner level (nesting, spec 4.4);
/// - a `cache_once` gets a point read at cell level: during a cell fill there is a single point instant, so every
///   point reuses the first point's value (spec 4.3). (At corner level each corner is its own instant, and batch
///   reads are recomputed per cell, so neither is observable.)
/// - a `cache_2d` gets a point read and its argument depends on y: it returns the previous read's value at the same
///   `(x, z)`, whatever its y (spec 4.1). Batch reads pass straight through.
///
/// A user `cache_all_in_cell` at cell level is not a problem: cell caches are filled children first, so every read
/// of it during a cell fill sees this cell's values.
public final class ChunkAnalysis {

    private ChunkAnalysis() {}

    /// Whether marker state is unobservable when `root` is a dimension's `final_density`.
    public static boolean isPure(DensityNode root) {
        return impurity(root) == null;
    }

    /// A description of the first construct that makes marker state observable, or null if there is none.
    public static String impurity(DensityNode root) {
        Walker w = new Walker();
        w.walk(root, false, true);
        return w.problem;
    }

    private static final class Walker {

        String problem;
        final Set<Visit> visited = new HashSet<>();
        final Map<DensityNode, Boolean> yIndependent = new IdentityHashMap<>();

        void walk(DensityNode n, boolean corner, boolean batch) {
            if (problem != null || !visited.add(new Visit(n, corner, batch))) return;

            if (n instanceof MarkerNode m) {
                switch (m.kind) {
                    case INTERPOLATED:
                        if (corner) {
                            problem = "interpolated inside another interpolated";
                            return;
                        }
                        walk(m.argument, true, true);
                        return;
                    case CACHE_ALL_IN_CELL:
                        if (corner) {
                            problem = "cache_all_in_cell inside an interpolated";
                            return;
                        }
                        walk(m.argument, false, true);
                        return;
                    case FLAT_CACHE:
                        return;
                    case CACHE_2D:
                        if (!batch && !isYIndependent(m.argument, yIndependent)) {
                            problem = "cache_2d of a function of y, read point by point";
                            return;
                        }
                        walk(m.argument, corner, batch);
                        return;
                    case CACHE_ONCE:
                        if (!corner && !batch) {
                            problem = "cache_once read point by point outside interpolators";
                            return;
                        }
                        walk(m.argument, corner, batch);
                        return;
                    default:
                        throw new AssertionError(m.kind);
                }
            }

            if (n instanceof PairNode p) {
                walk(p.a, corner, batch);
                walk(p.b, corner, batch && p.op == PairNode.Op.ADD);
            } else if (n instanceof AffineNode || n instanceof TransformNode || n instanceof ClampNode
                || n instanceof BlendDensityNode || n instanceof WeirdScaledNode) {
                // Their (single) input is read the same way they are.
                walk(n.inputs()[0], corner, batch);
            } else if (n instanceof RangeChoiceNode r) {
                walk(r.input, corner, batch);
                walk(r.whenIn, corner, false);
                walk(r.whenOut, corner, false);
            } else if (n instanceof FindTopSurfaceNode f) {
                walk(f.upperBound, corner, false);
            } else {
                for (DensityNode in : n.inputs()) walk(in, corner, false);
            }
        }
    }

    private static final class Visit {

        final DensityNode node;
        final boolean corner, batch;

        Visit(DensityNode node, boolean corner, boolean batch) {
            this.node = node;
            this.corner = corner;
            this.batch = batch;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Visit v && v.node == node && v.corner == corner && v.batch == batch;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(node) * 4 + (corner ? 2 : 0) + (batch ? 1 : 0);
        }
    }

    /// Whether `n`'s value inside a chunk is the same for every y of a column (up to the sign of a zero).
    ///
    /// Conservative: `false` means "may depend on y". Noise sampled with `y * 0.0` counts as independent, since the
    /// noise code treats `-0.0` and `0.0` alike (both add the same offset before sampling).
    public static boolean isYIndependent(DensityNode n) {
        return isYIndependent(n, new IdentityHashMap<>());
    }

    static boolean isYIndependent(DensityNode n, Map<DensityNode, Boolean> memo) {
        Boolean known = memo.get(n);
        if (known != null) return known;
        boolean result = computeYIndependent(n, memo);
        memo.put(n, result);
        return result;
    }

    private static boolean computeYIndependent(DensityNode n, Map<DensityNode, Boolean> memo) {
        if (n instanceof ConstantNode || n instanceof PlaceholderNode || n instanceof EndIslands) return true;
        if (n instanceof GradientNode || n instanceof OldBlendedNoise || n instanceof WeirdScaledNode) return false;
        if (n instanceof NoiseNode noise) {
            switch (noise.kind) {
                case NOISE: return noise.yScale == 0.0;
                case SHIFT_A:
                case SHIFT_B: return true;
                default: return false;
            }
        }
        if (n instanceof ShiftedNoiseNode s && s.yScale != 0.0) return false;
        if (n instanceof FindTopSurfaceNode f) return isYIndependent(f.upperBound, memo);
        if (n instanceof MarkerNode m) {
            switch (m.kind) {
                case FLAT_CACHE: return true;
                case CACHE_2D:
                case CACHE_ONCE: return isYIndependent(m.argument, memo);
                default: return false;
            }
        }
        if (n instanceof ShiftedNoiseNode || n instanceof SplineNode || n instanceof TransformNode
            || n instanceof ClampNode || n instanceof AffineNode || n instanceof PairNode || n instanceof RangeChoiceNode
            || n instanceof BlendDensityNode) {
            for (DensityNode in : n.inputs()) if (!isYIndependent(in, memo)) return false;
            return true;
        }
        return false;
    }

    /// Whether `n` is cheap enough that evaluating it where its value isn't needed costs less than working out where
    /// it is: no noise, and nothing that has to be computed per point. `interpolated` and `flat_cache` reads count as
    /// cheap.
    public static boolean isCheap(DensityNode n) {
        if (n instanceof ConstantNode || n instanceof PlaceholderNode || n instanceof GradientNode) return true;
        if (n instanceof MarkerNode m) {
            switch (m.kind) {
                case INTERPOLATED:
                case FLAT_CACHE: return true;
                default: return isCheap(m.argument);
            }
        }
        if (n instanceof TransformNode || n instanceof ClampNode || n instanceof AffineNode || n instanceof PairNode
            || n instanceof RangeChoiceNode || n instanceof BlendDensityNode) {
            for (DensityNode in : n.inputs()) if (!isCheap(in)) return false;
            return true;
        }
        return false;
    }
}
