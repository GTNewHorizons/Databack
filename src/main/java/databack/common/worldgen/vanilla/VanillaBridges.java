package databack.common.worldgen.vanilla;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/// Generates bridge classes: each implements a functional interface by calling one static method or constructor of
/// a rewritten class directly, so Databack reaches it through a plain interface call instead of reflection. A bridge
/// lives in its target's package and loader, which can see both the target and the interface.
final class VanillaBridges {

    /// What a constructor bridge names as its method.
    static final String CONSTRUCTOR = "<init>";

    private VanillaBridges() {}

    /// The single abstract method of `itf`. Throws [IllegalArgumentException] when it has none or several.
    @NotNull
    static Method functionalMethod(@NotNull Class<?> itf) {
        if (!itf.isInterface()) throw new IllegalArgumentException(itf + " is not an interface");

        Method found = null;
        for (Method m : itf.getMethods()) {
            if (!Modifier.isAbstract(m.getModifiers())) continue;
            if (found != null) throw new IllegalArgumentException(itf + " has several abstract methods");
            found = m;
        }
        if (found == null) throw new IllegalArgumentException(itf + " has no abstract method");
        return found;
    }

    /// Throws [IllegalArgumentException] unless `owner` has a non-private static method `method` (or constructor,
    /// for [#CONSTRUCTOR]) taking `implemented`'s parameters and giving something it can return.
    static void checkTarget(@NotNull Class<?> owner, @NotNull String method, @NotNull Method implemented) {
        Class<?>[] parameters = implemented.getParameterTypes();
        try {
            if (method.equals(CONSTRUCTOR)) {
                Constructor<?> c = owner.getDeclaredConstructor(parameters);
                if (Modifier.isPrivate(c.getModifiers())) throw new IllegalArgumentException(c + " is private");
                if (!implemented.getReturnType().isAssignableFrom(owner)) {
                    throw new IllegalArgumentException(owner + " is not a " + implemented.getReturnType());
                }
                return;
            }

            Method m = owner.getDeclaredMethod(method, parameters);
            if (!Modifier.isStatic(m.getModifiers()) || Modifier.isPrivate(m.getModifiers())) {
                throw new IllegalArgumentException(m + " is not a non-private static method");
            }
            if (m.getReturnType() != implemented.getReturnType()) {
                throw new IllegalArgumentException(m + " does not return " + implemented.getReturnType());
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(owner.getName() + " has no " + method + " taking "
                + Type.getMethodDescriptor(implemented), e);
        }
    }

    /// The class file of bridge `name` (internal) implementing `itf`'s `implemented` by calling `method` of `owner`
    /// (internal), which [#checkTarget] accepted.
    static byte @NotNull [] generate(
        @NotNull String name, @NotNull Class<?> itf, @NotNull Method implemented, @NotNull String owner,
        @NotNull String method
    ) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, name, null,
            "java/lang/Object", new String[] { Type.getInternalName(itf) });

        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, CONSTRUCTOR, "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", CONSTRUCTOR, "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        String descriptor = Type.getMethodDescriptor(implemented);
        Type[] arguments = Type.getArgumentTypes(descriptor);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, implemented.getName(), descriptor, null, null);
        mv.visitCode();

        boolean constructor = method.equals(CONSTRUCTOR);
        if (constructor) {
            mv.visitTypeInsn(Opcodes.NEW, owner);
            mv.visitInsn(Opcodes.DUP);
        }

        int slot = 1;
        for (Type argument : arguments) {
            mv.visitVarInsn(argument.getOpcode(Opcodes.ILOAD), slot);
            slot += argument.getSize();
        }

        if (constructor) {
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, CONSTRUCTOR,
                Type.getMethodDescriptor(Type.VOID_TYPE, arguments), false);
        } else {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, method, descriptor, false);
        }
        mv.visitInsn(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN));
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
