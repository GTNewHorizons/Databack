package databack.common.dto.worldgen.density_function;

public abstract class BinaryDensityFunction implements IDensityFunction {

    public IDensityFunction argument1, argument2;

    protected abstract float compute(float param1, float param2);

    @Override
    public float compute(float blockX, float blockY, float blockZ) {
        return compute(argument1.compute(blockX, blockY, blockZ), argument2.compute(blockX, blockY, blockZ));
    }
}
