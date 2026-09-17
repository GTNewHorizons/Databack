package databack.common.debug;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Tracks which RenderGameOverlayEvent.Text handlers are enabled or disabled.
 * Handlers are auto-discovered at runtime when their invoke() is intercepted by the mixin.
 */
public class DebugOverlayRegistry {

    private static final Logger LOG = LogManager.getLogger("Databack/DebugOverlay");

    public enum DisplayMode {
        OFF,
        IN_OVERLAY,
        ALWAYS
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, DisplayMode> handlers = new LinkedHashMap<>();
    /** Lang key (or literal name) for display, null means fall back to getDisplayName(). */
    private static final Map<String, String> titles = new LinkedHashMap<>();
    /** Lang key for tooltip description, null means no tooltip. */
    private static final Map<String, String> descriptions = new LinkedHashMap<>();
    /** Modes loaded from disk, applied when a handler is first discovered. */
    private static final Map<String, DisplayMode> savedModes = new LinkedHashMap<>();
    /** Default modes from @DebugOverlayEntry, used when no saved mode exists. */
    private static final Map<String, DisplayMode> defaultModes = new LinkedHashMap<>();
    /** Explicit handler ordering loaded from disk, applied when the screen is opened. */
    private static final List<String> savedOrder = new ArrayList<>();

    private static File configFile;

    /**
     * Called once on the client side (preInit) to point the registry at its config file.
     * Immediately loads any previously saved modes from disk.
     */
    public static void setConfigFile(File file) {
        configFile = file;
        load();
    }

    private static void load() {
        if (configFile == null || !configFile.exists()) return;
        try (FileReader r = new FileReader(configFile)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;

            savedOrder.clear();
            JsonArray orderArr = root.getAsJsonArray("order");
            if (orderArr != null) {
                for (JsonElement e : orderArr) savedOrder.add(e.getAsString());
            }

            savedModes.clear();
            JsonObject modes = root.getAsJsonObject("modes");
            if (modes != null) {
                for (Map.Entry<String, JsonElement> e : modes.entrySet()) {
                    try {
                        savedModes.put(e.getKey(), DisplayMode.valueOf(e.getValue().getAsString()));
                    } catch (IllegalArgumentException ex) {
                        LOG.warn("Unknown DisplayMode '{}' for handler '{}', ignoring", e.getValue().getAsString(), e.getKey());
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to load debug overlay config: {}", e.toString());
        }
    }

    public static void save() {
        if (configFile == null) return;
        JsonArray orderArr = new JsonArray();
        for (String id : handlers.keySet()) orderArr.add(id);

        JsonObject modes = new JsonObject();
        for (Map.Entry<String, DisplayMode> e : handlers.entrySet()) {
            modes.addProperty(e.getKey(), e.getValue().name());
        }

        JsonObject root = new JsonObject();
        root.add("order", orderArr);
        root.add("modes", modes);

        configFile.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(configFile)) {
            GSON.toJson(root, w);
        } catch (Exception e) {
            LOG.warn("Failed to save debug overlay config: {}", e.toString());
        }
    }

    /**
     * Register a handler if not already known. Called from the mixin on first encounter.
     * The readable string format is "ASM: some.package.ClassName methodName(Ldescriptor;)V".
     */
    public static void registerHandler(String readableId) {
        if (handlers.putIfAbsent(readableId, DisplayMode.IN_OVERLAY) == null) {
            lookupAnnotation(readableId);
            // Priority: saved > annotation default > IN_OVERLAY
            DisplayMode initial = savedModes.containsKey(readableId)
                ? savedModes.get(readableId)
                : defaultModes.getOrDefault(readableId, DisplayMode.IN_OVERLAY);
            handlers.put(readableId, initial);
        }
    }

    /**
     * Try to find @DebugOverlayEntry on the method named in readableId.
     * Only works for static handlers where the class name is unambiguous.
     * Silently skips instance handlers (those with @hash in the class part).
     */
    private static void lookupAnnotation(String readableId) {
        // Expected format: "ASM: some.package.ClassName methodName(descriptor)V"
        String s = readableId;
        if (s.startsWith("ASM: ")) s = s.substring(5);

        int parenIdx = s.indexOf('(');
        if (parenIdx >= 0) s = s.substring(0, parenIdx);

        String[] parts = s.split(" ");
        if (parts.length < 2) return;

        String className = parts[0];
        String methodName = parts[1];

        // Instance handlers: ASMEventHandler uses target.toString() which by default is
        // "com.example.ClassName@hash". Strip the @hash to recover the binary class name.
        int atIdx = className.indexOf('@');
        if (atIdx >= 0) className = className.substring(0, atIdx);

        try {
            Class<?> cls = Class.forName(className, false, DebugOverlayRegistry.class.getClassLoader());
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals(methodName)) continue;
                DebugOverlayEntry ann = m.getAnnotation(DebugOverlayEntry.class);
                if (ann != null) {
                    titles.put(readableId, ann.title());
                    if (!ann.description().isEmpty()) {
                        descriptions.put(readableId, ann.description());
                    }
                    defaultModes.put(readableId, ann.defaultMode());
                }
                break; // method name matched — stop even if no annotation
            }
        } catch (Exception e) {
            LOG.debug("Could not reflect on handler class for {}: {}", readableId, e.toString());
        }
    }

    /**
     * Returns true if the handler should fire during a normal F3 overlay render.
     */
    public static boolean isHandlerEnabled(String readableId) {
        DisplayMode mode = handlers.get(readableId);
        return mode != DisplayMode.OFF;
    }

    /**
     * Returns true if the handler should fire even when the F3 overlay is closed.
     */
    public static boolean isAlwaysOn(String readableId) {
        DisplayMode mode = handlers.get(readableId);
        return mode == DisplayMode.ALWAYS;
    }

    public static void setMode(String readableId, DisplayMode mode) {
        handlers.put(readableId, mode);
        save();
    }

    /** Set all known handlers to the given mode in one pass, then save once. */
    public static void setAll(DisplayMode mode) {
        for (String id : handlers.keySet()) {
            handlers.put(id, mode);
        }
        save();
    }

    public static void cycleMode(String readableId) {
        DisplayMode current = handlers.getOrDefault(readableId, DisplayMode.IN_OVERLAY);
        DisplayMode[] values = DisplayMode.values();
        handlers.put(readableId, values[(current.ordinal() + 1) % values.length]);
        save();
    }

    public static DisplayMode getMode(String readableId) {
        return handlers.getOrDefault(readableId, DisplayMode.IN_OVERLAY);
    }

    /**
     * Returns all known handler IDs in their current order.
     */
    public static Set<String> getKnownHandlers() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    /**
     * Returns a snapshot of handler IDs in their current order, for use by the
     * EventBus ordering mixin when building the priority rank map.
     */
    public static List<String> getOrder() {
        return new ArrayList<>(handlers.keySet());
    }

    /**
     * Reorder handlers to match newOrder, appending any unknown IDs at the end.
     * Persists immediately.
     */
    public static void reorder(List<String> newOrder) {
        reorderInternal(newOrder);
        save();
    }

    /**
     * Apply the order loaded from disk. Called when the debug screen opens, by
     * which point all handlers present this session have been discovered.
     * Does not persist (we're just restoring what was saved).
     */
    public static void applySavedOrder() {
        if (!savedOrder.isEmpty()) reorderInternal(savedOrder);
    }

    private static void reorderInternal(List<String> newOrder) {
        Map<String, DisplayMode> reordered = new LinkedHashMap<>();
        for (String id : newOrder) {
            DisplayMode mode = handlers.get(id);
            if (mode != null) reordered.put(id, mode);
        }
        // Append any handlers not covered by newOrder (newly discovered this session)
        for (Map.Entry<String, DisplayMode> e : handlers.entrySet()) {
            reordered.putIfAbsent(e.getKey(), e.getValue());
        }
        handlers.clear();
        handlers.putAll(reordered);
    }

    /**
     * Returns the annotation title lang key if present, otherwise null.
     */
    public static String getTitle(String readableId) {
        return titles.get(readableId);
    }

    /**
     * Returns the annotation description lang key if present, otherwise null.
     */
    public static String getDescription(String readableId) {
        return descriptions.get(readableId);
    }

    /**
     * Extract a display-friendly name from the readable string.
     * If a @DebugOverlayEntry title was found, returns that instead.
     * Input format: "ASM: some.package.ClassName methodName(Ldescriptor;)V"
     * Output: "ClassName.methodName"
     */
    public static String getDisplayName(String readableId) {
        String title = titles.get(readableId);
        if (title != null) return title;
        String s = readableId;
        if (s.startsWith("ASM: ")) {
            s = s.substring(5);
        }
        // Remove descriptor suffix
        int parenIdx = s.indexOf('(');
        if (parenIdx >= 0) {
            s = s.substring(0, parenIdx);
        }
        // s is now "some.package.ClassName methodName" or "Target@hash methodName"
        String[] parts = s.split(" ");
        if (parts.length >= 2) {
            String className = parts[0];
            String methodName = parts[1];
            // Strip package prefix
            int dotIdx = className.lastIndexOf('.');
            if (dotIdx >= 0) {
                className = className.substring(dotIdx + 1);
            }
            // Strip @hash suffix from anonymous/instance targets
            int atIdx = className.indexOf('@');
            if (atIdx >= 0) {
                className = className.substring(0, atIdx);
            }
            return className + "." + methodName;
        }
        return s;
    }
}