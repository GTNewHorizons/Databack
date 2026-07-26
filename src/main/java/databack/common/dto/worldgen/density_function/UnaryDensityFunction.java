package databack.common.dto.worldgen.density_function;

import databack.common.context.WorldContext;

public abstract class UnaryDensityFunction implements IDensityFunctionFactory {

    public IDensityFunctionFactory argument;

    protected abstract float compute(float param);

    @Override
    public IDensityFunction instantiate(WorldContext ctx) {
        IDensityFunction arg = argument.instantiate(ctx);
        return (context, x, y, z) -> compute(arg.compute(context, x, y, z));
    }
}
