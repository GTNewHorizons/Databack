package databack.common.worldgen.density;

/// Computes the terrain density of whole chunks. Not thread safe: [ChunkDensitySampler] keeps one per thread.
///
/// Every call must give the same result as generating that chunk on its own from scratch, whatever was generated
/// before (spec 4.4, last paragraph).
interface ChunkEvaluator {

    /// @param out receives density at `((y - plan.minY) * 16 + z) * 16 + x` for the plan's Y range
    void fill(int chunkX, int chunkZ, double[] out);
}
