package databack.common.worldgen.density;

import java.util.Arrays;

/// Cell-level evaluation for [PureChunkEvaluator]: each op computes a function over the **lanes** of the current
/// cell (one lane per block, ordered y, z, x) into an array.
///
/// An op is asked for a subset of lanes, given as `lanes[0 .. count)`, or all of them when `lanes` is null. Ops whose
/// whole subtree is **cheap** ([ChunkAnalysis#isCheap]) ignore the subset and fill every lane: tight loops over the
/// full array beat working out which lanes matter. Expensive ops compute only the requested lanes, which is how the
/// short circuits of `mul`, `min`, `max` and `range_choice` skip work: the second argument (or a branch) is asked for
/// only the lanes that need it, and not at all when none do.
///
/// Every op owns its scratch arrays. Evaluation of one op never re-enters itself, so they are never in use twice.
final class CellOps {

    private CellOps() {}

    abstract static class Op {

        final PureChunkEvaluator ev;
        final boolean cheap;

        Op(PureChunkEvaluator ev, boolean cheap) {
            this.ev = ev;
            this.cheap = cheap;
        }

        /// Writes the value of every requested lane into `out` (cheap ops: every lane).
        abstract void run(double[] out, int[] lanes, int count);

        /// Sound bounds on this op's value at every lane of the current cell, into `out[0..1]`, if they can be had
        /// without evaluating the lanes. False if not.
        boolean cellBounds(double[] out) {
            return false;
        }

        /// Whether this call has to produce every lane.
        final boolean allLanes(int[] lanes) {
            return lanes == null || cheap;
        }

        /// Asks `op` for the lanes `subset[0 .. count)`, passing null instead when that is every lane.
        final void runOn(Op op, double[] out, int[] subset, int count) {
            op.run(out, count == ev.lanes ? null : subset, count);
        }
    }

    // <editor-fold desc="Leaves">

    static final class Fixed extends Op {

        private final double value;

        Fixed(PureChunkEvaluator ev, double value) {
            super(ev, true);
            this.value = value;
        }

        @Override
        void run(double[] out, int[] lanes, int count) {
            Arrays.fill(out, 0, ev.lanes, value);
        }

        @Override
        boolean cellBounds(double[] out) {
            out[0] = out[1] = value;
            return !Double.isNaN(value);
        }
    }

    /// `y_clamped_gradient`: one value per y layer of the cell.
    static final class Gradient extends Op {

        private final GradientNode gradient;

        Gradient(PureChunkEvaluator ev, GradientNode gradient) {
            super(ev, true);
            this.gradient = gradient;
        }

        @Override
        void run(double[] out, int[] lanes, int count) {
            int layer = ev.cw * ev.cw;
            for (int dy = 0; dy < ev.ch; dy++) {
                Arrays.fill(out, dy * layer, (dy + 1) * layer, gradient.at(ev.cellY0 + dy));
            }
        }
    }

    static final class Interpolated extends Op {

        private final int index;
        private final double[] scratch;

        Interpolated(PureChunkEvaluator ev, int index) {
            super(ev, true);
            this.index = index;
            this.scratch = new double[ev.interpolationScratch()];
        }

        @Override
        void run(double[] out, int[] lanes, int count) {
            ev.interpolate(index, out, scratch);
        }

        @Override
        boolean cellBounds(double[] out) {
            return ev.cornerBounds(index, out);
        }
    }

    /// Any function without an array implementation, evaluated lane by lane at the lane's block position.
    static final class PerPoint extends Op {

        private final DensityNode node;

        PerPoint(PureChunkEvaluator ev, DensityNode node) {
            super(ev, false);
            this.node = node;
        }

        @Override
        void run(double[] out, int[] lanes, int count) {
            int x0 = ev.cellX0, y0 = ev.cellY0, z0 = ev.cellZ0;
            int[] lx = ev.laneX, ly = ev.laneY, lz = ev.laneZ;
            if (lanes == null) {
                for (int l = 0; l < ev.lanes; l++) out[l] = node.sample(x0 + lx[l], y0 + ly[l], z0 + lz[l]);
            } else {
                for (int i = 0; i < count; i++) {
                    int l = lanes[i];
                    out[l] = node.sample(x0 + lx[l], y0 + ly[l], z0 + lz[l]);
                }
            }
        }
    }

    // </editor-fold>

    // <editor-fold desc="Single input">

    static final class Transform extends Op {

        private static final double SQUEEZE_TOP = TransformNode.Kind.SQUEEZE.apply(1.0);
        private static final double SQUEEZE_BOTTOM = TransformNode.Kind.SQUEEZE.apply(-1.0);

        private final TransformNode.Kind kind;
        private final Op input;

        Transform(PureChunkEvaluator ev, TransformNode.Kind kind, Op input) {
            super(ev, input.cheap);
            this.kind = kind;
            this.input = input;
        }

        @Override
        void run(double[] out, int[] lanes, int count) {
            input.run(out, lanes, count);
            if (allLanes(lanes)) {
                int n = ev.lanes;
                switch (kind) {
                    case ABS: for (int l = 0; l < n; l++) out[l] = Math.abs(out[l]); break;
                    case SQUARE: for (int l = 0; l < n; l++) out[l] = out[l] * out[l]; break;
                    case CUBE: for (int l = 0; l < n; l++) out[l] = out[l] * out[l] * out[l]; break;
                    case HALF_NEGATIVE: for (int l = 0; l < n; l++) out[l] = out[l] > 0.0 ? out[l] : out[l] * 0.5; break;
                    case QUARTER_NEGATIVE: for (int l = 0; l < n; l++) out[l] = out[l] > 0.0 ? out[l] : out[l] * 0.25; break;
                    case INVERT: for (int l = 0; l < n; l++) out[l] = 1.0 / out[l]; break;
                    default:
                        // squeeze: inputs outside [-1, 1] clamp to the ends, whose results are fixed.
                        for (int l = 0; l < n; l++) {
                            double c = out[l];
                            if (c >= 1.0) out[l] = SQUEEZE_TOP;
                            else if (c <= -1.0) out[l] = SQUEEZE_BOTTOM;
                            else out[l] = c / 2.0 - c * c * c / 24.0;
                        }
                        break;
                }
            } else {
                for (int i = 0; i < count; i++) out[lanes[i]] = kind.apply(out[lanes[i]]);
            }
        }

        /// For the transforms that are monotonic in floating point.
        @Override
        boolean cellBounds(double[] out) {
            switch (kind) {
                case ABS:
                    if (!input.cellBounds(out)) return false;
                    double lo = out[0], hi = out[1];
                    out[0] = lo >= 0.0 ? lo : hi <= 0.0 ? -hi : 0.0;
                    out[1] = Math.max(Math.abs(lo), Math.abs(hi));
                    return true;
                case HALF_NEGATIVE:
                case QUARTER_NEGATIVE:
                    if (!input.cellBounds(out)) return false;
                    out[0] = kind.apply(out[0]);
                    out[1] = kind.apply(out[1]);
                    return true;
                default:
                    return false;
            }
        }
    }

    static final class Clamp extends Op {

        private final double min, max;
        private final Op input;

        Clamp(PureChunkEvaluator ev, double min, double max, Op input) {
            super(ev, input.cheap);
            this.min = min;
            this.max = max;
            this.input = input;
        }

        @Override
        void run(double[] out, int[] lanes, int count) {
            input.run(out, lanes, count);
            if (allLanes(lanes)) {
                for (int l = 0; l < ev.lanes; l++) out[l] = DensityMath.clamp(out[l], min, max);
            } else {
                for (int i = 0; i < count; i++) out[lanes[i]] = DensityMath.clamp(out[lanes[i]], min, max);
            }
        }
    }

    static final class Affine extends Op {

        private final boolean multiply;
        private final double c;
        private final Op input;

        Affine(PureChunkEvaluator ev, boolean multiply, double c, Op input) {
            super(ev, input.cheap);
            this.multiply = multiply;
            this.c = c;
            this.input = input;
        }

        @Override
        void run(double[] out, int[] lanes, int count) {
            input.run(out, lanes, count);
            if (allLanes(lanes)) {
                int n = ev.lanes;
                if (multiply) for (int l = 0; l < n; l++) out[l] = out[l] * c;
                else for (int l = 0; l < n; l++) out[l] = out[l] + c;
            } else if (multiply) {
                for (int i = 0; i < count; i++) out[lanes[i]] = out[lanes[i]] * c;
            } else {
                for (int i = 0; i < count; i++) out[lanes[i]] = out[lanes[i]] + c;
            }
        }

        @Override
        boolean cellBounds(double[] out) {
            if (!input.cellBounds(out)) return false;
            double lo, hi;
            if (!multiply) {
                lo = out[0] + c;
                hi = out[1] + c;
            } else if (c >= 0.0) {
                lo = out[0] * c;
                hi = out[1] * c;
            } else {
                lo = out[1] * c;
                hi = out[0] * c;
            }
            out[0] = lo;
            out[1] = hi;
            return !Double.isNaN(lo) && !Double.isNaN(hi);
        }
    }

    // </editor-fold>

    // <editor-fold desc="Two inputs">

    static final class Add extends Op {

        private final Op a, b;
        private final double[] tmp;

        Add(PureChunkEvaluator ev, Op a, Op b) {
            super(ev, a.cheap && b.cheap);
            this.a = a;
            this.b = b;
            this.tmp = new double[ev.lanes];
        }

        @Override
        void run(double[] out, int[] lanes, int count) {
            a.run(out, lanes, count);
            b.run(tmp, lanes, count);
            if (allLanes(lanes)) {
                for (int l = 0; l < ev.lanes; l++) out[l] = out[l] + tmp[l];
            } else {
                for (int i = 0; i < count; i++) out[lanes[i]] = out[lanes[i]] + tmp[lanes[i]];
            }
        }

        /// Rounding is monotonic, so adding the bounds bounds the sums.
        @Override
        boolean cellBounds(double[] out) {
            if (!a.cellBounds(out)) return false;
            double lo = out[0], hi = out[1];
            if (!b.cellBounds(out)) return false;
            out[0] = lo + out[0];
            out[1] = hi + out[1];
            return !Double.isNaN(out[0]) && !Double.isNaN(out[1]);
        }
    }

    /// The general forms of `mul`, `min` and `max`, whose second input is only needed on some lanes:
    ///
    /// - `mul`: `0` where `a == 0`, else `a * b`;
    /// - `min`: `a` where `a < b.lo`, else `min(a, b)`;
    /// - `max`: `a` where `a > b.hi`, else `max(a, b)`.
    ///
    /// Each kind has its own loops, so the per-lane tests stay inlined.
    static final class Gated extends Op {

        static final int MUL = 0, MIN = 1, MAX = 2;

        private final int kind;
        private final Op a, b;
        private final double threshold;
        private final double[] tmp, bounds = new double[2];
        private final int[] needed;

        /// @param threshold `b.lo` for `min`, `b.hi` for `max`, unused for `mul`
        Gated(PureChunkEvaluator ev, int kind, Op a, Op b, double threshold) {
            super(ev, a.cheap && b.cheap);
            this.kind = kind;
            this.a = a;
            this.b = b;
            this.threshold = threshold;
            this.tmp = new double[ev.lanes];
            this.needed = new int[ev.lanes];
        }

        /// Over [#collect]'s lanes: the largest value of a (`min`) or the smallest (`max`); NaN if any is NaN.
        private double extreme;

        /// Lists the lanes where b is needed in [#needed] and returns how many there are. One loop per kind and lane
        /// mode, so each stays a tight loop.
        private int collect(double[] out, int[] lanes, int total, boolean all) {
            int[] nd = needed;
            double t = threshold;
            int m = 0;
            if (kind == MUL) {
                if (all) {
                    for (int l = 0; l < total; l++) if (out[l] != 0.0) nd[m++] = l;
                } else {
                    for (int i = 0; i < total; i++) if (out[lanes[i]] != 0.0) nd[m++] = lanes[i];
                }
                return m;
            }
            double ext = kind == MIN ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            if (kind == MIN) {
                for (int i = 0; i < total; i++) {
                    int l = all ? i : lanes[i];
                    double v = out[l];
                    if (!(v < t)) {
                        nd[m++] = l;
                        ext = Math.max(ext, v);
                    }
                }
            } else {
                for (int i = 0; i < total; i++) {
                    int l = all ? i : lanes[i];
                    double v = out[l];
                    if (!(v > t)) {
                        nd[m++] = l;
                        ext = Math.min(ext, v);
                    }
                }
            }
            extreme = ext;
            return m;
        }

        @Override
        void run(double[] out, int[] lanes, int count) {
            a.run(out, lanes, count);
            boolean all = allLanes(lanes);
            int total = all ? ev.lanes : count;
            int m = collect(out, lanes, total, all);

            if (m == 0) {
                if (kind == MUL) for (int i = 0; i < total; i++) out[all ? i : lanes[i]] = 0.0;
                return;
            }
            // min(a, b) is a wherever b is certainly larger, and max likewise. (A NaN in a makes the extreme NaN, and
            // the comparison false.)
            if (kind != MUL && b.cellBounds(bounds) && (kind == MIN ? bounds[0] > extreme : bounds[1] < extreme)) return;
            if (b.cheap) b.run(tmp, null, ev.lanes);
            else runOn(b, tmp, needed, m);

            double[] vb = tmp;
            int[] nd = needed;
            switch (kind) {
                case MUL:
                    if (all) {
                        for (int l = 0; l < total; l++) out[l] = out[l] != 0.0 ? out[l] * vb[l] : 0.0;
                    } else {
                        for (int i = 0; i < total; i++) {
                            int l = lanes[i];
                            out[l] = out[l] != 0.0 ? out[l] * vb[l] : 0.0;
                        }
                    }
                    break;
                case MIN:
                    for (int i = 0; i < m; i++) out[nd[i]] = Math.min(out[nd[i]], vb[nd[i]]);
                    break;
                default:
                    for (int i = 0; i < m; i++) out[nd[i]] = Math.max(out[nd[i]], vb[nd[i]]);
                    break;
            }
        }

        @Override
        boolean cellBounds(double[] out) {
            if (kind == MUL || !a.cellBounds(out)) return false;
            double aLo = out[0], aHi = out[1];
            if (!b.cellBounds(out)) return false;
            double bLo = out[0], bHi = out[1];
            if (kind == MIN) {
                // Lanes are a (where a < threshold) or min(a, b).
                boolean canSkip = aLo < threshold;
                out[0] = Math.min(aLo, bLo);
                out[1] = canSkip ? aHi : Math.min(aHi, bHi);
            } else {
                boolean canSkip = aHi > threshold;
                out[0] = canSkip ? aLo : Math.max(aLo, bLo);
                out[1] = Math.max(aHi, bHi);
            }
            return !Double.isNaN(out[0]) && !Double.isNaN(out[1]);
        }
    }

    // </editor-fold>

    static final class Range extends Op {

        private final Op input, whenIn, whenOut;
        private final double minInclusive, maxExclusive;
        private final double[] selector, inValues, bounds = new double[2];
        private final int[] inLanes, outLanes;

        Range(PureChunkEvaluator ev, Op input, double minInclusive, double maxExclusive, Op whenIn, Op whenOut) {
            super(ev, input.cheap && whenIn.cheap && whenOut.cheap);
            this.input = input;
            this.whenIn = whenIn;
            this.whenOut = whenOut;
            this.minInclusive = minInclusive;
            this.maxExclusive = maxExclusive;
            this.selector = new double[ev.lanes];
            this.inValues = new double[ev.lanes];
            this.inLanes = new int[ev.lanes];
            this.outLanes = new int[ev.lanes];
        }

        private boolean inRange(double v) {
            return v >= minInclusive && v < maxExclusive;
        }

        /// 1 if every lane of the current cell is in range, -1 if none is, 0 if that isn't known.
        private int decided() {
            if (!input.cellBounds(bounds)) return 0;
            if (bounds[0] >= minInclusive && bounds[1] < maxExclusive) return 1;
            if (bounds[1] < minInclusive || bounds[0] >= maxExclusive) return -1;
            return 0;
        }

        @Override
        boolean cellBounds(double[] out) {
            int d = decided();
            if (d != 0) return (d > 0 ? whenIn : whenOut).cellBounds(out);
            if (!whenIn.cellBounds(out)) return false;
            double lo = out[0], hi = out[1];
            if (!whenOut.cellBounds(out)) return false;
            out[0] = Math.min(lo, out[0]);
            out[1] = Math.max(hi, out[1]);
            return true;
        }

        @Override
        void run(double[] out, int[] lanes, int count) {
            int d = decided();
            if (d != 0) {
                (d > 0 ? whenIn : whenOut).run(out, lanes, count);
                return;
            }
            input.run(selector, lanes, count);
            boolean all = allLanes(lanes);
            int total = all ? ev.lanes : count;

            int nIn = 0, nOut = 0;
            for (int i = 0; i < total; i++) {
                int l = all ? i : lanes[i];
                if (inRange(selector[l])) inLanes[nIn++] = l;
                else outLanes[nOut++] = l;
            }

            // Only one branch is needed: let it write the output directly.
            if (nOut == 0) {
                branch(whenIn, out, inLanes, nIn);
                return;
            }
            if (nIn == 0) {
                branch(whenOut, out, outLanes, nOut);
                return;
            }

            branch(whenIn, inValues, inLanes, nIn);
            branch(whenOut, out, outLanes, nOut);
            for (int i = 0; i < nIn; i++) out[inLanes[i]] = inValues[inLanes[i]];
        }

        private void branch(Op op, double[] out, int[] subset, int count) {
            if (op.cheap) op.run(out, null, ev.lanes);
            else runOn(op, out, subset, count);
        }
    }
}
