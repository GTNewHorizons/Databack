package databack.common.dto.worldgen.density_function;

import databack.common.context.WorldContext;

public abstract class BinaryDensityFunction implements IDensityFunction {

    public IDensityFunction argument1, argument2;

    protected abstract float compute(float param1, float param2);

    @Override
    public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
        return compute(argument1.compute(context, blockX, blockY, blockZ), argument2.compute(context, blockX, blockY, blockZ));
    }
}
