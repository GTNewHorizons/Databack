package net.minecraft.world.level.levelgen;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

/**
 * Generates golden data for Databack's aquifer parity tests by running vanilla's noise step (terrain and fluids) on
 * lone overworld chunks with aquifers on and ore veins off, as a black box. Writes into `<out>/aquifer/`:
 *
 * - `chunk_<seed>_<cx>_<cz>_blocks.bin.gz`: every block after the noise step (the surface harness's chunk format).
 * - `chunk_<seed>_<cx>_<cz>_density.bin.gz`: the chunk's terrain density (the density harness's chunk format), so a
 *   block's fate can be compared with whether the terrain left it open.
 * - `index.tsv`: every chunk written.
 */
final class AquiferReference {

    private AquiferReference() {}

    static void write(Path root, HolderLookup.Provider lookup) throws Exception {
        Path out = root.resolve("aquifer");
        Files.createDirectories(out);

        SurfaceReference.Context ctx = new SurfaceReference.Context(lookup);
        NoiseGeneratorSettings overworld = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
            .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        NoiseGeneratorSettings settings = SurfaceReference.withFeatures(overworld, overworld.surfaceRule(), true, false);

        long[] seeds = { 12345L, -987654321L };
        int[][] chunks = { { 0, 0 }, { -7, 13 }, { 25, -40 }, { -300, 180 }, { 1500, 2200 }, { 3, 3 }, { -40, 77 },
            { 120, -15 }, { -512, -512 }, { 800, 64 }, { 157, -188 }, { -64, -172 } };

        try (PrintWriter index = new PrintWriter(Files.newBufferedWriter(out.resolve("index.tsv")))) {
            index.println("seed\tchunkX\tchunkZ");
            for (long seed : seeds) {
                for (int[] c : chunks) {
                    String base = "chunk_" + seed + "_" + c[0] + "_" + c[1];
                    SurfaceReference.generate(ctx, settings, MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD,
                        seed, c[0], c[1], out.resolve(base + "_blocks.bin.gz"), null);
                    VanillaReference.writeChunk(out.resolve(base + "_density.bin.gz"), settings,
                        RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), seed), c[0], c[1]);
                    index.println(seed + "\t" + c[0] + "\t" + c[1]);
                }
                System.out.println("aquifer " + seed);
            }
        }
    }
}
