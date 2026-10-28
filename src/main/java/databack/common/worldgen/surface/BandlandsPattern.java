package databack.common.worldgen.surface;

import org.jetbrains.annotations.NotNull;

/// The badlands terracotta stripes (docs/worldgen/surface-rules.md section 2.1). **Approximation:** Databack's own
/// seeded pattern with vanilla's character (plain terracotta, mostly 1-block coloured stripes, occasional thicker
/// runs), indexed by height and shifted per column.
public final class BandlandsPattern {

    /// Colours by index into the pattern; [#PLAIN] is plain terracotta.
    public static final int PLAIN = 0, ORANGE = 1, BROWN = 2, YELLOW = 3, LIGHT_GRAY = 4, WHITE = 5, RED = 6;

    private static final int LENGTH = 192;

    private final byte[] bands = new byte[LENGTH];

    public BandlandsPattern(long seed) {
        // Counts tuned so each colour's share is close to vanilla's (about 43% plain; orange 15%, brown 12%,
        // light grey 9%, yellow 8%, white 7%, red 5%).
        addStripes(seed, "orange", ORANGE, 36, 1);
        addStripes(seed, "brown", BROWN, 15, 3);
        addStripes(seed, "yellow", YELLOW, 13, 2);
        addStripes(seed, "light_gray", LIGHT_GRAY, 15, 2);
        addStripes(seed, "white", WHITE, 12, 2);
        addStripes(seed, "red", RED, 7, 3);
    }

    /// The colour at height `y` once shifted by `offset`.
    public int colourAt(int y, int offset) {
        return bands[Math.floorMod(y + offset, LENGTH)];
    }

    /// `count` stripes of `colour`, each 1 to `maxThickness` blocks.
    private void addStripes(long seed, @NotNull String name, int colour, int count, int maxThickness) {
        for (int i = 0; i < count; i++) {
            int start = (int) (SurfaceRandom.at(seed, "databack:bands_" + name, i, 0, 0) * LENGTH);
            int thickness = 1 + (int) (SurfaceRandom.at(seed, "databack:bands_" + name, i, 1, 0) * maxThickness);
            for (int t = 0; t < thickness; t++) bands[(start + t) % LENGTH] = (byte) colour;
        }
    }
}
