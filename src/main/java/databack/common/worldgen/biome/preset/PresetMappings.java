package databack.common.worldgen.biome.preset;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;

/// Which vanilla names extracted preset code may use, and what each one becomes. All names are internal names
/// (`a/b/C`). Everything vanilla-side here is public API whose behaviour was established black-box.
final class PresetMappings {

    private static final String BIOME_PACKAGE = "net/minecraft/world/level/biome/";
    private static final String RESOURCE_KEY = "net/minecraft/resources/ResourceKey";
    private static final String BIOMES = BIOME_PACKAGE + "Biomes";
    private static final String SHARED_CONSTANTS = "net/minecraft/SharedConstants";
    private static final String LIST = "java/util/List";

    private static final Map<String, String> CLASSES = new HashMap<>();

    /// JDK types extracted code may use as they are.
    private static final Set<String> JDK = new HashSet<>();

    private static final Pattern OBJECT_TYPE = Pattern.compile("L([^;]+);");

    static {
        String standIns = Type.getInternalName(PresetParameter.class).replace("PresetParameter", "");
        CLASSES.put(BIOME_PACKAGE + "Climate", standIns + "PresetClimate");
        CLASSES.put(BIOME_PACKAGE + "Climate$Parameter", standIns + "PresetParameter");
        CLASSES.put(BIOME_PACKAGE + "Climate$ParameterPoint", standIns + "PresetPoint");
        CLASSES.put(BIOME_PACKAGE + "Climate$ParameterList", standIns + "PresetParameterList");
        CLASSES.put(BIOME_PACKAGE + "MultiNoiseBiomeSourceParameterList$Preset$SourceProvider",
            standIns + "PresetSourceProvider");
        CLASSES.put("com/mojang/datafixers/util/Pair", standIns + "PresetPair");
        CLASSES.put(RESOURCE_KEY, "java/lang/String");

        JDK.add("java/lang/Object");
        JDK.add("java/util/function/Consumer");
        JDK.add("java/util/function/Function");
    }

    private PresetMappings() {}

    /// Maps a class's internal name, or an array type's descriptor, to its stand-in. Unmapped names are unchanged.
    @NotNull
    @Contract(pure = true)
    static String mapName(@NotNull String name) {
        if (name.startsWith("[")) return mapDesc(name);
        String mapped = CLASSES.get(name);
        return mapped != null ? mapped : name;
    }

    /// Maps every class named in a field or method descriptor.
    @NotNull
    @Contract(pure = true)
    static String mapDesc(@NotNull String desc) {
        Matcher m = OBJECT_TYPE.matcher(desc);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement("L" + mapName(m.group(1)) + ";"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /// Whether code may use `name` (a class's internal name or an array descriptor) from inside `self`.
    @Contract(pure = true)
    static boolean isSupportedClass(@NotNull String self, @NotNull String name) {
        return isSupportedType(self, name.startsWith("[") ? Type.getType(name) : Type.getObjectType(name));
    }

    /// Whether code may use `type` from inside `self`. Primitives (and arrays of them) are always supported.
    @Contract(pure = true)
    static boolean isSupportedType(@NotNull String self, @NotNull Type type) {
        Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;
        if (element.getSort() != Type.OBJECT) return true;

        String name = element.getInternalName();
        return name.equals(self) || CLASSES.containsKey(name) || JDK.contains(name);
    }

    /// A biome key constant, e.g. `Biomes.PLAINS`. Read as its id instead (see [#biomeId]).
    @Contract(pure = true)
    static boolean isBiomeKey(@NotNull String owner, @NotNull String desc) {
        return owner.equals(BIOMES) && desc.equals("L" + RESOURCE_KEY + ";");
    }

    /// The id of the biome key constant `field`. Every vanilla biome key's id is its constant's name in lower case,
    /// in the `minecraft` namespace.
    @NotNull
    @Contract(pure = true)
    static String biomeId(@NotNull String field) {
        return "minecraft:" + field.toLowerCase(Locale.ROOT);
    }

    /// A debug flag. Read as `false` instead.
    @Contract(pure = true)
    static boolean isDebugFlag(@NotNull String owner, @NotNull String desc) {
        return owner.equals(SHARED_CONSTANTS) && desc.equals("Z");
    }

    /// `List.of`, which Java 8 lacks. Called on [PresetLists] instead.
    @Contract(pure = true)
    static boolean isListOf(@NotNull String owner, @NotNull String name) {
        return owner.equals(LIST) && name.equals("of");
    }

    @NotNull
    static String listOfOwner() {
        return Type.getInternalName(PresetLists.class);
    }
}
