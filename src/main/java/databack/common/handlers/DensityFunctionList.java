package databack.common.handlers;

import databack.common.dto.worldgen.density_function.IDensityFunction;

public class DensityFunctionList extends JsonDatapackTypeHandler<IDensityFunction> {

    public static final ResourceType<DensityFunctionList> RT = ResourceType.withPath("worldgen/density_function");

    public DensityFunctionList() {
        super(RT, IDensityFunction.class);
    }

    public IDensityFunction getDensityFunction(String name) {
        return super.getObject(name);
    }
}
