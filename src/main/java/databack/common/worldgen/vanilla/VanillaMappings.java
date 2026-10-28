package databack.common.worldgen.vanilla;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

/// What a set of vanilla classes may use from outside the set, and what each use becomes when the classes are
/// rewritten ([VanillaClassRewriter]). All names are internal names (`a/b/C`).
///
/// Everything vanilla-side a mapping replaces must be public API whose behaviour was established black-box; the
/// replacement must behave the same.
public final class VanillaMappings {

    private static final Pattern OBJECT_TYPE = Pattern.compile("L([^;]+);");

    private final Map<String, String> classes;
    private final Set<String> interfaceReplacements;
    private final Set<String> passthrough;
    private final Map<String, String> staticRedirects;
    private final Map<String, Function<String, Object>> constantFields;
    private final Map<String, List<String>> addedInterfaces;

    private VanillaMappings(Builder b) {
        this.classes = new HashMap<>(b.classes);
        this.interfaceReplacements = new HashSet<>(b.interfaceReplacements);
        this.passthrough = new HashSet<>(b.passthrough);
        this.staticRedirects = new HashMap<>(b.staticRedirects);
        this.constantFields = new HashMap<>(b.constantFields);
        this.addedInterfaces = new HashMap<>(b.addedInterfaces);
    }

    @NotNull
    @Contract("-> new")
    public static Builder builder() {
        return new Builder();
    }

    /// Maps a class's internal name, or an array type's descriptor, to its replacement. Unmapped names are unchanged.
    @NotNull
    @Contract(pure = true)
    public String mapName(@NotNull String name) {
        if (name.startsWith("[")) return mapDesc(name);
        String mapped = classes.get(name);
        return mapped != null ? mapped : name;
    }

    /// Maps every class named in a field or method descriptor.
    @NotNull
    @Contract(pure = true)
    public String mapDesc(@NotNull String desc) {
        Matcher m = OBJECT_TYPE.matcher(desc);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement("L" + mapName(m.group(1)) + ";"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /// Whether a call on `owner` (a vanilla name) is an interface call once mapped. `itf` is the original call's.
    @Contract(pure = true)
    boolean isInterfaceCall(@NotNull String owner, boolean itf) {
        String mapped = classes.get(owner);
        return mapped == null ? itf : interfaceReplacements.contains(mapped);
    }

    /// Whether code in `bundle` (the classes rewritten together) may use `name`, a class's internal name or an array
    /// descriptor.
    @Contract(pure = true)
    boolean isSupportedClass(@NotNull Set<String> bundle, @NotNull String name) {
        return isSupportedType(bundle, name.startsWith("[") ? Type.getType(name) : Type.getObjectType(name));
    }

    /// Whether code in `bundle` may use `type`. Primitives (and arrays of them) are always supported.
    @Contract(pure = true)
    boolean isSupportedType(@NotNull Set<String> bundle, @NotNull Type type) {
        Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;
        if (element.getSort() != Type.OBJECT) return true;

        String name = element.getInternalName();
        return bundle.contains(name) || classes.containsKey(name) || passthrough.contains(name);
    }

    /// The constant a static field read becomes, or null if the field is not replaced by a constant.
    @Nullable
    @Contract(pure = true)
    Object constant(@NotNull String owner, @NotNull String name, @NotNull String desc) {
        Function<String, Object> values = constantFields.get(owner + ":" + desc);
        return values == null ? null : values.apply(name);
    }

    /// The owner a static method call is redirected to (same name and descriptor), or null if it is not redirected.
    @Nullable
    @Contract(pure = true)
    String staticRedirect(@NotNull String owner, @NotNull String name) {
        return staticRedirects.get(owner + "." + name);
    }

    /// Interfaces to add to `className` (a vanilla name), so callers can reach its methods without reflection.
    @NotNull
    @Contract(pure = true)
    List<String> addedInterfaces(@NotNull String className) {
        List<String> added = addedInterfaces.get(className);
        return added == null ? Collections.emptyList() : added;
    }

    public static final class Builder {

        private final Map<String, String> classes = new HashMap<>();
        private final Set<String> interfaceReplacements = new HashSet<>();
        private final Set<String> passthrough = new HashSet<>();
        private final Map<String, String> staticRedirects = new HashMap<>();
        private final Map<String, Function<String, Object>> constantFields = new HashMap<>();
        private final Map<String, List<String>> addedInterfaces = new HashMap<>();

        private Builder() {
            passthrough.add("java/lang/Object");
        }

        /// Replaces every use of `vanilla` with `replacement`, which must have the members the vanilla code uses,
        /// with the same names and (mapped) descriptors.
        @NotNull
        @Contract("_, _ -> this")
        public Builder mapClass(@NotNull String vanilla, @NotNull Class<?> replacement) {
            String name = Type.getInternalName(replacement);
            classes.put(vanilla, name);
            if (replacement.isInterface()) interfaceReplacements.add(name);
            return this;
        }

        /// Lets the vanilla code use these classes unchanged (JDK or library classes present at runtime).
        @NotNull
        @Contract("_ -> this")
        public Builder passthrough(@NotNull String... names) {
            Collections.addAll(passthrough, names);
            return this;
        }

        /// Calls to the static methods `owner.name` (every descriptor) go to the same method on `target`.
        @NotNull
        @Contract("_, _, _ -> this")
        public Builder redirectStatic(@NotNull String owner, @NotNull String name, @NotNull Class<?> target) {
            staticRedirects.put(owner + "." + name, Type.getInternalName(target));
            return this;
        }

        /// Reads of static fields of `owner` with descriptor `desc` become the constant `values` gives for the
        /// field's name: a [String], a boxed number, or a [Boolean].
        @NotNull
        @Contract("_, _, _ -> this")
        public Builder constantFields(
            @NotNull String owner, @NotNull String desc, @NotNull Function<String, Object> values
        ) {
            constantFields.put(owner + ":" + desc, values);
            return this;
        }

        /// Makes `className` (a vanilla name) implement `itf`. Its methods must already match the class's mapped
        /// methods.
        @NotNull
        @Contract("_, _ -> this")
        public Builder addInterface(@NotNull String className, @NotNull Class<?> itf) {
            if (!itf.isInterface()) throw new IllegalArgumentException(itf + " is not an interface");
            addedInterfaces.computeIfAbsent(className, k -> new ArrayList<>()).add(Type.getInternalName(itf));
            return this;
        }

        @NotNull
        @Contract("-> new")
        public VanillaMappings build() {
            return new VanillaMappings(this);
        }
    }
}
