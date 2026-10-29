package databack.common.worldgen.orevein;

import org.jetbrains.annotations.NotNull;

/// The vein router fields of one chunk over `layers` layers from `minY`, each at index
/// `((y - minY) * 16 + z) * 16 + x`. `vein_gap` is read through [GapField], since placement needs it at few blocks.
public final class VeinFields {

    /// `vein_gap` at a block of the chunk (chunk-local `x` and `z`).
    @FunctionalInterface
    public interface GapField {

        double at(int x, int y, int z);
    }

    public final int minY;
    public final int layers;
    public final double @NotNull [] toggle;
    public final double @NotNull [] ridged;
    @NotNull
    private final GapField gap;

    /// Assumption: each array holds `layers * 256` values.
    public VeinFields(
        int minY, int layers, double @NotNull [] toggle, double @NotNull [] ridged, double @NotNull [] gap
    ) {
        this(minY, layers, toggle, ridged, (x, y, z) -> gap[((y - minY) * 16 + z) * 16 + x]);
    }

    /// Assumption: each array holds `layers * 256` values.
    public VeinFields(
        int minY, int layers, double @NotNull [] toggle, double @NotNull [] ridged, @NotNull GapField gap
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

    /// `vein_gap` at a block; `x` and `z` are chunk-local. Assumption: [#covers] `y`.
    public double gap(int x, int y, int z) {
        return gap.at(x, y, z);
    }
}
