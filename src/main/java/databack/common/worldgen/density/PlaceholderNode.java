package databack.common.worldgen.density;

/// A blending or structure input that always has the same value, because Databack has no chunk blending and no
/// structure terrain adaptation (spec 3.2): `blend_alpha` (1), `blend_offset` (0) and the beardifier (0).
///
/// Unlike [ConstantNode], these are not constants for `add`/`mul`: `mul(blend_offset, x)` is the general form, which
/// returns 0 without evaluating `x` (verified by case `blend_offset_constant`).
public final class PlaceholderNode extends DensityNode {

    public static final PlaceholderNode BLEND_ALPHA = new PlaceholderNode("blend_alpha", 1.0);
    public static final PlaceholderNode BLEND_OFFSET = new PlaceholderNode("blend_offset", 0.0);
    public static final PlaceholderNode BEARDIFIER = new PlaceholderNode("beardifier", 0.0);

    public final String name;
    public final double value;

    private PlaceholderNode(String name, double value) {
        this(name, value, value, value);
    }

    private PlaceholderNode(String name, double value, double lo, double hi) {
        super(lo, hi, name.hashCode());
        this.name = name;
        this.value = value;
    }

    @Override
    public double sample(int x, int y, int z) {
        return value;
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new PlaceholderNode(name, value, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode other) {
        return name.equals(((PlaceholderNode) other).name);
    }

    @Override
    public String toString() {
        return name;
    }
}
