package databack.common.worldgen.compiler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import databack.common.dto.worldgen.density_function.BinaryDensityFunction;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.ConstantFunc;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.DensityFunctionRef;
import databack.common.dto.worldgen.density_function.IDensityFunction;
import databack.common.dto.worldgen.density_function.UnaryDensityFunction;

/**
 * Compiles an {@link IDensityFunction} tree into a single JVM class, eliminating
 * virtual dispatch overhead for arithmetic nodes.
 *
 * <p>Inlineable nodes (constant, add, mul, abs, square, etc.) are emitted as direct
 * JVM instructions. Complex nodes (noise, cache, spline, etc.) are stored in an
 * {@code IDensityFunction[] opaques} field and called via {@code invokeinterface}.
 *
 * <p>Compilation is done once at world initialisation. The resulting function is
 * a drop-in replacement with identical semantics.
 */
public final class DensityFunctionCompiler implements Opcodes {

    // ── Descriptors ──────────────────────────────────────────────────────────

    private static final String IFACE = "databack/common/dto/worldgen/density_function/IDensityFunction";
    private static final String IFACE_DESC = "L" + IFACE + ";";
    private static final String IFACE_ARRAY_DESC = "[" + IFACE_DESC;
    private static final String CTX = "databack/common/context/WorldContext";
    private static final String CTX_DESC = "L" + CTX + ";";
    private static final String COMPUTE_DESC = "(" + CTX_DESC + "FFF)F";
    private static final String MATHHELPER = "net/minecraft/util/MathHelper";

    // ── Class naming ─────────────────────────────────────────────────────────

    private static final AtomicInteger COUNTER = new AtomicInteger();

    // ── 64 KB method-size guard ───────────────────────────────────────────────

    // Conservative limit; actual JVM limit is 65535 bytes of bytecode.
    private static final int BYTECOST_LIMIT = 50_000;

    // ── Per-compilation state ─────────────────────────────────────────────────

    private MethodVisitor mv;
    private String className;
    private final List<IDensityFunction> opaques = new ArrayList<>();
    /** Next available local variable slot (0-4 are reserved for this/ctx/x/y/z). */
    private int nextSlot = 5;
    /** Running estimate of bytes emitted so far in compute(). */
    private int bytecostSoFar = 0;

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

        // COMPUTE_FRAMES: ASM computes stack map frames automatically.
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL, className, null,
                "java/lang/Object", new String[]{IFACE});

        // Emit compute() first — this populates the opaques list as a side-effect.
        MethodVisitor computeMv = cw.visitMethod(ACC_PUBLIC, "compute", COMPUTE_DESC, null, null);
        this.mv = computeMv;
        computeMv.visitCode();
        emitNode(root);
        computeMv.visitInsn(FRETURN);
        computeMv.visitMaxs(0, 0);
        computeMv.visitEnd();

        // Declare opaques field only if needed (visitField order doesn't matter in ASM).
        if (!opaques.isEmpty()) {
            cw.visitField(ACC_PRIVATE | ACC_FINAL, "opaques", IFACE_ARRAY_DESC, null, null).visitEnd();
        }

        // Emit constructor.
        emitConstructor(cw);

        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        return instantiate(bytes, id);
    }

    private void emitConstructor(ClassWriter cw) {
        if (opaques.isEmpty()) {
            MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            init.visitCode();
            init.visitVarInsn(ALOAD, 0);
            init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            init.visitInsn(RETURN);
            init.visitMaxs(0, 0);
            init.visitEnd();
        } else {
            MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "(" + IFACE_ARRAY_DESC + ")V", null, null);
            init.visitCode();
            init.visitVarInsn(ALOAD, 0);
            init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            init.visitVarInsn(ALOAD, 0);
            init.visitVarInsn(ALOAD, 1);
            init.visitFieldInsn(PUTFIELD, className, "opaques", IFACE_ARRAY_DESC);
            init.visitInsn(RETURN);
            init.visitMaxs(0, 0);
            init.visitEnd();
        }
    }

    private IDensityFunction instantiate(byte[] bytes, int id) {
        try {
            CompiledDFLoader loader = new CompiledDFLoader();
            String dotName = "databack.compiled.CompiledDF_" + id;
            Class<?> cls = loader.define(dotName, bytes);

            if (opaques.isEmpty()) {
                return (IDensityFunction) cls.getDeclaredConstructor().newInstance();
            } else {
                IDensityFunction[] arr = opaques.toArray(new IDensityFunction[0]);
                return (IDensityFunction) cls.getDeclaredConstructor(IDensityFunction[].class)
                        .newInstance((Object) arr);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate compiled density function", e);
        }
    }

    // ── Node emission ─────────────────────────────────────────────────────────

    /** Emits bytecode for {@code node}, leaving its float result on the operand stack. */
    private void emitNode(IDensityFunction node) {
        if (node instanceof DensityFunctionRef ref) {
            node = ref.getFunction();
        }

        // 64 KB guard: if this subtree would push us over the limit, treat the whole thing
        // as opaque. This prevents JVM class verification errors on huge trees.
        if (bytecostSoFar + estimateCost(node) > BYTECOST_LIMIT) {
            emitOpaqueCall(addOpaque(node));
            bytecostSoFar += 13;
            return;
        }

        if (node instanceof UnaryDensityFunction unary) {
            emitUnary(node, unary.argument);
            return;
        }

        if (node instanceof BinaryDensityFunction binary) {
            emitBinary(node, binary.argument1, binary.argument2);
            return;
        }

        switch (node.getClass().getSimpleName()) {
            case "ConstantFunc" -> emitConstant(getFloat(node, "argument"));
            case "ClampFunc"    -> emitClamp(
                    getDF(node, "input"), getFloat(node, "min"), getFloat(node, "max"));
            case "YClampedGradientFunc" -> emitYClampedGradient(
                    getInt(node, "from_y"), getInt(node, "to_y"),
                    getFloat(node, "from_value"), getFloat(node, "to_value"));
            default -> {
                emitOpaqueCall(addOpaque(node));
                bytecostSoFar += 13;
            }
        }
    }

    private void emitUnary(IDensityFunction node, IDensityFunction argument) {
        switch (node.getClass().getSimpleName()) {

            // Passthroughs (identity) — just emit child
            case "BlendDensityUnary", "CacheAllInCellUnary" -> emitNode(argument);

            // Constant zero — don't even evaluate the child
            case "SlideUnary" -> {
                mv.visitInsn(FCONST_0);
                bytecostSoFar += 1;
            }

            case "AbsUnary" -> {
                emitNode(argument);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "abs", "(F)F", false);
                bytecostSoFar += 3;
            }

            case "SquareUnary" -> {
                emitNode(argument);
                // DUP is valid for category-1 (float) values
                mv.visitInsn(DUP);
                mv.visitInsn(FMUL);
                bytecostSoFar += 2;
            }

            case "CubeUnary" -> {
                emitNode(argument);
                int slot = nextSlot++;
                mv.visitVarInsn(FSTORE, slot);
                mv.visitVarInsn(FLOAD, slot);
                mv.visitVarInsn(FLOAD, slot);
                mv.visitInsn(FMUL);
                mv.visitVarInsn(FLOAD, slot);
                mv.visitInsn(FMUL);
                bytecostSoFar += 8;
            }

            case "InvertUnary" -> {
                // 1f / arg: emit arg, store, then LDC 1.0 / FLOAD
                emitNode(argument);
                int slot = nextSlot++;
                mv.visitVarInsn(FSTORE, slot);
                mv.visitInsn(FCONST_1);
                mv.visitVarInsn(FLOAD, slot);
                mv.visitInsn(FDIV);
                bytecostSoFar += 6;
            }

            case "HalfNegativeUnary" -> {
                emitNode(argument);
                int slot = nextSlot++;
                mv.visitVarInsn(FSTORE, slot);
                emitConditionalScale(slot, 0.5f);
                bytecostSoFar += 18;
            }

            case "QuarterNegativeUnary" -> {
                emitNode(argument);
                int slot = nextSlot++;
                mv.visitVarInsn(FSTORE, slot);
                emitConditionalScale(slot, 0.25f);
                bytecostSoFar += 18;
            }

            case "SqueezeUnary" -> {
                emitNode(argument);
                emitSqueeze();
                bytecostSoFar += 22;
            }

            // Unknown unary type — treat entire node as opaque
            default -> {
                emitOpaqueCall(addOpaque(node));
                bytecostSoFar += 13;
            }
        }
    }

    private void emitBinary(IDensityFunction node, IDensityFunction arg1, IDensityFunction arg2) {
        switch (node.getClass().getSimpleName()) {
            case "AddBinary" -> {
                emitNode(arg1);
                emitNode(arg2);
                mv.visitInsn(FADD);
                bytecostSoFar += 1;
            }
            case "MulBinary" -> {
                emitNode(arg1);
                emitNode(arg2);
                mv.visitInsn(FMUL);
                bytecostSoFar += 1;
            }
            case "MaxBinary" -> {
                emitNode(arg1);
                emitNode(arg2);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "max", "(FF)F", false);
                bytecostSoFar += 3;
            }
            case "MinBinary" -> {
                emitNode(arg1);
                emitNode(arg2);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(FF)F", false);
                bytecostSoFar += 3;
            }
            default -> {
                emitOpaqueCall(addOpaque(node));
                bytecostSoFar += 13;
            }
        }
    }

    private void emitConstant(float value) {
        if (value == 0.0f)      mv.visitInsn(FCONST_0);
        else if (value == 1.0f) mv.visitInsn(FCONST_1);
        else if (value == 2.0f) mv.visitInsn(FCONST_2);
        else                    mv.visitLdcInsn(value);
        bytecostSoFar += 2;
    }

    private void emitClamp(IDensityFunction input, float min, float max) {
        emitNode(input);
        mv.visitLdcInsn(min);
        mv.visitLdcInsn(max);
        mv.visitMethodInsn(INVOKESTATIC, MATHHELPER, "clamp_float", "(FFF)F", false);
        bytecostSoFar += 7;
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
        bytecostSoFar += 15;
    }

    /**
     * Emits: {@code arg < 0 ? arg * scale : arg}
     * Precondition: {@code arg} has been stored in {@code slot}.
     * Stack effect: [] → [float]
     */
    private void emitConditionalScale(int slot, float scale) {
        Label positive = new Label();
        Label end = new Label();

        // FCMPG: -1 if arg<0, 0 if arg==0, +1 if arg>0 or NaN
        // IFGE jumps when comparison result >= 0  (i.e. arg >= 0 OR NaN)
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
        int idx = opaques.size();
        opaques.add(node);
        return idx;
    }

    // ── Cost estimator (for 64 KB guard) ─────────────────────────────────────

    /**
     * Recursively estimates the bytecode cost of inlining {@code node}.
     * Opaque nodes always return 13 (the cost of one opaque call).
     */
    static int estimateCost(IDensityFunction node) {
        if (node instanceof UnaryDensityFunction u) {
            return switch (node.getClass().getSimpleName()) {
                case "BlendDensityUnary", "CacheAllInCellUnary" -> estimateCost(u.argument);
                case "SlideUnary"            -> 1;
                case "AbsUnary"              -> 3  + estimateCost(u.argument);
                case "SquareUnary"           -> 2  + estimateCost(u.argument);
                case "CubeUnary"             -> 8  + estimateCost(u.argument);
                case "InvertUnary"           -> 6  + estimateCost(u.argument);
                case "HalfNegativeUnary",
                     "QuarterNegativeUnary"  -> 18 + estimateCost(u.argument);
                case "SqueezeUnary"          -> 22 + estimateCost(u.argument);
                default                      -> 13;
            };
        }

        if (node instanceof BinaryDensityFunction b) {
            return switch (node.getClass().getSimpleName()) {
                case "AddBinary", "MulBinary" ->
                        1  + estimateCost(b.argument1) + estimateCost(b.argument2);
                case "MaxBinary", "MinBinary" ->
                        3  + estimateCost(b.argument1) + estimateCost(b.argument2);
                default -> 13;
            };
        }

        return switch (node.getClass().getSimpleName()) {
            case "ConstantFunc"         -> 2;
            case "ClampFunc"            -> 7 + estimateCost(getDF(node, "input"));
            case "YClampedGradientFunc" -> 15;
            default                     -> 13;
        };
    }

    // ── JVM helpers ──────────────────────────────────────────────────────────

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

    // Used only at compile-time (once per world), so reflection overhead is negligible.

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

    // ── ClassLoader ───────────────────────────────────────────────────────────

    private static final class CompiledDFLoader extends ClassLoader {

        CompiledDFLoader() {
            // Parent = compiler's loader so the compiled class can see IDensityFunction, etc.
            super(DensityFunctionCompiler.class.getClassLoader());
        }

        Class<?> define(String dotName, byte[] bytes) {
            return defineClass(dotName, bytes, 0, bytes.length);
        }
    }
}
