package databack.common.worldgen.feature;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.feature.standin.FtChunkGenerator;
import databack.common.worldgen.feature.standin.FtLevel;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.vanilla.standin.VnBlockPos;

/// Limits a decoration to the features positioned in a band of layers, so a Cubic Chunks world can decorate a column
/// a cube at a time.
///
/// Vanilla's placed features hand each position they find to their configured feature; that call comes here
/// ([FeatureMappings]). Within [#within], a position outside the band is skipped. Features placed by other features
/// (a patch's plants) always go ahead: their placer was in the band.
///
/// Each position's feature gets its own random, forked whether or not it's placed, so the positions a placed
/// feature finds are the same whichever band is decorated. Outside [#within], features are placed as vanilla does.
public final class FeatureBand {

    private static final ThreadLocal<FeatureBand> CURRENT = new ThreadLocal<>();

    private final int minY, maxY;
    /// How many configured features are being placed on this thread.
    private int depth;

    private FeatureBand(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
    }

    /// Runs `decoration` on this thread, placing only the features positioned in layers `minY..maxY`. Not reentrant.
    public static void within(int minY, int maxY, @NotNull Runnable decoration) {
        if (CURRENT.get() != null) throw new IllegalStateException("Already decorating a band");

        CURRENT.set(new FeatureBand(minY, maxY));
        try {
            decoration.run();
        } finally {
            CURRENT.remove();
        }
    }

    /// Vanilla's `ConfiguredFeature.place`, redirected.
    @ApiStatus.Internal
    public static boolean place(
        @NotNull Object feature, @NotNull FtLevel level, @NotNull FtChunkGenerator generator,
        @NotNull RandomSource random, @NotNull VnBlockPos origin
    ) {
        VanillaConfiguredFeature configured = (VanillaConfiguredFeature) feature;

        FeatureBand band = CURRENT.get();
        if (band == null || band.depth > 0) return configured.place(level, generator, random, origin);

        RandomSource own = random.fork();
        if (!band.contains(origin)) return false;

        band.depth++;
        try {
            return configured.place(level, generator, own, origin);
        } finally {
            band.depth--;
        }
    }

    private boolean contains(VnBlockPos pos) {
        return pos.getY() >= minY && pos.getY() <= maxY;
    }
}
