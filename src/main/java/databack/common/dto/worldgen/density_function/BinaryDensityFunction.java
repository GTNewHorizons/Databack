package databack.common.dto.worldgen.density_function;

import databack.common.context.WorldContext;

public abstract class BinaryDensityFunction implements IDensityFunctionFactory {

    public IDensityFunctionFactory argument1, argument2;

    protected abstract float compute(float param1, float param2);

    @Override
    public IDensityFunction instantiate(WorldContext ctx) {
        IDensityFunction a1 = argument1.instantiate(ctx);
        IDensityFunction a2 = argument2.instantiate(ctx);
        return (context, x, y, z) -> compute(a1.compute(context, x, y, z), a2.compute(context, x, y, z));
    }
}
