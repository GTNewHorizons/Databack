package databack.common.interop.modern_block;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.Test;

class StateFormsTest {

    private static final Block LIT_FURNACE = new Block(Material.rock) {};
    private static final Block DOUBLE_SLAB = new Block(Material.rock) {};

    private static final ResourceLocation FURNACE = new ResourceLocation("test:furnace");
    private static final ResourceLocation SLAB = new ResourceLocation("test:slab");

    private static Map<String, String> props(String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put(kv[i], kv[i + 1]);
        return map;
    }

    @Test
    void selectsFormByPropertyValue() {
        StateForms forms = new StateForms();
        BlockIdentity lit = new BlockIdentity(FURNACE, LIT_FURNACE, null);
        forms.add("lit", "true", lit);

        assertSame(lit, forms.select(FURNACE, props("lit", "true", "facing", "north")).identity);
        assertNull(forms.select(FURNACE, props("lit", "false")));
        assertNull(forms.select(FURNACE, props("facing", "north")));
        assertNull(forms.select(FURNACE, Collections.emptyMap()));
        assertNull(forms.select(FURNACE, null));
    }

    @Test
    void formsAreKeptPerModernBlock() {
        StateForms forms = new StateForms();
        forms.add("lit", "true", new BlockIdentity(FURNACE, LIT_FURNACE, null));
        BlockIdentity doubled = new BlockIdentity(SLAB, DOUBLE_SLAB, null);
        forms.add("type", "double", doubled);

        assertNull(forms.select(SLAB, props("lit", "true")));
        assertSame(doubled, forms.select(SLAB, props("type", "double")).identity);
        assertNull(forms.select(SLAB, props("type", "top")));
    }

    @Test
    void firstMatchingFormWins() {
        StateForms forms = new StateForms();
        BlockIdentity first = new BlockIdentity(FURNACE, LIT_FURNACE, null);
        BlockIdentity second = new BlockIdentity(FURNACE, DOUBLE_SLAB, null);
        forms.add("lit", "true", first);
        forms.add("lit", "true", second);

        assertSame(first, forms.select(FURNACE, props("lit", "true")).identity);
    }
}
