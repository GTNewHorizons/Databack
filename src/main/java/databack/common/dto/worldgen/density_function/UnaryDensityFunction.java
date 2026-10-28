package databack.common.dto.worldgen.density_function;

import java.util.Collections;
import java.util.List;

import databack.common.worldgen.density.DensityBuildContext;
import databack.common.worldgen.density.Density;
import databack.common.worldgen.density.DensityNode;
import databack.common.worldgen.density.TransformNode;
import java.util.Objects;

import lombok.ToString;

@ToString
public abstract class UnaryDensityFunction implements IDensityFunctionFactory {

    public final IDensityFunctionFactory argument;

    public UnaryDensityFunction(IDensityFunctionFactory argument) {
        this.argument = argument;
    }

    @Override
    public List<IDensityFunctionFactory> children() {
        return Collections.singletonList(argument);
    }

    protected abstract TransformNode.Kind kind();

    @Override
    public DensityNode instantiate(DensityBuildContext ctx) {
        return Density.transform(kind(), ctx.build(argument));
    }

    // Lombok's equals would consider e.g. abs(x) == square(x), which breaks the build cache.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || o.getClass() != getClass()) return false;
        UnaryDensityFunction other = (UnaryDensityFunction) o;
        return Objects.equals(argument, other.argument);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() * 31 + Objects.hash(argument);
    }
}
