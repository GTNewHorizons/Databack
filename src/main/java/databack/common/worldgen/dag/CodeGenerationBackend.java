package databack.common.worldgen.dag;

import java.util.HashMap;
import java.util.Map;

import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;

public class CodeGenerationBackend {

    public static final CodeGenerationBackend VULKAN = new CodeGenerationBackend();

    private final Map<Class<? extends IDensityFunctionFactory>, DFCodeGenerator> generators = new HashMap<>();

    public void registerCodeGenerator(Class<? extends IDensityFunctionFactory> func, InlineDFCodeGenerator generator) {
        var replaced = generators.put(func, generator);

        if (replaced != null) {
            throw new IllegalStateException("Replaced code generator for " + func + " with " + generator + " (was " + replaced + ")");
        }
    }

    public void registerCodeGenerator(Class<? extends IDensityFunctionFactory> func, BarrierDFCodeGenerator generator) {
        var replaced = generators.put(func, generator);

        if (replaced != null) {
            throw new IllegalStateException("Replaced code generator for " + func + " with " + generator + " (was " + replaced + ")");
        }
    }

    public DFCodeGenerator getCodeGenerator(IDensityFunctionFactory func) {
        DFCodeGenerator gen = generators.get(func.getClass());

        if (gen == null) throw new IllegalArgumentException(
            "No code generator registered for: " + func.getClass().getSimpleName());

        return gen;
    }
}
