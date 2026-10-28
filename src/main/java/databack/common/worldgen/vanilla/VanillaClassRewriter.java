package databack.common.worldgen.vanilla;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

/// Turns a vanilla class file into one a Java 8 JVM can load next to Databack's replacements:
///
/// - the class file version becomes Java 8's;
/// - uses of vanilla classes, static fields and static methods are mapped as its [VanillaMappings] say, and the
///   interfaces they add are added;
/// - methods that use anything else ([ForeignMethodScanner]) throw [UnsupportedOperationException] instead, or are
///   removed when their descriptor names anything else;
/// - generic signatures, debug info, annotations and nest/inner-class attributes are dropped.
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
        ClassReader reader = new ClassReader(withJava8Version(classFile));

        ForeignMethodScanner scanner = new ForeignMethodScanner(mappings, bundle);
        reader.accept(scanner, ClassReader.SKIP_DEBUG);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassRewriter(writer, mappings, scanner), ClassReader.SKIP_DEBUG);
        return writer.toByteArray();
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

        ClassRewriter(ClassVisitor next, VanillaMappings mappings, ForeignMethodScanner scan) {
            super(Opcodes.ASM5, next);
            this.mappings = mappings;
            this.scan = scan;
        }

        @Override
        public void visit(
            int version, int access, String name, String signature, String superName, String[] interfaces
        ) {
            List<String> mapped = new ArrayList<>();
            for (String itf : interfaces) mapped.add(mappings.mapName(itf));
            mapped.addAll(mappings.addedInterfaces(name));

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
        public void visitInnerClass(String name, String outerName, String innerName, int access) {}

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            super.visitField(access, name, mappings.mapDesc(desc), null, value);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions
        ) {
            if (scan.unloadableMethods().contains(name + desc)) return null;

            MethodVisitor next = super.visitMethod(access, name, mappings.mapDesc(desc), null, exceptions);

            if (scan.foreignMethods().contains(name + desc)) {
                writeUnsupportedBody(next, name);
                return null;
            }

            return new MethodRewriter(next, mappings);
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

        MethodRewriter(MethodVisitor next, VanillaMappings mappings) {
            super(Opcodes.ASM5, next);
            this.mappings = mappings;
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
            super.visitTypeInsn(opcode, mappings.mapName(type));
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            Object constant = opcode == Opcodes.GETSTATIC ? mappings.constant(owner, name, desc) : null;

            if (constant instanceof Boolean) {
                super.visitInsn((Boolean) constant ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            } else if (constant != null) {
                super.visitLdcInsn(constant);
            } else {
                super.visitFieldInsn(opcode, mappings.mapName(owner), name, mappings.mapDesc(desc));
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            String redirect = opcode == Opcodes.INVOKESTATIC ? mappings.staticRedirect(owner, name) : null;

            if (redirect != null) {
                super.visitMethodInsn(opcode, redirect, name, mappings.mapDesc(desc), false);
            } else {
                boolean mappedItf = mappings.isInterfaceCall(owner, itf);
                super.visitMethodInsn(virtualOpcode(opcode, mappedItf), mappings.mapName(owner), name,
                    mappings.mapDesc(desc), mappedItf);
            }
        }

        /// A replacement may be an interface where vanilla's class was a class, or the other way around; virtual
        /// calls must use the matching instruction.
        private static int virtualOpcode(int opcode, boolean itf) {
            if (opcode == Opcodes.INVOKEVIRTUAL && itf) return Opcodes.INVOKEINTERFACE;
            if (opcode == Opcodes.INVOKEINTERFACE && !itf) return Opcodes.INVOKEVIRTUAL;
            return opcode;
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
