package databack.test.support;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/// Strips FML's `@Optional` parts of mods' classes, as FML does at launch for mods that are absent: interfaces
/// (`@Optional.Interface`) whose class is not on the classpath, and every `@Optional.Method`, since tests load no
/// optional mods.
final class TestOptionalStripper {

    private static final String OPTIONAL = "Lcpw/mods/fml/common/Optional$";

    private TestOptionalStripper() {}

    /// The class without its absent optional parts, or null if it has none.
    @Nullable
    static byte[] transform(byte @NotNull [] classFile) {
        if (!contains(classFile, "cpw/mods/fml/common/Optional$")) return null;

        ClassNode node = new ClassNode();
        new ClassReader(classFile).accept(node, 0);

        boolean changed = stripInterfaces(node);
        changed |= node.methods.removeIf(TestOptionalStripper::isOptional);
        if (!changed) return null;

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean stripInterfaces(ClassNode node) {
        if (node.visibleAnnotations == null && node.invisibleAnnotations == null) return false;

        boolean changed = false;
        for (List<AnnotationNode> annotations : new List[] { node.visibleAnnotations, node.invisibleAnnotations }) {
            if (annotations == null) continue;
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.equals(OPTIONAL + "Interface;")) {
                    changed |= strip(node, annotation);
                } else if (annotation.desc.equals(OPTIONAL + "InterfaceList;")) {
                    for (Object inner : (List<?>) value(annotation, "value")) changed |= strip(node, (AnnotationNode) inner);
                }
            }
        }
        return changed;
    }

    private static boolean strip(ClassNode node, AnnotationNode optional) {
        String iface = ((String) value(optional, "iface")).replace('.', '/');
        if (TestOptionalStripper.class.getClassLoader().getResource(iface + ".class") != null) return false;
        return node.interfaces.remove(iface);
    }

    private static boolean isOptional(MethodNode method) {
        for (List<AnnotationNode> annotations : new List[] { method.visibleAnnotations, method.invisibleAnnotations }) {
            if (annotations == null) continue;
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.equals(OPTIONAL + "Method;")) return true;
            }
        }
        return false;
    }

    @Nullable
    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values == null) return null;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (name.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    /// Whether the class file's constant pool mentions `text`; a cheap check before parsing.
    private static boolean contains(byte[] classFile, String text) {
        byte[] wanted = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i + wanted.length <= classFile.length; i++) {
            for (int j = 0; j < wanted.length; j++) {
                if (classFile[i + j] != wanted[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
