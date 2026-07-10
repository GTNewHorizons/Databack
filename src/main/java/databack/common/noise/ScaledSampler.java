package databack.common.noise;

/// Scales another sampler by a certain amount in each axis.
/// Effects are the opposite of what you'd expect - scaling by 2 in an axis shrinks the noise by half along that axis.
public class ScaledSampler implements NoiseSampler {

    private final NoiseSampler base;
    private final float scaleX;
    private final float scaleY;
    private final float scaleZ;

    public ScaledSampler(NoiseSampler base, float scaleX, float scaleY, float scaleZ) {
        this.base = base;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
    }

    public ScaledSampler(NoiseSampler base, float scale) {
        this(base, scale, scale, scale);
    }

    @Override
    public float sample(float x, float y) {
        return base.sample(x * scaleX, y * scaleY);
    }

    @Override
    public float sample(float x, float y, float z) {
        return base.sample(x * scaleX, y * scaleY, z * scaleZ);
    }
}
