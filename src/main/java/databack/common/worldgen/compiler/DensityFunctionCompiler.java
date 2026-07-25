package databack.common.worldgen.compiler;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import databack.DatabackConfig;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import databack.common.context.StateSlot;
import databack.common.context.WorldContextImpl;
import databack.common.dto.worldgen.density_function.BinaryDensityFunction;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.ConstantFunc;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.DensityFunctionRef;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.RarityType;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.SplinePoint;
import databack.common.dto.worldgen.density_function.IDensityFunction;
import databack.common.dto.worldgen.density_function.UnaryDensityFunction;

/**
 * Compiles an {@link IDensityFunction} tree into a single JVM class, eliminating
 * virtual dispatch overhead for arithmetic nodes.
 *
 * <p>Every unique DF node in the tree gets its own private {@code df_N} method with
 * the same signature as {@link IDensityFunction#compute}. The public {@code compute()}
 * method simply calls {@code df_0} (the root). Shared subtrees emit one method, called
 * from multiple parents. The JIT inlines trivial methods naturally.
 *
 * <p>Inlineable nodes (constant, add, mul, abs, square, noise, spline, etc.) are emitted
 * as direct JVM instructions. Truly complex nodes (caches, blend, end_islands, etc.) are
 * stored in an {@code IDensityFunction[] opaques} field and called via {@code invokeinterface}.
 * Noise state-slots and noise names are stored in an {@code Object[] extras} field.
 *
 * <p>Compilation is done once at world initialisation. The resulting function is
 * a drop-in replacement with identical semantics.
 */
public final class DensityFunctionCompiler implements Opcodes {

    // ── Descriptors ──────────────────────────────────────────────────────────

    private static final String IFACE           = "databack/common/dto/worldgen/density_function/IDensityFunction";
    private static final String IFACE_DESC      = "L" + IFACE + ";";
    private static final String IFACE_ARRAY_DESC = "[" + IFACE_DESC;
    private static final String CTX             = "databack/common/context/WorldContext";
    private static final String CTX_DESC        = "L" + CTX + ";";
    private static final String COMPUTE_DESC    = "(" + CTX_DESC + "FFF)F";
    private static final String MATHHELPER      = "net/minecraft/util/MathHelper";

    private static final String EXTRAS_FIELD_DESC = "[Ljava/lang/Object;";
    private static final String STATE_SLOT        = "databack/common/context/StateSlot";
    private static final String STATE_SLOT_DESC   = "L" + STATE_SLOT + ";";
    private static final String CACHE_SLOT        = "databack/common/context/CacheSlot";
    private static final String CACHE_SLOT_DESC   = "L" + CACHE_SLOT + ";";
    private static final String MAP_2D            = "databack/common/util/QuantizedFloatMap2D";
    private static final String MAP_3D            = "databack/common/util/QuantizedFloatMap3D";
    private static final String NOISE_HANDLER     = "databack/common/handlers/DatapackNoiseList";
    private static final String RESOURCE_TYPE     = "databack/common/handlers/ResourceType";
    private static final String I_HANDLER         = "databack/common/handlers/IDatapackTypeHandler";
    private static final String NOISE_SAMPLER     = "com/gtnewhorizon/gtnhlib/noise/NoiseSampler";
    private static final String NOISE_SAMPLER_DESC = "L" + NOISE_SAMPLER + ";";

    // ── Class naming ─────────────────────────────────────────────────────────

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final Logger LOGGER = LogManager.getLogger("DensityFunctionCompiler");

    // ── Per-compilation state ─────────────────────────────────────────────────

    private MethodVisitor mv;
    private String className;
    private final List<IDensityFunction> opaques = new ArrayList<>();
    private final List<Object> extras = new ArrayList<>();

    /** Maps each unique node instance (identity) to its df_N index. */
    private final IdentityHashMap<IDensityFunction, Integer> nodeIndex = new IdentityHashMap<>();
    /** Ordered list of nodes; {@code nodes.get(i)} is emitted as {@code df_i}. */
    private final List<IDensityFunction> nodes = new ArrayList<>();

    /** Next available local variable slot (0-4 are reserved for this/ctx/x/y/z). */
    private int nextSlot = 5;

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Compiles {@code root} into a concrete JVM class and returns an instance.
     * Falls back to {@code root} unchanged if compilation fails.
     */
    public static IDensityFunction compile(IDensityFunction root) {
        if (root == null) return null;
        if (root instanceof ConstantFunc) return root;
        return new DensityFunctionCompiler().doCompile(root);
    }

    // ── Core compilation ──────────────────────────────────────────────────────

    private IDensityFunction doCompile(IDensityFunction root) {
        int id = COUNTER.getAndIncrement();
        className = "databack/compiled/CompiledDF_" + id;

        // Phase 1: walk the tree, assign an index to every unique node instance.
        registerTree(root);

        // COMPUTE_MAXS: ASM computes max stack/locals; no StackMapTable needed for V1_6.
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC | ACC_FINAL, className, null,
                "java/lang/Object", new String[]{IFACE});

        // compute() → tail call to df_0 (the root node's method).
        MethodVisitor computeMv = cw.visitMethod(ACC_PUBLIC, "compute", COMPUTE_DESC, null, null);
        computeMv.visitCode();
        computeMv.visitVarInsn(ALOAD, 0);
        computeMv.visitVarInsn(ALOAD, 1);
        computeMv.visitVarInsn(FLOAD, 2);
        computeMv.visitVarInsn(FLOAD, 3);
        computeMv.visitVarInsn(FLOAD, 4);
        computeMv.visitMethodInsn(INVOKESPECIAL, className, nodeMethodName(0), COMPUTE_DESC, false);
        computeMv.visitInsn(FRETURN);
        computeMv.visitMaxs(0, 0);
        computeMv.visitEnd();

        // Phase 2: emit one private df_N method per registered node.
        for (int i = 0; i < nodes.size(); i++) {
            emitNodeMethod(cw, i);
        }

        // Declare fields only if needed.
        if (!opaques.isEmpty()) {
            cw.visitField(ACC_PRIVATE | ACC_FINAL, "opaques", IFACE_ARRAY_DESC, null, null).visitEnd();
        }
        if (!extras.isEmpty()) {
            cw.visitField(ACC_PRIVATE | ACC_FINAL, "extras", EXTRAS_FIELD_DESC, null, null).visitEnd();
        }

        // Emit constructor.
        emitConstructor(cw);

        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        if (DatabackConfig.dumpCompiledDensityFunctions) {
            dumpClass(bytes, id);
        }
        return instantiate(bytes, id);
    }

    // ── Tree registration ─────────────────────────────────────────────────────

    /**
     * Assigns {@code node} the next available index and recurses into its children.
     * Shared subtrees (same instance referenced from multiple parents) are registered once.
     */
    private void registerTree(IDensityFunction node) {
        if (node instanceof DensityFunctionRef ref) node = ref.getFunction();
        if (nodeIndex.containsKey(node)) return;
        int idx = nodes.size();
        nodes.add(node);
        nodeIndex.put(node, idx);
        registerChildren(node);
    }

    /**
     * Recurses into the children of {@code node}. Must stay in sync with
     * {@link #emitNodeBody} — every child that {@code emitNodeBody} would call
     * via {@code emitChildCall} must be registered here.
     */
    @SuppressWarnings("unchecked")
    private void registerChildren(IDensityFunction node) {
        if (node instanceof UnaryDensityFunction unary) {
            switch (node.getClass().getSimpleName()) {
                case "SlideUnary" -> {} // argument is never evaluated at runtime
                default -> registerTree(unary.argument);
            }
            return;
        }
        if (node instanceof BinaryDensityFunction binary) {
            registerTree(binary.argument1);
            registerTree(binary.argument2);
            return;
        }
        switch (node.getClass().getSimpleName()) {
            case "ClampFunc"          -> registerTree(getDF(node, "input"));
            case "Cache2DFunc",
                 "FlatCacheUnary",
                 "CacheOnceUnary"     -> registerTree(getDF(node, "argument"));
            case "RangeChoiceFunc"    -> {
                registerTree(getDF(node, "input"));
                registerTree(getDF(node, "when_in_range"));
                registerTree(getDF(node, "when_out_of_range"));
            }
            case "IntervalSelectFunc" -> {
                registerTree(getDF(node, "input"));
                IDensityFunction[] fns = getDF(node, "functions");
                for (IDensityFunction fn : fns) registerTree(fn);
            }
            case "FindTopSurfaceFunc" -> {
                registerTree(getDF(node, "density"));
                registerTree(getDF(node, "upper_bound"));
            }
            case "InterpolatedFunc"   -> registerTree(getDF(node, "argument"));
            case "ShiftedNoiseFunc"   -> {
                registerTree(getDF(node, "shift_x"));
                registerTree(getDF(node, "shift_y"));
                registerTree(getDF(node, "shift_z"));
            }
            case "WeirdScaledSampler" -> registerTree(getDF(node, "input"));
            case "SplineFunc"         -> registerTree(getDF(node, "spline"));
            case "SplineCurve"        -> {
                registerTree(getDF(node, "coordinate"));
                SplinePoint[] points = getDF(node, "points");
                for (SplinePoint p : points) registerTree(getDF(p, "value"));
            }
            default -> {} // ConstantFunc, YClampedGradient, Noise*, SplineValue, opaques — no DF children
        }
    }

    // ── Per-node method emission ──────────────────────────────────────────────

    /** Emits the private {@code df_idx} method for {@code nodes.get(idx)}. */
    private void emitNodeMethod(ClassWriter cw, int idx) {
        IDensityFunction node = nodes.get(idx);
        MethodVisitor nodeMv = cw.visitMethod(ACC_PRIVATE, nodeMethodName(idx), COMPUTE_DESC, null, null);
        MethodVisitor savedMv   = this.mv;
        int           savedSlot = this.nextSlot;
        this.mv       = nodeMv;
        this.nextSlot = 5;           // slots 0-4 = this/ctx/x/y/z
        nodeMv.visitCode();
        emitNodeBody(node);
        nodeMv.visitInsn(FRETURN);
        nodeMv.visitMaxs(0, 0);
        nodeMv.visitEnd();
        this.mv       = savedMv;
        this.nextSlot = savedSlot;
    }

    /**
     * Emits bytecode for {@code node}, leaving its float result on the operand stack.
     * Children are called via {@code emitChildCall*} helpers that emit INVOKESPECIAL
     * to the child's own {@code df_N} method.
     */
    private void emitNodeBody(IDensityFunction node) {
        if (node instanceof DensityFunctionRef ref) node = ref.getFunction();

        if (node instanceof UnaryDensityFunction unary) {
            emitUnary(node, unary.argument);
            return;
        }
        if (node instanceof BinaryDensityFunction binary) {
            emitBinary(node, binary.argument1, binary.argument2);
            return;
        }

        switch (node.getClass().getSimpleName()) {
            case "ConstantFunc"          -> emitConstant(getFloat(node, "argument"));
            case "ClampFunc"             -> emitClamp(
                    getDF(node, "input"), getFloat(node, "min"), getFloat(node, "max"));
            case "YClampedGradientFunc"  -> emitYClampedGradient(
                    getInt(node, "from_y"), getInt(node, "to_y"),
                    getFloat(node, "from_value"), getFloat(node, "to_value"));
            case "Cache2DFunc"           -> emitCache2D(getDF(node, "argument"), 1024f, false);
            case "FlatCacheUnary"        -> emitCache2D(getDF(node, "argument"), 0.25f, true);
            case "CacheOnceUnary"        -> emitCacheOnce(getDF(node, "argument"));
            case "RangeChoiceFunc"       -> emitRangeChoiceFunc(node);
            case "IntervalSelectFunc"    -> emitIntervalSelectFunc(node);
            case "FindTopSurfaceFunc"    -> emitFindTopSurfaceFunc(node);
            case "InterpolatedFunc"      -> emitInterpolatedFunc(node);
            case "NoiseFunc"             -> emitNoiseFunc(node);
            case "ShiftFunc"             -> emitShiftFunc(node);
            case "ShiftAFunc"            -> emitShiftAFunc(node);
            case "ShiftBFunc"            -> emitShiftBFunc(node);
            case "ShiftedNoiseFunc"      -> emitShiftedNoiseFunc(node);
            case "WeirdScaledSampler"    -> emitWeirdScaledSampler(node);
            case "SplineFunc"            -> emitChildCall(getDF(node, "spline"));
            case "SplineValue"           -> emitConstant(getFloat(node, "coordinate"));
            case "SplineCurve"           -> emitSplineCurve(node);
            default                      -> emitOpaqueCall(addOpaque(node));
        }
    }

    // ── Child-call helpers ────────────────────────────────────────────────────

    /**
     * Emits: {@code INVOKESPECIAL df_N(this, ctx, x, y, z)}
     * using the current method's parameter slots for x/y/z.
     */
    private void emitChildCall(IDensityFunction child) {
        if (child instanceof DensityFunctionRef ref) child = ref.getFunction();
        int idx = nodeIndex.get(child);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(FLOAD, 2);
        mv.visitVarInsn(FLOAD, 3);
        mv.visitVarInsn(FLOAD, 4);
        mv.visitMethodInsn(INVOKESPECIAL, className, nodeMethodName(idx), COMPUTE_DESC, false);
    }

    /**
     * Emits: {@code INVOKESPECIAL df_N(this, ctx, x, 0.0f, z)}
     * Used by FlatCacheUnary, which evaluates its argument at y=0.
     */
    private void emitChildCallAtY0(IDensityFunction child) {
        if (child instanceof DensityFunctionRef ref) child = ref.getFunction();
        int idx = nodeIndex.get(child);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(FLOAD, 2);
        mv.visitInsn(FCONST_0);
        mv.visitVarInsn(FLOAD, 4);
        mv.visitMethodInsn(INVOKESPECIAL, className, nodeMethodName(idx), COMPUTE_DESC, false);
    }

    /**
     * Emits: {@code INVOKESPECIAL df_N(this, ctx, x, (float)yIntSlot, z)}
     * Used by FindTopSurfaceFunc to pass the loop variable as y.
     */
    private void emitChildCallWithIntY(IDensityFunction child, int yIntSlot) {
        if (child instanceof DensityFunctionRef ref) child = ref.getFunction();
        int idx = nodeIndex.get(child);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(FLOAD, 2);
        mv.visitVarInsn(ILOAD, yIntSlot);
        mv.visitInsn(I2F);
        mv.visitVarInsn(FLOAD, 4);
        mv.visitMethodInsn(INVOKESPECIAL, className, nodeMethodName(idx), COMPUTE_DESC, false);
    }

    // ── Node emission ─────────────────────────────────────────────────────────

    private void emitUnary(IDensityFunction node, IDensityFunction argument) {
        switch (node.getClass().getSimpleName()) {

            // Passthroughs (identity) — just emit child
            case "BlendDensityUnary", "CacheAllInCellUnary" -> emitChildCall(argument);

            // Constant zero — don't even evaluate the child
            case "SlideUnary" -> mv.visitInsn(FCONST_0);

            case "AbsUnary" -> {
                emitChildCall(argument);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "abs", "(F)F", false);
            }

            case "SquareUnary" -> {
                emitChildCall(argument);
                // DUP is valid for category-1 (float) values
                mv.visitInsn(DUP);
                mv.visitInsn(FMUL);
            }

            case "CubeUnary" -> {
                emitChildCall(argument);
                int slot = nextSlot++;
                mv.visitVarInsn(FSTORE, slot);
                mv.visitVarInsn(FLOAD, slot);
                mv.visitVarInsn(FLOAD, slot);
                mv.visitInsn(FMUL);
                mv.visitVarInsn(FLOAD, slot);
                mv.visitInsn(FMUL);
            }

            case "InvertUnary" -> {
                // 1f / arg
                emitChildCall(argument);
                int slot = nextSlot++;
                mv.visitVarInsn(FSTORE, slot);
                mv.visitInsn(FCONST_1);
                mv.visitVarInsn(FLOAD, slot);
                mv.visitInsn(FDIV);
            }

            case "HalfNegativeUnary" -> {
                emitChildCall(argument);
                int slot = nextSlot++;
                mv.visitVarInsn(FSTORE, slot);
                emitConditionalScale(slot, 0.5f);
            }

            case "QuarterNegativeUnary" -> {
                emitChildCall(argument);
                int slot = nextSlot++;
                mv.visitVarInsn(FSTORE, slot);
                emitConditionalScale(slot, 0.25f);
            }

            case "SqueezeUnary" -> {
                emitChildCall(argument);
                emitSqueeze();
            }

            // Unknown unary type — treat entire node as opaque
            default -> emitOpaqueCall(addOpaque(node));
        }
    }

    private void emitBinary(IDensityFunction node, IDensityFunction arg1, IDensityFunction arg2) {
        switch (node.getClass().getSimpleName()) {
            case "AddBinary" -> {
                emitChildCall(arg1);
                emitChildCall(arg2);
                mv.visitInsn(FADD);
            }
            case "MulBinary" -> {
                emitChildCall(arg1);
                emitChildCall(arg2);
                mv.visitInsn(FMUL);
            }
            case "MaxBinary" -> {
                emitChildCall(arg1);
                emitChildCall(arg2);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "max", "(FF)F", false);
            }
            case "MinBinary" -> {
                emitChildCall(arg1);
                emitChildCall(arg2);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(FF)F", false);
            }
            default -> emitOpaqueCall(addOpaque(node));
        }
    }

    // ── Specific emitters ────────────────────────────────────────────────────

    private void emitConstant(float value) {
        if (value == 0.0f)      mv.visitInsn(FCONST_0);
        else if (value == 1.0f) mv.visitInsn(FCONST_1);
        else if (value == 2.0f) mv.visitInsn(FCONST_2);
        else                    mv.visitLdcInsn(value);
    }

    private void emitClamp(IDensityFunction input, float min, float max) {
        emitChildCall(input);
        mv.visitLdcInsn(min);
        mv.visitLdcInsn(max);
        mv.visitMethodInsn(INVOKESTATIC, MATHHELPER, "clamp_float", "(FFF)F", false);
    }

    private void emitYClampedGradient(int from_y, int to_y, float from_value, float to_value) {
        // Source: (blockY - from_y) * (to_value - from_value) / (to_y - from_y) + from_value
        // Precompute scale at compile time to save a runtime divide.
        float scale = (to_value - from_value) / (to_y - from_y);
        mv.visitVarInsn(FLOAD, 3);          // blockY
        mv.visitLdcInsn((float) from_y);
        mv.visitInsn(FSUB);
        mv.visitLdcInsn(scale);
        mv.visitInsn(FMUL);
        mv.visitLdcInsn(from_value);
        mv.visitInsn(FADD);
    }

    /**
     * RangeChoiceFunc: if input in [min_inclusive, max_exclusive) → when_in_range, else when_out_of_range.
     */
    private void emitRangeChoiceFunc(IDensityFunction node) {
        IDensityFunction input        = getDF(node, "input");
        float            minInclusive = getFloat(node, "min_inclusive");
        float            maxExclusive = getFloat(node, "max_exclusive");
        IDensityFunction whenIn       = getDF(node, "when_in_range");
        IDensityFunction whenOut      = getDF(node, "when_out_of_range");

        int tVar = nextSlot++;
        Label outOfRange = new Label();
        Label end        = new Label();

        emitChildCall(input);
        mv.visitVarInsn(FSTORE, tVar);

        // if t < min_inclusive → out of range
        mv.visitVarInsn(FLOAD, tVar);
        mv.visitLdcInsn(minInclusive);
        mv.visitInsn(FCMPG);           // 1 if t < minInclusive or NaN
        mv.visitJumpInsn(IFLT, outOfRange);

        // if t >= max_exclusive → out of range
        mv.visitVarInsn(FLOAD, tVar);
        mv.visitLdcInsn(maxExclusive);
        mv.visitInsn(FCMPL);           // -1 if t >= maxExclusive or NaN
        mv.visitJumpInsn(IFGE, outOfRange);

        // In range
        emitChildCall(whenIn);
        mv.visitJumpInsn(GOTO, end);

        mv.visitLabel(outOfRange);
        emitChildCall(whenOut);

        mv.visitLabel(end);
    }

    /**
     * IntervalSelectFunc: evaluate input, then select from functions[] based on thresholds[].
     */
    private void emitIntervalSelectFunc(IDensityFunction node) {
        IDensityFunction   input      = getDF(node, "input");
        float[]            thresholds = getDF(node, "thresholds");
        IDensityFunction[] functions  = getDF(node, "functions");

        int   tVar = nextSlot++;
        Label end  = new Label();

        emitChildCall(input);
        mv.visitVarInsn(FSTORE, tVar);

        int N = thresholds.length;
        Label[] labels = new Label[N];
        for (int i = 0; i < N; i++) labels[i] = new Label();

        for (int i = 0; i < N; i++) {
            // if t >= thresholds[i] → skip this function
            mv.visitVarInsn(FLOAD, tVar);
            mv.visitLdcInsn(thresholds[i]);
            mv.visitInsn(FCMPL);          // -1 if t >= threshold or NaN
            mv.visitJumpInsn(IFGE, labels[i]);

            emitChildCall(functions[i]);
            mv.visitJumpInsn(GOTO, end);

            mv.visitLabel(labels[i]);
        }

        // Fallthrough: t >= all thresholds
        emitChildCall(functions[N]);

        mv.visitLabel(end);
    }

    /**
     * FindTopSurfaceFunc: loop from upper_bound down by cell_height, return first y where density > 0.
     * The y override is passed explicitly as an argument to the density child method;
     * the current method's parameter slots are never modified.
     */
    private void emitFindTopSurfaceFunc(IDensityFunction node) {
        IDensityFunction density     = getDF(node, "density");
        IDensityFunction upperBound  = getDF(node, "upper_bound");
        int              lowerBound  = getInt(node, "lower_bound");
        int              cellHeight  = getInt(node, "cell_height");

        int yVar      = nextSlot++;
        int resultVar = nextSlot++;

        Label loopStart = new Label();
        Label noHit     = new Label();
        Label loopEnd   = new Label();

        // y = (int) upper_bound.compute(...)
        emitChildCall(upperBound);
        mv.visitInsn(F2I);
        mv.visitVarInsn(ISTORE, yVar);

        // Default result = (float) lowerBound
        pushInt(lowerBound);
        mv.visitInsn(I2F);
        mv.visitVarInsn(FSTORE, resultVar);

        mv.visitLabel(loopStart);

        // if y <= lowerBound → exit loop (result remains lowerBound)
        mv.visitVarInsn(ILOAD, yVar);
        pushInt(lowerBound);
        mv.visitJumpInsn(IF_ICMPLE, loopEnd);

        // Evaluate density at (x, yVar, z) — y passed explicitly, no slot override
        emitChildCallWithIntY(density, yVar);

        // if density <= 0 → no hit
        mv.visitInsn(FCONST_0);
        mv.visitInsn(FCMPG);
        mv.visitJumpInsn(IFLE, noHit);

        // Hit: save y as float result
        mv.visitVarInsn(ILOAD, yVar);
        mv.visitInsn(I2F);
        mv.visitVarInsn(FSTORE, resultVar);
        mv.visitJumpInsn(GOTO, loopEnd);

        // No hit: decrement y and loop
        mv.visitLabel(noHit);
        mv.visitVarInsn(ILOAD, yVar);
        pushInt(cellHeight);
        mv.visitInsn(ISUB);
        mv.visitVarInsn(ISTORE, yVar);
        mv.visitJumpInsn(GOTO, loopStart);

        mv.visitLabel(loopEnd);
        mv.visitVarInsn(FLOAD, resultVar);
        // → result on stack
    }

    /**
     * InterpolatedFunc: trilinear interpolation of argument evaluated at 8 cell corners.
     * Each corner is evaluated by calling df_N directly with explicit corner coordinates;
     * the current method's parameter slots are never modified.
     */
    private void emitInterpolatedFunc(IDensityFunction node) {
        IDensityFunction argument = getDF(node, "argument");

        int bx2Var  = nextSlot++;  // int
        int by2Var  = nextSlot++;  // int
        int bz2Var  = nextSlot++;  // int
        int kxVar   = nextSlot++;  // float (0..1)
        int kyVar   = nextSlot++;
        int kzVar   = nextSlot++;
        int kxiVar  = nextSlot++;  // 1 - kx
        int kyiVar  = nextSlot++;
        int kziVar  = nextSlot++;
        int c000    = nextSlot++;
        int c100    = nextSlot++;
        int c010    = nextSlot++;
        int c110    = nextSlot++;
        int c001    = nextSlot++;
        int c101    = nextSlot++;
        int c011    = nextSlot++;
        int c111    = nextSlot++;

        // bx2 = ((int) blockX) & ~3  (i.e., & -4)
        mv.visitVarInsn(FLOAD, 2); mv.visitInsn(F2I); mv.visitIntInsn(BIPUSH, -4); mv.visitInsn(IAND);
        mv.visitVarInsn(ISTORE, bx2Var);
        mv.visitVarInsn(FLOAD, 3); mv.visitInsn(F2I); mv.visitIntInsn(BIPUSH, -4); mv.visitInsn(IAND);
        mv.visitVarInsn(ISTORE, by2Var);
        mv.visitVarInsn(FLOAD, 4); mv.visitInsn(F2I); mv.visitIntInsn(BIPUSH, -4); mv.visitInsn(IAND);
        mv.visitVarInsn(ISTORE, bz2Var);

        // kx = ((int)blockX & 3) * 0.25f
        mv.visitVarInsn(FLOAD, 2); mv.visitInsn(F2I); mv.visitInsn(ICONST_3); mv.visitInsn(IAND);
        mv.visitInsn(I2F); mv.visitLdcInsn(0.25f); mv.visitInsn(FMUL);
        mv.visitVarInsn(FSTORE, kxVar);

        mv.visitVarInsn(FLOAD, 3); mv.visitInsn(F2I); mv.visitInsn(ICONST_3); mv.visitInsn(IAND);
        mv.visitInsn(I2F); mv.visitLdcInsn(0.25f); mv.visitInsn(FMUL);
        mv.visitVarInsn(FSTORE, kyVar);

        mv.visitVarInsn(FLOAD, 4); mv.visitInsn(F2I); mv.visitInsn(ICONST_3); mv.visitInsn(IAND);
        mv.visitInsn(I2F); mv.visitLdcInsn(0.25f); mv.visitInsn(FMUL);
        mv.visitVarInsn(FSTORE, kzVar);

        // kxi = 1 - kx, etc.
        mv.visitInsn(FCONST_1); mv.visitVarInsn(FLOAD, kxVar); mv.visitInsn(FSUB); mv.visitVarInsn(FSTORE, kxiVar);
        mv.visitInsn(FCONST_1); mv.visitVarInsn(FLOAD, kyVar); mv.visitInsn(FSUB); mv.visitVarInsn(FSTORE, kyiVar);
        mv.visitInsn(FCONST_1); mv.visitVarInsn(FLOAD, kzVar); mv.visitInsn(FSUB); mv.visitVarInsn(FSTORE, kziVar);

        // Evaluate 8 corners — each call passes explicit coordinates to df_N
        emitInterpolatedCorner(bx2Var, by2Var, bz2Var, c000, false, false, false, argument);
        emitInterpolatedCorner(bx2Var, by2Var, bz2Var, c100, true,  false, false, argument);
        emitInterpolatedCorner(bx2Var, by2Var, bz2Var, c010, false, true,  false, argument);
        emitInterpolatedCorner(bx2Var, by2Var, bz2Var, c110, true,  true,  false, argument);
        emitInterpolatedCorner(bx2Var, by2Var, bz2Var, c001, false, false, true,  argument);
        emitInterpolatedCorner(bx2Var, by2Var, bz2Var, c101, true,  false, true,  argument);
        emitInterpolatedCorner(bx2Var, by2Var, bz2Var, c011, false, true,  true,  argument);
        emitInterpolatedCorner(bx2Var, by2Var, bz2Var, c111, true,  true,  true,  argument);

        // Trilinear sum: c000*kxi*kyi*kzi + c100*kx*kyi*kzi + ... (8 terms)
        emitTrilinearTerm(c000, kxiVar, kyiVar, kziVar, false);
        emitTrilinearTerm(c100, kxVar,  kyiVar, kziVar, true);
        emitTrilinearTerm(c010, kxiVar, kyVar,  kziVar, true);
        emitTrilinearTerm(c110, kxVar,  kyVar,  kziVar, true);
        emitTrilinearTerm(c001, kxiVar, kyiVar, kzVar,  true);
        emitTrilinearTerm(c101, kxVar,  kyiVar, kzVar,  true);
        emitTrilinearTerm(c011, kxiVar, kyVar,  kzVar,  true);
        emitTrilinearTerm(c111, kxVar,  kyVar,  kzVar,  true);
    }

    /**
     * Evaluates argument at corner (bx2 [+4 if ox], by2 [+4 if oy], bz2 [+4 if oz])
     * via direct INVOKESPECIAL — no slot overrides of the current method's parameters.
     */
    private void emitInterpolatedCorner(int bx2, int by2, int bz2, int resultVar,
                                        boolean ox, boolean oy, boolean oz,
                                        IDensityFunction argument) {
        if (argument instanceof DensityFunctionRef ref) argument = ref.getFunction();
        int argIdx = nodeIndex.get(argument);

        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);

        // x = bx2 [+ 4] as float
        mv.visitVarInsn(ILOAD, bx2);
        if (ox) { mv.visitInsn(ICONST_4); mv.visitInsn(IADD); }
        mv.visitInsn(I2F);

        // y = by2 [+ 4] as float
        mv.visitVarInsn(ILOAD, by2);
        if (oy) { mv.visitInsn(ICONST_4); mv.visitInsn(IADD); }
        mv.visitInsn(I2F);

        // z = bz2 [+ 4] as float
        mv.visitVarInsn(ILOAD, bz2);
        if (oz) { mv.visitInsn(ICONST_4); mv.visitInsn(IADD); }
        mv.visitInsn(I2F);

        mv.visitMethodInsn(INVOKESPECIAL, className, nodeMethodName(argIdx), COMPUTE_DESC, false);
        mv.visitVarInsn(FSTORE, resultVar);
    }

    /** Emits one term of the trilinear sum: c * wa * wb * wc [, then FADD if addToStack]. */
    private void emitTrilinearTerm(int cVar, int waVar, int wbVar, int wcVar, boolean addToStack) {
        mv.visitVarInsn(FLOAD, cVar);
        mv.visitVarInsn(FLOAD, waVar); mv.visitInsn(FMUL);
        mv.visitVarInsn(FLOAD, wbVar); mv.visitInsn(FMUL);
        mv.visitVarInsn(FLOAD, wcVar); mv.visitInsn(FMUL);
        if (addToStack) mv.visitInsn(FADD);
    }

    // ── Cache emitters ───────────────────────────────────────────────────────

    @SuppressWarnings("rawtypes")
    private int addCacheSlot2D() {
        return addExtra(WorldContextImpl.allocateCacheSlot((java.util.Map m) -> m.clear()));
    }

    @SuppressWarnings("rawtypes")
    private int addCacheSlot3D() {
        return addExtra(WorldContextImpl.allocateCacheSlot((java.util.Map m) -> m.clear()));
    }

    /**
     * Cache2DFunc / FlatCacheUnary: lookup or create a {@code QuantizedFloatMap2D}, then
     * return the cached (x,z) value. When {@code flatY} is true the argument is evaluated
     * with y=0 (FlatCacheUnary semantics); no slot-3 override is performed.
     */
    private void emitCache2D(IDensityFunction argument, float resolution, boolean flatY) {
        int cacheSlotIdx = addCacheSlot2D();
        int slotVar = nextSlot++;
        int mapVar  = nextSlot++;
        int valVar  = nextSlot++;

        Label skipInit    = new Label();
        Label skipCompute = new Label();

        // slotVar = (CacheSlot) extras[cacheSlotIdx]
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "extras", EXTRAS_FIELD_DESC);
        pushInt(cacheSlotIdx);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, CACHE_SLOT);
        mv.visitVarInsn(ASTORE, slotVar);

        // mapVar = (Map2D) ctx.getCache(slotVar)
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, slotVar);
        mv.visitMethodInsn(INVOKEINTERFACE, CTX, "getCache",
                "(" + CACHE_SLOT_DESC + ")Ljava/lang/Object;", true);
        mv.visitTypeInsn(CHECKCAST, MAP_2D);
        mv.visitVarInsn(ASTORE, mapVar);

        // if mapVar != null, skip init
        mv.visitVarInsn(ALOAD, mapVar);
        mv.visitJumpInsn(IFNONNULL, skipInit);

        // mapVar = new QuantizedFloatMap2D(resolution)
        mv.visitTypeInsn(NEW, MAP_2D);
        mv.visitInsn(DUP);
        mv.visitLdcInsn(resolution);
        mv.visitMethodInsn(INVOKESPECIAL, MAP_2D, "<init>", "(F)V", false);
        mv.visitVarInsn(ASTORE, mapVar);

        // mapVar.defaultReturnValue(Float.NaN)
        mv.visitVarInsn(ALOAD, mapVar);
        mv.visitLdcInsn(Float.NaN);
        mv.visitMethodInsn(INVOKEVIRTUAL, MAP_2D, "defaultReturnValue", "(F)V", false);

        // ctx.setCache(slotVar, mapVar)
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, slotVar);
        mv.visitVarInsn(ALOAD, mapVar);
        mv.visitMethodInsn(INVOKEINTERFACE, CTX, "setCache",
                "(" + CACHE_SLOT_DESC + "Ljava/lang/Object;)V", true);

        mv.visitLabel(skipInit);

        // valVar = mapVar.get(blockX, blockZ)
        mv.visitVarInsn(ALOAD, mapVar);
        mv.visitVarInsn(FLOAD, 2);
        mv.visitVarInsn(FLOAD, 4);
        mv.visitMethodInsn(INVOKEVIRTUAL, MAP_2D, "get", "(FF)F", false);
        mv.visitVarInsn(FSTORE, valVar);

        // if !Float.isNaN(valVar) goto skipCompute
        mv.visitVarInsn(FLOAD, valVar);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "isNaN", "(F)Z", false);
        mv.visitJumpInsn(IFEQ, skipCompute);

        // Evaluate argument: flatY passes y=0, regular passes current y
        if (flatY) {
            emitChildCallAtY0(argument);
        } else {
            emitChildCall(argument);
        }
        mv.visitVarInsn(FSTORE, valVar);

        // mapVar.put(blockX, blockZ, valVar); discard old value
        mv.visitVarInsn(ALOAD, mapVar);
        mv.visitVarInsn(FLOAD, 2);
        mv.visitVarInsn(FLOAD, 4);
        mv.visitVarInsn(FLOAD, valVar);
        mv.visitMethodInsn(INVOKEVIRTUAL, MAP_2D, "put", "(FFF)F", false);
        mv.visitInsn(POP);

        mv.visitLabel(skipCompute);
        mv.visitVarInsn(FLOAD, valVar);
    }

    /**
     * CacheOnceUnary: lookup or create a {@code QuantizedFloatMap3D}, then
     * return the cached (x,y,z) value.
     */
    private void emitCacheOnce(IDensityFunction argument) {
        int cacheSlotIdx = addCacheSlot3D();
        int slotVar = nextSlot++;
        int mapVar  = nextSlot++;
        int valVar  = nextSlot++;

        Label skipInit    = new Label();
        Label skipCompute = new Label();

        // slotVar = (CacheSlot) extras[cacheSlotIdx]
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "extras", EXTRAS_FIELD_DESC);
        pushInt(cacheSlotIdx);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, CACHE_SLOT);
        mv.visitVarInsn(ASTORE, slotVar);

        // mapVar = (Map3D) ctx.getCache(slotVar)
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, slotVar);
        mv.visitMethodInsn(INVOKEINTERFACE, CTX, "getCache",
                "(" + CACHE_SLOT_DESC + ")Ljava/lang/Object;", true);
        mv.visitTypeInsn(CHECKCAST, MAP_3D);
        mv.visitVarInsn(ASTORE, mapVar);

        // if mapVar != null, skip init
        mv.visitVarInsn(ALOAD, mapVar);
        mv.visitJumpInsn(IFNONNULL, skipInit);

        // mapVar = new QuantizedFloatMap3D(1024)
        mv.visitTypeInsn(NEW, MAP_3D);
        mv.visitInsn(DUP);
        pushInt(1024);
        mv.visitMethodInsn(INVOKESPECIAL, MAP_3D, "<init>", "(I)V", false);
        mv.visitVarInsn(ASTORE, mapVar);

        // mapVar.defaultReturnValue(Float.NaN)
        mv.visitVarInsn(ALOAD, mapVar);
        mv.visitLdcInsn(Float.NaN);
        mv.visitMethodInsn(INVOKEVIRTUAL, MAP_3D, "defaultReturnValue", "(F)V", false);

        // ctx.setCache(slotVar, mapVar)
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, slotVar);
        mv.visitVarInsn(ALOAD, mapVar);
        mv.visitMethodInsn(INVOKEINTERFACE, CTX, "setCache",
                "(" + CACHE_SLOT_DESC + "Ljava/lang/Object;)V", true);

        mv.visitLabel(skipInit);

        // valVar = mapVar.get(blockX, blockY, blockZ)
        mv.visitVarInsn(ALOAD, mapVar);
        mv.visitVarInsn(FLOAD, 2);
        mv.visitVarInsn(FLOAD, 3);
        mv.visitVarInsn(FLOAD, 4);
        mv.visitMethodInsn(INVOKEVIRTUAL, MAP_3D, "get", "(FFF)F", false);
        mv.visitVarInsn(FSTORE, valVar);

        // if !Float.isNaN(valVar) goto skipCompute
        mv.visitVarInsn(FLOAD, valVar);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "isNaN", "(F)Z", false);
        mv.visitJumpInsn(IFEQ, skipCompute);

        emitChildCall(argument);
        mv.visitVarInsn(FSTORE, valVar);

        // mapVar.put(blockX, blockY, blockZ, valVar); discard old value
        mv.visitVarInsn(ALOAD, mapVar);
        mv.visitVarInsn(FLOAD, 2);
        mv.visitVarInsn(FLOAD, 3);
        mv.visitVarInsn(FLOAD, 4);
        mv.visitVarInsn(FLOAD, valVar);
        mv.visitMethodInsn(INVOKEVIRTUAL, MAP_3D, "put", "(FFFF)F", false);
        mv.visitInsn(POP);

        mv.visitLabel(skipCompute);
        mv.visitVarInsn(FLOAD, valVar);
    }

    // ── Noise emitters ───────────────────────────────────────────────────────

    /**
     * Emits the noise-sampler lookup preamble.
     * Leaves a {@code NoiseSampler} reference on the operand stack, ready for a
     * {@code sample(DDD)D} call. Allocates two local variable slots internally.
     *
     * @param slotIdx index into {@code extras[]} where the {@code StateSlot} lives;
     *                {@code extras[slotIdx+1]} must be the noise name {@code String}.
     */
    private void emitNoiseLookup(int slotIdx) {
        int nameIdx  = slotIdx + 1;
        int slotVar  = nextSlot++;   // Object (StateSlot<?>)
        int rawVar   = nextSlot++;   // Object (NoiseSampler or null)

        Label gotSampler = new Label();

        // slot = (StateSlot) extras[slotIdx]
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "extras", EXTRAS_FIELD_DESC);
        pushInt(slotIdx);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, STATE_SLOT);
        mv.visitVarInsn(ASTORE, slotVar);

        // raw = ctx.getState(slot)
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, slotVar);
        mv.visitMethodInsn(INVOKEINTERFACE, CTX, "getState",
                "(" + STATE_SLOT_DESC + ")Ljava/lang/Object;", true);
        mv.visitVarInsn(ASTORE, rawVar);

        // if (raw != null) goto gotSampler
        mv.visitVarInsn(ALOAD, rawVar);
        mv.visitJumpInsn(IFNONNULL, gotSampler);

        // raw = DatapackNoiseList.RT.getHandler().getSampler(ctx.getDimensionSeed(), name)
        mv.visitFieldInsn(GETSTATIC, NOISE_HANDLER, "RT",
                "L" + RESOURCE_TYPE + ";");
        mv.visitMethodInsn(INVOKEINTERFACE, RESOURCE_TYPE, "getHandler",
                "()L" + I_HANDLER + ";", true);
        mv.visitTypeInsn(CHECKCAST, NOISE_HANDLER);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEINTERFACE, CTX, "getDimensionSeed", "()J", true);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "extras", EXTRAS_FIELD_DESC);
        pushInt(nameIdx);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, "java/lang/String");
        mv.visitMethodInsn(INVOKEVIRTUAL, NOISE_HANDLER, "getSampler",
                "(JLjava/lang/String;)" + NOISE_SAMPLER_DESC, false);
        mv.visitVarInsn(ASTORE, rawVar);

        // ctx.setState(slot, raw)
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, slotVar);
        mv.visitVarInsn(ALOAD, rawVar);
        mv.visitMethodInsn(INVOKEINTERFACE, CTX, "setState",
                "(" + STATE_SLOT_DESC + "Ljava/lang/Object;)V", true);

        mv.visitLabel(gotSampler);
        mv.visitVarInsn(ALOAD, rawVar);
        mv.visitTypeInsn(CHECKCAST, NOISE_SAMPLER);
        // → NoiseSampler on stack
    }

    /** Emits: {@code (float)(sampler.sample(x * xzScale, y * yScale, z * xzScale))} */
    private void emitNoiseSampleXYZ(int slotIdx, double xzScale, double yScale) {
        emitNoiseLookup(slotIdx);
        mv.visitVarInsn(FLOAD, 2); mv.visitInsn(F2D); mv.visitLdcInsn(xzScale); mv.visitInsn(DMUL);
        mv.visitVarInsn(FLOAD, 3); mv.visitInsn(F2D); mv.visitLdcInsn(yScale);  mv.visitInsn(DMUL);
        mv.visitVarInsn(FLOAD, 4); mv.visitInsn(F2D); mv.visitLdcInsn(xzScale); mv.visitInsn(DMUL);
        mv.visitMethodInsn(INVOKEINTERFACE, NOISE_SAMPLER, "sample", "(DDD)D", true);
        mv.visitInsn(D2F);
    }

    /** NoiseFunc: {@code sample(x * xz_scale, y * y_scale, z * xz_scale)} */
    private void emitNoiseFunc(IDensityFunction node) {
        String noiseName = getDF(node, "noise");
        double xzScale   = getFloat(node, "xz_scale");
        double yScale    = getFloat(node, "y_scale");
        int    slotIdx   = addNoiseSlot(noiseName);
        emitNoiseSampleXYZ(slotIdx, xzScale, yScale);
    }

    /** ShiftFunc: {@code sample(x/4, y/4, z/4) * 4} */
    private void emitShiftFunc(IDensityFunction node) {
        String noiseName = getDF(node, "argument");
        int    slotIdx   = addNoiseSlot(noiseName);
        emitNoiseLookup(slotIdx);
        mv.visitVarInsn(FLOAD, 2); mv.visitInsn(F2D); mv.visitLdcInsn(0.25); mv.visitInsn(DMUL);
        mv.visitVarInsn(FLOAD, 3); mv.visitInsn(F2D); mv.visitLdcInsn(0.25); mv.visitInsn(DMUL);
        mv.visitVarInsn(FLOAD, 4); mv.visitInsn(F2D); mv.visitLdcInsn(0.25); mv.visitInsn(DMUL);
        mv.visitMethodInsn(INVOKEINTERFACE, NOISE_SAMPLER, "sample", "(DDD)D", true);
        mv.visitInsn(D2F);
        mv.visitLdcInsn(4.0f); mv.visitInsn(FMUL);
    }

    /** ShiftAFunc: {@code sample(x/4, 0, z/4) * 4} */
    private void emitShiftAFunc(IDensityFunction node) {
        String noiseName = getDF(node, "argument");
        int    slotIdx   = addNoiseSlot(noiseName);
        emitNoiseLookup(slotIdx);
        mv.visitVarInsn(FLOAD, 2); mv.visitInsn(F2D); mv.visitLdcInsn(0.25); mv.visitInsn(DMUL);
        mv.visitInsn(DCONST_0);
        mv.visitVarInsn(FLOAD, 4); mv.visitInsn(F2D); mv.visitLdcInsn(0.25); mv.visitInsn(DMUL);
        mv.visitMethodInsn(INVOKEINTERFACE, NOISE_SAMPLER, "sample", "(DDD)D", true);
        mv.visitInsn(D2F);
        mv.visitLdcInsn(4.0f); mv.visitInsn(FMUL);
    }

    /** ShiftBFunc: {@code sample(z/4, x/4, 0) * 4} */
    private void emitShiftBFunc(IDensityFunction node) {
        String noiseName = getDF(node, "argument");
        int    slotIdx   = addNoiseSlot(noiseName);
        emitNoiseLookup(slotIdx);
        mv.visitVarInsn(FLOAD, 4); mv.visitInsn(F2D); mv.visitLdcInsn(0.25); mv.visitInsn(DMUL);
        mv.visitVarInsn(FLOAD, 2); mv.visitInsn(F2D); mv.visitLdcInsn(0.25); mv.visitInsn(DMUL);
        mv.visitInsn(DCONST_0);
        mv.visitMethodInsn(INVOKEINTERFACE, NOISE_SAMPLER, "sample", "(DDD)D", true);
        mv.visitInsn(D2F);
        mv.visitLdcInsn(4.0f); mv.visitInsn(FMUL);
    }

    /** ShiftedNoiseFunc: {@code sample((x+sx)*xz_scale, (y+sy)*y_scale, (z+sz)*xz_scale)} */
    private void emitShiftedNoiseFunc(IDensityFunction node) {
        String           noiseName = getDF(node, "noise");
        double           xzScale   = getFloat(node, "xz_scale");
        double           yScale    = getFloat(node, "y_scale");
        IDensityFunction shiftX    = getDF(node, "shift_x");
        IDensityFunction shiftY    = getDF(node, "shift_y");
        IDensityFunction shiftZ    = getDF(node, "shift_z");

        int slotIdx = addNoiseSlot(noiseName);
        int sxVar   = nextSlot++;
        int syVar   = nextSlot++;
        int szVar   = nextSlot++;

        emitChildCall(shiftX); mv.visitInsn(F2I); mv.visitVarInsn(ISTORE, sxVar);
        emitChildCall(shiftY); mv.visitInsn(F2I); mv.visitVarInsn(ISTORE, syVar);
        emitChildCall(shiftZ); mv.visitInsn(F2I); mv.visitVarInsn(ISTORE, szVar);

        emitNoiseLookup(slotIdx);

        mv.visitVarInsn(FLOAD, 2); mv.visitVarInsn(ILOAD, sxVar); mv.visitInsn(I2F); mv.visitInsn(FADD);
        mv.visitInsn(F2D); mv.visitLdcInsn(xzScale); mv.visitInsn(DMUL);

        mv.visitVarInsn(FLOAD, 3); mv.visitVarInsn(ILOAD, syVar); mv.visitInsn(I2F); mv.visitInsn(FADD);
        mv.visitInsn(F2D); mv.visitLdcInsn(yScale);  mv.visitInsn(DMUL);

        mv.visitVarInsn(FLOAD, 4); mv.visitVarInsn(ILOAD, szVar); mv.visitInsn(I2F); mv.visitInsn(FADD);
        mv.visitInsn(F2D); mv.visitLdcInsn(xzScale); mv.visitInsn(DMUL);

        mv.visitMethodInsn(INVOKEINTERFACE, NOISE_SAMPLER, "sample", "(DDD)D", true);
        mv.visitInsn(D2F);
    }

    /**
     * WeirdScaledSampler: rarity_value_mapper known at compile time → unrolled comparisons.
     * Result: rarity * sample(x * rarityInv, y * rarityInv, z * rarityInv)
     */
    private void emitWeirdScaledSampler(IDensityFunction node) {
        RarityType       rarityType = getDF(node, "rarity_value_mapper");
        String           noiseName  = getDF(node, "noise");
        IDensityFunction input      = getDF(node, "input");

        int slotIdx   = addNoiseSlot(noiseName);
        int valVar    = nextSlot++;
        int rarityVar = nextSlot++;
        int invVar    = nextSlot++;

        emitChildCall(input);
        mv.visitVarInsn(FSTORE, valVar);

        // Default: rarity = 1, rarityInv = 1
        mv.visitInsn(FCONST_1); mv.visitVarInsn(FSTORE, rarityVar);
        mv.visitInsn(FCONST_1); mv.visitVarInsn(FSTORE, invVar);

        emitRarityLogic(rarityType, valVar, rarityVar, invVar);

        // sampler.sample(x * rarityInv, y * rarityInv, z * rarityInv)
        emitNoiseLookup(slotIdx);
        mv.visitVarInsn(FLOAD, 2); mv.visitVarInsn(FLOAD, invVar); mv.visitInsn(FMUL);
        mv.visitInsn(F2D);
        mv.visitVarInsn(FLOAD, 3); mv.visitVarInsn(FLOAD, invVar); mv.visitInsn(FMUL);
        mv.visitInsn(F2D);
        mv.visitVarInsn(FLOAD, 4); mv.visitVarInsn(FLOAD, invVar); mv.visitInsn(FMUL);
        mv.visitInsn(F2D);
        mv.visitMethodInsn(INVOKEINTERFACE, NOISE_SAMPLER, "sample", "(DDD)D", true);
        mv.visitInsn(D2F);
        mv.visitVarInsn(FLOAD, rarityVar);
        mv.visitInsn(FMUL);
    }

    /**
     * Emits the rarity/rarityInv computation for WeirdScaledSampler.
     * Reads {@code valVar}, writes {@code rarityVar} and {@code invVar}.
     */
    private void emitRarityLogic(RarityType type, int valVar, int rarityVar, int invVar) {
        Label doSample = new Label();

        if (type == RarityType.type_1) {
            Label l1 = new Label(), l2 = new Label(), l3 = new Label(), l4 = new Label();

            // val < -0.75 → rarity=0.5, inv=2
            mv.visitVarInsn(FLOAD, valVar); mv.visitLdcInsn(-0.75f); mv.visitInsn(FCMPG);
            mv.visitJumpInsn(IFGE, l1);
            mv.visitLdcInsn(0.5f);  mv.visitVarInsn(FSTORE, rarityVar);
            mv.visitInsn(FCONST_2); mv.visitVarInsn(FSTORE, invVar);
            mv.visitJumpInsn(GOTO, doSample);

            mv.visitLabel(l1);
            // val < -0.5 → rarity=0.75, inv=4/3
            mv.visitVarInsn(FLOAD, valVar); mv.visitLdcInsn(-0.5f); mv.visitInsn(FCMPG);
            mv.visitJumpInsn(IFGE, l2);
            mv.visitLdcInsn(0.75f);            mv.visitVarInsn(FSTORE, rarityVar);
            mv.visitLdcInsn(4f / 3f);          mv.visitVarInsn(FSTORE, invVar);
            mv.visitJumpInsn(GOTO, doSample);

            mv.visitLabel(l2);
            // val < 0.5 → defaults (already set), goto doSample
            mv.visitVarInsn(FLOAD, valVar); mv.visitLdcInsn(0.5f); mv.visitInsn(FCMPG);
            mv.visitJumpInsn(IFGE, l3);
            mv.visitJumpInsn(GOTO, doSample);

            mv.visitLabel(l3);
            // val < 0.75 → rarity=2, inv=0.5
            mv.visitVarInsn(FLOAD, valVar); mv.visitLdcInsn(0.75f); mv.visitInsn(FCMPG);
            mv.visitJumpInsn(IFGE, l4);
            mv.visitInsn(FCONST_2); mv.visitVarInsn(FSTORE, rarityVar);
            mv.visitLdcInsn(0.5f);  mv.visitVarInsn(FSTORE, invVar);
            mv.visitJumpInsn(GOTO, doSample);

            mv.visitLabel(l4);
            // val >= 0.75 → rarity=3, inv=1/3
            mv.visitLdcInsn(3f);       mv.visitVarInsn(FSTORE, rarityVar);
            mv.visitLdcInsn(1f / 3f);  mv.visitVarInsn(FSTORE, invVar);

        } else { // type_2
            Label l1 = new Label(), l2 = new Label(), l3 = new Label();

            // val < -0.5 → rarity=0.75, inv=4/3
            mv.visitVarInsn(FLOAD, valVar); mv.visitLdcInsn(-0.5f); mv.visitInsn(FCMPG);
            mv.visitJumpInsn(IFGE, l1);
            mv.visitLdcInsn(0.75f);   mv.visitVarInsn(FSTORE, rarityVar);
            mv.visitLdcInsn(4f / 3f); mv.visitVarInsn(FSTORE, invVar);
            mv.visitJumpInsn(GOTO, doSample);

            mv.visitLabel(l1);
            // -0.5 <= val < 0 → defaults (rarity=1, inv=1 already set)
            mv.visitVarInsn(FLOAD, valVar); mv.visitInsn(FCONST_0); mv.visitInsn(FCMPG);
            mv.visitJumpInsn(IFGE, l2);          // val >= 0 → skip defaults
            mv.visitJumpInsn(GOTO, doSample);    // val < 0: use defaults

            mv.visitLabel(l2);
            // 0 <= val < 0.5 → rarity=1.5, inv=2/3
            mv.visitVarInsn(FLOAD, valVar); mv.visitLdcInsn(0.5f); mv.visitInsn(FCMPG);
            mv.visitJumpInsn(IFGE, l3);
            mv.visitLdcInsn(1.5f);    mv.visitVarInsn(FSTORE, rarityVar);
            mv.visitLdcInsn(2f / 3f); mv.visitVarInsn(FSTORE, invVar);
            mv.visitJumpInsn(GOTO, doSample);

            mv.visitLabel(l3);
            // val >= 0.5 → rarity=2, inv=0.5
            mv.visitInsn(FCONST_2); mv.visitVarInsn(FSTORE, rarityVar);
            mv.visitLdcInsn(0.5f);  mv.visitVarInsn(FSTORE, invVar);
            // falls through to doSample
        }

        mv.visitLabel(doSample);
    }

    // ── SplineCurve emitter ───────────────────────────────────────────────────

    /**
     * Emits the SplineCurve computation with all point locations/derivatives baked in as
     * LDC constants. Segment selection is unrolled; child value nodes are called via
     * emitChildCall (INVOKESPECIAL to their df_N method).
     */
    private void emitSplineCurve(IDensityFunction node) {
        IDensityFunction coordinate = getDF(node, "coordinate");
        SplinePoint[]    points     = getDF(node, "points");
        int              N          = points.length;

        int tVar    = nextSlot++;
        int invDxVar = nextSlot++;
        int uVar    = nextSlot++;
        int u2Var   = nextSlot++;
        int u3Var   = nextSlot++;
        int f0Var   = nextSlot++;
        int f1Var   = nextSlot++;

        Label end = new Label();

        emitChildCall(coordinate);
        mv.visitVarInsn(FSTORE, tVar);

        if (N == 0) {
            // Degenerate: return 0
            mv.visitInsn(FCONST_0);
            return;
        }

        // Lower clamp: t <= points[0].location → return points[0].value
        Label afterLower = new Label();
        mv.visitVarInsn(FLOAD, tVar);
        mv.visitLdcInsn(getFloat(points[0], "location"));
        mv.visitInsn(FCMPG);                        // 1 if t < loc0
        mv.visitJumpInsn(IFGT, afterLower);          // if t > loc0, skip
        emitChildCall(getDF(points[0], "value"));
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(afterLower);

        if (N == 1) {
            // Single point: already handled by clamp above
            mv.visitInsn(FCONST_0);                 // fallback (never reached)
            mv.visitLabel(end);
            return;
        }

        // Upper clamp: t >= points[N-1].location → return points[N-1].value
        Label afterUpper = new Label();
        mv.visitVarInsn(FLOAD, tVar);
        mv.visitLdcInsn(getFloat(points[N - 1], "location"));
        mv.visitInsn(FCMPL);                        // -1 if t > locN-1
        mv.visitJumpInsn(IFLT, afterUpper);          // if t < locN-1, skip
        emitChildCall(getDF(points[N - 1], "value"));
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(afterUpper);

        // Interior segments [0, N-2]
        for (int i = 0; i < N - 1; i++) {
            float loc0   = getFloat(points[i],     "location");
            float loc1   = getFloat(points[i + 1], "location");
            float deriv0 = getFloat(points[i],     "derivative");
            float deriv1 = getFloat(points[i + 1], "derivative");
            float dx     = loc1 - loc0;
            float k0     = dx * deriv0;   // pre-multiplied constant
            float k1     = dx * deriv1;

            Label nextSeg = (i < N - 2) ? new Label() : null;

            // if t >= points[i+1].location → try next segment
            if (nextSeg != null) {
                mv.visitVarInsn(FLOAD, tVar);
                mv.visitLdcInsn(loc1);
                mv.visitInsn(FCMPG);
                mv.visitJumpInsn(IFGE, nextSeg);
            }

            // invDx = 1 / dx (baked in)
            mv.visitLdcInsn(1f / dx);
            mv.visitVarInsn(FSTORE, invDxVar);

            // u = (t - loc0) / dx
            mv.visitVarInsn(FLOAD, tVar);
            mv.visitLdcInsn(loc0);
            mv.visitInsn(FSUB);
            mv.visitVarInsn(FLOAD, invDxVar);
            mv.visitInsn(FMUL);
            mv.visitVarInsn(FSTORE, uVar);

            // u2 = u * u, u3 = u2 * u
            mv.visitVarInsn(FLOAD, uVar); mv.visitVarInsn(FLOAD, uVar); mv.visitInsn(FMUL);
            mv.visitVarInsn(FSTORE, u2Var);
            mv.visitVarInsn(FLOAD, u2Var); mv.visitVarInsn(FLOAD, uVar); mv.visitInsn(FMUL);
            mv.visitVarInsn(FSTORE, u3Var);

            // f0 = points[i].value.compute(), f1 = points[i+1].value.compute()
            emitChildCall(getDF(points[i],     "value")); mv.visitVarInsn(FSTORE, f0Var);
            emitChildCall(getDF(points[i + 1], "value")); mv.visitVarInsn(FSTORE, f1Var);

            // Cubic Hermite interpolation
            emitHermite(uVar, u2Var, u3Var, f0Var, f1Var, k0, k1);

            mv.visitJumpInsn(GOTO, end);

            if (nextSeg != null) mv.visitLabel(nextSeg);
        }

        // Fallback (shouldn't be reached with well-formed data, but needed for bytecode legality)
        emitChildCall(getDF(points[N - 1], "value"));

        mv.visitLabel(end);
    }

    /** Emits the cubic Hermite polynomial evaluation. Leaves result float on stack. */
    private void emitHermite(int uVar, int u2Var, int u3Var, int f0Var, int f1Var,
                              float k0, float k1) {
        // term1: (2u3 - 3u2 + 1) * f0
        mv.visitInsn(FCONST_2); mv.visitVarInsn(FLOAD, u3Var); mv.visitInsn(FMUL);
        mv.visitLdcInsn(3f);    mv.visitVarInsn(FLOAD, u2Var); mv.visitInsn(FMUL); mv.visitInsn(FSUB);
        mv.visitInsn(FCONST_1); mv.visitInsn(FADD);
        mv.visitVarInsn(FLOAD, f0Var); mv.visitInsn(FMUL);
        // → term1 on stack

        // + (u3 - 2u2 + u) * k0
        mv.visitVarInsn(FLOAD, u3Var);
        mv.visitInsn(FCONST_2); mv.visitVarInsn(FLOAD, u2Var); mv.visitInsn(FMUL); mv.visitInsn(FSUB);
        mv.visitVarInsn(FLOAD, uVar); mv.visitInsn(FADD);
        mv.visitLdcInsn(k0); mv.visitInsn(FMUL);
        mv.visitInsn(FADD);

        // + (-2u3 + 3u2) * f1
        mv.visitLdcInsn(-2f); mv.visitVarInsn(FLOAD, u3Var); mv.visitInsn(FMUL);
        mv.visitLdcInsn(3f);  mv.visitVarInsn(FLOAD, u2Var); mv.visitInsn(FMUL); mv.visitInsn(FADD);
        mv.visitVarInsn(FLOAD, f1Var); mv.visitInsn(FMUL);
        mv.visitInsn(FADD);

        // + (u3 - u2) * k1
        mv.visitVarInsn(FLOAD, u3Var); mv.visitVarInsn(FLOAD, u2Var); mv.visitInsn(FSUB);
        mv.visitLdcInsn(k1); mv.visitInsn(FMUL);
        mv.visitInsn(FADD);
        // → final result on stack
    }

    // ── Existing emitters ────────────────────────────────────────────────────

    /**
     * Emits: {@code arg < 0 ? arg * scale : arg}
     * Precondition: {@code arg} has been stored in {@code slot}.
     * Stack effect: [] → [float]
     */
    private void emitConditionalScale(int slot, float scale) {
        Label positive = new Label();
        Label end = new Label();

        mv.visitVarInsn(FLOAD, slot);
        mv.visitInsn(FCONST_0);
        mv.visitInsn(FCMPG);
        mv.visitJumpInsn(IFGE, positive);

        // Negative branch: arg * scale
        mv.visitVarInsn(FLOAD, slot);
        mv.visitLdcInsn(scale);
        mv.visitInsn(FMUL);
        mv.visitJumpInsn(GOTO, end);

        // Non-negative / NaN branch: return arg unchanged
        mv.visitLabel(positive);
        mv.visitVarInsn(FLOAD, slot);

        mv.visitLabel(end);
    }

    /**
     * Emits the squeeze operation.
     * Precondition: the argument float is on top of the operand stack.
     * Stack effect: [float] → [float]
     *
     * <p>Equivalent to: {@code t = clamp(x, -1, 1); return t/2 - t*t*t/24}
     */
    private void emitSqueeze() {
        // Stack: [arg]
        mv.visitLdcInsn(-1.0f);
        mv.visitLdcInsn(1.0f);
        mv.visitMethodInsn(INVOKESTATIC, MATHHELPER, "clamp_float", "(FFF)F", false);
        // Stack: [t]
        int t = nextSlot++;
        mv.visitVarInsn(FSTORE, t);

        // t / 2
        mv.visitVarInsn(FLOAD, t);
        mv.visitLdcInsn(2.0f);
        mv.visitInsn(FDIV);
        // Stack: [t/2]

        // t^3 / 24
        mv.visitVarInsn(FLOAD, t);
        mv.visitVarInsn(FLOAD, t);
        mv.visitInsn(FMUL);
        mv.visitVarInsn(FLOAD, t);
        mv.visitInsn(FMUL);
        mv.visitLdcInsn(24.0f);
        mv.visitInsn(FDIV);
        // Stack: [t/2, t^3/24]

        mv.visitInsn(FSUB);
        // Stack: [t/2 - t^3/24]
    }

    // ── Opaque call ───────────────────────────────────────────────────────────

    /**
     * Emits: {@code opaques[index].compute(ctx, x, y, z)}
     * Stack effect: [] → [float]
     */
    private void emitOpaqueCall(int index) {
        mv.visitVarInsn(ALOAD, 0);   // this
        mv.visitFieldInsn(GETFIELD, className, "opaques", IFACE_ARRAY_DESC);
        pushInt(index);
        mv.visitInsn(AALOAD);        // opaques[index]
        mv.visitVarInsn(ALOAD, 1);   // ctx
        mv.visitVarInsn(FLOAD, 2);   // x
        mv.visitVarInsn(FLOAD, 3);   // y
        mv.visitVarInsn(FLOAD, 4);   // z
        mv.visitMethodInsn(INVOKEINTERFACE, IFACE, "compute", COMPUTE_DESC, true);
    }

    private int addOpaque(IDensityFunction node) {
        LOGGER.warn("DensityFunctionCompiler: uncompiled opaque node type '{}' — falling back to virtual dispatch",
                node.getClass().getSimpleName());
        int idx = opaques.size();
        opaques.add(node);
        return idx;
    }

    /** Stores an arbitrary object in {@code extras[]} and returns its index. */
    private int addExtra(Object o) {
        int idx = extras.size();
        extras.add(o);
        return idx;
    }

    /**
     * Stores a freshly allocated {@link StateSlot} and the noise name in consecutive
     * {@code extras[]} slots, then returns the slot index.
     * The name is always at {@code slotIdx + 1}.
     */
    private int addNoiseSlot(String noiseName) {
        int slotIdx = addExtra(WorldContextImpl.allocateSlot());
        addExtra(noiseName);
        return slotIdx;
    }

    // ── Constructor / instantiation ───────────────────────────────────────────

    private void emitConstructor(ClassWriter cw) {
        boolean hasOpaques = !opaques.isEmpty();
        boolean hasExtras  = !extras.isEmpty();

        String desc;
        if (!hasOpaques && !hasExtras) {
            desc = "()V";
        } else if (hasOpaques && !hasExtras) {
            desc = "(" + IFACE_ARRAY_DESC + ")V";
        } else if (!hasOpaques) {
            desc = "(" + EXTRAS_FIELD_DESC + ")V";
        } else {
            desc = "(" + IFACE_ARRAY_DESC + EXTRAS_FIELD_DESC + ")V";
        }

        MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", desc, null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        int slot = 1;
        if (hasOpaques) {
            init.visitVarInsn(ALOAD, 0);
            init.visitVarInsn(ALOAD, slot++);
            init.visitFieldInsn(PUTFIELD, className, "opaques", IFACE_ARRAY_DESC);
        }
        if (hasExtras) {
            init.visitVarInsn(ALOAD, 0);
            init.visitVarInsn(ALOAD, slot);
            init.visitFieldInsn(PUTFIELD, className, "extras", EXTRAS_FIELD_DESC);
        }

        init.visitInsn(RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
    }

    private IDensityFunction instantiate(byte[] bytes, int id) {
        try {
            CompiledDFLoader loader = new CompiledDFLoader();
            String dotName = "databack.compiled.CompiledDF_" + id;
            Class<?> cls = loader.define(dotName, bytes);

            boolean hasOpaques = !opaques.isEmpty();
            boolean hasExtras  = !extras.isEmpty();

            IDensityFunction[] opaquesArr = hasOpaques ? opaques.toArray(new IDensityFunction[0]) : null;
            Object[]           extrasArr  = hasExtras  ? extras.toArray()                         : null;

            if (!hasOpaques && !hasExtras) {
                return (IDensityFunction) cls.getDeclaredConstructor().newInstance();
            } else if (hasOpaques && !hasExtras) {
                return (IDensityFunction) cls.getDeclaredConstructor(IDensityFunction[].class)
                        .newInstance((Object) opaquesArr);
            } else if (!hasOpaques) {
                return (IDensityFunction) cls.getDeclaredConstructor(Object[].class)
                        .newInstance((Object) extrasArr);
            } else {
                return (IDensityFunction) cls.getDeclaredConstructor(IDensityFunction[].class, Object[].class)
                        .newInstance(opaquesArr, extrasArr);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate compiled density function", e);
        }
    }

    // ── JVM helpers ──────────────────────────────────────────────────────────

    /** Returns the name of the private method for node {@code idx}: {@code df_N_ClassName}. */
    private String nodeMethodName(int idx) {
        // Strip characters illegal in JVM method names (e.g. '/' in lambda class names).
        String simpleName = nodes.get(idx).getClass().getSimpleName()
                .replaceAll("[^A-Za-z0-9_$]", "_");
        return "df_" + idx + "_" + simpleName;
    }

    /** Emits the most compact integer push instruction for {@code value}. */
    private void pushInt(int value) {
        switch (value) {
            case -1 -> mv.visitInsn(ICONST_M1);
            case  0 -> mv.visitInsn(ICONST_0);
            case  1 -> mv.visitInsn(ICONST_1);
            case  2 -> mv.visitInsn(ICONST_2);
            case  3 -> mv.visitInsn(ICONST_3);
            case  4 -> mv.visitInsn(ICONST_4);
            case  5 -> mv.visitInsn(ICONST_5);
            default -> {
                if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE)
                    mv.visitIntInsn(BIPUSH, value);
                else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE)
                    mv.visitIntInsn(SIPUSH, value);
                else
                    mv.visitLdcInsn(value);
            }
        }
    }

    // ── Reflection field access ───────────────────────────────────────────────

    private static float getFloat(Object obj, String fieldName) {
        return ((Number) getFieldValue(obj, fieldName)).floatValue();
    }

    private static int getInt(Object obj, String fieldName) {
        return ((Number) getFieldValue(obj, fieldName)).intValue();
    }

    @SuppressWarnings("unchecked")
    static <T> T getDF(Object obj, String fieldName) {
        return (T) getFieldValue(obj, fieldName);
    }

    private static Object getFieldValue(Object obj, String fieldName) {
        Class<?> cls = obj.getClass();
        while (cls != null) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot access field '" + fieldName + "' on " + obj.getClass(), e);
            }
        }
        throw new RuntimeException("Field '" + fieldName + "' not found on " + obj.getClass());
    }

    // ── Class dumping ─────────────────────────────────────────────────────────

    private void dumpClass(byte[] bytes, int id) {
        File outFile = new File(DatabackConfig.compiledDensityFunctionDumpDir,
                className.replace('/', File.separatorChar) + ".class");
        try {
            outFile.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(bytes);
            }
            LOGGER.debug("Dumped CompiledDF_{} to {}", id, outFile.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.warn("Failed to dump CompiledDF_{} to {}: {}", id, outFile.getAbsolutePath(), e.getMessage());
        }
    }

    // ── ClassLoader ───────────────────────────────────────────────────────────

    private static final class CompiledDFLoader extends ClassLoader {

        CompiledDFLoader() {
            super(DensityFunctionCompiler.class.getClassLoader());
        }

        Class<?> define(String dotName, byte[] bytes) {
            return defineClass(dotName, bytes, 0, bytes.length);
        }
    }
}
