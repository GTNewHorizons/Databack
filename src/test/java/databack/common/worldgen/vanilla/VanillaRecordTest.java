package databack.common.worldgen.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/// A vanilla record (`SimpleBlockConfiguration`) rewritten for the JVM running the tests: its generated methods work
/// where the JVM has records, and are unsupported elsewhere.
class VanillaRecordTest {

    private static final String ENTRY = "net/minecraft/world/level/levelgen/feature/configurations/"
        + "SimpleBlockConfiguration.class";

    private static Constructor<?> constructor() throws IOException, ReflectiveOperationException {
        VanillaMappings mappings = VanillaMappings.builder()
            .inert("com/mojang/", "net/minecraft/world/level/levelgen/feature/configurations/FeatureConfiguration")
            .mapClass("net/minecraft/world/level/levelgen/feature/stateproviders/BlockStateProvider", Object.class)
            .passthrough("java/lang/String", "java/lang/Boolean")
            .build();
        Class<?> record = VanillaClassLoader.load(Collections.singletonMap(ENTRY, VanillaJar.classFile(ENTRY)), mappings)
            .loadClass(VanillaClassLoader.binaryName(ENTRY));
        return record.getConstructor(Object.class, boolean.class);
    }

    @Test
    void generatedMethodsWorkOnModernRuntimes() throws Exception {
        assumeTrue(isModernRuntime(), "needs a JVM with records");

        Constructor<?> make = constructor();
        Object a = make.newInstance("stone", true), b = make.newInstance("stone", true);
        Object c = make.newInstance("dirt", true);

        assertEquals("java.lang.Record", a.getClass().getSuperclass().getName());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertEquals("SimpleBlockConfiguration[toPlace=stone, scheduleTick=true]", a.toString());
    }

    @Test
    void generatedMethodsAreUnsupportedOnJava8() throws Exception {
        assumeFalse(isModernRuntime(), "needs a JVM without records");

        Object a = constructor().newInstance("stone", true);

        assertEquals(Object.class, a.getClass().getSuperclass());
        InvocationTargetException e = assertThrows(InvocationTargetException.class,
            () -> a.getClass().getMethod("hashCode").invoke(a));
        assertInstanceOf(UnsupportedOperationException.class, e.getCause());
    }

    private static boolean isModernRuntime() {
        try {
            Class.forName("java.lang.Record");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
