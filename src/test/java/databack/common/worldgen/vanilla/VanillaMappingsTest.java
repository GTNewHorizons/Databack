package databack.common.worldgen.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

class VanillaMappingsTest {

    private static final String KEY = "net/minecraft/resources/ResourceKey";
    private static final String HOLDER = "net/minecraft/core/Holder";
    private static final String SUPPLIER = "net/minecraft/Supplierish";

    private final VanillaMappings mappings = VanillaMappings.builder()
        .mapClass(KEY, String.class)
        .mapClass(SUPPLIER, Supplier.class)
        .passthrough("java/util/List")
        .redirectStatic("java/lang/Math", "floorMod", VanillaMappingsTest.class)
        .constantFields("net/minecraft/Flags", "Z", name -> Boolean.FALSE)
        .addInterface("net/minecraft/Thing", Supplier.class)
        .build();

    @Test
    void descriptorsAndArraysAreMapped() {
        assertEquals("(IL" + HOLDER + ";)Ljava/lang/String;", mappings.mapDesc("(IL" + HOLDER + ";)L" + KEY + ";"));
        assertEquals("[[Ljava/lang/String;", mappings.mapName("[[L" + KEY + ";"));
        assertEquals(HOLDER, mappings.mapName(HOLDER));
    }

    @Test
    void supportedClasses() {
        Set<String> bundle = Collections.singleton("net/minecraft/Self");

        assertTrue(mappings.isSupportedClass(bundle, "net/minecraft/Self"));
        assertTrue(mappings.isSupportedClass(bundle, KEY));
        assertTrue(mappings.isSupportedClass(bundle, "java/util/List"));
        assertTrue(mappings.isSupportedClass(bundle, "java/lang/Object"));
        assertTrue(mappings.isSupportedType(bundle, Type.getType("[I")));
        assertFalse(mappings.isSupportedClass(bundle, HOLDER));
        assertFalse(mappings.isSupportedClass(bundle, "[L" + HOLDER + ";"));
    }

    @Test
    void interfaceCallsFollowTheReplacement() {
        assertTrue(mappings.isInterfaceCall(SUPPLIER, false));
        assertFalse(mappings.isInterfaceCall(KEY, true));
        assertTrue(mappings.isInterfaceCall("java/util/List", true));
    }

    @Test
    void constantsRedirectsAndInterfaces() {
        assertEquals(Boolean.FALSE, mappings.constant("net/minecraft/Flags", "DEBUG", "Z"));
        assertNull(mappings.constant("net/minecraft/Flags", "COUNT", "I"));
        assertEquals(Type.getInternalName(VanillaMappingsTest.class),
            mappings.staticRedirect("java/lang/Math", "floorMod"));
        assertNull(mappings.staticRedirect("java/lang/Math", "abs"));
        assertEquals(Collections.singletonList(Type.getInternalName(Supplier.class)),
            mappings.addedInterfaces("net/minecraft/Thing"));
        assertThrows(IllegalArgumentException.class,
            () -> VanillaMappings.builder().addInterface("x/Y", String.class));
    }
}
