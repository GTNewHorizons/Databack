package databack.common.worldgen.vanilla;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

/// Turns a vanilla class file into one a Java 8 JVM can load next to Databack's replacements:
///
/// - the class file version becomes Java 8's;
/// - uses of vanilla classes, static fields and static methods are mapped as its [VanillaMappings] say, and the
///   interfaces they add are added; redirected instance calls become static calls taking the receiver first;
/// - lambdas are kept with their types mapped, or become null where the mappings say so;
/// - methods that use anything else ([ForeignMethodScanner]) throw [UnsupportedOperationException] instead, or are
///   removed when their descriptor names anything else;
/// - uses of inert classes ([VanillaMappings.Builder#inert]) do nothing, and give null or zero, as do fields of inert
///   types and methods that return one;
/// - replacements see the bundled classes in their members' descriptors as `Object`
///   ([VanillaMappings#eraseBundle]), and what they return is cast back;
/// - static initializers are best-effort: everything unsupported in them, calls to the bundle's foreign methods
///   included, is treated as inert, so the constants they cannot build are null instead of the class failing to
///   initialize;
/// - records stay subclasses of `Record` with working generated methods where the JVM has records, and become plain
///   classes elsewhere ([VanillaMappings.Builder#modernRuntime]);
/// - members of nested classes lose `private`, since the nest attributes that grant nestmates access are dropped;
/// - generic signatures, debug info, annotations and nest/inner-class/record attributes are dropped.
///
/// Nothing here depends on what the class's methods do, only on which names they use.
public final class VanillaClassRewriter {

    private static final int JAVA_8 = 52;

    private VanillaClassRewriter() {}

    /// `bundle` holds the internal names of the classes rewritten together; they may use each other freely. Throws
    /// when the class cannot be parsed.
    @NotNull
    @Contract("_, _, _ -> new")
    public static byte[] rewrite(
        byte @NotNull [] classFile, @NotNull VanillaMappings mappings, @NotNull Set<String> bundle
    ) {
        Set<String> failing = failingMethods(Collections.singleton(classFile), mappings, bundle);
        return rewrite(classFile, mappings, bundle, failing);
    }

    /// [#rewrite(byte[], VanillaMappings, Set)], where `foreign` holds the bundle's methods that may throw for being
    /// foreign ([#failingMethods]): static initializers skip calls to them.
    @NotNull
    @Contract("_, _, _, _ -> new")
    public static byte[] rewrite(
        byte @NotNull [] classFile, @NotNull VanillaMappings mappings, @NotNull Set<String> bundle,
        @NotNull Set<String> foreign
    ) {
        ClassReader reader = new ClassReader(withJava8Version(classFile));

        ForeignMethodScanner scanner = new ForeignMethodScanner(mappings, bundle);
        reader.accept(scanner, ClassReader.SKIP_DEBUG);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassRewriter(writer, mappings, scanner, bundle, foreign), ClassReader.SKIP_DEBUG);
        return writer.toByteArray();
    }

    /// The methods of `classFiles` (a bundle) that use something unsupported, or call a bundle method that may, as
    /// `owner.name` + descriptor in vanilla names. Throws when a class cannot be parsed.
    @NotNull
    @Contract("_, _, _ -> new")
    public static Set<String> failingMethods(
        @NotNull Collection<byte[]> classFiles, @NotNull VanillaMappings mappings, @NotNull Set<String> bundle
    ) {
        Set<String> failing = new HashSet<>();
        Map<String, Set<String>> callers = new HashMap<>();
        for (byte[] classFile : classFiles) {
            ClassReader reader = new ClassReader(withJava8Version(classFile));
            ForeignMethodScanner scanner = new ForeignMethodScanner(mappings, bundle);
            reader.accept(scanner, ClassReader.SKIP_DEBUG);

            String owner = reader.getClassName() + ".";
            for (String method : scanner.foreignMethods()) failing.add(owner + method);
            for (Map.Entry<String, Set<String>> e : scanner.bundleCalls().entrySet()) {
                for (String callee : e.getValue()) {
                    callers.computeIfAbsent(callee, k -> new HashSet<>()).add(owner + e.getKey());
                }
            }
        }

        Deque<String> pending = new ArrayDeque<>(failing);
        while (!pending.isEmpty()) {
            for (String caller : callers.getOrDefault(pending.pop(), Collections.emptySet())) {
                if (failing.add(caller)) pending.push(caller);
            }
        }
        return failing;
    }

    /// ASM 5 refuses class files newer than Java 8. The rewrite removes everything newer, so the version can be
    /// lowered before parsing.
    private static byte[] withJava8Version(byte[] classFile) {
        byte[] copy = classFile.clone();
        copy[4] = 0;
        copy[5] = 0;
        copy[6] = (byte) (JAVA_8 >>> 8);
        copy[7] = (byte) JAVA_8;
        return copy;
    }

    private static final class ClassRewriter extends ClassVisitor {

        private final VanillaMappings mappings;
        private final ForeignMethodScanner scan;
        private final Set<String> bundle;
        private final Set<String> foreign;
        private String className;
        private boolean isInterface;
        /// Whether the class is in a nest: it is an inner class, or has one.
        private boolean nested;

        ClassRewriter(
            ClassVisitor next, VanillaMappings mappings, ForeignMethodScanner scan, Set<String> bundle,
            Set<String> foreign
        ) {
            super(Opcodes.ASM5, next);
            this.mappings = mappings;
            this.scan = scan;
            this.bundle = bundle;
            this.foreign = foreign;
        }

        @Override
        public void visit(
            int version, int access, String name, String signature, String superName, String[] interfaces
        ) {
            List<String> mapped = new ArrayList<>();
            for (String itf : interfaces) {
                if (!mappings.isInert(itf)) mapped.add(mappings.mapName(itf));
            }
            mapped.addAll(mappings.addedInterfaces(name));

            className = name;
            isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            super.visit(JAVA_8, access, name, null, mappings.mapName(superName), mapped.toArray(new String[0]));
        }

        @Override
        public void visitSource(String source, String debug) {}

        @Override
        public void visitOuterClass(String owner, String name, String desc) {}

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            return null;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            return null;
        }

        /// Also drops attributes ASM 5 does not know (nest members, records...): their raw bytes refer to the old
        /// constant pool.
        @Override
        public void visitAttribute(Attribute attr) {}

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (name.equals(className) || className.equals(outerName) || name.startsWith(className + "$")) {
                nested = true;
            }
        }

        /// Nestmates may use each other's private members through nest attributes, which Java 8 lacks and which are
        /// dropped; their members lose `private` instead (interface members become public, the only alternative they
        /// allow). Inner classes are visited before members, so [#nested] is known here.
        private int widen(int access) {
            if (!nested || (access & Opcodes.ACC_PRIVATE) == 0) return access;
            access &= ~Opcodes.ACC_PRIVATE;
            return isInterface ? access | Opcodes.ACC_PUBLIC : access;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            super.visitField(widen(access), name, mappings.mapFieldDesc(bundle, desc), null, value);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions
        ) {
            if (scan.unloadableMethods().contains(name + desc)) return null;

            MethodVisitor next = super.visitMethod(widen(access), name, mappings.mapDesc(desc), null, exceptions);

            if (scan.foreignMethods().contains(name + desc)) {
                writeUnsupportedBody(next, name);
                return null;
            }

            Predicate<String> inert = name.equals("<clinit>")
                ? type -> mappings.isInert(type) || !mappings.isSupportedClass(bundle, type)
                : mappings::isInert;
            return new MethodRewriter(next, mappings, bundle, inert, name.equals("<clinit>") ? foreign : null);
        }

        private static void writeUnsupportedBody(MethodVisitor mv, String name) {
            String exception = Type.getInternalName(UnsupportedOperationException.class);

            mv.visitCode();
            mv.visitTypeInsn(Opcodes.NEW, exception);
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn("Not available in rewritten vanilla code: " + name);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, exception, "<init>", "(Ljava/lang/String;)V", false);
            mv.visitInsn(Opcodes.ATHROW);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    private static final class MethodRewriter extends MethodVisitor {

        private final VanillaMappings mappings;
        private final Set<String> bundle;
        /// Classes whose uses do nothing here: the inert ones, and in a static initializer every unsupported one.
        private final Predicate<String> inert;
        /// Whether this is a static initializer: everything unsupported in it is skipped.
        private final boolean bestEffort;
        /// The bundle's foreign methods, when [#bestEffort]: calls to them are skipped too.
        private final Set<String> foreign;

        MethodRewriter(
            MethodVisitor next, VanillaMappings mappings, Set<String> bundle, Predicate<String> inert,
            @Nullable Set<String> foreign
        ) {
            super(Opcodes.ASM5, next);
            this.mappings = mappings;
            this.bundle = bundle;
            this.inert = inert;
            this.bestEffort = foreign != null;
            this.foreign = foreign == null ? Collections.emptySet() : foreign;
        }

        private void pop(Type type) {
            if (type.getSort() != Type.VOID) super.visitInsn(type.getSize() == 2 ? Opcodes.POP2 : Opcodes.POP);
        }

        /// Pushes the zero value of `type`: what an inert read or call gives.
        private void pushDefault(Type type) {
            switch (type.getSort()) {
                case Type.VOID:
                    return;
                case Type.LONG:
                    super.visitInsn(Opcodes.LCONST_0);
                    return;
                case Type.FLOAT:
                    super.visitInsn(Opcodes.FCONST_0);
                    return;
                case Type.DOUBLE:
                    super.visitInsn(Opcodes.DCONST_0);
                    return;
                case Type.OBJECT:
                case Type.ARRAY:
                    super.visitInsn(Opcodes.ACONST_NULL);
                    return;
                default:
                    super.visitInsn(Opcodes.ICONST_0);
            }
        }

        @Override
        public void visitParameter(String name, int access) {}

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            return null;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            return null;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            return null;
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
            return null;
        }

        @Override
        public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            return null;
        }

        @Override
        public AnnotationVisitor visitTryCatchAnnotation(
            int typeRef, TypePath typePath, String desc, boolean visible
        ) {
            return null;
        }

        @Override
        public void visitAttribute(Attribute attr) {}

        @Override
        public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
            super.visitFrame(type, nLocal, mapFrame(local, nLocal), nStack, mapFrame(stack, nStack));
        }

        private Object[] mapFrame(Object[] entries, int count) {
            if (entries == null) return null;

            Object[] mapped = entries.clone();
            for (int i = 0; i < count; i++) {
                if (mapped[i] instanceof String) mapped[i] = mappings.mapName((String) mapped[i]);
            }
            return mapped;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (!inert.test(type)) {
                super.visitTypeInsn(opcode, mappings.mapName(type));
                return;
            }

            switch (opcode) {
                case Opcodes.NEW:
                    super.visitInsn(Opcodes.ACONST_NULL);
                    break;
                case Opcodes.CHECKCAST:
                    // Whatever this was, it is null now: null passes any later type check.
                    super.visitInsn(Opcodes.POP);
                    super.visitInsn(Opcodes.ACONST_NULL);
                    break;
                case Opcodes.INSTANCEOF:
                    super.visitInsn(Opcodes.POP);
                    super.visitInsn(Opcodes.ICONST_0);
                    break;
                default:
                    super.visitTypeInsn(opcode, VanillaMappings.OBJECT);
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (inert.test(owner) || mappings.isInertType(Type.getType(desc))) {
                visitInertFieldInsn(opcode, Type.getType(desc));
                return;
            }

            Object constant = opcode == Opcodes.GETSTATIC ? mappings.constant(owner, name, desc) : null;

            if (constant instanceof Boolean) {
                super.visitInsn((Boolean) constant ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            } else if (constant != null) {
                super.visitLdcInsn(constant);
            } else if (mappings.isReplaced(owner)) {
                super.visitFieldInsn(opcode, mappings.mapName(owner), name,
                    mappings.mapDesc(mappings.eraseBundle(bundle, desc)));
                if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) castBundled(Type.getType(desc));
            } else {
                super.visitFieldInsn(opcode, mappings.mapName(owner), name, mappings.mapFieldDesc(bundle, desc));
            }
        }

        /// A replacement's member gives a bundled class as [Object] ([VanillaMappings#eraseBundle]); casts it back.
        private void castBundled(Type type) {
            Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;
            if (element.getSort() != Type.OBJECT || !bundle.contains(element.getInternalName())) return;

            String cast = type.getSort() == Type.ARRAY ? type.getDescriptor() : type.getInternalName();
            super.visitTypeInsn(Opcodes.CHECKCAST, mappings.mapName(cast));
        }

        private void visitInertFieldInsn(int opcode, Type type) {
            switch (opcode) {
                case Opcodes.GETSTATIC:
                    pushDefault(type);
                    break;
                case Opcodes.PUTSTATIC:
                    pop(type);
                    break;
                case Opcodes.GETFIELD:
                    super.visitInsn(Opcodes.POP);
                    pushDefault(type);
                    break;
                default:
                    pop(type);
                    super.visitInsn(Opcodes.POP);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            String redirect = opcode == Opcodes.INVOKESTATIC ? mappings.staticRedirect(owner, name) : null;
            String instanceRedirect = opcode == Opcodes.INVOKESTATIC ? null : mappings.instanceRedirect(owner, name);

            if (instanceRedirect != null) {
                String receiver = "L" + mappings.mapName(owner) + ";";
                super.visitMethodInsn(Opcodes.INVOKESTATIC, instanceRedirect, name,
                    "(" + receiver + mappings.mapDesc(desc).substring(1), false);
            } else if (redirect == null && isInertCall(owner, name, desc)) {
                Type[] arguments = Type.getArgumentTypes(desc);
                for (int i = arguments.length - 1; i >= 0; i--) pop(arguments[i]);
                if (opcode != Opcodes.INVOKESTATIC) super.visitInsn(Opcodes.POP);
                pushDefault(Type.getReturnType(desc));
            } else if (redirect != null) {
                super.visitMethodInsn(opcode, redirect, name, mappings.mapDesc(mappings.eraseBundle(bundle, desc)),
                    false);
                castBundled(Type.getReturnType(desc));
            } else if (mappings.isReplaced(owner)) {
                boolean mappedItf = mappings.isInterfaceCall(owner, itf);
                super.visitMethodInsn(virtualOpcode(opcode, mappedItf), mappings.mapName(owner), name,
                    mappings.mapDesc(mappings.eraseBundle(bundle, desc)), mappedItf);
                castBundled(Type.getReturnType(desc));
            } else {
                boolean mappedItf = mappings.isInterfaceCall(owner, itf);
                super.visitMethodInsn(virtualOpcode(opcode, mappedItf), mappings.mapName(owner), name,
                    mappings.mapDesc(desc), mappedItf);
            }
        }

        /// Constructors of supported classes are never inert: their `new` already ran. Other methods are when they
        /// give an inert class (a codec, say), and in a static initializer when they name an unsupported one.
        private boolean isInertCall(String owner, String name, String desc) {
            if (inert.test(owner)) return true;
            if (name.equals("<init>")) return false;
            if (mappings.isInertType(Type.getReturnType(desc))) return true;
            return bestEffort
                && (!mappings.isSupportedMethod(bundle, desc) || foreign.contains(owner + "." + name + desc));
        }

        /// A replacement may be an interface where vanilla's class was a class, or the other way around; virtual
        /// calls must use the matching instruction.
        private static int virtualOpcode(int opcode, boolean itf) {
            if (opcode == Opcodes.INVOKEVIRTUAL && itf) return Opcodes.INVOKEINTERFACE;
            if (opcode == Opcodes.INVOKEINTERFACE && !itf) return Opcodes.INVOKEVIRTUAL;
            return opcode;
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
            VanillaMappings.Lambda lambda = mappings.lambda(bundle, desc, bsm, bsmArgs);
            if (lambda == VanillaMappings.Lambda.NULL || bestEffort && lambda == VanillaMappings.Lambda.UNSUPPORTED) {
                // The captured values are on the stack; the lambda would have consumed them.
                Type[] captured = Type.getArgumentTypes(desc);
                for (int i = captured.length - 1; i >= 0; i--) {
                    super.visitInsn(captured[i].getSize() == 2 ? Opcodes.POP2 : Opcodes.POP);
                }
                super.visitInsn(Opcodes.ACONST_NULL);
                return;
            }

            Object[] mapped = new Object[bsmArgs.length];
            for (int i = 0; i < bsmArgs.length; i++) mapped[i] = mapConstant(bsmArgs[i]);
            super.visitInvokeDynamicInsn(name, mappings.mapDesc(desc), bsm, mapped);
        }

        private Object mapConstant(Object constant) {
            if (constant instanceof Type) {
                Type type = (Type) constant;
                return type.getSort() == Type.METHOD ? Type.getMethodType(mappings.mapDesc(type.getDescriptor()))
                    : Type.getType(mappings.mapDesc(type.getDescriptor()));
            }
            if (constant instanceof Handle) {
                Handle h = (Handle) constant;
                boolean itf = mappings.isInterfaceCall(h.getOwner(), h.getTag() == Opcodes.H_INVOKEINTERFACE);
                int tag = h.getTag() == Opcodes.H_INVOKEVIRTUAL && itf ? Opcodes.H_INVOKEINTERFACE
                    : h.getTag() == Opcodes.H_INVOKEINTERFACE && !itf ? Opcodes.H_INVOKEVIRTUAL : h.getTag();
                String desc = h.getTag() <= Opcodes.H_PUTSTATIC ? mappings.mapFieldDesc(bundle, h.getDesc())
                    : mappings.mapDesc(h.getDesc());
                return new Handle(tag, mappings.mapName(h.getOwner()), h.getName(), desc);
            }
            return constant;
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
            super.visitMultiANewArrayInsn(mappings.mapDesc(desc), dims);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            super.visitTryCatchBlock(start, end, handler, type == null ? null : mappings.mapName(type));
        }

        @Override
        public void visitLocalVariable(
            String name, String desc, String signature, Label start, Label end, int index
        ) {}
    }
}
