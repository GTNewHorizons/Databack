package databack.common.worldgen.density;

import org.jetbrains.annotations.NotNull;

/// Remembers its argument's recent values, for trees sampled point by point at nearby positions ([DensityMemo]).
/// Values are keyed on the column when the argument doesn't depend on y, else on the position, in a small
/// direct-mapped table.
///
/// Mutable: one per thread. Never equal to another node, so interning and optimizing leave it alone.
public final class MemoNode extends DensityNode {

    private static final int SLOTS = 64;

    @NotNull
    final DensityNode argument;
    /// Whether values are keyed on `(x, z)` only.
    final boolean column;

    private final long[] keys = new long[SLOTS];
    private final double[] values = new double[SLOTS];
    private final boolean[] filled = new boolean[SLOTS];

    MemoNode(@NotNull DensityNode argument, boolean column) {
        this(argument, column, argument.lo, argument.hi);
    }

    private MemoNode(@NotNull DensityNode argument, boolean column, double lo, double hi) {
        super(lo, hi, System.identityHashCode(new Object()), argument);
        this.argument = argument;
        this.column = column;
    }

    @Override
    public double sample(int x, int y, int z) {
        long key = column ? (long) x << 32 | z & 0xFFFFFFFFL
            : ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | y & 0xFFF;
        int slot = (int) (key * 0x9E3779B97F4A7C15L >>> 58);

        if (filled[slot] && keys[slot] == key) return values[slot];

        double value = argument.sample(x, y, z);
        keys[slot] = key;
        values[slot] = value;
        filled[slot] = true;
        return value;
    }

    @Override
    public DensityNode[] inputs() {
        return new DensityNode[] { argument };
    }

    @Override
    public DensityNode withInputs(DensityNode[] inputs) {
        return new MemoNode(inputs[0], column, lo, hi);
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new MemoNode(argument, column, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode other) {
        return this == other;
    }
}
