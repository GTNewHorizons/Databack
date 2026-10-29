package databack.common.worldgen.orevein;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Assertions;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.surface.SurfaceDump;

/// Shared pieces of the ore vein tests (`run.sh ore_vein`).
final class OreVeinFixture {

    static final int MIN_Y = -64, HEIGHT = 384;

    private OreVeinFixture() {}

    /// The system for `seed`, with our positional random.
    static OreVeinSystem system(long seed) {
        return OreVeinSystem.create(VanillaWorldgen.context(seed, false).random());
    }

    static SurfaceDump blocks(String base) throws IOException {
        return SurfaceDump.read("ore_vein", base + "_blocks.bin.gz");
    }

    /// Vanilla's interpolated vein fields (`*_fields.bin.gz`): int minY, int layers, then triples of doubles.
    static VeinFields fields(String base) throws IOException {
        InputStream raw = OreVeinFixture.class.getResourceAsStream("/vanilla_reference/ore_vein/" + base
            + "_fields.bin.gz");
        Assertions.assertNotNull(raw, "Missing reference data " + base + " (run misc/vanilla-reference/run.sh ore_vein)");

        try (DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            int minY = in.readInt(), layers = in.readInt();
            double[] toggle = new double[layers * 256], ridged = new double[layers * 256], gap = new double[layers * 256];
            for (int i = 0; i < toggle.length; i++) {
                toggle[i] = in.readDouble();
                ridged[i] = in.readDouble();
                gap[i] = in.readDouble();
            }
            return new VeinFields(minY, layers, toggle, ridged, gap);
        }
    }

    /// Constant fields over the whole dumped range, from a case's `router`.
    static VeinFields constantFields(JsonObject router) {
        double[] toggle = new double[HEIGHT * 256], ridged = new double[HEIGHT * 256], gap = new double[HEIGHT * 256];
        Arrays.fill(toggle, router.get("vein_toggle").getAsDouble());
        Arrays.fill(ridged, router.get("vein_ridged").getAsDouble());
        Arrays.fill(gap, router.get("vein_gap").getAsDouble());
        return new VeinFields(MIN_Y, HEIGHT, toggle, ridged, gap);
    }

    static JsonObject caseJson(String name) throws IOException {
        InputStream in = OreVeinFixture.class.getResourceAsStream("/vanilla_reference/ore_vein/cases/" + name + ".json");
        Assertions.assertNotNull(in, "Missing case " + name);
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(r).getAsJsonObject();
        }
    }

    /// Whether vanilla put a vein block (filler, ore or raw block) there.
    static boolean isVeinBlock(String block) {
        for (VeinBlock vein : VeinBlock.values()) {
            if (vein.id.equals(block)) return true;
        }
        return false;
    }

    /// Whether it is an ore or a raw block.
    static boolean isOre(String block) {
        return block.equals(VeinBlock.COPPER_ORE.id) || block.equals(VeinBlock.DEEPSLATE_IRON_ORE.id)
            || block.equals(VeinBlock.RAW_COPPER_BLOCK.id) || block.equals(VeinBlock.RAW_IRON_BLOCK.id);
    }
}
