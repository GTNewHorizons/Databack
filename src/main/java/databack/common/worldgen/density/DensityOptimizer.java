package databack.common.worldgen.density;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/// Build-time simplification of density function trees.
///
/// Every rewrite returns the same value as the original tree for every input (at most the sign of a zero can differ).
/// The rewrites mostly matter for datapacks, which tend to contain `clamp`s that can never trigger, `range_choice`s
/// whose input never leaves one side, constant arithmetic, and so on:
///
/// - constant folding of transforms, `clamp`, `add`/`mul`, `min`/`max` and markers of constants
/// - `x + 0` and `x * 1` become `x`; `x * 0` becomes `0` when `x` is finite; a general `add`/`mul` with a constant
///   argument becomes the constant form where the two agree
/// - `clamp` whose input already lies within the clamp range becomes its input
/// - `min`/`max` where one side always wins become that side
/// - `range_choice` whose input is always in (or always out of) range becomes that branch; identical branches too
/// - identical subtrees are deduplicated, so caches and interpolators get shared
///
/// Range decisions use [#bounds], a conservative interval analysis, rather than the bound estimates: some estimates
/// are not sound (the `mul` estimate is wrong when one input is positive and the other negative, for example).
///
/// A replacement keeps the estimate of the node it replaces ([DensityNode#withBounds]): estimates propagate up the
/// tree and decide `min`/`max` skips, so changing one could change a result elsewhere.
///
/// Rewrites drop evaluations, which is only invisible when marker state is unobservable. Trees in the stateful regime
/// ([ChunkAnalysis#isPure]) are returned unchanged.
public final class DensityOptimizer {

    private final Map<DensityNode, DensityNode> seen = new HashMap<>();
    private final Map<DensityNode, double[]> boundsCache = new IdentityHashMap<>();

    private DensityOptimizer() {}

    public static DensityNode optimize(DensityNode function) {
        if (!ChunkAnalysis.isPure(function)) return function;
        DensityOptimizer optimizer = new DensityOptimizer();
        return function.rewrite(optimizer::apply);
    }

    private DensityNode apply(DensityNode node) {
        DensityNode simplified = simplify(node);
        if (simplified != node && !(DensityMath.same(simplified.lo, node.lo) && DensityMath.same(simplified.hi, node.hi))) {
            simplified = simplified.withBounds(node.lo, node.hi);
        }
        DensityNode existing = seen.putIfAbsent(simplified, simplified);
        return existing != null ? existing : simplified;
    }

    private static boolean isConstant(DensityNode n) {
        return n instanceof ConstantNode;
    }

    private static double value(DensityNode n) {
        return ((ConstantNode) n).value;
    }

    private DensityNode simplify(DensityNode node) {
        if (node instanceof TransformNode t && isConstant(t.input)) {
            return Density.constant(t.kind.apply(value(t.input)));
        }

        if (node instanceof ClampNode c) {
            if (isConstant(c.input)) return Density.constant(DensityMath.clamp(value(c.input), c.min, c.max));
            double[] in = bounds(c.input);
            if (in[0] >= c.min && in[1] <= c.max) return c.input;
        }

        if (node instanceof AffineNode a) {
            // A leading-zero mul is 0 or input * 0 depending on where it is evaluated; both agree for finite inputs.
            if (isConstant(a.input) && (!a.zeroFirst || Double.isFinite(value(a.input)))) {
                return Density.constant(a.apply(value(a.input)));
            }
            if (!a.multiply && a.c == 0.0) return a.input;
            if (a.multiply && a.c == 1.0) return a.input;
            if (a.multiply && a.c == 0.0 && isFinite(bounds(a.input))) return Density.ZERO;
        }

        if (node instanceof PairNode p) {
            DensityNode r = simplifyPair(p);
            if (r != null) return r;
        }

        if (node instanceof RangeChoiceNode r) {
            if (r.whenIn.equals(r.whenOut)) return r.whenIn;
            double[] in = bounds(r.input);
            if (isFinite(in)) {
                if (in[0] >= r.minInclusive && in[1] < r.maxExclusive) return r.whenIn;
                if (in[1] < r.minInclusive || in[0] >= r.maxExclusive) return r.whenOut;
            }
        }

        if (node instanceof BlendDensityNode b && isConstant(b.input)) return b.input;

        // An interpolation of equal corners is the corner value, as long as it is finite (inf - inf is NaN).
        if (node instanceof MarkerNode m && isConstant(m.argument) && Double.isFinite(value(m.argument))) {
            return m.argument;
        }

        return node;
    }

    private DensityNode simplifyPair(PairNode p) {
        DensityNode a = p.a, b = p.b;
        boolean ca = isConstant(a), cb = isConstant(b);

        switch (p.op) {
            case ADD:
                if (ca && cb) return Density.constant(value(a) + value(b));
                // a + b == b + a, so the constant form agrees whichever side the constant is on.
                if (ca) return value(a) == 0.0 ? b : new AffineNode(b, false, value(a));
                if (cb) return value(b) == 0.0 ? a : new AffineNode(a, false, value(b));
                return null;
            case MUL:
                if (ca) {
                    // a == 0 skips b entirely; otherwise a * b == b * a.
                    if (value(a) == 0.0) return Density.ZERO;
                    if (cb) return Density.constant(value(a) * value(b));
                    return value(a) == 1.0 ? b : new AffineNode(b, true, value(a));
                }
                if (cb) {
                    // a == 0 gives 0.0 where the constant form would give 0 * c: equal unless c is NaN or infinite.
                    double c = value(b);
                    if (c == 0.0) return isFinite(bounds(a)) ? Density.ZERO : null;
                    if (!Double.isFinite(c)) return null;
                    return c == 1.0 ? a : new AffineNode(a, true, c);
                }
                return null;
            case MIN: {
                if (ca && cb) return Density.constant(Math.min(value(a), value(b)));
                double[] ba = bounds(a), bb = bounds(b);
                // a is always below b's estimate, so b is always skipped.
                if (ba[1] < b.lo) return a;
                // b is never skipped and always the smaller one.
                if (ba[0] >= b.lo && bb[1] < ba[0]) return b;
                return null;
            }
            case MAX: {
                if (ca && cb) return Density.constant(Math.max(value(a), value(b)));
                double[] ba = bounds(a), bb = bounds(b);
                if (ba[0] > b.hi) return a;
                if (ba[1] <= b.hi && bb[0] > ba[1]) return b;
                return null;
            }
            default:
                throw new AssertionError(p.op);
        }
    }

    // <editor-fold desc="Interval analysis">

    private static final double[] UNBOUNDED = { Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };

    private static boolean isFinite(double[] b) {
        return Double.isFinite(b[0]) && Double.isFinite(b[1]);
    }

    /// Conservative `[min, max]` of the values `function` can produce. Every interval is padded slightly so that
    /// floating point rounding during evaluation can't escape it.
    double[] bounds(DensityNode function) {
        double[] b = boundsCache.get(function);
        if (b == null) {
            b = pad(computeBounds(function));
            boundsCache.put(function, b);
        }
        return b;
    }

    private static double[] pad(double[] b) {
        if (Double.isNaN(b[0]) || Double.isNaN(b[1])) return UNBOUNDED;
        double lo = b[0], hi = b[1];
        if (lo == hi) return new double[] { lo, hi };
        return new double[] { lo - 1e-9 * (1.0 + Math.abs(lo)), hi + 1e-9 * (1.0 + Math.abs(hi)) };
    }

    private static double[] of(double lo, double hi) {
        return new double[] { lo, hi };
    }

    private static double[] mul(double[] a, double[] b) {
        double p1 = a[0] * b[0], p2 = a[0] * b[1], p3 = a[1] * b[0], p4 = a[1] * b[1];
        return of(Math.min(Math.min(p1, p2), Math.min(p3, p4)), Math.max(Math.max(p1, p2), Math.max(p3, p4)));
    }

    private double[] computeBounds(DensityNode f) {
        if (f instanceof ConstantNode c) return of(c.value, c.value);
        if (f instanceof PlaceholderNode p) return of(p.value, p.value);
        if (f instanceof GradientNode g) return of(Math.min(g.fromValue, g.toValue), Math.max(g.fromValue, g.toValue));
        // |ImprovedNoise| <= 2, which is what NormalNoise#maxValue assumes; likewise for the shift noises.
        if (f instanceof NoiseNode noise) {
            double n = noise.kind == NoiseNode.Kind.NOISE ? noise.noise.maxValue() : 4.0 * noise.noise.maxValue();
            return of(-n, n);
        }
        if (f instanceof ShiftedNoiseNode s) return of(-s.noise.maxValue(), s.noise.maxValue());
        if (f instanceof WeirdScaledNode w) return of(0.0, w.rarity.maxRarity * w.noise.maxValue());
        if (f instanceof EndIslands) return of(-0.84375, 0.5625);
        if (f instanceof BlendDensityNode bd) return bounds(bd.input);
        if (f instanceof MarkerNode m) return bounds(m.argument);

        if (f instanceof ClampNode c) {
            double[] in = bounds(c.input);
            return of(DensityMath.clamp(in[0], c.min, c.max), DensityMath.clamp(in[1], c.min, c.max));
        }

        if (f instanceof TransformNode t) {
            double[] in = bounds(t.input);
            double lo = in[0], hi = in[1];
            switch (t.kind) {
                case ABS: return lo >= 0 ? of(lo, hi) : hi <= 0 ? of(-hi, -lo) : of(0, Math.max(-lo, hi));
                case SQUARE: return lo >= 0 ? of(lo * lo, hi * hi) : hi <= 0 ? of(hi * hi, lo * lo) : of(0, Math.max(lo * lo, hi * hi));
                case INVERT: return lo > 0 || hi < 0 ? of(1.0 / hi, 1.0 / lo) : UNBOUNDED;
                // The rest are monotonically non-decreasing.
                default: return of(t.kind.apply(lo), t.kind.apply(hi));
            }
        }

        if (f instanceof AffineNode a) {
            double[] in = bounds(a.input);
            return a.multiply ? mul(in, of(a.c, a.c)) : of(in[0] + a.c, in[1] + a.c);
        }

        if (f instanceof PairNode p) {
            double[] a = bounds(p.a), b = bounds(p.b);
            switch (p.op) {
                case ADD: return of(a[0] + b[0], a[1] + b[1]);
                // a == 0 short circuits to 0, which the product interval already includes when a can be 0.
                case MUL: return mul(a, b);
                // Either a (skipped, then a < b.lo) or min(a, b).
                case MIN: return of(Math.min(a[0], b[0]), Math.min(a[1], Math.max(b[1], p.b.lo)));
                default: return of(Math.max(a[0], Math.min(b[0], p.b.hi)), Math.max(a[1], b[1]));
            }
        }

        if (f instanceof RangeChoiceNode rc) {
            double[] a = bounds(rc.whenIn), b = bounds(rc.whenOut);
            return of(Math.min(a[0], b[0]), Math.max(a[1], b[1]));
        }

        if (f instanceof FindTopSurfaceNode fts) {
            double[] upper = bounds(fts.upperBound);
            return of(fts.lowerBound, Math.max(fts.lowerBound, upper[1]));
        }

        if (f instanceof SplineNode s) return splineBounds(s.spline);

        // OldBlendedNoise and anything unknown.
        return UNBOUNDED;
    }

    /// Re-runs the spline bound estimate (which is sound given sound inputs: the cubic term is `t(1-t) * lerp(t, a, b)`
    /// with `t(1-t) <= 1/4`) using our coordinate bounds, in double precision.
    private double[] splineBounds(Spline spline) {
        if (spline instanceof Spline.Fixed c) return of(c.value, c.value);

        Spline.Curve m = (Spline.Curve) spline;
        float[] locations = m.locations;
        float[] derivatives = m.derivatives;
        int lastIndex = locations.length - 1;

        double[][] values = new double[m.size()][];
        for (int i = 0; i < values.length; i++) values[i] = pad(splineBounds(m.value(i)));

        double[] coord = bounds(m.coordinate);
        double minValue = Double.POSITIVE_INFINITY, maxValue = Double.NEGATIVE_INFINITY;

        if (coord[0] < locations[0]) {
            if (derivatives[0] != 0 && !Double.isFinite(coord[0])) return UNBOUNDED;
            double e1 = values[0][0] + derivatives[0] * (coord[0] - locations[0]);
            double e2 = values[0][1] + derivatives[0] * (coord[0] - locations[0]);
            minValue = Math.min(minValue, Math.min(e1, e2));
            maxValue = Math.max(maxValue, Math.max(e1, e2));
        }

        if (coord[1] > locations[lastIndex]) {
            if (derivatives[lastIndex] != 0 && !Double.isFinite(coord[1])) return UNBOUNDED;
            double e1 = values[lastIndex][0] + derivatives[lastIndex] * (coord[1] - locations[lastIndex]);
            double e2 = values[lastIndex][1] + derivatives[lastIndex] * (coord[1] - locations[lastIndex]);
            minValue = Math.min(minValue, Math.min(e1, e2));
            maxValue = Math.max(maxValue, Math.max(e1, e2));
        }

        for (double[] v : values) {
            minValue = Math.min(minValue, v[0]);
            maxValue = Math.max(maxValue, v[1]);
        }

        for (int i = 0; i < lastIndex; ++i) {
            double xDiff = (double) locations[i + 1] - locations[i];
            double min1 = values[i][0], max1 = values[i][1];
            double min2 = values[i + 1][0], max2 = values[i + 1][1];
            double d1 = derivatives[i], d2 = derivatives[i + 1];
            // a = d1 * xDiff - (y2 - y1), b = -d2 * xDiff + (y2 - y1)
            double minA = d1 * xDiff - max2 + min1, maxA = d1 * xDiff - min2 + max1;
            double minB = -d2 * xDiff + min2 - max1, maxB = -d2 * xDiff + max2 - min1;
            double minLerp2 = Math.min(Math.min(minA, minB), 0), maxLerp2 = Math.max(Math.max(maxA, maxB), 0);
            minValue = Math.min(minValue, Math.min(min1, min2) + 0.25 * minLerp2);
            maxValue = Math.max(maxValue, Math.max(max1, max2) + 0.25 * maxLerp2);
        }

        // Evaluation happens in float; leave room for its rounding.
        double slack = 1e-5 * (1 + Math.max(Math.abs(minValue), Math.abs(maxValue)));
        return of(minValue - slack, maxValue + slack);
    }

    // </editor-fold>
}
