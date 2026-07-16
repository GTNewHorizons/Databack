package databack.common.dto.worldgen.density_function;

import databack.common.context.WorldContext;

public abstract class UnaryDensityFunction implements IDensityFunction {

    public IDensityFunction argument;

    protected abstract float compute(float param);

    @Override
    public float compute(WorldContext context, float blockX, float blockY, float blockZ) {
        return compute(argument.compute(context, blockX, blockY, blockZ));
    }
}
