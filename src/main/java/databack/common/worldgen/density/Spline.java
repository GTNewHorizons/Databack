package databack.common.worldgen.density;

import java.util.Arrays;
import java.util.List;

/// A `spline` value (spec 3.9): either a fixed number or a curve over a coordinate function. All arithmetic is
/// `float`.
///
/// Evaluation is split into [Curve#segment], [Curve#extend] and [Curve#cubic], so that the different evaluators (single
/// point, and the stateful chunk evaluator) share the arithmetic while each supplies coordinate values its own way.
public abstract class Spline {

    /// Bound estimate.
    public final float lo, hi;

    private Spline(float lo, float hi) {
        this.lo = lo;
        this.hi = hi;
    }

    /// The value at a single block position.
    public abstract float sample(int x, int y, int z);

    /// Appends every coordinate function, the curve's own first, then its points' in order.
    abstract void collectCoordinates(List<DensityNode> out);

    /// The same spline with its coordinates replaced, taking them from `source` in [#collectCoordinates] order.
    abstract Spline withCoordinates(DensityNode[] source, int[] next);

    abstract boolean sameShape(Spline other);

    abstract int shapeHash();

    public static Spline fixed(float value) {
        return new Fixed(value);
    }

    public static Spline curve(DensityNode coordinate, float[] locations, Spline[] values, float[] derivatives) {
        if (locations.length == 0 || locations.length != values.length || locations.length != derivatives.length) {
            throw new IllegalArgumentException("spline points need matching locations, values and derivatives");
        }
        return new Curve(coordinate, locations.clone(), values.clone(), derivatives.clone());
    }

    public static final class Fixed extends Spline {

        public final float value;

        Fixed(float value) {
            super(value, value);
            this.value = value;
        }

        @Override
        public float sample(int x, int y, int z) {
            return value;
        }

        @Override
        void collectCoordinates(List<DensityNode> out) {}

        @Override
        Spline withCoordinates(DensityNode[] source, int[] next) {
            return this;
        }

        @Override
        boolean sameShape(Spline other) {
            return other instanceof Fixed f && Float.floatToIntBits(f.value) == Float.floatToIntBits(value);
        }

        @Override
        int shapeHash() {
            return Float.hashCode(value);
        }
    }

    public static final class Curve extends Spline {

        public final DensityNode coordinate;
        final float[] locations, derivatives;
        final Spline[] values;

        Curve(DensityNode coordinate, float[] locations, Spline[] values, float[] derivatives) {
            super(estimateLo(coordinate, locations, values, derivatives), estimateHi(coordinate, locations, values, derivatives));
            this.coordinate = coordinate;
            this.locations = locations;
            this.values = values;
            this.derivatives = derivatives;
        }

        public int size() {
            return locations.length;
        }

        public Spline value(int i) {
            return values[i];
        }

        /// Index of the last point whose location is `<= t`, or -1. NaN sorts after every location, so it lands on
        /// the last point.
        public int segment(float t) {
            int lo = 0, hi = locations.length;
            // First index whose location is > t (for NaN, none is).
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (t < locations[mid]) hi = mid;
                else lo = mid + 1;
            }
            return lo - 1;
        }

        public int last() {
            return locations.length - 1;
        }

        /// Linear continuation past point `k`, given that point's value `v`.
        public float extend(int k, float t, float v) {
            float d = derivatives[k];
            return d == 0.0f ? v : v + d * (t - locations[k]);
        }

        /// The cubic between points `i` and `i + 1`, given their values.
        public float cubic(int i, float t0, float y1, float y2) {
            float x1 = locations[i], x2 = locations[i + 1];
            float t = (t0 - x1) / (x2 - x1);
            float d1 = derivatives[i], d2 = derivatives[i + 1];
            float a = d1 * (x2 - x1) - (y2 - y1);
            float b = -d2 * (x2 - x1) + (y2 - y1);
            return DensityMath.lerp(t, y1, y2) + t * (1.0f - t) * DensityMath.lerp(t, a, b);
        }

        @Override
        public float sample(int x, int y, int z) {
            float t = (float) coordinate.sample(x, y, z);
            int i = segment(t);
            if (i < 0) return extend(0, t, values[0].sample(x, y, z));
            if (i == last()) return extend(i, t, values[i].sample(x, y, z));
            float y1 = values[i].sample(x, y, z);
            float y2 = values[i + 1].sample(x, y, z);
            return cubic(i, t, y1, y2);
        }

        @Override
        void collectCoordinates(List<DensityNode> out) {
            out.add(coordinate);
            for (Spline v : values) v.collectCoordinates(out);
        }

        @Override
        Spline withCoordinates(DensityNode[] source, int[] next) {
            DensityNode c = source[next[0]++];
            Spline[] vs = new Spline[values.length];
            for (int i = 0; i < vs.length; i++) vs[i] = values[i].withCoordinates(source, next);
            return new Curve(c, locations, vs, derivatives);
        }

        @Override
        boolean sameShape(Spline other) {
            if (!(other instanceof Curve c)) return false;
            if (!Arrays.equals(locations, c.locations) || !Arrays.equals(derivatives, c.derivatives)) return false;
            for (int i = 0; i < values.length; i++) if (!values[i].sameShape(c.values[i])) return false;
            return true;
        }

        @Override
        int shapeHash() {
            int h = Arrays.hashCode(locations) * 31 + Arrays.hashCode(derivatives);
            for (Spline v : values) h = h * 31 + v.shapeHash();
            return h;
        }

        // <editor-fold desc="Bound estimate (spec 3.9)">

        private static float extendBound(float d, float location, float c, float v) {
            return d == 0.0f ? v : v + d * (c - location);
        }

        private static float[] estimate(DensityNode coordinate, float[] loc, Spline[] values, float[] der) {
            float cLo = (float) coordinate.lo, cHi = (float) coordinate.hi;
            int last = loc.length - 1;
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;

            if (cLo < loc[0]) {
                float e1 = extendBound(der[0], loc[0], cLo, values[0].lo);
                float e2 = extendBound(der[0], loc[0], cLo, values[0].hi);
                min = Math.min(min, Math.min(e1, e2));
                max = Math.max(max, Math.max(e1, e2));
            }
            if (cHi > loc[last]) {
                float e1 = extendBound(der[last], loc[last], cHi, values[last].lo);
                float e2 = extendBound(der[last], loc[last], cHi, values[last].hi);
                min = Math.min(min, Math.min(e1, e2));
                max = Math.max(max, Math.max(e1, e2));
            }
            for (Spline v : values) {
                min = Math.min(min, v.lo);
                max = Math.max(max, v.hi);
            }
            for (int k = 0; k < last; k++) {
                if (der[k] == 0.0f && der[k + 1] == 0.0f) continue;
                float dx = loc[k + 1] - loc[k];
                float p1 = der[k] * dx, p2 = der[k + 1] * dx;
                float lo1 = values[k].lo, hi1 = values[k].hi, lo2 = values[k + 1].lo, hi2 = values[k + 1].hi;
                float minA = p1 - hi2 + lo1, maxA = p1 - lo2 + hi1;
                float minB = -p2 + lo2 - hi1, maxB = -p2 + hi2 - lo1;
                min = Math.min(min, Math.min(lo1, lo2) + 0.25f * Math.min(minA, minB));
                max = Math.max(max, Math.max(hi1, hi2) + 0.25f * Math.max(maxA, maxB));
            }
            return new float[] { min, max };
        }

        private static float estimateLo(DensityNode coordinate, float[] loc, Spline[] values, float[] der) {
            return estimate(coordinate, loc, values, der)[0];
        }

        private static float estimateHi(DensityNode coordinate, float[] loc, Spline[] values, float[] der) {
            return estimate(coordinate, loc, values, der)[1];
        }

        // </editor-fold>
    }
}
