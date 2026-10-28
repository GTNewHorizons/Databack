package databack.common.worldgen.density;

import databack.common.worldgen.noise.ImprovedNoise;
import databack.common.worldgen.noise.PerlinNoise;
import databack.common.worldgen.rng.RandomSource;

/// Runtime density function for `minecraft:old_blended_noise`, a port of vanilla's `BlendedNoise`.
public final class OldBlendedNoise extends DensityNode {

    private final PerlinNoise minLimitNoise;
    private final PerlinNoise maxLimitNoise;
    private final PerlinNoise mainNoise;
    private final double xzScale, yScale;
    private final double xzMultiplier;
    private final double yMultiplier;
    private final double xzFactor;
    private final double yFactor;
    private final double smearScaleMultiplier;
    private final double maxValue;

    // Octaves in loop order (getOctaveNoise(i)), so compute() doesn't index backwards through noiseLevels.
    private final ImprovedNoise[] mainOctaves = new ImprovedNoise[8];
    private final ImprovedNoise[] minOctaves = new ImprovedNoise[16];
    private final ImprovedNoise[] maxOctaves = new ImprovedNoise[16];

    /// @param terrainRandom vanilla uses `new LegacyRandomSource(seed)` for legacy-random settings, otherwise
    ///                      `positionalRandom.fromHashOf("minecraft:terrain")`. See [DensityBuildContext].
    public OldBlendedNoise(
        RandomSource terrainRandom, double xzScale, double yScale, double xzFactor, double yFactor,
        double smearScaleMultiplier
    ) {
        this(terrainRandom, xzScale, yScale, xzFactor, yFactor, smearScaleMultiplier, Double.NaN, Double.NaN);
    }

    /// With an explicit bound estimate; NaN bounds mean the default `[-maxValue, maxValue]`.
    private OldBlendedNoise(
        RandomSource terrainRandom, double xzScale, double yScale, double xzFactor, double yFactor,
        double smearScaleMultiplier, double lo, double hi
    ) {
        this(PerlinNoise.createLegacyForBlendedNoise(terrainRandom, -15, 16),
            PerlinNoise.createLegacyForBlendedNoise(terrainRandom, -15, 16),
            PerlinNoise.createLegacyForBlendedNoise(terrainRandom, -7, 8), xzScale, yScale, xzFactor, yFactor,
            smearScaleMultiplier, lo, hi);
    }

    private OldBlendedNoise(
        PerlinNoise minLimitNoise, PerlinNoise maxLimitNoise, PerlinNoise mainNoise, double xzScale, double yScale,
        double xzFactor, double yFactor, double smearScaleMultiplier, double lo, double hi
    ) {
        super(
            Double.isNaN(lo) ? -minLimitNoise.maxBrokenValue(684.412 * yScale) : lo,
            Double.isNaN(hi) ? minLimitNoise.maxBrokenValue(684.412 * yScale) : hi,
            (((System.identityHashCode(mainNoise) * 31 + Double.hashCode(xzScale)) * 31 + Double.hashCode(yScale)) * 31
                + Double.hashCode(xzFactor)) * 31 + Double.hashCode(yFactor) * 31 + Double.hashCode(smearScaleMultiplier));
        this.minLimitNoise = minLimitNoise;
        this.maxLimitNoise = maxLimitNoise;
        this.mainNoise = mainNoise;
        this.xzScale = xzScale;
        this.yScale = yScale;
        this.xzMultiplier = 684.412 * xzScale;
        this.yMultiplier = 684.412 * yScale;
        this.xzFactor = xzFactor;
        this.yFactor = yFactor;
        this.smearScaleMultiplier = smearScaleMultiplier;
        this.maxValue = minLimitNoise.maxBrokenValue(this.yMultiplier);

        for (int i = 0; i < 8; i++) mainOctaves[i] = mainNoise.getOctaveNoise(i);
        for (int i = 0; i < 16; i++) minOctaves[i] = minLimitNoise.getOctaveNoise(i);
        for (int i = 0; i < 16; i++) maxOctaves[i] = maxLimitNoise.getOctaveNoise(i);
    }

    @Override
    public double sample(int blockX, int blockY, int blockZ) {
        double limitX = (double) blockX * this.xzMultiplier;
        double limitY = (double) blockY * this.yMultiplier;
        double limitZ = (double) blockZ * this.xzMultiplier;
        double mainX = limitX / this.xzFactor;
        double mainY = limitY / this.yFactor;
        double mainZ = limitZ / this.xzFactor;
        double limitSmear = this.yMultiplier * this.smearScaleMultiplier;
        double mainSmear = limitSmear / this.yFactor;
        double blendMin = 0.0;
        double blendMax = 0.0;
        double mainNoiseValue = 0.0;
        double pow = 1.0;

        for (int i = 0; i < 8; ++i) {
            ImprovedNoise noise = mainOctaves[i];
            if (noise != null) {
                mainNoiseValue += noise.noise(
                    PerlinNoise.wrap(mainX * pow), PerlinNoise.wrap(mainY * pow), PerlinNoise.wrap(mainZ * pow),
                    mainSmear * pow, mainY * pow) / pow;
            }

            pow /= 2.0;
        }

        double factor = (mainNoiseValue / 10.0 + 1.0) / 2.0;
        boolean isMax = factor >= 1.0;
        boolean isMin = factor <= 0.0;
        pow = 1.0;

        for (int i = 0; i < 16; ++i) {
            double wx = PerlinNoise.wrap(limitX * pow);
            double wy = PerlinNoise.wrap(limitY * pow);
            double wz = PerlinNoise.wrap(limitZ * pow);
            double yScalePow = limitSmear * pow;
            if (!isMax) {
                ImprovedNoise minNoise = minOctaves[i];
                if (minNoise != null) {
                    blendMin += minNoise.noise(wx, wy, wz, yScalePow, limitY * pow) / pow;
                }
            }

            if (!isMin) {
                ImprovedNoise maxNoise = maxOctaves[i];
                if (maxNoise != null) {
                    blendMax += maxNoise.noise(wx, wy, wz, yScalePow, limitY * pow) / pow;
                }
            }

            pow /= 2.0;
        }

        return DensityMath.clampedLerp(factor, blendMin / 512.0, blendMax / 512.0) / 128.0;
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new OldBlendedNoise(
            minLimitNoise, maxLimitNoise, mainNoise, xzScale, yScale, xzFactor, yFactor, smearScaleMultiplier, lo, hi);
    }

    public PerlinNoise getMainNoise()     { return mainNoise; }
    public PerlinNoise getMinLimitNoise() { return minLimitNoise; }
    public PerlinNoise getMaxLimitNoise() { return maxLimitNoise; }

    @Override
    protected boolean sameParams(DensityNode o) {
        OldBlendedNoise other = (OldBlendedNoise) o;
        return mainNoise == other.mainNoise && Double.compare(xzScale, other.xzScale) == 0
            && Double.compare(yScale, other.yScale) == 0 && Double.compare(xzFactor, other.xzFactor) == 0
            && Double.compare(yFactor, other.yFactor) == 0
            && Double.compare(smearScaleMultiplier, other.smearScaleMultiplier) == 0;
    }
}
