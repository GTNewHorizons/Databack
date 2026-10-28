package databack.common.worldgen.surface;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import databack.common.worldgen.density.SeededNoise;

/// The surface rule types (docs/worldgen/surface-rules.md section 2).
public final class SurfaceRules {

    private SurfaceRules() {}

    @NotNull
    public static SurfaceRule block(@NotNull ImmutableBlockMeta block) {
        return ctx -> block;
    }

    @NotNull
    public static SurfaceRule sequence(@NotNull List<SurfaceRule> rules) {
        SurfaceRule[] array = rules.toArray(new SurfaceRule[0]);
        return ctx -> {
            for (SurfaceRule rule : array) {
                ImmutableBlockMeta block = rule.apply(ctx);
                if (block != null) return block;
            }
            return null;
        };
    }

    @NotNull
    public static SurfaceRule condition(@NotNull SurfaceCondition condition, @NotNull SurfaceRule then) {
        return ctx -> condition.test(ctx) ? then.apply(ctx) : null;
    }

    /// `colours` holds a block per [BandlandsPattern] colour. The per-column shift comes from `offsetNoise`.
    @NotNull
    public static SurfaceRule bandlands(
        @NotNull BandlandsPattern pattern, @NotNull SeededNoise offsetNoise, @NotNull ImmutableBlockMeta[] colours
    ) {
        return ctx -> {
            int offset = (int) Math.round(offsetNoise.getValue(ctx.x(), 0, ctx.z()) * 4.0);
            return colours[pattern.colourAt(ctx.y(), offset)];
        };
    }
}
