package databack.common.dto.worldgen.density_function;

import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.OldBlendedNoiseFunc;
import databack.common.worldgen.noise.ImprovedNoise;
import databack.common.worldgen.noise.PerlinNoise;
import databack.common.worldgen.rng.RandomFactory;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.rng.StandardRandomFactory;

/**
 * Runtime density function for minecraft:old_blended_noise.
 *
 * Ported from net.minecraft.world.level.levelgen.synth.BlendedNoise.
 * Seeded and instantiated by {@link OldBlendedNoiseFunc}.
 */
public class OldBlendedNoise implements IDensityFunction {

    private final PerlinNoise minLimitNoise;
    private final PerlinNoise maxLimitNoise;
    private final PerlinNoise mainNoise;
    private final double xzMultiplier;
    private final double yMultiplier;
    private final double xzFactor;
    private final double yFactor;
    private final double smearScaleMultiplier;
    private final double maxValue;

    // Reused each compute() call; valid only until the next compute() call on this instance.
    private final DensityBuffer.CubeBuffer buffer = new DensityBuffer.CubeBuffer(null);

    public OldBlendedNoise(
        RandomFactory random, double xzScale, double yScale, double xzFactor, double yFactor,
        double smearScaleMultiplier
    ) {

        RandomSource rng;

        if (random instanceof StandardRandomFactory) {
            rng = random.newInstance();
        } else {
            rng = random.fromHashOf("minecraft:terrain");
        }

        this.minLimitNoise = PerlinNoise.createLegacyForBlendedNoise(rng, -15, 16);
        this.maxLimitNoise = PerlinNoise.createLegacyForBlendedNoise(rng, -15, 16);
        this.mainNoise     = PerlinNoise.createLegacyForBlendedNoise(rng,  -7,  8);
        this.xzMultiplier = 684.412 * xzScale;
        this.yMultiplier  = 684.412 * yScale;
        this.xzFactor = xzFactor;
        this.yFactor  = yFactor;
        this.smearScaleMultiplier = smearScaleMultiplier;
        this.maxValue = minLimitNoise.maxBrokenValue(this.yMultiplier);
    }

    @Override
    public DensityBuffer compute(int cubeX, int cubeY, int cubeZ, DensityMask mask) {
        int baseX = cubeX << 4;
        int baseY = cubeY << 4;
        int baseZ = cubeZ << 4;

        double limitSmear = this.yMultiplier * this.smearScaleMultiplier;
        double mainSmear  = limitSmear / this.yFactor;

        for (int relZ = 0; relZ < 16; relZ++) {
            for (int relY = 0; relY < 16; relY++) {
                for (int relX = 0; relX < 16; relX++) {
                    if (!mask.isSet(relX, relY, relZ)) continue;

                    double limitX = (baseX + relX) * this.xzMultiplier;
                    double limitY = (baseY + relY) * this.yMultiplier;
                    double limitZ = (baseZ + relZ) * this.xzMultiplier;
                    double mainX  = limitX / this.xzFactor;
                    double mainY  = limitY / this.yFactor;
                    double mainZ  = limitZ / this.xzFactor;

                    // 8-octave main-noise loop → blend selector
                    double mainNoiseValue = 0.0;
                    double pow = 1.0;
                    for (int i = 0; i < 8; i++) {
                        ImprovedNoise noise = this.mainNoise.getOctaveNoise(i);
                        if (noise != null) {
                            mainNoiseValue += noise.noise(
                                PerlinNoise.wrap(mainX * pow),
                                PerlinNoise.wrap(mainY * pow),
                                PerlinNoise.wrap(mainZ * pow),
                                mainSmear * pow,
                                mainY * pow
                            ) / pow;
                        }
                        pow /= 2.0;
                    }

                    double factor = (mainNoiseValue / 10.0 + 1.0) / 2.0;
                    boolean isMax = factor >= 1.0;
                    boolean isMin = factor <= 0.0;

                    // 16-octave limit-noise loops → blendMin / blendMax
                    double blendMin = 0.0;
                    double blendMax = 0.0;
                    pow = 1.0;
                    for (int i = 0; i < 16; i++) {
                        double wx = PerlinNoise.wrap(limitX * pow);
                        double wy = PerlinNoise.wrap(limitY * pow);
                        double wz = PerlinNoise.wrap(limitZ * pow);
                        double yScalePow = limitSmear * pow;
                        double limitYPow = limitY * pow;
                        if (!isMax) {
                            ImprovedNoise minNoise = this.minLimitNoise.getOctaveNoise(i);
                            if (minNoise != null) {
                                blendMin += minNoise.noise(wx, wy, wz, yScalePow, limitYPow) / pow;
                            }
                        }
                        if (!isMin) {
                            ImprovedNoise maxNoise = this.maxLimitNoise.getOctaveNoise(i);
                            if (maxNoise != null) {
                                blendMax += maxNoise.noise(wx, wy, wz, yScalePow, limitYPow) / pow;
                            }
                        }
                        pow /= 2.0;
                    }

                    double result = clampedLerp(factor, blendMin / 512.0, blendMax / 512.0) / 128.0;
                    buffer.set(relX, relY, relZ, (float) result);
                }
            }
        }

        return buffer;
    }

    public PerlinNoise getMainNoise()     { return mainNoise; }
    public PerlinNoise getMinLimitNoise() { return minLimitNoise; }
    public PerlinNoise getMaxLimitNoise() { return maxLimitNoise; }

    public double minValue() {
        return -maxValue;
    }

    public double maxValue() {
        return maxValue;
    }

    /** lerp(clamp(factor, 0, 1), a, b) */
    private static double clampedLerp(double factor, double a, double b) {
        if (factor <= 0.0) return a;
        if (factor >= 1.0) return b;
        return a + factor * (b - a);
    }
}
