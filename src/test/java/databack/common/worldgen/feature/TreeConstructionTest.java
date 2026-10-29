package databack.common.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import databack.common.dto.worldgen.configured_feature.IConfiguredFeature;
import databack.common.dto.worldgen.configured_feature.tree.IFeatureSize;
import databack.common.dto.worldgen.configured_feature.tree.IRootPlacer;
import databack.common.dto.worldgen.configured_feature.tree.ITreeDecorator;
import databack.common.dto.worldgen.configured_feature.tree.ITreeFoliagePlacer;
import databack.common.dto.worldgen.configured_feature.tree.ITreeTrunkPlacer;
import databack.common.dto.worldgen.placed_feature.PlacedFeature;
import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.feature.standin.FtIntProvider;
import databack.common.worldgen.rng.StandardRandom;
import databack.common.worldgen.vanilla.VanillaJar;

/// Every tree part type, built from JSON whose values are all different, lands each value in the vanilla field of the
/// same name: the arguments are passed in the order vanilla's constructors take them.
class TreeConstructionTest {

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

    /// Distinct values for one JSON object, built from `key=kind` pairs: `i` int, `f` float, `p` constant int
    /// provider, `u` uniform range object, `b` block state provider, `l` block list, `d` direction list, `o:...` a
    /// nested object of the pairs after the colon, separated by `;`.
    private int next = 2;

    private JsonObject json(String type, String spec) {
        JsonObject json = spec.isEmpty() ? new JsonObject() : object(spec.split(","));
        if (type != null) json.addProperty("type", type);
        return json;
    }

    private JsonObject object(String[] pairs) {
        JsonObject json = new JsonObject();
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            json.add(kv[0], value(kv[1]));
        }
        return json;
    }

    private JsonElement value(String kind) {
        if (kind.startsWith("o:")) return object(kind.substring(2).split(";"));
        switch (kind) {
            case "i":
                return new JsonPrimitive(next++);
            case "f":
                return new JsonPrimitive((next++) / 100f);
            case "p": {
                JsonObject p = new JsonObject();
                p.addProperty("type", "minecraft:constant");
                p.addProperty("value", next++);
                return p;
            }
            case "u": {
                JsonObject u = new JsonObject();
                u.addProperty("min_inclusive", next++);
                u.addProperty("max_inclusive", next++ + 50);
                return u;
            }
            case "b": {
                JsonObject b = new JsonObject();
                b.addProperty("type", "minecraft:simple_state_provider");
                JsonObject state = new JsonObject();
                state.addProperty("Name", "minecraft:stone");
                b.add("state", state);
                return b;
            }
            case "l":
                return new JsonPrimitive("minecraft:stone");
            case "d": {
                JsonArray d = new JsonArray();
                d.add(new JsonPrimitive("down"));
                return d;
            }
            default:
                throw new IllegalArgumentException(kind);
        }
    }

    private static String normalized(String name) {
        return name.replace("_", "").toLowerCase();
    }

    @Nullable
    private static Field field(Class<?> type, String jsonKey) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (normalized(f.getName()).equals(normalized(jsonKey))) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        return null;
    }

    /// Each scalar of `json` against the field of its name in `built`; mismatches are added to `problems`.
    private static void check(String at, JsonObject json, Object built, List<String> problems)
        throws ReflectiveOperationException {
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            if (e.getKey().equals("type")) continue;
            Field f = field(built.getClass(), e.getKey());
            if (f == null) {
                problems.add(at + "." + e.getKey() + ": no field");
                continue;
            }

            Object actual = f.get(built);
            if (actual instanceof Optional) actual = ((Optional<?>) actual).orElse(null);
            JsonElement value = e.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                Object expected = actual instanceof Float ? (Object) value.getAsFloat() : value.getAsInt();
                if (actual instanceof OptionalInt) actual = ((OptionalInt) actual).getAsInt();
                if (!expected.equals(actual)) problems.add(at + "." + e.getKey() + ": " + actual + " != " + expected);
            } else if (value.isJsonObject() && value.getAsJsonObject().has("value")) {
                int sampled = ((FtIntProvider) actual).sample(new StandardRandom(0));
                int expected = value.getAsJsonObject().get("value").getAsInt();
                if (sampled != expected) problems.add(at + "." + e.getKey() + ": " + sampled + " != " + expected);
            } else if (value.isJsonObject() && value.getAsJsonObject().has("min_inclusive")) {
                FtIntProvider range = (FtIntProvider) actual;
                int min = value.getAsJsonObject().get("min_inclusive").getAsInt();
                if (range.minInclusive() != min) problems.add(at + "." + e.getKey() + ": min " + range.minInclusive());
            } else if (value.isJsonObject() && !value.getAsJsonObject().has("type")) {
                check(at + "." + e.getKey(), value.getAsJsonObject(), actual, problems);
            }
        }
    }

    private void verify(Class<?> dto, String type, String spec, List<String> problems)
        throws ReflectiveOperationException {
        JsonObject json = json(type, spec);
        Object parsed = VanillaWorldgen.parse(json, dto);
        Object built = parsed.getClass().getMethod("instantiate", FeatureBuildContext.class).invoke(parsed, context());
        if (built == null) {
            problems.add(type + ": not built");
            return;
        }
        check(type, json, built, problems);
    }

    @Test
    void argumentsLandInTheirFields() throws ReflectiveOperationException {
        List<String> problems = new ArrayList<>();
        String heights = "base_height=i,height_rand_a=i,height_rand_b=i";
        for (String t : new String[] { "straight", "forking", "giant", "mega_jungle", "dark_oak", "fancy" }) {
            verify(ITreeTrunkPlacer.class, "minecraft:" + t + "_trunk_placer", heights, problems);
        }
        verify(ITreeTrunkPlacer.class, "minecraft:bending_trunk_placer",
            heights + ",bend_length=p,min_height_for_leaves=i", problems);
        verify(ITreeTrunkPlacer.class, "minecraft:cherry_trunk_placer", heights
            + ",branch_count=p,branch_horizontal_length=p,branch_start_offset_from_top=u,branch_end_offset_from_top=p",
            problems);
        verify(ITreeTrunkPlacer.class, "minecraft:upwards_branching_trunk_placer", heights
            + ",extra_branch_steps=p,place_branch_per_log_probability=f,extra_branch_length=p,can_grow_through=l",
            problems);

        String foliage = "radius=p,offset=p";
        for (String t : new String[] { "blob", "bush", "fancy", "jungle" }) {
            verify(ITreeFoliagePlacer.class, "minecraft:" + t + "_foliage_placer", foliage + ",height=i", problems);
        }
        for (String t : new String[] { "acacia", "dark_oak" }) {
            verify(ITreeFoliagePlacer.class, "minecraft:" + t + "_foliage_placer", foliage, problems);
        }
        verify(ITreeFoliagePlacer.class, "minecraft:spruce_foliage_placer", foliage + ",trunk_height=p", problems);
        verify(ITreeFoliagePlacer.class, "minecraft:pine_foliage_placer", foliage + ",height=p", problems);
        verify(ITreeFoliagePlacer.class, "minecraft:mega_pine_foliage_placer", foliage + ",crown_height=p", problems);
        verify(ITreeFoliagePlacer.class, "minecraft:cherry_foliage_placer", foliage + ",height=p,"
            + "wide_bottom_layer_hole_chance=f,corner_hole_chance=f,hanging_leaves_chance=f,"
            + "hanging_leaves_extension_chance=f", problems);
        verify(ITreeFoliagePlacer.class, "minecraft:random_spread_foliage_placer",
            foliage + ",foliage_height=p,leaf_placement_attempts=i", problems);

        for (String t : new String[] { "leave_vine", "cocoa", "beehive", "creaking_heart" }) {
            verify(ITreeDecorator.class, "minecraft:" + t, "probability=f", problems);
        }
        verify(ITreeDecorator.class, "minecraft:trunk_vine", "", problems);
        verify(ITreeDecorator.class, "minecraft:pale_moss",
            "leaves_probability=f,trunk_probability=f,ground_probability=f", problems);
        verify(ITreeDecorator.class, "minecraft:alter_ground", "provider=b", problems);
        verify(ITreeDecorator.class, "minecraft:place_on_ground", "tries=i,radius=i,height=i,block_state_provider=b",
            problems);
        verify(ITreeDecorator.class, "minecraft:attached_to_leaves", "probability=f,exclusion_radius_xz=i,"
            + "exclusion_radius_y=i,required_empty_blocks=i,block_provider=b,directions=d", problems);

        verify(IFeatureSize.class, "minecraft:two_layers_feature_size",
            "limit=i,lower_size=i,upper_size=i,min_clipped_height=i", problems);
        verify(IFeatureSize.class, "minecraft:three_layers_feature_size",
            "limit=i,upper_limit=i,lower_size=i,middle_size=i,upper_size=i,min_clipped_height=i", problems);

        verify(IRootPlacer.class, "minecraft:mangrove_root_placer", "trunk_offset_y=p,root_provider=b,"
            + "above_root_placement=o:above_root_provider=b;above_root_placement_chance=f,"
            + "mangrove_root_placement=o:can_grow_through=l;muddy_roots_in=l;muddy_roots_provider=b;max_root_width=i;"
            + "max_root_length=i;random_skew_chance=f", problems);

        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void vanillasTreesBuild() {
        FeatureBuildContext ctx = context();
        for (String id : new String[] { "minecraft:oak", "minecraft:birch", "minecraft:spruce", "minecraft:fancy_oak",
            "minecraft:jungle_tree", "minecraft:dark_oak", "minecraft:acacia", "minecraft:mega_spruce",
            "minecraft:cherry", "minecraft:mangrove", "minecraft:oak_bees_005" }) {
            assertNotNull(ctx.configuredFeature(id), id);
        }
    }
}
