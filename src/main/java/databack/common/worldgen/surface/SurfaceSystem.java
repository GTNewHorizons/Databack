package databack.common.worldgen.surface;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.density.DensityNode;
import databack.common.worldgen.density.SeededNoise;

/// Applies a dimension's surface rule to chunks (docs/worldgen/surface-rules.md). Thread-safe: each call has its own
/// state.
@ThreadSafe
public final class SurfaceSystem {

    /// Block kinds: [#FLUID] is the default fluid; [#LAVA] is lava (both are fluid to the surface rules).
    public static final byte AIR = 0, FLUID = 1, SOLID = 2, LAVA = 3;

    @NotNull
    private final SurfaceRule rule;
    private final int minY;
    private final int height;
    private final long seed;
    @NotNull
    private final SeededNoise surfaceNoise;
    @NotNull
    private final SeededNoise secondaryNoise;
    @NotNull
    private final DensityNode preliminarySurface;

    /// `minY` and `height` are the dimension's (for anchors); `preliminarySurface` is the router's field.
    public SurfaceSystem(
        @NotNull SurfaceRule rule, int minY, int height, long seed, @NotNull SeededNoise surfaceNoise,
        @NotNull SeededNoise secondaryNoise, @NotNull DensityNode preliminarySurface
    ) {
        this.rule = rule;
        this.minY = minY;
        this.height = height;
        this.seed = seed;
        this.surfaceNoise = surfaceNoise;
        this.secondaryNoise = secondaryNoise;
        this.preliminarySurface = preliminarySurface;
    }

    /// Decides the solid blocks of chunk `(chunkX, chunkZ)`. `kinds` covers `layers` layers from `bottomY`, each entry
    /// [#AIR], [#FLUID], [#LAVA] or [#SOLID], at index `((y - bottomY) * 16 + z) * 16 + x`. Returns, at the same
    /// indices, the block the rule places at each solid position, or null where the default block stays.
    @NotNull
    public ImmutableBlockMeta[] apply(
        int chunkX, int chunkZ, byte @NotNull [] kinds, int bottomY, int layers, @NotNull BlockBiomes biomes
    ) {
        ImmutableBlockMeta[] result = new ImmutableBlockMeta[kinds.length];
        int[] heights = topHeights(kinds, bottomY, layers);
        SurfaceContext ctx = new SurfaceContext(minY, height, seed, biomes);
        int[] ceiling = new int[layers];
        int[] preliminary = preliminarySurfaceCells(chunkX, chunkZ);

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int bx = chunkX * 16 + x, bz = chunkZ * 16 + z;
                ctx.startColumn(bx, bz, surfaceDepth(bx, bz), secondaryNoise.getValue(bx, 0, bz),
                    preliminary[(z >> 2) * 4 + (x >> 2)], steep(heights, x, z));

                ceilingDepths(kinds, x, z, layers, ceiling);
                walkColumn(ctx, kinds, x, z, bottomY, layers, ceiling, result);
            }
        }

        return result;
    }

    /// The block the rule places at one exposed block, as if it were the top of its column (floor and ceiling depth 1),
    /// or null for none. Carvers re-run the rule on the dirt they expose under grass. `underFluid` puts a fluid right
    /// above it. **Approximation:** steepness is not known for a single block and is taken as false.
    @Nullable
    public ImmutableBlockMeta topMaterial(int x, int y, int z, @NotNull BlockBiomes biomes, boolean underFluid) {
        SurfaceContext ctx = new SurfaceContext(minY, height, seed, biomes);
        ctx.startColumn(x, z, surfaceDepth(x, z), secondaryNoise.getValue(x, 0, z), preliminarySurface(x & ~3, z & ~3),
            false);
        if (underFluid) ctx.fluidLevel = y + 1;
        ctx.startBlock(y, 1, 1);
        return rule.apply(ctx);
    }

    /// Top-down: floor depth (section 1.1), the fluid level, and the rule for each solid block.
    private void walkColumn(
        SurfaceContext ctx, byte[] kinds, int x, int z, int bottomY, int layers, int[] ceiling,
        ImmutableBlockMeta[] result
    ) {
        int depth = 0;
        boolean buried = false;
        boolean previousSolid = false;

        for (int layer = layers - 1; layer >= 0; layer--) {
            int index = (layer * 16 + z) * 16 + x;
            byte kind = kinds[index];

            if (kind != SOLID) {
                boolean fluid = kind == FLUID || kind == LAVA;
                if (fluid && ctx.fluidLevel == SurfaceContext.NO_FLUID) ctx.fluidLevel = bottomY + layer + 1;
                // Fluid under solid ground (a flooded cave), and fluid below it, neither resets nor counts.
                buried = fluid && (previousSolid || buried);
                if (!buried) depth = 0;
                previousSolid = false;
                continue;
            }

            previousSolid = true;
            buried = false;
            depth++;

            ctx.startBlock(bottomY + layer, depth, ceiling[layer]);
            result[index] = rule.apply(ctx);
        }
    }

    /// Bottom-up: ceiling depth of each solid block (1 right above an open block), 0 where open.
    private static void ceilingDepths(byte[] kinds, int x, int z, int layers, int[] ceiling) {
        int depth = 0;
        for (int layer = 0; layer < layers; layer++) {
            depth = kinds[(layer * 16 + z) * 16 + x] == SOLID ? depth + 1 : 0;
            ceiling[layer] = depth;
        }
    }

    /// The preliminary surface level of each 4x4-column cell of the chunk (index `(z / 4) * 4 + x / 4`), sampled at the
    /// cell's corner: vanilla's is constant per cell (section 1.2).
    private int[] preliminarySurfaceCells(int chunkX, int chunkZ) {
        int[] cells = new int[16];
        for (int cz = 0; cz < 4; cz++) {
            for (int cx = 0; cx < 4; cx++) {
                int bx = chunkX * 16 + cx * 4, bz = chunkZ * 16 + cz * 4;
                cells[cz * 4 + cx] = preliminarySurface(bx, bz);
            }
        }
        return cells;
    }

    /// The router's preliminary surface level at a column, as vanilla reports it.
    @Contract(pure = true)
    int preliminarySurface(int x, int z) {
        return (int) Math.floor(preliminarySurface.sample(x, 0, z));
    }

    /// **Approximation:** the random part of surface depth is Databack's own (section 1.2).
    @Contract(pure = true)
    private int surfaceDepth(int x, int z) {
        double random = SurfaceRandom.at(seed, "databack:surface_depth", x, 0, z);
        return (int) Math.floor(surfaceNoise.getValue(x, 0, z) * 2.75 + 3.0 + random * 0.25);
    }

    /// The top non-air layer of each column (index `z * 16 + x`), or -1.
    private static int[] topHeights(byte[] kinds, int bottomY, int layers) {
        int[] heights = new int[256];
        for (int column = 0; column < 256; column++) {
            heights[column] = -1;
            for (int layer = layers - 1; layer >= 0; layer--) {
                if (kinds[layer * 256 + column] != AIR) {
                    heights[column] = layer;
                    break;
                }
            }
        }
        return heights;
    }

    /// Neighbours outside the chunk are replaced by the column itself (section 3, `steep`).
    private static boolean steep(int[] heights, int x, int z) {
        int north = heights[Math.max(z - 1, 0) * 16 + x], south = heights[Math.min(z + 1, 15) * 16 + x];
        int west = heights[z * 16 + Math.max(x - 1, 0)], east = heights[z * 16 + Math.min(x + 1, 15)];
        return south - north >= 4 || west - east >= 4;
    }
}
