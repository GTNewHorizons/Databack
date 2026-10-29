package databack.common.worldgen.orevein;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.rng.RandomFactory;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.surface.SurfaceSystem;

/// Places ore veins in the blocks the terrain and aquifer leave as the default block (docs/worldgen/ore-veins.md).
/// Thread-safe: each call has its own state.
@ThreadSafe
public final class OreVeinSystem {

    /// The vein kinds and their bands (section 2).
    enum Kind {

        COPPER(0, 50, VeinBlock.GRANITE, VeinBlock.COPPER_ORE, VeinBlock.RAW_COPPER_BLOCK),
        IRON(-60, -8, VeinBlock.TUFF, VeinBlock.DEEPSLATE_IRON_ORE, VeinBlock.RAW_IRON_BLOCK);

        final int bottom, top;
        final VeinBlock filler, ore, raw;

        Kind(int bottom, int top, VeinBlock filler, VeinBlock ore, VeinBlock raw) {
            this.bottom = bottom;
            this.top = top;
            this.filler = filler;
            this.ore = ore;
            this.raw = raw;
        }

        static Kind of(double toggle) {
            return toggle > 0 ? COPPER : IRON;
        }
    }

    /// The lowest and highest heights any vein can occupy.
    public static final int MIN_Y = Kind.IRON.bottom, MAX_Y = Kind.COPPER.top;

    private static final double TOGGLE_THRESHOLD = 0.4;
    private static final int FADE_DISTANCE = 20;
    private static final double FADE_DEPTH = 0.2;
    private static final float VEIN_MATERIAL_CHANCE = 0.7f;
    private static final double GAP_THRESHOLD = -0.3;
    private static final float RAW_CHANCE = 0.02f;

    @NotNull
    private final RandomFactory random;

    private OreVeinSystem(@NotNull RandomFactory random) {
        this.random = random;
    }

    /// `random` is the world's positional random factory. **Approximation:** vanilla's rolls are not reproduced, so a
    /// stream of ours is used (section 3).
    @NotNull
    @Contract("_ -> new")
    public static OreVeinSystem create(@NotNull RandomFactory random) {
        return new OreVeinSystem(random.fromHashOf("minecraft:ore").forkFactory());
    }

    /// The vein block of each default block of chunk `(chunkX, chunkZ)`, or null where it stays. `kinds` covers `layers`
    /// layers from `bottomY` ([SurfaceSystem]'s kinds; [SurfaceSystem#SOLID] is the default block); the result uses the
    /// same indices. Blocks `fields` doesn't cover are left alone.
    @NotNull
    public VeinBlock[] place(
        int chunkX, int chunkZ, byte @NotNull [] kinds, int bottomY, int layers, @NotNull VeinFields fields
    ) {
        VeinBlock[] result = new VeinBlock[kinds.length];

        for (int layer = 0; layer < layers; layer++) {
            int y = bottomY + layer;
            if (y < MIN_Y || y > MAX_Y || !fields.covers(y)) continue;

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = (layer * 16 + z) * 16 + x;
                    if (kinds[index] != SurfaceSystem.SOLID) continue;

                    int field = fields.index(x, y, z);
                    result[index] = at(chunkX * 16 + x, y, chunkZ * 16 + z, fields.toggle[field],
                        fields.ridged[field], fields.gap[field]);
                }
            }
        }

        return result;
    }

    /// The vein block at a default block with the given field values, or null if it stays (section 3).
    @Nullable
    public VeinBlock at(int x, int y, int z, double toggle, double ridged, double gap) {
        Kind kind = Kind.of(toggle);
        if (!inVein(kind, y, toggle, ridged)) return null;

        RandomSource rolls = random.at(x, y, z);
        if (rolls.nextFloat() >= VEIN_MATERIAL_CHANCE) return null;

        if (rolls.nextFloat() < oreChance(toggle) && gap >= GAP_THRESHOLD) {
            return rolls.nextFloat() < RAW_CHANCE ? kind.raw : kind.ore;
        }

        return kind.filler;
    }

    /// The deterministic part of placement: whether a block lies in a vein of `kind` (section 3).
    @Contract(pure = true)
    static boolean inVein(@NotNull Kind kind, int y, double toggle, double ridged) {
        if (y < kind.bottom || y > kind.top) return false;

        int distance = Math.min(y - kind.bottom, kind.top - y);
        double fade = -FADE_DEPTH + FADE_DEPTH * Math.min(distance, FADE_DISTANCE) / FADE_DISTANCE;
        return Math.abs(toggle) + fade > TOGGLE_THRESHOLD && ridged < 0;
    }

    /// 0.1 at `|toggle| = 0.4`, rising linearly to 0.3 at 0.6 and above (section 3).
    @Contract(pure = true)
    static double oreChance(double toggle) {
        double progress = (Math.abs(toggle) - 0.4) / 0.2;
        return 0.1 + 0.2 * Math.max(0, Math.min(1, progress));
    }
}
