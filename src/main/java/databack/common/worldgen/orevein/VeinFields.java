package databack.common.worldgen.orevein;

import org.jetbrains.annotations.NotNull;

/// The vein router fields of one chunk over `layers` layers from `minY`, each at index
/// `((y - minY) * 16 + z) * 16 + x`.
public final class VeinFields {

    public final int minY;
    public final int layers;
    public final double @NotNull [] toggle;
    public final double @NotNull [] ridged;
    public final double @NotNull [] gap;

    /// Assumption: each array holds `layers * 256` values.
    public VeinFields(
        int minY, int layers, double @NotNull [] toggle, double @NotNull [] ridged, double @NotNull [] gap
    ) {
        this.minY = minY;
        this.layers = layers;
        this.toggle = toggle;
        this.ridged = ridged;
        this.gap = gap;
    }

    /// Whether the fields cover height `y`.
    public boolean covers(int y) {
        return y >= minY && y < minY + layers;
    }

    /// The index of a block in the arrays; `x` and `z` are chunk-local. Assumption: [#covers] `y`.
    public int index(int x, int y, int z) {
        return ((y - minY) * 16 + z) * 16 + x;
    }
}
