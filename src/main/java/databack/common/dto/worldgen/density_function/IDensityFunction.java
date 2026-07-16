package databack.common.dto.worldgen.density_function;

import databack.common.context.WorldContext;

public interface IDensityFunction {

    float compute(WorldContext context, float blockX, float blockY, float blockZ);

}
