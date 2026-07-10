package databack.common.handlers;

import databack.common.dto.worldgen.density_function.IDensityFunction;

public class DensityFunctionList extends JsonDatapackTypeHandler<IDensityFunction> {

    public static final DensityFunctionList INSTANCE = new DensityFunctionList();

    public DensityFunctionList() {
        super("worldgen/density_function", IDensityFunction.class);
    }

    public IDensityFunction getDensityFunction(String name) {
        return super.getObject(name);
    }
}
