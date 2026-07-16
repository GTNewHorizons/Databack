package databack.common.tags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import databack.common.dto.tag.TagEntry;
import databack.common.dto.tag.TagFile;

/**
 * Central registry for all datapack tags.
 *
 * <p>Manages two data layers:
 * <ul>
 *   <li><b>Static entries</b>: vanilla shim values registered at startup via
 *       {@link #registerStatic}. These survive {@link #clearDynamic()} and are always
 *       included in query results.</li>
 *   <li><b>Dynamic entries</b>: values accumulated from loaded datapacks via
 *       {@link #accumulate}. These are cleared at the start of each load cycle.</li>
 * </ul>
 *
 * <p>Call order per world load:
 * <ol>
 *   <li>{@link #clearDynamic()} — reset dynamic state</li>
 *   <li>{@link #accumulate} (once per tag file, packs in HIGH → LOW priority order)</li>
 *   <li>{@link #resolve()} — expand {@code #tag} references; must be called before queries</li>
 * </ol>
 */
public final class TagRegistry {

    public static final TagRegistry INSTANCE = new TagRegistry();
    private static final Logger LOGGER = LogManager.getLogger("databack-tags");

    // --- Static layer (never cleared) ---
    // tagType -> tagId -> list of plain resource IDs (no tag refs)
    private final Map<String, Map<String, List<String>>> staticEntries = new HashMap<>();

    // --- Dynamic layer (cleared each load) ---
    // tagType -> tagId -> accumulation state
    private final Map<String, Map<String, AccumulationState>> pending = new HashMap<>();

    // tagType -> tagId -> resolved set of plain resource IDs
    private Map<String, Map<String, Set<String>>> resolved = null;

    private TagRegistry() {}

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    private static final class AccumulationState {

        final List<TagEntry> entries = new ArrayList<>();
        boolean replaceSeen = false;
    }

    // -------------------------------------------------------------------------
    // API
    // -------------------------------------------------------------------------

    /**
     * Registers static shim entries that survive {@link #clearDynamic()}.
     *
     * @param tagType the tag type folder path, e.g. {@code "blocks"} or {@code "worldgen/biome"}
     * @param tagId   the fully-qualified tag ID, e.g. {@code "minecraft:logs"}
     * @param ids     plain resource IDs (no {@code #} prefix)
     */
    public void registerStatic(String tagType, String tagId, List<String> ids) {
        staticEntries.computeIfAbsent(tagType, k -> new HashMap<>())
            .computeIfAbsent(tagId, k -> new ArrayList<>())
            .addAll(ids);
    }

    /**
     * Clears all dynamic (datapack-sourced) state. Does not affect static entries.
     * Call at the start of each load cycle.
     */
    public void clearDynamic() {
        pending.clear();
        resolved = null;
    }

    /** Clears all state including static entries. Only for use in tests. */
    public void clearForTesting() {
        staticEntries.clear();
        pending.clear();
        resolved = null;
    }

    /**
     * Accumulates tag entries from one pack. Must be called in HIGH → LOW priority order.
     *
     * <p>If a higher-priority pack already set {@code replace: true} for this tag, this call
     * is silently ignored.
     *
     * @param tagType the tag type folder path, e.g. {@code "blocks"}
     * @param tagId   the fully-qualified tag ID, e.g. {@code "minecraft:logs"}
     * @param tagFile the parsed tag file
     */
    public void accumulate(String tagType, String tagId, TagFile tagFile) {
        AccumulationState state = pending.computeIfAbsent(tagType, k -> new HashMap<>())
            .computeIfAbsent(tagId, k -> new AccumulationState());

        if (state.replaceSeen) {
            // A higher-priority pack already replaced this tag; ignore lower-priority packs.
            return;
        }

        if (tagFile.values != null) {
            state.entries.addAll(tagFile.values);
        }

        if (tagFile.replace) {
            state.replaceSeen = true;
        }
    }

    /**
     * Resolves all accumulated tag entries by expanding {@code #tag} references recursively.
     * Must be called after all {@link #accumulate} calls and before any {@link #getEntries}
     * queries. Cycle detection prevents infinite recursion; cycles produce a warning and
     * resolve to an empty contribution. Missing optional entries are warned and skipped.
     */
    public void resolve() {
        resolved = new HashMap<>();

        // Collect all tag types from both layers
        Set<String> allTypes = new LinkedHashSet<>();
        allTypes.addAll(staticEntries.keySet());
        allTypes.addAll(pending.keySet());

        for (String tagType : allTypes) {
            Set<String> tagIds = new LinkedHashSet<>();
            Map<String, List<String>> staticType = staticEntries.get(tagType);
            Map<String, AccumulationState> pendingType = pending.get(tagType);
            if (staticType != null) tagIds.addAll(staticType.keySet());
            if (pendingType != null) tagIds.addAll(pendingType.keySet());

            for (String tagId : tagIds) {
                resolveTag(tagType, tagId, new LinkedHashSet<>());
            }
        }
    }

    /**
     * Returns the resolved set of resource IDs for a tag.
     *
     * <p>If {@link #resolve()} has not been called (e.g. before any pack load), falls back
     * to the static entry list with no tag-ref expansion.
     *
     * @param tagType the tag type folder path, e.g. {@code "blocks"}
     * @param tagId   the fully-qualified tag ID, e.g. {@code "minecraft:logs"}
     * @return an unmodifiable set of resource IDs; empty if the tag is unknown
     */
    public Set<String> getEntries(String tagType, String tagId) {
        if (resolved != null) {
            Map<String, Set<String>> typeMap = resolved.get(tagType);
            if (typeMap != null) {
                Set<String> result = typeMap.get(tagId);
                if (result != null) return result;
            }
            return Collections.emptySet();
        }

        // Fallback: static entries only (no tag-ref expansion)
        Map<String, List<String>> staticType = staticEntries.get(tagType);
        if (staticType != null) {
            List<String> staticIds = staticType.get(tagId);
            if (staticIds != null) return Collections.unmodifiableSet(new LinkedHashSet<>(staticIds));
        }
        return Collections.emptySet();
    }

    // -------------------------------------------------------------------------
    // Internal resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves a single tag by DFS, using {@code visitPath} for cycle detection.
     *
     * @param tagType   the tag type
     * @param tagId     the fully-qualified tag ID being resolved
     * @param visitPath tags currently on the DFS stack (for cycle detection)
     * @return the resolved set of plain resource IDs
     */
    private Set<String> resolveTag(String tagType, String tagId, LinkedHashSet<String> visitPath) {
        Map<String, Set<String>> typeMap = resolved.computeIfAbsent(tagType, k -> new HashMap<>());

        // Already resolved in this cycle
        if (typeMap.containsKey(tagId)) {
            return typeMap.get(tagId);
        }

        String key = tagType + ":" + tagId;

        // Cycle detected
        if (visitPath.contains(key)) {
            LOGGER.warn(
                "Circular tag reference detected for '{}' in type '{}'; cycle path: {} -> {}. "
                    + "The circular entries will be ignored.",
                tagId,
                tagType,
                visitPath,
                key);
            // Return empty to break the cycle; do NOT store in resolved (avoids caching partial state)
            return Collections.emptySet();
        }

        visitPath.add(key);

        Set<String> result = new LinkedHashSet<>();

        // 1. Static entries for this tag (plain IDs only, no tag refs)
        Map<String, List<String>> staticType = staticEntries.get(tagType);
        if (staticType != null) {
            List<String> staticIds = staticType.get(tagId);
            if (staticIds != null) {
                result.addAll(staticIds);
            }
        }

        // 2. Dynamic entries (may contain tag refs)
        Map<String, AccumulationState> pendingType = pending.get(tagType);
        if (pendingType != null) {
            AccumulationState state = pendingType.get(tagId);
            if (state != null) {
                for (TagEntry entry : state.entries) {
                    if (entry.isTagRef) {
                        // Recursively resolve the referenced tag
                        String refId = entry.id; // already stripped of '#'
                        Map<String, Set<String>> refTypeMap = resolved.computeIfAbsent(tagType, k -> new HashMap<>());

                        Set<String> refResult;
                        if (refTypeMap.containsKey(refId)) {
                            refResult = refTypeMap.get(refId);
                        } else {
                            refResult = resolveTag(tagType, refId, visitPath);
                        }

                        if (refResult.isEmpty() && !tagExists(tagType, refId)) {
                            if (entry.required) {
                                LOGGER.warn(
                                    "Tag '{}' in type '{}' references unknown tag '#{}'; "
                                        + "this entry will be skipped.",
                                    tagId,
                                    tagType,
                                    refId);
                            }
                            // Either way: skip the missing tag
                        } else {
                            result.addAll(refResult);
                        }
                    } else {
                        result.add(entry.id);
                    }
                }
            }
        }

        visitPath.remove(key);

        Set<String> unmodifiable = Collections.unmodifiableSet(result);
        typeMap.put(tagId, unmodifiable);
        return unmodifiable;
    }

    /** Returns true if the tag exists in either the static or pending layer. */
    private boolean tagExists(String tagType, String tagId) {
        Map<String, List<String>> staticType = staticEntries.get(tagType);
        if (staticType != null && staticType.containsKey(tagId)) return true;
        Map<String, AccumulationState> pendingType = pending.get(tagType);
        return pendingType != null && pendingType.containsKey(tagId);
    }
}
