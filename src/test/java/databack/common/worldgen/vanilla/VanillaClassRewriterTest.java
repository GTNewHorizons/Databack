package databack.common.worldgen.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import databack.common.worldgen.vanilla.fixture.InertThing;
import databack.common.worldgen.vanilla.fixture.MissingThing;
import databack.common.worldgen.vanilla.fixture.RewriteFixture;
import databack.common.worldgen.vanilla.fixture.ShelfStandIn;
import databack.common.worldgen.vanilla.fixture.VanillaShelf;

/// Inert classes, replacements and best-effort static initializers, on [RewriteFixture].
class VanillaClassRewriterTest {

    private static final String SUBJECT = Type.getInternalName(RewriteFixture.class);

    private static Class<?> rewritten;

    @BeforeAll
    static void rewrite() throws IOException, ClassNotFoundException {
        VanillaMappings mappings = VanillaMappings.builder()
            .inert(Type.getInternalName(InertThing.class))
            .mapClass(Type.getInternalName(VanillaShelf.class), ShelfStandIn.class)
            .passthrough("java/lang/Integer", "java/lang/String", "java/util/function/Supplier")
            .build();

        byte[] classFile = VanillaClassRewriter.rewrite(read(RewriteFixture.class), mappings,
            Collections.singleton(SUBJECT));
        rewritten = new IsolatingLoader(RewriteFixture.class.getName(), classFile)
            .loadClass(RewriteFixture.class.getName());
    }

    private static byte[] read(Class<?> type) throws IOException {
        try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) > 0;) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    @Test
    void staticInitializerKeepsWhatItCanBuild() throws ReflectiveOperationException {
        assertEquals(7, rewritten.getField("VALUE").get(null));
    }

    @Test
    void inertAndUnsupportedConstantsAreNull() throws ReflectiveOperationException {
        assertNull(rewritten.getField("CODEC").get(null));
        assertNull(rewritten.getField("MISSING").get(null));
        assertNull(rewritten.getField("FACTORY").get(null));
    }

    @Test
    void fieldsOfUnsupportedOrInertTypesAreObjects() throws ReflectiveOperationException {
        assertEquals(Object.class, rewritten.getField("CODEC").getType());
        assertEquals(Object.class, rewritten.getField("MISSING").getType());
    }

    @Test
    void inertCallsGiveZero() throws ReflectiveOperationException {
        Method use = rewritten.getMethod("useInert", Object.class);
        assertEquals(1, use.invoke(null, (Object) null));
    }

    @Test
    void fieldsAndCallsGivingInertTypesGiveNull() throws ReflectiveOperationException {
        assertEquals(true, rewritten.getMethod("inertMembersAreNull").invoke(null));
    }

    @Test
    void replacementsTakeAndGiveBundledClassesAsObjects() throws ReflectiveOperationException {
        Object value = rewritten.getConstructor().newInstance();
        assertSame(value, rewritten.getMethod("echo", rewritten).invoke(null, value));
    }

    @Test
    void unsupportedMethodsStillThrow() throws ReflectiveOperationException {
        Method use = rewritten.getMethod("useMissing");
        InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> use.invoke(null));
        assertInstanceOf(UnsupportedOperationException.class, e.getCause());
    }

    /// Defines the rewritten class itself, and refuses the fixture's inert, unsupported and replaced classes: nothing
    /// rewritten may still need them.
    private static final class IsolatingLoader extends ClassLoader {

        private final String name;
        private final byte[] classFile;

        IsolatingLoader(String name, byte[] classFile) {
            super(IsolatingLoader.class.getClassLoader());
            this.name = name;
            this.classFile = classFile;
        }

        @Override
        protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
            if (className.equals(InertThing.class.getName()) || className.equals(MissingThing.class.getName())
                || className.equals(VanillaShelf.class.getName())) {
                throw new ClassNotFoundException(className + " must not be loaded");
            }
            if (!className.equals(name)) return super.loadClass(className, resolve);

            synchronized (getClassLoadingLock(className)) {
                Class<?> defined = findLoadedClass(className);
                return defined != null ? defined : defineClass(className, classFile, 0, classFile.length);
            }
        }
    }
}
