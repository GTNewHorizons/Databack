package databack.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions;
import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;
import databack.common.dto.worldgen.height_provider.IHeightProvider;
import databack.common.dto.worldgen.int_provider.IIntProvider;
import databack.common.worldgen.density.VanillaWorldgen;

/// Datapack values of unknown types read as values that do nothing, instead of failing their file.
class UnknownTypesTest {

    private static <T> T parse(String json, Class<T> type) {
        return VanillaWorldgen.parse(new JsonParser().parse(json), type);
    }

    @Test
    void unknownDensityFunctionsAreZero() {
        IDensityFunctionFactory function = parse("{\"type\":\"databack:no_such_function\"}",
            IDensityFunctionFactory.class);
        assertEquals(new BuiltinDensityFunctions.ConstantFunc(0), function);
    }

    @Test
    void unknownIntProvidersGiveZero() {
        assertEquals(0, parse("{\"type\":\"databack:no_such_provider\"}", IIntProvider.class).get(new Random(1)));
    }

    @Test
    void unknownHeightProvidersGiveTheBottom() {
        IHeightProvider provider = parse("{\"type\":\"databack:no_such_provider\"}", IHeightProvider.class);
        assertEquals(-64, provider.get(new Random(1), -64, 384));
    }
}
