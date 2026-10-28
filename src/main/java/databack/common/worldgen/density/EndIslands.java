package databack.common.worldgen.density;

import databack.common.worldgen.noise.SimplexNoise;
import databack.common.worldgen.rng.StandardRandom;

/// `minecraft:end_islands`.
public final class EndIslands extends DensityNode {

    private final long seed;
    private final SimplexNoise islandNoise;

    public EndIslands(long seed) {
        this(seed, -0.84375, 0.5625);
    }

    private EndIslands(long seed, double lo, double hi) {
        super(lo, hi, Long.hashCode(seed));
        this.seed = seed;
        StandardRandom islandRandom = new StandardRandom(seed);
        islandRandom.consumeCount(17292);
        this.islandNoise = new SimplexNoise(islandRandom);
    }

    private static float getHeightValue(SimplexNoise islandNoise, int sectionX, int sectionZ) {
        int chunkX = sectionX / 2;
        int chunkZ = sectionZ / 2;
        int subSectionX = sectionX % 2;
        int subSectionZ = sectionZ % 2;
        float doffs = 100.0F - (float) Math.sqrt((float) (sectionX * sectionX + sectionZ * sectionZ)) * 8.0F;
        doffs = clampF(doffs, -100.0F, 80.0F);

        for (int xo = -12; xo <= 12; ++xo) {
            for (int zo = -12; zo <= 12; ++zo) {
                long totalChunkX = chunkX + xo;
                long totalChunkZ = chunkZ + zo;
                if (totalChunkX * totalChunkX + totalChunkZ * totalChunkZ > 4096L
                    && islandNoise.getValue((double) totalChunkX, (double) totalChunkZ) < (double) -0.9F) {
                    float islandSize = (Math.abs((float) totalChunkX) * 3439.0F
                        + Math.abs((float) totalChunkZ) * 147.0F) % 13.0F + 9.0F;
                    float xd = (float) (subSectionX - xo * 2);
                    float zd = (float) (subSectionZ - zo * 2);
                    float newDoffs = 100.0F - (float) Math.sqrt(xd * xd + zd * zd) * islandSize;
                    newDoffs = clampF(newDoffs, -100.0F, 80.0F);
                    doffs = Math.max(doffs, newDoffs);
                }
            }
        }

        return doffs;
    }

    private static float clampF(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    @Override
    public double sample(int blockX, int blockY, int blockZ) {
        return ((double) getHeightValue(islandNoise, blockX / 8, blockZ / 8) - 8.0) / 128.0;
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new EndIslands(seed, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode other) {
        return seed == ((EndIslands) other).seed;
    }
}