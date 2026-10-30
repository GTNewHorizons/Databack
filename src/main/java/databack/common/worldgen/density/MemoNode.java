package databack.common.worldgen.density;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;

/// Remembers its argument's recent values, for trees sampled point by point at nearby positions ([DensityMemo]).
/// Values are keyed on the column when the argument doesn't depend on y, else on the position, in a direct-mapped
/// table. Column slots come from the low bits of the quart coordinates, so the columns of any 16×16 quart window
/// (biome lookups sample quart corners) never evict each other; other positions still work, with more misses.
///
/// Mutable: one per thread. Never equal to another node, so interning and optimizing leave it alone.
public final class MemoNode extends DensityNode {

    /// One per column of a 16×16 quart window, or per hashed position.
    private static final int COLUMN_SLOTS = 256;
    private static final int POINT_SLOTS = 64;

    @NotNull
    final DensityNode argument;
    /// Whether values are keyed on `(x, z)` only.
    final boolean column;

    private final long[] keys;
    private final double[] values;
    private final boolean[] filled;

    MemoNode(@NotNull DensityNode argument, boolean column) {
        this(argument, column, argument.lo, argument.hi);
    }

    private MemoNode(@NotNull DensityNode argument, boolean column, double lo, double hi) {
        super(lo, hi, System.identityHashCode(new Object()), argument);
        this.argument = argument;
        this.column = column;

        int slots = column ? COLUMN_SLOTS : POINT_SLOTS;
        this.keys = new long[slots];
        this.values = new double[slots];
        this.filled = new boolean[slots];
    }

    @Override
    public double sample(int x, int y, int z) {
        long key;
        int slot;
        if (column) {
            key = (long) x << 32 | z & 0xFFFFFFFFL;
            slot = (x >> 2 & 15) << 4 | z >> 2 & 15;
        } else {
            key = CoordinatePacker.pack(x, y, z);
            slot = (int) (key * 0x9E3779B97F4A7C15L >>> 58);
        }

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
