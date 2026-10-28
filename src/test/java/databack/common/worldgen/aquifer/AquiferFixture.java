package databack.common.worldgen.aquifer;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Assertions;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;
import databack.common.worldgen.density.DensityBuildContext;
import databack.common.worldgen.density.DensityNode;
import databack.common.worldgen.density.DensityRouter;
import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.surface.SurfaceSystem;
import databack.common.worldgen.vanilla.VanillaJar;

/// Shared pieces of the aquifer parity tests.
final class AquiferFixture {

    static final int SEA_LEVEL = 63;

    private AquiferFixture() {}

    private static VanillaAquifer aquifer;

    /// Vanilla's aquifer class from the cached 26.1 jar.
    static synchronized VanillaAquifer aquifer() throws IOException {
        if (aquifer == null) aquifer = VanillaAquifer.load(VanillaJar.classFile(VanillaAquifer.CLASS));
        return aquifer;
    }

    /// The system for the vanilla overworld router at `seed`.
    static AquiferSystem overworld(long seed) throws IOException {
        return AquiferSystem.enabled(SEA_LEVEL, aquifer(), VanillaWorldgen.router("overworld", seed, true),
            VanillaWorldgen.context(seed, false).random());
    }

    /// The system for a synthetic case's router (`aquifer/cases/<name>.json`; absent fields are 0).
    static AquiferSystem forCase(String name) throws IOException {
        JsonObject json = caseJson(name);
        long seed = json.get("seed").getAsLong();
        JsonObject router = json.getAsJsonObject("router");
        DensityBuildContext ctx = VanillaWorldgen.context(seed, false);

        DensityNode[] fields = new DensityNode[DensityRouter.FIELD_NAMES.length];
        for (int i = 0; i < fields.length; i++) fields[i] = field(ctx, router, JSON_NAMES[i]);
        DensityRouter built = new DensityRouter(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5],
            fields[6], fields[7], fields[8], fields[9], fields[10], fields[11], fields[12], fields[13], fields[14]);
        return AquiferSystem.enabled(SEA_LEVEL, aquifer(), built, ctx.random());
    }

    /// Router fields in [DensityRouter]'s order, by their noise settings JSON names.
    private static final String[] JSON_NAMES = { "barrier", "fluid_level_floodedness", "fluid_level_spread", "lava",
        "temperature", "vegetation", "continents", "erosion", "depth", "ridges", "preliminary_surface_level",
        "final_density", "vein_toggle", "vein_ridged", "vein_gap" };

    static JsonObject caseJson(String name) throws IOException {
        InputStream in = AquiferFixture.class.getResourceAsStream("/vanilla_reference/aquifer/cases/" + name + ".json");
        Assertions.assertNotNull(in, "Missing case " + name);
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(r).getAsJsonObject();
        }
    }

    private static DensityNode field(DensityBuildContext ctx, JsonObject router, String name) {
        JsonElement json = router.has(name) ? router.get(name) : new JsonPrimitive(0.0);
        return ctx.build(VanillaWorldgen.parse(json, IDensityFunctionFactory.class));
    }

    /// A density dump (`run.sh aquifer`): int minY, int height, then doubles at `((y - minY) * 16 + z) * 16 + x`.
    static double[] density(String name) throws IOException {
        InputStream raw = AquiferFixture.class.getResourceAsStream("/vanilla_reference/aquifer/" + name);
        Assertions.assertNotNull(raw,
            "Missing reference data " + name + " (run misc/vanilla-reference/run.sh aquifer)");
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            in.readInt();
            double[] values = new double[in.readInt() * 256];
            for (int i = 0; i < values.length; i++) values[i] = in.readDouble();
            return values;
        }
    }

    static String kindName(byte kind) {
        switch (kind) {
            case SurfaceSystem.AIR:
                return "air";
            case SurfaceSystem.FLUID:
                return "water";
            case SurfaceSystem.LAVA:
                return "lava";
            default:
                return "stone";
        }
    }
}
