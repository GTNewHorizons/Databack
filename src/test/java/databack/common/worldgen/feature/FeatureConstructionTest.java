package databack.common.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import databack.common.dto.worldgen.block_state_provider.IBlockStateProvider;
import databack.common.dto.worldgen.configured_feature.IConfiguredFeature;
import databack.common.dto.worldgen.placed_feature.PlacedFeature;
import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.feature.standin.FtBlock;
import databack.common.worldgen.feature.standin.FtHolder;
import databack.common.worldgen.vanilla.VanillaJar;

/// Vanilla 26.1's own features, built from its datapack JSON into vanilla's feature objects.
class FeatureConstructionTest {

    private static VanillaFeatures vanilla;

    @BeforeAll
    static void load() {
        assumeTrue(VanillaWorldgen.available(), "No extracted vanilla datapack");
        vanilla = VanillaFeatures.load(entry -> {
            try {
                return VanillaJar.classFile(entry);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static FeatureBuildContext context() {
        return new FeatureBuildContext(vanilla,
            id -> VanillaWorldgen.definition("configured_feature", id, IConfiguredFeature.class),
            id -> VanillaWorldgen.definition("placed_feature", id, PlacedFeature.class));
    }

    @Test
    void everyPlacedFeatureBuildsOrIsSkipped() {
        FeatureBuildContext ctx = context();
        List<String> built = new ArrayList<>();
        for (String id : VanillaWorldgen.ids("placed_feature")) {
            if (ctx.placedFeature(id) != null) built.add(id);
        }

        for (String id : Arrays.asList("minecraft:ore_iron_upper", "minecraft:ore_diamond", "minecraft:ore_gravel",
            "minecraft:disk_sand", "minecraft:disk_clay", "minecraft:disk_gravel", "minecraft:patch_grass_plain",
            "minecraft:flower_default", "minecraft:ore_dirt")) {
            assertEquals(true, built.contains(id), id + " should build; built: " + built);
        }
    }

    @Test
    void unknownModifiersSkipTheirFeature() {
        PlacedFeature feature = VanillaWorldgen.parse(new JsonParser().parse("{\"feature\":\"minecraft:ore_diamond\","
            + "\"placement\":[{\"type\":\"minecraft:in_square\"},{\"type\":\"databack:no_such_modifier\"}]}"),
            PlacedFeature.class);
        assertNull(feature.build(context()));
    }

    @Test
    void noOpBuilds() {
        IConfiguredFeature feature = VanillaWorldgen.parse(new JsonParser().parse(
            "{\"type\":\"minecraft:no_op\",\"config\":{}}"), IConfiguredFeature.class);
        assertNotNull(feature.instantiate(context()));
    }

    @Test
    void placedFeaturesAreBuiltOnce() {
        FeatureBuildContext ctx = context();
        FtHolder first = ctx.placedFeature("minecraft:ore_diamond");
        assertNotNull(first);
        assertSame(first, ctx.placedFeature("minecraft:ore_diamond"));
        assertSame(ctx.configuredFeature("minecraft:ore_diamond_small"),
            ctx.configuredFeature("minecraft:ore_diamond_small"));
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException e) {
                // Declared further up.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object provider(String json) {
        return VanillaWorldgen.parse(new JsonParser().parse(json), IBlockStateProvider.class).instantiate(context());
    }

    private static String states(Object list) {
        StringBuilder out = new StringBuilder();
        for (Object state : (List<?>) list) out.append(state).append(' ');
        return out.toString().trim();
    }

    @Test
    void noiseThresholdArgumentsLandInTheirFields() throws ReflectiveOperationException {
        Object provider = provider("{\"type\":\"minecraft:noise_threshold_provider\",\"seed\":7,"
            + "\"noise\":{\"firstOctave\":-2,\"amplitudes\":[1.0]},\"scale\":0.5,\"threshold\":0.25,"
            + "\"high_chance\":0.75,\"default_state\":{\"Name\":\"minecraft:stone\"},"
            + "\"low_states\":[{\"Name\":\"minecraft:dirt\"}],\"high_states\":[{\"Name\":\"minecraft:sand\"}]}");

        assertEquals(0.25f, field(provider, "threshold"));
        assertEquals(0.75f, field(provider, "highChance"));
        assertEquals(0.5f, field(provider, "scale"));
        assertEquals(7L, field(provider, "seed"));
        assertEquals("minecraft:dirt", states(field(provider, "lowStates")));
        assertEquals("minecraft:sand", states(field(provider, "highStates")));
    }

    @Test
    void dualNoiseArgumentsLandInTheirFields() throws ReflectiveOperationException {
        Object provider = provider("{\"type\":\"minecraft:dual_noise_provider\",\"seed\":7,"
            + "\"noise\":{\"firstOctave\":-3,\"amplitudes\":[1.0]},\"scale\":0.5,"
            + "\"slow_noise\":{\"firstOctave\":-10,\"amplitudes\":[1.0]},\"slow_scale\":0.25,"
            + "\"variety\":[1,3],\"states\":[{\"Name\":\"minecraft:dirt\"}]}");

        assertEquals(0.5f, field(provider, "scale"));
        assertEquals(0.25f, field(provider, "slowScale"));
        assertEquals(-3, ((databack.common.worldgen.noise.NormalNoise.NoiseParameters) field(provider, "parameters"))
            .firstOctave);
        assertEquals(-10, ((databack.common.worldgen.noise.NormalNoise.NoiseParameters) field(provider,
            "slowNoiseParameters")).firstOctave);
    }

    @Test
    void booleanSelectorArgumentsLandInTheirFields() throws ReflectiveOperationException {
        FeatureBuildContext ctx = context();
        Object feature = VanillaWorldgen.parse(new JsonParser().parse("{\"type\":\"minecraft:random_boolean_selector\","
            + "\"config\":{\"feature_true\":\"minecraft:ore_diamond\",\"feature_false\":\"minecraft:ore_iron_upper\"}}"),
            IConfiguredFeature.class).instantiate(ctx);
        assertNotNull(feature);

        Object config = field(feature, "config");
        assertEquals("minecraft:ore_diamond", ((FtHolder) field(config, "featureTrue")).id);
        assertEquals("minecraft:ore_iron_upper", ((FtHolder) field(config, "featureFalse")).id);
    }

    @Test
    void rotatedBlocksTakeTheStatesBlock() throws ReflectiveOperationException {
        Object provider = provider("{\"type\":\"minecraft:rotated_block_provider\","
            + "\"state\":{\"Name\":\"minecraft:oak_log\",\"Properties\":{\"axis\":\"y\"}}}");
        assertSame(FtBlock.of("minecraft:oak_log"), field(provider, "block"));
    }
}
