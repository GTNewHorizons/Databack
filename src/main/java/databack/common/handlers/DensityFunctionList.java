package databack.common.handlers;

import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;

public class DensityFunctionList extends JsonDatapackTypeHandler<IDensityFunctionFactory> {

    public static final ResourceType<DensityFunctionList> RT = ResourceType.withPath("worldgen/density_function");

    public DensityFunctionList() {
        super(RT, IDensityFunctionFactory.class);
    }

    public IDensityFunctionFactory getDensityFunction(String name) {
        return super.getObject(name);
    }
}
