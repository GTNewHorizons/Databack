package databack.common.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import databack.common.worldgen.vanilla.VanillaClassRewriter;
import databack.common.worldgen.vanilla.VanillaJar;

/// The rewritten feature bundle against its stand-ins: what vanilla runs is supported, and everything it names
/// exists.
class FeatureLinkTest {

    /// The methods expected to be unsupported: Java-side factories and codec helpers that name classes the bundle
    /// lacks, the carving mask, which no placement in 26.1 reads, and the pale garden's tree decorators (the pale oak,
    /// its moss and the creaking have no 1.7 blocks).
    private static final List<String> UNSUPPORTED = Arrays.asList("blockpredicates/BlockPredicate.matchesFluids",
        "blockpredicates/BlockPredicate.noFluid", "placement/CaveSurface.valueOf",
        "placement/HeightRangePlacement.triangle", "placement/HeightRangePlacement.uniform",
        "placement/PlacementContext.getCarvingMask", "feature/treedecorators/CreakingHeartDecorator.place",
        "feature/treedecorators/PaleMossDecorator.place", "feature/treedecorators/PaleMossDecorator.addMossHanger",
        "feature/treedecorators/PaleMossDecorator.lambda$place$0",
        "feature/treedecorators/PaleMossDecorator.lambda$place$1",
        "feature/treedecorators/PaleMossDecorator.lambda$place$2",
        "feature/treedecorators/PaleMossDecorator.lambda$place$3");

    private static final Map<String, byte[]> files = new HashMap<>();
    private static final Set<String> bundle = new HashSet<>();
    private static Set<String> failing;

    @BeforeAll
    static void read() throws IOException {
        for (String entry : VanillaFeatures.CLASSES) {
            files.put(entry, VanillaJar.classFile(entry));
            bundle.add(entry.substring(0, entry.length() - ".class".length()));
        }
        failing = VanillaClassRewriter.failingMethods(files.values(), FeatureMappings.MAPPINGS, bundle);
    }

    @Test
    void onlyExpectedMethodsAreUnsupported() {
        Set<String> unexpected = new TreeSet<>();
        for (String method : failing) {
            String name = method.substring(VanillaFeatures.LEVELGEN.length(), method.indexOf('('));
            if (!UNSUPPORTED.contains(name)) unexpected.add(method);
        }
        assertEquals(new TreeSet<>(), unexpected);
    }

    @Test
    void everythingNamedExists() {
        ClassLoader loader = VanillaFeatures.load(entry -> {
            try {
                return VanillaJar.classFile(entry);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).classLoader();

        Set<String> missing = new TreeSet<>();
        for (byte[] classFile : files.values()) {
            byte[] rewritten = VanillaClassRewriter.rewrite(classFile, FeatureMappings.MAPPINGS, bundle, failing);
            for (String[] ref : references(rewritten)) {
                if (!resolves(loader, ref)) missing.add(String.join(" ", ref));
            }
        }
        assertTrue(missing.isEmpty(), "Missing members:\n" + String.join("\n", missing));
    }

    /// Each field, method and method handle a class names, as `{kind, owner, name, desc}`.
    private static List<String[]> references(byte[] classFile) {
        List<String[]> refs = new ArrayList<>();
        new ClassReader(classFile).accept(new ClassVisitor(Opcodes.ASM5) {

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String field, String fieldDesc) {
                        refs.add(new String[] { "field", owner, field, fieldDesc });
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method, String methodDesc, boolean itf) {
                        refs.add(new String[] { "method", owner, method, methodDesc });
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String indy, String indyDesc, Handle bsm, Object... args) {
                        for (Object arg : args) {
                            if (!(arg instanceof Handle)) continue;
                            Handle h = (Handle) arg;
                            String kind = h.getTag() <= Opcodes.H_PUTSTATIC ? "field" : "method";
                            refs.add(new String[] { kind, h.getOwner(), h.getName(), h.getDesc() });
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG);
        return refs;
    }

    private static boolean resolves(ClassLoader loader, String[] ref) {
        if (ref[1].startsWith("[")) return true;

        Class<?> owner;
        try {
            owner = Class.forName(ref[1].replace('/', '.'), false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }

        if (ref[2].equals("<init>")) {
            for (Constructor<?> c : owner.getDeclaredConstructors()) {
                if (Type.getConstructorDescriptor(c).equals(ref[3])) return true;
            }
            return false;
        }

        Deque<Class<?>> pending = new ArrayDeque<>();
        pending.push(owner);
        while (!pending.isEmpty()) {
            Class<?> c = pending.pop();
            if (ref[0].equals("field")) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getName().equals(ref[2]) && Type.getDescriptor(f.getType()).equals(ref[3])) return true;
                }
            } else {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(ref[2]) && Type.getMethodDescriptor(m).equals(ref[3])) return true;
                }
            }
            if (c.getSuperclass() != null) pending.push(c.getSuperclass());
            for (Class<?> itf : c.getInterfaces()) pending.push(itf);
        }
        return false;
    }
}
