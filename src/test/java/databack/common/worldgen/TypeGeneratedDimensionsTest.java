package databack.common.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.WorldProviderSurface;

import org.junit.jupiter.api.Test;

/// The world type generates the dimensions whose providers leave it to the type, as in 1.7.
class TypeGeneratedDimensionsTest {

    @Test
    void generatesTheOverworld() {
        assertTrue(TypeGeneratedDimensions.includes(WorldProviderSurface.class));
    }

    @Test
    void leavesTheNetherAndEndToTheirProviders() {
        assertFalse(TypeGeneratedDimensions.includes(WorldProviderHell.class));
        assertFalse(TypeGeneratedDimensions.includes(WorldProviderEnd.class));
    }
}
