package databack.common.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import databack.common.worldgen.vanilla.VanillaJar;

/// The feature bundle loads, and builds the feature types it runs.
class VanillaFeaturesTest {

    private static VanillaFeatures features;

    @BeforeAll
    static void load() {
        features = VanillaFeatures.load(entry -> {
            try {
                return VanillaJar.classFile(entry);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    @Test
    void runFeatureTypesAreBuilt() {
        for (String name : new String[] { "ORE", "DISK", "SIMPLE_BLOCK", "RANDOM_SELECTOR", "SIMPLE_RANDOM_SELECTOR",
            "RANDOM_BOOLEAN_SELECTOR", "NO_OP", "TREE" }) {
            assertNotNull(features.featureType(name), name);
        }
    }

    @Test
    void otherFeatureTypesAreNull() {
        assertNull(features.featureType("LAKE"));
    }

    @Test
    void everyClassLoads() throws ClassNotFoundException {
        for (String entry : VanillaFeatures.CLASSES) {
            String name = entry.substring(VanillaFeatures.LEVELGEN.length(), entry.length() - ".class".length());
            assertNotNull(features.vanillaClass(name), name);
        }
    }
}
