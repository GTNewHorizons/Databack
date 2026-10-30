package databack.common.worldgen.biome;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import databack.common.worldgen.biome.preset.VanillaPresetExtractor;
import databack.common.worldgen.biome.zoom.BiomeZoom;
import databack.common.worldgen.density.DensityRouter;
import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.surface.BlockBiomes;
import databack.common.worldgen.vanilla.VanillaJar;

/// The overworld's biome lookup, part by part: sampling one quart cell's climate, searching the climate tree for one
/// target, and the zoomed per-block biomes of a chunk as the generator asks for them (16×16 columns at three heights,
/// through one [SurfaceBiomes#forChunk]). Chunks and cells sweep a region far larger than any cache.
///
/// Needs the vanilla rewriter: `./gradlew jmhClasspath -Pjmh.args='BiomeBenchmark -prof gc'`.
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgsAppend = { "-Xmx2G" })
public class BiomeBenchmark {

    private static final long SEED = 12345;
    private static final int TARGETS = 4096;
    private static final int ROW = 64;
    private static final int[] HEIGHTS = { 40, 64, 100 };

    private ClimateSampler sampler;
    private ClimateTree tree;
    private SurfaceBiomes surface;
    private long[][] targets;
    private final long[] target = new long[ClimateEntry.PARAMETER_COUNT];
    private final ClimateTree.Query query = new ClimateTree.Query();
    private int cell, chunk;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        List<ClimateEntry> preset = VanillaPresetExtractor.overworld(
            VanillaJar.classFile(VanillaPresetExtractor.OVERWORLD_CLASS));
        DensityRouter router = VanillaWorldgen.router("overworld", SEED, true);
        sampler = ClimateSampler.of(router);
        tree = new ClimateTree(preset);

        BiomeZoom zoom = BiomeZoom.load(VanillaJar.classFile(BiomeZoom.MANAGER_CLASS),
            VanillaJar.classFile(BiomeZoom.LCG_CLASS));
        surface = new SurfaceBiomes(new MultiNoiseBiomeSource(preset, tree, sampler), zoom, zoom.zoomSeed(SEED));

        targets = new long[TARGETS][ClimateEntry.PARAMETER_COUNT];
        for (int i = 0; i < TARGETS; i++) sampler.sample(i % ROW * 7, 16, i / ROW * 7, targets[i]);
    }

    @Benchmark
    public long[] climate() {
        int i = cell++ & 0xFFFF;
        sampler.sample(i & 0xFF, 16, i >> 8, target);
        return target;
    }

    @Benchmark
    public int search() {
        return tree.closest(targets[cell++ & (TARGETS - 1)], query);
    }

    @Benchmark
    public int chunkBiomes() {
        int i = chunk++ & 0xFFF;
        int chunkX = i % ROW, chunkZ = i / ROW;
        BlockBiomes biomes = surface.forChunk();

        int hash = 0;
        for (int y : HEIGHTS) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) hash += biomes.biome(chunkX * 16 + x, y, chunkZ * 16 + z).hashCode();
            }
        }
        return hash;
    }
}
