package databack.common.interop.modern_block;

import static databack.common.interop.VanillaBlockIdentities.bits;
import static databack.common.interop.VanillaBlockIdentities.colored;
import static databack.common.interop.VanillaBlockIdentities.lookup;
import static databack.common.interop.VanillaBlockIdentities.torchPlacement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.gtnewhorizon.gtnhlib.blockstate.core.InvalidPropertyTextException;

class NamedVariantPropertyTest {

    @Test
    void decodesMaskedBits() {
        // Anvil: bits 3:2 = damage, bits 1:0 = facing
        var anvil = new NamedVariantProperty(bits(0b1100), "anvil", "chipped_anvil", "damaged_anvil");

        assertEquals("anvil", anvil.getValue(0b0011).name());
        assertEquals("chipped_anvil", anvil.getValue(0b0110).name());
        assertEquals("damaged_anvil", anvil.getValue(0b1001).name());
    }

    @Test
    void encodingKeepsOtherBits() {
        var anvil = new NamedVariantProperty(bits(0b1100), "anvil", "chipped_anvil", "damaged_anvil");

        NamedVariant damaged = anvil.getVariants()[2];

        assertEquals(0b1011, anvil.getMeta(damaged, 0b0011));
    }

    @Test
    void outOfRangeIndicesWrap() {
        // Wooden slabs only use 6 of the 8 values in bits 2:0
        var slabs = new NamedVariantProperty(bits(0b111), "a", "b", "c", "d", "e", "f");

        assertEquals("a", slabs.getValue(6).name());
        assertEquals("b", slabs.getValue(7).name());
        assertEquals("b", slabs.getValue(7 | 8).name());

        var negative = new NamedVariantProperty(lookup(meta -> meta - 1, (value, existing) -> value + 1), "a", "b", "c");

        assertEquals("c", negative.getValue(0).name());
    }

    @Test
    void variantsAreStableInstances() {
        var property = new NamedVariantProperty(bits(0b1), "sand", "red_sand");

        assertSame(property.getValue(1), property.getValue(1));
        assertSame(property.getVariants()[1], property.getValue(1));
    }

    @Test
    void parsesAndStringifiesByName() throws InvalidPropertyTextException {
        var property = new NamedVariantProperty(bits(0b1), "sand", "red_sand");

        assertSame(property.getVariants()[1], property.parse("red_sand"));
        assertEquals("red_sand", property.stringify(property.getVariants()[1]));
        assertThrows(InvalidPropertyTextException.class, () -> property.parse("RED_SAND"));
    }

    @Test
    void torchPlacementSplitsWallAndFloor() {
        var torch = new NamedVariantProperty(torchPlacement(), "torch", "wall_torch");

        assertEquals("torch", torch.getValue(5).name());
        assertEquals("torch", torch.getValue(0).name());

        for (int meta = 1; meta <= 4; meta++) {
            assertEquals("wall_torch", torch.getValue(meta).name());
        }

        // Floor always becomes 5; wall keeps an existing wall facing, or picks one
        assertEquals(5, torch.getMeta(torch.getVariants()[0], 3));
        assertEquals(3, torch.getMeta(torch.getVariants()[1], 3));
        assertEquals(1, torch.getMeta(torch.getVariants()[1], 5));
    }

    @Test
    void colorsAreInDyeOrder() {
        String[] wool = colored("minecraft:", "_wool");

        assertEquals(16, wool.length);
        assertEquals("minecraft:white_wool", wool[0]);
        assertEquals("minecraft:light_gray_wool", wool[8]);
        assertEquals("minecraft:black_wool", wool[15]);
    }
}
