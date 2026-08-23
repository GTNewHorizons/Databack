package databack.common.worldgen.dag;

import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.Cache2DFunc;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.FindTopSurfaceFunc;

/// All density functions must produce a float for a given coordinate, but not all functions produce a 16x16x16 buffer.
/// Some, like [Cache2DFunc], produce a flat buffer. The buffer can be interpreted as a 16x16x16 buffer, but
/// [Cache2DFunc]'s output is only a 16x16 flat buffer (one float per block column).
public enum CellSize {
    /// One cell per block.
    BLOCKS(4096),
    /// One cell = (x/4, y/4, z/4). feeds into an interpolation kernel.
    BLOCKS_REDUCED(125),
    /// One cell per block column. Y invariant.
    COLUMNS(256),
    /// One cell per (x/4, z/4) block column. Y invariant.
    COLUMNS_REDUCED(16);

    public final int elementCount;

    CellSize(int elementCount) {
        this.elementCount = elementCount;
    }

    // Cell value does not change depending on the block's Y location.
    public boolean isYInvariant() {
        return this == COLUMNS || this == COLUMNS_REDUCED;
    }

    /// Grid size is divided by 4.
    public boolean isReduced() {
        return this == BLOCKS_REDUCED || this == COLUMNS_REDUCED;
    }

    public CellSize max(CellSize other) {
        if (this == other) return this;

        if (this.isYInvariant() && other.isYInvariant()) {
            // Both are flat, but one is reduced and one isn't
            return COLUMNS;
        } else {
            // Both are y variant, but one is reduced and one isn't
            return BLOCKS;
        }
    }
}
