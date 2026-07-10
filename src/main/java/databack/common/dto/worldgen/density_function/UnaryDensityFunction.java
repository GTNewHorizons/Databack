package databack.common.dto.worldgen.density_function;

public abstract class UnaryDensityFunction implements IDensityFunction {

    public IDensityFunction argument;

    protected abstract float compute(float param);

    @Override
    public float compute(float blockX, float blockY, float blockZ) {
        return compute(argument.compute(blockX, blockY, blockZ));
    }
}
