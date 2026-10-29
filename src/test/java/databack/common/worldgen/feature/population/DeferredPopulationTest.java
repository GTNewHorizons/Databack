package databack.common.worldgen.feature.population;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/// [DeferredPopulation] driven the way 1.7 drives it: each newly loaded chunk completes the 2×2 windows it is the
/// last of, and each window is populated once.
class DeferredPopulationTest {

    /// 1.7's side: the generated (here: always loaded) chunks and which windows were populated.
    private static final class Simulation {

        final Set<Long> generated = new HashSet<>();
        final Set<Long> populatedWindows = new HashSet<>();
        final Map<Long, Integer> decorated = new HashMap<>();
        final DeferredPopulation population = new DeferredPopulation(new LongOpenHashSet(), () -> {});
        int depth, maxDepth;

        boolean isGenerated(int x, int z) {
            return generated.contains(DeferredPopulation.key(x, z));
        }

        void generate(int x, int z) {
            generated.add(DeferredPopulation.key(x, z));
            population.markGenerated(x, z);
            load(x, z);
        }

        void load(int x, int z) {
            for (int wx = x - 1; wx <= x; wx++) {
                for (int wz = z - 1; wz <= z; wz++) {
                    if (populatedWindows.contains(DeferredPopulation.key(wx, wz))) continue;
                    if (!isGenerated(wx, wz) || !isGenerated(wx + 1, wz) || !isGenerated(wx, wz + 1)
                        || !isGenerated(wx + 1, wz + 1)) continue;

                    populatedWindows.add(DeferredPopulation.key(wx, wz));
                    population.onWindowComplete(wx, wz, this::isGenerated, this::decorate);
                }
            }
        }

        void decorate(int x, int z) {
            depth++;
            maxDepth = Math.max(maxDepth, depth);
            for (int nx = x - 1; nx <= x + 1; nx++) {
                for (int nz = z - 1; nz <= z + 1; nz++) assertTrue(isGenerated(nx, nz), "decorated too early");
            }
            decorated.merge(DeferredPopulation.key(x, z), 1, Integer::sum);
            // Decorating loads neighbours, which completes windows again.
            population.onWindowComplete(x, z, this::isGenerated, this::decorate);
            depth--;
        }
    }

    @Test
    void everyChunkWithAGeneratedNeighbourhoodIsDecoratedOnce() {
        for (long seed = 0; seed < 20; seed++) {
            Simulation sim = new Simulation();
            List<int[]> order = new ArrayList<>();
            for (int x = -5; x < 5; x++) {
                for (int z = -5; z < 5; z++) order.add(new int[] { x, z });
            }
            Collections.shuffle(order, new Random(seed));
            for (int[] chunk : order) sim.generate(chunk[0], chunk[1]);

            for (int x = -5; x < 5; x++) {
                for (int z = -5; z < 5; z++) {
                    boolean inner = x > -5 && x < 4 && z > -5 && z < 4;
                    Integer times = sim.decorated.get(DeferredPopulation.key(x, z));
                    assertEquals(inner ? Integer.valueOf(1) : null, times, x + ", " + z + " seed " + seed);
                    assertEquals(!inner, sim.population.isPending(x, z));
                }
            }
            assertEquals(1, sim.maxDepth);
        }
    }

    @Test
    void chunksWithoutNeighboursWait() {
        Simulation sim = new Simulation();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 2; z++) sim.generate(x, z);
        }
        assertTrue(sim.decorated.isEmpty());
        assertTrue(sim.population.isPending(1, 1));

        for (int x = 0; x < 3; x++) sim.generate(x, 2);
        assertEquals(Integer.valueOf(1), sim.decorated.get(DeferredPopulation.key(1, 1)));
        assertFalse(sim.population.isPending(1, 1));
    }

    @Test
    void pendingChunksAreSaved() {
        PendingPopulationData data = new PendingPopulationData(PendingPopulationData.NAME);
        data.chunks().add(DeferredPopulation.key(-3, 7));
        data.chunks().add(DeferredPopulation.key(2_000_000, -2_000_000));

        NBTTagCompound tag = new NBTTagCompound();
        data.writeToNBT(tag);
        PendingPopulationData read = new PendingPopulationData(PendingPopulationData.NAME);
        read.readFromNBT(tag);

        assertEquals(data.chunks(), read.chunks());
    }
}
