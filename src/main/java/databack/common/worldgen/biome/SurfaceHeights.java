package databack.common.worldgen.biome;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/// The top solid block of each column of a chunk, from its terrain density.
public final class SurfaceHeights {

    /// Marks a column with no solid block in the sampled range.
    public static final int NONE = Integer.MIN_VALUE;

    private SurfaceHeights() {}

    /// For `density` laid out as [databack.common.worldgen.density.ChunkDensitySampler] writes it (`height` layers from
    /// `minY`, index `((y - minY) * 16 + z) * 16 + x`, solid where positive), the highest solid `y` of each column at
    /// index `z * 16 + x`, or [#NONE].
    @NotNull
    @Contract("_, _, _ -> new")
    public static int[] fromDensity(double @NotNull [] density, int minY, int height) {
        int[] heights = new int[256];

        for (int column = 0; column < 256; column++) {
            heights[column] = NONE;
            for (int layer = height - 1; layer >= 0; layer--) {
                if (density[layer * 256 + column] > 0) {
                    heights[column] = minY + layer;
                    break;
                }
            }
        }

        return heights;
    }
}
