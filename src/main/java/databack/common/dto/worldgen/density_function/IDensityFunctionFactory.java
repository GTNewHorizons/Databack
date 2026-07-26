package databack.common.dto.worldgen.density_function;

import databack.common.context.WorldContext;

public interface IDensityFunctionFactory {

    IDensityFunction instantiate(WorldContext ctx);

}
