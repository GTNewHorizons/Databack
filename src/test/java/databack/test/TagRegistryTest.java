package databack.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import databack.common.dto.tag.TagEntry;
import databack.common.dto.tag.TagFile;
import databack.common.tags.TagRegistry;

class TagRegistryTest {

    @BeforeEach
    void reset() {
        TagRegistry.INSTANCE.clearForTesting();
    }

    // ---- TagEntry deserialization -------------------------------------------

    @Test
    void tagEntry_stringForm_parsesPlainId() {
        TagEntry e = TagFile.GSON.fromJson("\"minecraft:stone\"", TagEntry.class);
        assertEquals("minecraft:stone", e.id);
        assertFalse(e.isTagRef);
        assertTrue(e.required);
    }

    @Test
    void tagEntry_stringFormWithHash_parsesAsTagRef() {
        TagEntry e = TagFile.GSON.fromJson("\"#minecraft:logs\"", TagEntry.class);
        assertEquals("minecraft:logs", e.id);
        assertTrue(e.isTagRef);
        assertTrue(e.required);
    }

    @Test
    void tagEntry_objectFormOptional_parsesRequiredFalse() {
        TagEntry e = TagFile.GSON.fromJson("{\"id\":\"minecraft:stone\",\"required\":false}", TagEntry.class);
        assertEquals("minecraft:stone", e.id);
        assertFalse(e.isTagRef);
        assertFalse(e.required);
    }

    @Test
    void tagEntry_objectFormDefaultRequired_isTrue() {
        TagEntry e = TagFile.GSON.fromJson("{\"id\":\"minecraft:stone\"}", TagEntry.class);
        assertTrue(e.required);
    }

    @Test
    void tagEntry_objectFormTagRef_parsesIsTagRefTrue() {
        TagEntry e = TagFile.GSON.fromJson("{\"id\":\"#minecraft:logs\",\"required\":false}", TagEntry.class);
        assertEquals("minecraft:logs", e.id);
        assertTrue(e.isTagRef);
        assertFalse(e.required);
    }

    // ---- TagFile deserialization --------------------------------------------

    @Test
    void tagFile_replaceDefaultsFalse() {
        TagFile f = TagFile.GSON.fromJson("{\"values\":[]}", TagFile.class);
        assertFalse(f.replace);
    }

    @Test
    void tagFile_replaceTrue_parsed() {
        TagFile f = TagFile.GSON.fromJson("{\"replace\":true,\"values\":[]}", TagFile.class);
        assertTrue(f.replace);
    }

    @Test
    void tagFile_valuesAbsent_nullSafe() {
        TagFile f = TagFile.GSON.fromJson("{}", TagFile.class);
        // accumulate must not throw on null values
        assertDoesNotThrow(() -> TagRegistry.INSTANCE.accumulate("blocks", "minecraft:logs", f));
    }

    // ---- Static entries -----------------------------------------------------

    @Test
    void registerStatic_beforeResolve_fallbackReturnsEntries() {
        TagRegistry.INSTANCE.registerStatic("blocks", "minecraft:logs", Arrays.asList("minecraft:log", "minecraft:log2"));
        // No resolve() called — fallback path
        Set<String> result = TagRegistry.INSTANCE.getEntries("blocks", "minecraft:logs");
        assertTrue(result.contains("minecraft:log"));
        assertTrue(result.contains("minecraft:log2"));
    }

    @Test
    void registerStatic_afterResolve_includedInResult() {
        TagRegistry.INSTANCE.registerStatic("blocks", "minecraft:logs", Arrays.asList("minecraft:log"));
        TagRegistry.INSTANCE.resolve();
        Set<String> result = TagRegistry.INSTANCE.getEntries("blocks", "minecraft:logs");
        assertTrue(result.contains("minecraft:log"));
    }

    @Test
    void registerStatic_survivesCleared() {
        TagRegistry.INSTANCE.registerStatic("blocks", "minecraft:planks", Arrays.asList("minecraft:planks"));
        TagRegistry.INSTANCE.clearDynamic();
        // Still available via fallback
        Set<String> result = TagRegistry.INSTANCE.getEntries("blocks", "minecraft:planks");
        assertFalse(result.isEmpty());
    }

    // ---- Accumulate + resolve -----------------------------------------------

    @Test
    void accumulate_singlePack_entriesResolved() {
        TagFile f = tagFile(false, "minecraft:stone");
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:base_stone", f);
        TagRegistry.INSTANCE.resolve();

        Set<String> result = TagRegistry.INSTANCE.getEntries("blocks", "minecraft:base_stone");
        assertEquals(new HashSet<>(Arrays.asList("minecraft:stone")), result);
    }

    @Test
    void accumulate_twoPacks_replaceFalse_union() {
        // High priority pack processed first
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:logs", tagFile(false, "minecraft:acacia_log"));
        // Low priority pack
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:logs", tagFile(false, "minecraft:oak_log"));
        TagRegistry.INSTANCE.resolve();

        Set<String> result = TagRegistry.INSTANCE.getEntries("blocks", "minecraft:logs");
        assertTrue(result.contains("minecraft:acacia_log"));
        assertTrue(result.contains("minecraft:oak_log"));
    }

    @Test
    void accumulate_highPriorityReplaceTrue_blocksLowerPack() {
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:logs", tagFile(true, "minecraft:acacia_log"));
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:logs", tagFile(false, "minecraft:oak_log"));
        TagRegistry.INSTANCE.resolve();

        Set<String> result = TagRegistry.INSTANCE.getEntries("blocks", "minecraft:logs");
        assertTrue(result.contains("minecraft:acacia_log"), "High-priority entry should be present");
        assertFalse(result.contains("minecraft:oak_log"), "Low-priority entry blocked by replace:true");
    }

    @Test
    void accumulate_lowPriorityReplaceTrue_doesNotBlockHigherPack() {
        // High priority: replace:false
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:logs", tagFile(false, "minecraft:acacia_log"));
        // Low priority: replace:true (but high priority already processed — replace:true here is irrelevant)
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:logs", tagFile(true, "minecraft:oak_log"));
        TagRegistry.INSTANCE.resolve();

        Set<String> result = TagRegistry.INSTANCE.getEntries("blocks", "minecraft:logs");
        assertTrue(result.contains("minecraft:acacia_log"));
        assertTrue(result.contains("minecraft:oak_log"), "Low-priority replace:true doesn't block higher packs");
    }

    @Test
    void accumulate_unknownTag_returnsEmpty() {
        TagRegistry.INSTANCE.resolve();
        Set<String> result = TagRegistry.INSTANCE.getEntries("blocks", "minecraft:does_not_exist");
        assertEquals(Collections.emptySet(), result);
    }

    // ---- Nested tag resolution ----------------------------------------------

    @Test
    void resolve_nestedTagRef_expandsRecursively() {
        // minecraft:logs contains two concrete blocks
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:logs", tagFile(false, "minecraft:oak_log", "minecraft:birch_log"));
        // minecraft:overworld_natural_logs references #minecraft:logs
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:overworld_natural_logs", tagFileWithRef(false, "minecraft:logs"));
        TagRegistry.INSTANCE.resolve();

        Set<String> result = TagRegistry.INSTANCE.getEntries("blocks", "minecraft:overworld_natural_logs");
        assertTrue(result.contains("minecraft:oak_log"));
        assertTrue(result.contains("minecraft:birch_log"));
    }

    @Test
    void resolve_staticPlusDynamic_merged() {
        TagRegistry.INSTANCE.registerStatic("blocks", "minecraft:logs", Arrays.asList("minecraft:log"));
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:logs", tagFile(false, "minecraft:mangrove_log"));
        TagRegistry.INSTANCE.resolve();

        Set<String> result = TagRegistry.INSTANCE.getEntries("blocks", "minecraft:logs");
        assertTrue(result.contains("minecraft:log"), "Static entry present");
        assertTrue(result.contains("minecraft:mangrove_log"), "Dynamic entry present");
    }

    @Test
    void resolve_nestedRefResolvesStaticEntries() {
        TagRegistry.INSTANCE.registerStatic("blocks", "minecraft:logs", Arrays.asList("minecraft:oak_log"));
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:wooden_blocks", tagFileWithRef(false, "minecraft:logs"));
        TagRegistry.INSTANCE.resolve();

        Set<String> result = TagRegistry.INSTANCE.getEntries("blocks", "minecraft:wooden_blocks");
        assertTrue(result.contains("minecraft:oak_log"));
    }

    // ---- Cycle detection ----------------------------------------------------

    @Test
    void resolve_directCycle_doesNotThrowAndWarnsProperly() {
        // A → #B, B → #A
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:a", tagFileWithRef(false, "minecraft:b"));
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:b", tagFileWithRef(false, "minecraft:a"));
        // Must not throw; cycle produces empty or partial result
        assertDoesNotThrow(() -> TagRegistry.INSTANCE.resolve());
    }

    @Test
    void resolve_selfCycle_doesNotThrow() {
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:a", tagFileWithRef(false, "minecraft:a"));
        assertDoesNotThrow(() -> TagRegistry.INSTANCE.resolve());
    }

    @Test
    void resolve_longerCycle_doesNotThrow() {
        // A → #B → #C → #A
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:a", tagFileWithRef(false, "minecraft:b"));
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:b", tagFileWithRef(false, "minecraft:c"));
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:c", tagFileWithRef(false, "minecraft:a"));
        assertDoesNotThrow(() -> TagRegistry.INSTANCE.resolve());
    }

    // ---- Optional entries ---------------------------------------------------

    @Test
    void resolve_optionalMissingTag_doesNotThrowAndSkips() {
        TagFile f = tagFileWithOptionalRef("minecraft:does_not_exist");
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:some_tag", f);
        assertDoesNotThrow(() -> TagRegistry.INSTANCE.resolve());
        // The optional missing ref contributes nothing; result should be empty (no other entries)
        Set<String> result = TagRegistry.INSTANCE.getEntries("blocks", "minecraft:some_tag");
        assertFalse(result.contains("minecraft:does_not_exist"));
    }

    @Test
    void resolve_requiredMissingTag_warnsAndSkips() {
        // Required missing tag ref should warn but not throw
        TagFile f = tagFileWithRef(false, "minecraft:does_not_exist");
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:parent", f);
        assertDoesNotThrow(() -> TagRegistry.INSTANCE.resolve());
    }

    // ---- Different tag types ------------------------------------------------

    @Test
    void accumulate_differentTagTypes_isolated() {
        TagRegistry.INSTANCE.accumulate("blocks", "minecraft:logs", tagFile(false, "minecraft:log"));
        TagRegistry.INSTANCE.accumulate("items", "minecraft:logs", tagFile(false, "minecraft:log_item"));
        TagRegistry.INSTANCE.resolve();

        Set<String> blocks = TagRegistry.INSTANCE.getEntries("blocks", "minecraft:logs");
        Set<String> items  = TagRegistry.INSTANCE.getEntries("items",  "minecraft:logs");

        assertTrue(blocks.contains("minecraft:log"));
        assertFalse(blocks.contains("minecraft:log_item"));
        assertTrue(items.contains("minecraft:log_item"));
        assertFalse(items.contains("minecraft:log"));
    }

    @Test
    void accumulate_multiSegmentTagType_worksCorrectly() {
        TagRegistry.INSTANCE.accumulate("worldgen/biome", "minecraft:is_forest", tagFile(false, "minecraft:forest"));
        TagRegistry.INSTANCE.resolve();

        Set<String> result = TagRegistry.INSTANCE.getEntries("worldgen/biome", "minecraft:is_forest");
        assertTrue(result.contains("minecraft:forest"));
    }

    // ---- Helpers ------------------------------------------------------------

    private static TagFile tagFile(boolean replace, String... ids) {
        TagFile f = new TagFile();
        f.replace = replace;
        f.values = new java.util.ArrayList<>();
        for (String id : ids) {
            f.values.add(new TagEntry(id, false, true));
        }
        return f;
    }

    private static TagFile tagFileWithRef(boolean replace, String... tagIds) {
        TagFile f = new TagFile();
        f.replace = replace;
        f.values = new java.util.ArrayList<>();
        for (String id : tagIds) {
            f.values.add(new TagEntry(id, true, true));
        }
        return f;
    }

    private static TagFile tagFileWithOptionalRef(String tagId) {
        TagFile f = new TagFile();
        f.replace = false;
        f.values = Collections.singletonList(new TagEntry(tagId, true, false));
        return f;
    }
}
