package databack.common.worldgen.biome.preset;

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

/// Turns a vanilla class file that builds a biome preset into one a Java 8 JVM can load next to the stand-ins:
///
/// - the class file version becomes Java 8's;
/// - vanilla names become stand-ins (see [PresetMappings]), biome key constants become their ids, debug flags become
///   `false` and `List.of` calls go to [PresetLists];
/// - methods that use anything else ([ForeignMethodScanner]) throw [UnsupportedOperationException] instead, or are
///   removed when their descriptor names anything else;
/// - generic signatures, debug info, annotations and nest/inner-class attributes are dropped.
///
/// Nothing here depends on what the class's methods do, only on which names they use.
final class PresetClassRewriter {

    private static final int JAVA_8 = 52;

    private PresetClassRewriter() {}

    /// Throws when the class cannot be parsed.
    @NotNull
    @Contract("_ -> new")
    static byte[] rewrite(byte @NotNull [] classFile) {
        ClassReader reader = new ClassReader(withJava8Version(classFile));

        ForeignMethodScanner scanner = new ForeignMethodScanner();
        reader.accept(scanner, ClassReader.SKIP_DEBUG);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassRewriter(writer, scanner.foreignMethods(), scanner.unloadableMethods()),
            ClassReader.SKIP_DEBUG);
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

        private final Set<String> foreignMethods;
        private final Set<String> unloadableMethods;

        ClassRewriter(ClassVisitor next, Set<String> foreignMethods, Set<String> unloadableMethods) {
            super(Opcodes.ASM5, next);
            this.foreignMethods = foreignMethods;
            this.unloadableMethods = unloadableMethods;
        }

        @Override
        public void visit(
            int version, int access, String name, String signature, String superName, String[] interfaces
        ) {
            String[] mapped = new String[interfaces.length];
            for (int i = 0; i < interfaces.length; i++) mapped[i] = PresetMappings.mapName(interfaces[i]);
            super.visit(JAVA_8, access, name, null, superName, mapped);
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
            super.visitField(access, name, PresetMappings.mapDesc(desc), null, value);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions
        ) {
            if (unloadableMethods.contains(name + desc)) return null;

            MethodVisitor next = super.visitMethod(access, name, PresetMappings.mapDesc(desc), null, exceptions);

            if (foreignMethods.contains(name + desc)) {
                writeUnsupportedBody(next, name);
                return null;
            }

            return new MethodRewriter(next);
        }

        private static void writeUnsupportedBody(MethodVisitor mv, String name) {
            String exception = Type.getInternalName(UnsupportedOperationException.class);

            mv.visitCode();
            mv.visitTypeInsn(Opcodes.NEW, exception);
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn("Not available in extracted preset code: " + name);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, exception, "<init>", "(Ljava/lang/String;)V", false);
            mv.visitInsn(Opcodes.ATHROW);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    private static final class MethodRewriter extends MethodVisitor {

        MethodRewriter(MethodVisitor next) {
            super(Opcodes.ASM5, next);
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

        private static Object[] mapFrame(Object[] entries, int count) {
            if (entries == null) return null;

            Object[] mapped = entries.clone();
            for (int i = 0; i < count; i++) {
                if (mapped[i] instanceof String) mapped[i] = PresetMappings.mapName((String) mapped[i]);
            }
            return mapped;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            super.visitTypeInsn(opcode, PresetMappings.mapName(type));
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.GETSTATIC && PresetMappings.isBiomeKey(owner, desc)) {
                super.visitLdcInsn(PresetMappings.biomeId(name));
            } else if (opcode == Opcodes.GETSTATIC && PresetMappings.isDebugFlag(owner, desc)) {
                super.visitInsn(Opcodes.ICONST_0);
            } else {
                super.visitFieldInsn(opcode, PresetMappings.mapName(owner), name, PresetMappings.mapDesc(desc));
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (opcode == Opcodes.INVOKESTATIC && PresetMappings.isListOf(owner, name)) {
                super.visitMethodInsn(opcode, PresetMappings.listOfOwner(), name, desc, false);
                return;
            }

            String mappedOwner = PresetMappings.mapName(owner);
            // A stand-in replacing a vanilla interface may be a class, and the other way around.
            boolean mappedItf = mappedOwner.equals(owner) ? itf : isStandInInterface(mappedOwner);
            super.visitMethodInsn(opcode, mappedOwner, name, PresetMappings.mapDesc(desc), mappedItf);
        }

        private static boolean isStandInInterface(String internalName) {
            return internalName.equals(Type.getInternalName(PresetSourceProvider.class));
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
            super.visitMultiANewArrayInsn(PresetMappings.mapDesc(desc), dims);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            super.visitTryCatchBlock(start, end, handler, type == null ? null : PresetMappings.mapName(type));
        }

        @Override
        public void visitLocalVariable(
            String name, String desc, String signature, Label start, Label end, int index
        ) {}
    }
}
