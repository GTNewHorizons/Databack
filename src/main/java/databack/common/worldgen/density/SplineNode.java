package databack.common.worldgen.density;

import java.util.ArrayList;
import java.util.List;

/// `spline` as a density function: the spline's `float` value widened to `double`. Its inputs are every coordinate
/// function of the spline, outermost first.
public final class SplineNode extends DensityNode {

    public final Spline spline;
    private final DensityNode[] coordinates;

    SplineNode(Spline spline) {
        this(spline, spline.lo, spline.hi);
    }

    private SplineNode(Spline spline, double lo, double hi) {
        this(spline, coordinatesOf(spline), lo, hi);
    }

    private SplineNode(Spline spline, DensityNode[] coordinates, double lo, double hi) {
        super(lo, hi, spline.shapeHash(), coordinates);
        this.spline = spline;
        this.coordinates = coordinates;
    }

    private static DensityNode[] coordinatesOf(Spline spline) {
        List<DensityNode> list = new ArrayList<>();
        spline.collectCoordinates(list);
        return list.toArray(NO_INPUTS);
    }

    @Override
    public double sample(int x, int y, int z) {
        return spline.sample(x, y, z);
    }

    @Override
    public DensityNode[] inputs() {
        return coordinates.clone();
    }

    @Override
    public DensityNode withInputs(DensityNode[] inputs) {
        return new SplineNode(spline.withCoordinates(inputs, new int[1]), lo, hi);
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new SplineNode(spline, coordinates, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode other) {
        return spline.sameShape(((SplineNode) other).spline);
    }
}
