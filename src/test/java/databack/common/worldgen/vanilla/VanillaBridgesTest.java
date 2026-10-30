package databack.common.worldgen.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.function.IntToDoubleFunction;
import java.util.function.LongUnaryOperator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import databack.common.worldgen.vanilla.fixture.BridgeTarget;

/// [VanillaClassLoader#bridge] over a rewritten [BridgeTarget].
class VanillaBridgesTest {

    private static final String TARGET = BridgeTarget.class.getName();

    private static VanillaClassLoader loader;

    /// A constructor bridge may give its target as anything the target is.
    @FunctionalInterface
    public interface Factory {

        Object create(String name, long count);
    }

    @BeforeAll
    static void load() throws IOException {
        VanillaMappings mappings = VanillaMappings.builder().passthrough("java/lang/String").build();
        loader = VanillaClassLoader.load(Collections.singletonMap(
            Type.getInternalName(BridgeTarget.class) + ".class", read(BridgeTarget.class)), mappings);
    }

    private static byte[] read(Class<?> type) throws IOException {
        try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) > 0;) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    @Test
    void staticMethodsAreCalled() {
        assertEquals(42L, loader.bridge(LongUnaryOperator.class, TARGET, "twice").applyAsLong(21));
        assertEquals(1.5, loader.bridge(IntToDoubleFunction.class, TARGET, "half").applyAsDouble(3));
    }

    @Test
    void constructorsAreCalled() throws ReflectiveOperationException {
        Object made = loader.bridge(Factory.class, TARGET, "<init>").create("made", 7);
        assertEquals("made7", made.getClass().getField("name").get(made));
    }

    @Test
    void badTargetsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> loader.bridge(LongUnaryOperator.class, TARGET, "missing"));
        assertThrows(IllegalArgumentException.class, () -> loader.bridge(LongUnaryOperator.class, TARGET, "instance"));
        assertThrows(IllegalArgumentException.class, () -> loader.bridge(IntToDoubleFunction.class, TARGET, "twice"));
        assertThrows(IllegalArgumentException.class, () -> loader.bridge(Runnable.class, TARGET, "<init>"));
    }
}
