package databack.common.worldgen.vanilla;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/// Finds the methods of a class that use something its [VanillaMappings] do not support. Callers are expected not to
/// need those methods; [VanillaClassRewriter] replaces their bodies, or removes them when their descriptor names an
/// unsupported class (reflection would try to load it).
final class ForeignMethodScanner extends ClassVisitor {

    private final VanillaMappings mappings;
    private final Set<String> bundle;
    private final Set<String> foreign = new HashSet<>();
    private final Set<String> unloadable = new HashSet<>();

    ForeignMethodScanner(VanillaMappings mappings, Set<String> bundle) {
        super(Opcodes.ASM5);
        this.mappings = mappings;
        this.bundle = bundle;
    }

    /// Methods as `name + desc`, in vanilla names. Valid after the class has been visited.
    @NotNull
    Set<String> foreignMethods() {
        return foreign;
    }

    /// The foreign methods whose descriptors name an unsupported class. Valid after the class has been visited.
    @NotNull
    Set<String> unloadableMethods() {
        return unloadable;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (!isSupportedDescriptor(desc)) {
            foreign.add(name + desc);
            unloadable.add(name + desc);
        }
        return new MethodScanner(name + desc);
    }

    private boolean isSupportedDescriptor(String desc) {
        Type method = Type.getMethodType(desc);
        if (!mappings.isSupportedType(bundle, method.getReturnType())) return false;
        for (Type argument : method.getArgumentTypes()) {
            if (!mappings.isSupportedType(bundle, argument)) return false;
        }
        return true;
    }

    private final class MethodScanner extends MethodVisitor {

        private final String method;

        MethodScanner(String method) {
            super(Opcodes.ASM5);
            this.method = method;
        }

        private void require(boolean supported) {
            if (!supported) foreign.add(method);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            require(mappings.isSupportedClass(bundle, type));
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            boolean constant = opcode == Opcodes.GETSTATIC && mappings.constant(owner, name, desc) != null;
            require(constant || mappings.isSupportedClass(bundle, owner));
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            boolean redirected = opcode == Opcodes.INVOKESTATIC && mappings.staticRedirect(owner, name) != null;
            require(redirected || mappings.isSupportedClass(bundle, owner));
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
            require(false);
        }

        @Override
        public void visitLdcInsn(Object cst) {
            require(!(cst instanceof Type) && !(cst instanceof Handle));
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
            require(mappings.isSupportedClass(bundle, desc));
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            require(type == null || mappings.isSupportedClass(bundle, type));
        }
    }
}
