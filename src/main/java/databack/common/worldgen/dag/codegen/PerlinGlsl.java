package databack.common.worldgen.dag.codegen;

/**
 * GLSL function stubs for Perlin noise sampling.
 * <p>
 * The actual Perlin implementation is deferred; this stub returns 0.0 at every sample point
 * so that shaders compile and link correctly while noise integration is pending.
 */
public final class PerlinGlsl {

    /** Stub — noise integration deferred. Returns 0 at all sample points. */
    public static final String PERLIN_FUNCTION =
        "float sampleNoise(uint tableBase, float x, float y, float z) { return 0.0f; }\n";

    private PerlinGlsl() {}
}
