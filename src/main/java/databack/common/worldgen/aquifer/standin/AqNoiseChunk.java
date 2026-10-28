package databack.common.worldgen.aquifer.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the chunk noise state's preliminary surface queries.
@ApiStatus.Internal
public interface AqNoiseChunk {

    /// The preliminary surface level of the 4x4-column cell containing the column.
    int preliminarySurfaceLevel(int x, int z);

    /// The highest [#preliminarySurfaceLevel] sampled at `(x1 + 4i, z1 + 4j)` within the rectangle (behaviour
    /// observed black-box).
    default int maxPreliminarySurfaceLevel(int x1, int z1, int x2, int z2) {
        int max = Integer.MIN_VALUE;
        for (int x = x1; x <= x2; x += 4) {
            for (int z = z1; z <= z2; z += 4) max = Math.max(max, preliminarySurfaceLevel(x, z));
        }
        return max;
    }
}
