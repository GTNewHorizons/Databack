package databack.common.worldgen.dag;

public enum BarrierKind {
    /** CacheOnce: expensive PER_VOXEL sub-expression — materialize to avoid recomputation. */
    CACHE_ONCE,

    /** Cache2D / FlatCache: Flat (Y-independent) sub-expression — materialize to PER_COLUMN buffer. */
    FLAT_CACHE,

    /** FindTopSurface: scans Y per column to find first solid point — PER_COLUMN output. */
    COLUMN_REDUCE,

    /**
     * Interpolated, argument sampling pass: evaluates argument at 5×5×5 corners — PER_CORNER kernel.
     * Always paired with {@link #INTERPOLATED_INTERP}; never appears standalone.
     */
    INTERPOLATED_SAMPLE,

    /**
     * Interpolated, interpolation pass: trilinearly interpolates from 125 corners to 4096 voxels.
     * Reads from an {@link #INTERPOLATED_SAMPLE} buffer — PER_VOXEL output.
     * This is the node visible to consumers of an Interpolated function.
     */
    INTERPOLATED_INTERP,
}
