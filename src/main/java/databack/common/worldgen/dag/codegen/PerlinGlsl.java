package databack.common.worldgen.dag.codegen;

/**
 * GLSL source fragments for Perlin-family noise sampling.
 *
 * <h3>Injection order</h3>
 * <ol>
 *   <li>{@link #PERLIN_FUNCTION} — always injected first whenever any noise function is needed.
 *       Contains the shared octave primitives ({@code obnPerm}, {@code obnSampleOctave},
 *       {@code obnWrap}) plus the full {@code sampleNoise} implementation for NormalNoise.</li>
 *   <li>{@link #OBN_FUNCTION} — injected after {@code PERLIN_FUNCTION} when an
 *       {@code old_blended_noise} node is present. Contains only the gradient table and
 *       {@code sampleOldBlendedNoise} (which calls the primitives from {@code PERLIN_FUNCTION}).</li>
 * </ol>
 *
 * <h3>Per-octave layout in {@code constants[]} (68 uint32s = 272 bytes)</h3>
 * <ul>
 *   <li>{@code constants[base + 0..63]}: permutation table, 4 bytes packed per uint32
 *       ({@code p[4k] | (p[4k+1]<<8) | (p[4k+2]<<16) | (p[4k+3]<<24)})</li>
 *   <li>{@code constants[base + 64]}: {@code xo} as IEEE 754 float bits</li>
 *   <li>{@code constants[base + 65]}: {@code yo} as IEEE 754 float bits</li>
 *   <li>{@code constants[base + 66]}: {@code zo} as IEEE 754 float bits</li>
 *   <li>{@code constants[base + 67]}: padding</li>
 * </ul>
 *
 * <h3>NormalNoise block layout (at push-constant tableBase)</h3>
 * See {@link databack.common.worldgen.noise.NormalNoiseGpuSerializer} for the full layout.
 * Summary: 4-uint32 header + N amplitude floats + two stacks of N octaves each.
 *
 * <h3>OldBlendedNoise block layout (2720 uint32s total, at push-constant tableBase)</h3>
 * <ul>
 *   <li>Octaves 0..7   (544 uint32s): mainNoise octaves, getOctaveNoise(0..7)</li>
 *   <li>Octaves 8..23  (1088 uint32s): minLimitNoise octaves, getOctaveNoise(0..15)</li>
 *   <li>Octaves 24..39 (1088 uint32s): maxLimitNoise octaves, getOctaveNoise(0..15)</li>
 * </ul>
 */
public final class PerlinGlsl {

    /**
     * Shared octave primitives ({@code obnPerm}, {@code obnSampleOctave}, {@code obnWrap})
     * plus the full {@code sampleNoise} implementation for NormalNoise.
     * <p>
     * Always inject this before {@link #OBN_FUNCTION}; {@code sampleOldBlendedNoise}
     * calls into these primitives.
     */
    public static final String PERLIN_FUNCTION =
        // Gradient table matching ImprovedNoise.GRADIENT (indices 0-15).
        // Placed here so obnSampleOctave (below) can reference it; OBN_FUNCTION only adds
        // sampleOldBlendedNoise and does not re-declare this array.
        "const vec3 OBN_GRADIENT[16] = vec3[16](\n"
        + "    vec3( 1.0f,  1.0f,  0.0f), vec3(-1.0f,  1.0f,  0.0f),\n"
        + "    vec3( 1.0f, -1.0f,  0.0f), vec3(-1.0f, -1.0f,  0.0f),\n"
        + "    vec3( 1.0f,  0.0f,  1.0f), vec3(-1.0f,  0.0f,  1.0f),\n"
        + "    vec3( 1.0f,  0.0f, -1.0f), vec3(-1.0f,  0.0f, -1.0f),\n"
        + "    vec3( 0.0f,  1.0f,  1.0f), vec3( 0.0f, -1.0f,  1.0f),\n"
        + "    vec3( 0.0f,  1.0f, -1.0f), vec3( 0.0f, -1.0f, -1.0f),\n"
        + "    vec3( 1.0f,  1.0f,  0.0f), vec3( 0.0f, -1.0f,  1.0f),\n"
        + "    vec3(-1.0f,  1.0f,  0.0f), vec3( 0.0f, -1.0f, -1.0f)\n"
        + ");\n"
        + "\n"
        // Read one byte from the packed permutation table at constants[base + 0..63].
        // Packing: packed[k] = p[4k] | (p[4k+1]<<8) | (p[4k+2]<<16) | (p[4k+3]<<24)
        + "int obnPerm(uint base, int x) {\n"
        + "    int xi = x & 255;\n"
        + "    uint packed = constants[base + uint(xi >> 2)];\n"
        + "    return int((packed >> (uint(xi & 3) * 8u)) & 255u);\n"
        + "}\n"
        + "\n"
        // Full port of ImprovedNoise.noise() including yScale/yFudge smearing.
        // constants[base+64..66] = xo, yo, zo as float bits.
        + "float obnSampleOctave(uint base, float _x, float _y, float _z, float yScale, float yFudge) {\n"
        + "    float xo = uintBitsToFloat(constants[base + 64u]);\n"
        + "    float yo = uintBitsToFloat(constants[base + 65u]);\n"
        + "    float zo = uintBitsToFloat(constants[base + 66u]);\n"
        + "    float x = _x + xo, y = _y + yo, z = _z + zo;\n"
        + "    int xf = int(floor(x)), yf = int(floor(y)), zf = int(floor(z));\n"
        + "    float xr = x - float(xf), yr = y - float(yf), zr = z - float(zf);\n"
        + "    float yrAdj;\n"
        + "    if (yScale != 0.0f) {\n"
        + "        float fl = (yFudge >= 0.0f && yFudge < yr) ? yFudge : yr;\n"
        + "        yrAdj = yr - floor(fl / yScale + 1.0e-7f) * yScale;\n"
        + "    } else {\n"
        + "        yrAdj = yr;\n"
        + "    }\n"
        + "    int p0  = obnPerm(base, xf),         p1  = obnPerm(base, xf + 1);\n"
        + "    int p00 = obnPerm(base, p0 + yf),    p01 = obnPerm(base, p0 + yf + 1);\n"
        + "    int p10 = obnPerm(base, p1 + yf),    p11 = obnPerm(base, p1 + yf + 1);\n"
        + "    float d000 = dot(OBN_GRADIENT[obnPerm(base, p00 + zf)     & 15], vec3(xr,        yrAdj,        zr));\n"
        + "    float d100 = dot(OBN_GRADIENT[obnPerm(base, p10 + zf)     & 15], vec3(xr - 1.0f, yrAdj,        zr));\n"
        + "    float d010 = dot(OBN_GRADIENT[obnPerm(base, p01 + zf)     & 15], vec3(xr,        yrAdj - 1.0f, zr));\n"
        + "    float d110 = dot(OBN_GRADIENT[obnPerm(base, p11 + zf)     & 15], vec3(xr - 1.0f, yrAdj - 1.0f, zr));\n"
        + "    float d001 = dot(OBN_GRADIENT[obnPerm(base, p00 + zf + 1) & 15], vec3(xr,        yrAdj,        zr - 1.0f));\n"
        + "    float d101 = dot(OBN_GRADIENT[obnPerm(base, p10 + zf + 1) & 15], vec3(xr - 1.0f, yrAdj,        zr - 1.0f));\n"
        + "    float d011 = dot(OBN_GRADIENT[obnPerm(base, p01 + zf + 1) & 15], vec3(xr,        yrAdj - 1.0f, zr - 1.0f));\n"
        + "    float d111 = dot(OBN_GRADIENT[obnPerm(base, p11 + zf + 1) & 15], vec3(xr - 1.0f, yrAdj - 1.0f, zr - 1.0f));\n"
        + "    float xA = xr * xr * xr * (xr * (xr * 6.0f - 15.0f) + 10.0f);\n"
        + "    float yA = yr * yr * yr * (yr * (yr * 6.0f - 15.0f) + 10.0f);\n"
        + "    float zA = zr * zr * zr * (zr * (zr * 6.0f - 15.0f) + 10.0f);\n"
        + "    return mix(mix(mix(d000, d100, xA), mix(d010, d110, xA), yA),\n"
        + "               mix(mix(d001, d101, xA), mix(d011, d111, xA), yA), zA);\n"
        + "}\n"
        + "\n"
        // Reduces coordinate to [-33554432, 33554432], matching PerlinNoise.wrap().
        + "float obnWrap(float v) { return v - floor(v * 2.9802322e-8f + 0.5f) * 3.3554432e7f; }\n"
        + "\n"
        // NormalNoise sampling. Layout at tableBase: see NormalNoiseGpuSerializer.
        //   [0] valueFactor, [1] numOctaves N, [2] lowestFreqInputFactor, [3] lowestFreqValueFactor
        //   [4..4+N-1] amplitudes, [4+N..4+N+N*68-1] first stack, [4+N+N*68..] second stack
        // The second stack's input coordinates are pre-scaled by INPUT_FACTOR = 1.0181268882175227.
        + "float sampleNoise(uint tableBase, float x, float y, float z) {\n"
        + "    float valueFactor = uintBitsToFloat(constants[tableBase]);\n"
        + "    uint  numOctaves  = constants[tableBase + 1u];\n"
        + "    float lowestInF   = uintBitsToFloat(constants[tableBase + 2u]);\n"
        + "    float lowestVaF   = uintBitsToFloat(constants[tableBase + 3u]);\n"
        + "    uint  ampBase     = tableBase + 4u;\n"
        + "    uint  firstBase   = ampBase + numOctaves;\n"
        + "    uint  secondBase  = firstBase + numOctaves * 68u;\n"
        + "    float x2 = x * 1.0181268882175227f;\n"
        + "    float y2 = y * 1.0181268882175227f;\n"
        + "    float z2 = z * 1.0181268882175227f;\n"
        + "    float r1 = 0.0f, r2 = 0.0f;\n"
        + "    float vf = lowestVaF, inf = lowestInF;\n"
        + "    for (uint i = 0u; i < numOctaves; i++) {\n"
        + "        float amp = uintBitsToFloat(constants[ampBase + i]);\n"
        + "        if (amp != 0.0f) {\n"
        + "            r1 += amp * obnSampleOctave(firstBase  + i * 68u,\n"
        + "                obnWrap(x  * inf), obnWrap(y  * inf), obnWrap(z  * inf), 0.0f, 0.0f) * vf;\n"
        + "            r2 += amp * obnSampleOctave(secondBase + i * 68u,\n"
        + "                obnWrap(x2 * inf), obnWrap(y2 * inf), obnWrap(z2 * inf), 0.0f, 0.0f) * vf;\n"
        + "        }\n"
        + "        vf *= 0.5f;\n"
        + "        inf *= 2.0f;\n"
        + "    }\n"
        + "    return (r1 + r2) * valueFactor;\n"
        + "}\n";

    /**
     * Gradient table and {@code sampleOldBlendedNoise} for {@code minecraft:old_blended_noise}.
     * <p>
     * Must be injected <em>after</em> {@link #PERLIN_FUNCTION} since {@code sampleOldBlendedNoise}
     * calls {@code obnPerm}, {@code obnSampleOctave}, and {@code obnWrap}.
     */
    public static final String OBN_FUNCTION =
        // Full OldBlendedNoise: 8-octave main selector + 16-octave min/max limit blend.
        // Pre-baked call-site parameters:
        //   xzMul = 684.412 * xzScale,  yMul = 684.412 * yScale
        //   limitSmear = yMul * smearScaleMultiplier,  mainSmear = limitSmear / yFactor
        "float sampleOldBlendedNoise(uint tableBase, float wx, float wy, float wz,\n"
        + "        float xzMul, float yMul, float xzFactor, float yFactor,\n"
        + "        float limitSmear, float mainSmear) {\n"
        + "    float mainX = wx * xzMul / xzFactor;\n"
        + "    float mainY = wy * yMul / yFactor;\n"
        + "    float mainZ = wz * xzMul / xzFactor;\n"
        + "    float mainVal = 0.0f, pow = 1.0f;\n"
        + "    for (int i = 0; i < 8; i++) {\n"
        + "        mainVal += obnSampleOctave(tableBase + uint(i) * 68u,\n"
        + "            obnWrap(mainX * pow), obnWrap(mainY * pow), obnWrap(mainZ * pow),\n"
        + "            mainSmear * pow, mainY * pow) / pow;\n"
        + "        pow *= 0.5f;\n"
        + "    }\n"
        + "    float factor = (mainVal / 10.0f + 1.0f) * 0.5f;\n"
        + "    bool isMax = factor >= 1.0f, isMin = factor <= 0.0f;\n"
        + "    float limitX = wx * xzMul, limitY = wy * yMul, limitZ = wz * xzMul;\n"
        + "    float blendMin = 0.0f, blendMax = 0.0f;\n"
        + "    pow = 1.0f;\n"
        + "    for (int i = 0; i < 16; i++) {\n"
        + "        float lwx = obnWrap(limitX * pow), lwy = obnWrap(limitY * pow), lwz = obnWrap(limitZ * pow);\n"
        + "        float ys = limitSmear * pow, ly = limitY * pow;\n"
        + "        if (!isMax) blendMin += obnSampleOctave(tableBase + 544u  + uint(i) * 68u, lwx, lwy, lwz, ys, ly) / pow;\n"
        + "        if (!isMin) blendMax += obnSampleOctave(tableBase + 1632u + uint(i) * 68u, lwx, lwy, lwz, ys, ly) / pow;\n"
        + "        pow *= 0.5f;\n"
        + "    }\n"
        + "    float f = clamp(factor, 0.0f, 1.0f);\n"
        + "    return mix(blendMin / 512.0f, blendMax / 512.0f, f) / 128.0f;\n"
        + "}\n";

    private PerlinGlsl() {}
}
