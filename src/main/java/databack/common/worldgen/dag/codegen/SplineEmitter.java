package databack.common.worldgen.dag.codegen;

import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.SplineCurve;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.SplinePoint;
import mcgpu.core.hwaccel.shader.KernelBuilder;

/**
 * Emits GLSL cubic-Hermite spline evaluation code for a {@link SplineCurve} node.
 * <p>
 * The {@code inputVars} array is laid out as follows:
 * <ul>
 *   <li>{@code inputVars[0]} — the evaluated coordinate (t)</li>
 *   <li>{@code inputVars[1..N]} — the evaluated values at each of the N spline points</li>
 * </ul>
 *
 * Edge-clamp behaviour matches the Java reference implementation:
 * <ul>
 *   <li>t &lt;= locs[0]  → return value at point 0 (inputVars[1])</li>
 *   <li>t &gt;= locs[N-1] → return value at point N-1 (inputVars[N])</li>
 *   <li>otherwise, find the enclosing segment [lo, lo+1] and apply cubic Hermite interpolation</li>
 * </ul>
 *
 * Cubic Hermite per segment [lo, lo+1]:
 * <pre>
 *   dx = locs[lo+1] - locs[lo]
 *   u  = (t - locs[lo]) / dx
 *   k0 = dx * derivs[lo]; k1 = dx * derivs[lo+1]
 *   result = (2u³-3u²+1)*f0 + (u³-2u²+u)*k0 + (-2u³+3u²)*f1 + (u³-u²)*k1
 * </pre>
 *
 * where f0 = inputVars[lo+1] and f1 = inputVars[lo+2].
 */
public final class SplineEmitter {

    private SplineEmitter() {}

    /**
     * Emits GLSL spline evaluation logic into {@code builder.logic} and returns the name of
     * the result variable.
     *
     * @param src       the SplineCurve node being emitted
     * @param inputVars resolved variable names: [0]=coordinate, [1..N]=point values
     * @param builder   kernel builder to append logic to
     * @param nodeIdx   unique index of this node in the kernel (used for variable naming)
     * @return the GLSL variable name holding the spline result, e.g. {@code "_spline_3"}
     */
    public static String emit(SplineCurve src, String[] inputVars, KernelBuilder builder, int nodeIdx) {
        SplinePoint[] points = src.points();
        int n = points.length;
        String resultVar = "_spline_" + nodeIdx;
        String tVar = "_t_" + nodeIdx;

        // Wrap in a nested block to keep variables scoped and avoid name collisions.
        builder.logic.append("    float ").append(resultVar).append(";\n");
        builder.logic.append("    {\n");
        builder.logic.append("        float ").append(tVar).append(" = ").append(inputVars[0]).append(";\n");

        if (n == 0) {
            // Degenerate: no points, always 0.
            builder.logic.append("        ").append(resultVar).append(" = 0.0f;\n");
            builder.logic.append("    }\n");
            return resultVar;
        }

        if (n == 1) {
            // Only one point: always return its value.
            builder.logic.append("        ").append(resultVar).append(" = ").append(inputVars[1]).append(";\n");
            builder.logic.append("    }\n");
            return resultVar;
        }

        // Build float literal arrays for locations and derivatives.
        float[] locs = new float[n];
        float[] derivs = new float[n];
        for (int i = 0; i < n; i++) {
            locs[i] = points[i].location();
            derivs[i] = points[i].derivative();
        }

        // Lower edge clamp.
        builder.logic.append("        if (").append(tVar).append(" <= ").append(locs[0]).append("f) {\n");
        builder.logic.append("            ").append(resultVar).append(" = ").append(inputVars[1]).append(";\n");
        builder.logic.append("        }");

        // Upper edge clamp.
        builder.logic.append(" else if (").append(tVar).append(" >= ").append(locs[n - 1]).append("f) {\n");
        builder.logic.append("            ").append(resultVar).append(" = ").append(inputVars[n]).append(";\n");
        builder.logic.append("        }");

        // Interior segments: emit as else-if chain over segments [0, n-2].
        for (int lo = 0; lo < n - 1; lo++) {
            float locLo = locs[lo];
            float locHi = locs[lo + 1];
            float dx = locHi - locLo;
            float k0 = dx * derivs[lo];
            float k1 = dx * derivs[lo + 1];

            // Condition: t < locs[lo+1]  (we already know t > locs[lo] from earlier branches)
            // For the last segment we don't need a condition since the upper-clamp branch
            // handled t >= locs[n-1], but we still emit "else if" for clarity (it always fires).
            if (lo < n - 2) {
                builder.logic.append(" else if (").append(tVar).append(" < ").append(locHi).append("f) {\n");
            } else {
                builder.logic.append(" else {\n");
            }

            String uVar = "_u_" + nodeIdx + "_" + lo;
            String u2Var = "_u2_" + nodeIdx + "_" + lo;
            String u3Var = "_u3_" + nodeIdx + "_" + lo;

            // u = (t - locs[lo]) / dx
            builder.logic.append("            float ").append(uVar)
                .append(" = (").append(tVar).append(" - ").append(locLo).append("f) / ").append(dx).append("f;\n");
            builder.logic.append("            float ").append(u2Var)
                .append(" = ").append(uVar).append(" * ").append(uVar).append(";\n");
            builder.logic.append("            float ").append(u3Var)
                .append(" = ").append(u2Var).append(" * ").append(uVar).append(";\n");

            // f0 = inputVars[lo+1], f1 = inputVars[lo+2]
            String f0 = inputVars[lo + 1];
            String f1 = inputVars[lo + 2];

            // result = (2u³-3u²+1)*f0 + (u³-2u²+u)*k0 + (-2u³+3u²)*f1 + (u³-u²)*k1
            builder.logic.append("            ").append(resultVar).append(" = ")
                .append("(2.0f * ").append(u3Var).append(" - 3.0f * ").append(u2Var).append(" + 1.0f) * ").append(f0)
                .append("\n                + (").append(u3Var).append(" - 2.0f * ").append(u2Var).append(" + ").append(uVar).append(") * ").append(k0).append("f")
                .append("\n                + (-2.0f * ").append(u3Var).append(" + 3.0f * ").append(u2Var).append(") * ").append(f1)
                .append("\n                + (").append(u3Var).append(" - ").append(u2Var).append(") * ").append(k1).append("f;\n");

            builder.logic.append("        }");
        }

        builder.logic.append("\n    }\n");
        return resultVar;
    }
}
