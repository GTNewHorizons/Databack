package databack.common.worldgen.vanilla;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;

/// What a set of vanilla classes may use from outside the set, and what each use becomes when the classes are
/// rewritten ([VanillaClassRewriter]). All names are internal names (`a/b/C`).
///
/// Everything vanilla-side a mapping replaces must be public API whose behaviour was established black-box; the
/// replacement must behave the same.
public final class VanillaMappings {

    private static final Pattern OBJECT_TYPE = Pattern.compile("L([^;]+);");
    static final String OBJECT = "java/lang/Object";

    private final Map<String, String> classes;
    private final Set<String> interfaceReplacements;
    private final Set<String> passthrough;
    private final Map<String, String> staticRedirects;
    private final Map<String, String> instanceRedirects;
    private final Map<String, Function<String, Object>> constantFields;
    private final Map<String, List<String>> addedInterfaces;
    private final Set<String> nullLambdaTargets;
    private final List<String> inertPrefixes;
    private final boolean modernRuntime;

    private VanillaMappings(Builder b) {
        this.classes = new HashMap<>(b.classes);
        this.interfaceReplacements = new HashSet<>(b.interfaceReplacements);
        this.passthrough = new HashSet<>(b.passthrough);
        this.staticRedirects = new HashMap<>(b.staticRedirects);
        this.instanceRedirects = new HashMap<>(b.instanceRedirects);
        this.constantFields = new HashMap<>(b.constantFields);
        this.addedInterfaces = new HashMap<>(b.addedInterfaces);
        this.nullLambdaTargets = new HashSet<>(b.nullLambdaTargets);
        this.inertPrefixes = new ArrayList<>(b.inertPrefixes);
        this.modernRuntime = b.modernRuntime;
        // Records stay records where the JVM has them; elsewhere they are plain classes.
        if (modernRuntime) {
            passthrough.add(RECORD);
        } else {
            classes.put(RECORD, OBJECT);
        }
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
        if (isInert(name)) return OBJECT;
        String mapped = classes.get(name);
        return mapped != null ? mapped : name;
    }

    /// Maps a field's descriptor. Fields of unsupported types become [Object] fields: only best-effort static
    /// initializers can set them (to null), and reflection on the class would otherwise try to load the type.
    @NotNull
    @Contract(pure = true)
    String mapFieldDesc(@NotNull Set<String> bundle, @NotNull String desc) {
        return isSupportedType(bundle, Type.getType(desc)) ? mapDesc(desc) : "L" + OBJECT + ";";
    }

    /// Whether `type` is an inert class ([Builder#inert]), or an array of one.
    @Contract(pure = true)
    boolean isInertType(@NotNull Type type) {
        Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;
        return element.getSort() == Type.OBJECT && isInert(element.getInternalName());
    }

    /// Whether `owner` (a vanilla name) is replaced by a Databack class ([Builder#mapClass]).
    @Contract(pure = true)
    boolean isReplaced(@NotNull String owner) {
        return classes.containsKey(owner);
    }

    /// Erases the bundled classes in a field or method descriptor to [Object]. Replacements are compiled without the
    /// bundle, so this is how they see it.
    @NotNull
    @Contract(pure = true)
    String eraseBundle(@NotNull Set<String> bundle, @NotNull String desc) {
        Matcher m = OBJECT_TYPE.matcher(desc);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String name = bundle.contains(m.group(1)) ? OBJECT : m.group(1);
            m.appendReplacement(out, Matcher.quoteReplacement("L" + name + ";"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /// Whether `name` (a class's internal name) is inert ([Builder#inert]).
    @Contract(pure = true)
    public boolean isInert(@NotNull String name) {
        for (String prefix : inertPrefixes) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
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
        return bundle.contains(name) || classes.containsKey(name) || passthrough.contains(name) || isInert(name);
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

    /// The owner an instance method call is redirected to as a static call taking the receiver first, or null if it
    /// is not redirected.
    @Nullable
    @Contract(pure = true)
    String instanceRedirect(@NotNull String owner, @NotNull String name) {
        return instanceRedirects.get(owner + "." + name);
    }

    /// How an `invokedynamic` instruction is rewritten.
    enum Lambda {
        /// Kept, with its types and method handles mapped.
        MAPPED,
        /// Replaced by null ([Builder#nullLambdasOver]).
        NULL,
        /// Unsupported: its method is foreign.
        UNSUPPORTED
    }

    private static final String LAMBDA_FACTORY = "java/lang/invoke/LambdaMetafactory";
    private static final String OBJECT_METHODS = "java/lang/runtime/ObjectMethods";
    private static final String STRING_CONCAT = "java/lang/invoke/StringConcatFactory";
    private static final String RECORD = "java/lang/Record";

    /// Classifies an `invokedynamic`. Supported: plain lambdas and method references
    /// ([java.lang.invoke.LambdaMetafactory]'s `metafactory`), and on a modern runtime ([Builder#modernRuntime])
    /// records' generated methods and string concatenation. References to inert classes' methods are null.
    @NotNull
    @Contract(pure = true)
    Lambda lambda(@NotNull Set<String> bundle, @NotNull String desc, @NotNull Handle bootstrap, Object @NotNull [] args) {
        if (bootstrap.getOwner().equals(OBJECT_METHODS) || bootstrap.getOwner().equals(STRING_CONCAT)) {
            return modernRuntime && isSupportedMethod(bundle, desc) && areSupportedConstants(bundle, args)
                ? Lambda.MAPPED : Lambda.UNSUPPORTED;
        }
        if (!bootstrap.getOwner().equals(LAMBDA_FACTORY) || !bootstrap.getName().equals("metafactory")
            || args.length != 3 || !(args[1] instanceof Handle)) {
            return Lambda.UNSUPPORTED;
        }

        Handle impl = (Handle) args[1];
        // A method of an inert class would do nothing; so may the lambda.
        if (isInert(impl.getOwner())) return Lambda.NULL;

        boolean nulled = nullLambdaTargets.contains(impl.getOwner());
        for (Type t : methodTypes(impl.getDesc())) nulled |= nullLambdaTargets.contains(typeName(t));

        boolean supported = isSupportedClass(bundle, impl.getOwner()) && isSupportedMethod(bundle, impl.getDesc())
            && isSupportedMethod(bundle, ((Type) args[0]).getDescriptor())
            && isSupportedMethod(bundle, ((Type) args[2]).getDescriptor());
        if (!isSupportedMethod(bundle, desc)) return Lambda.UNSUPPORTED;
        if (supported) return Lambda.MAPPED;
        return nulled ? Lambda.NULL : Lambda.UNSUPPORTED;
    }

    /// Whether the classes and method handles among an `invokedynamic`'s bootstrap arguments are supported.
    private boolean areSupportedConstants(Set<String> bundle, Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Type && ((Type) arg).getSort() != Type.METHOD && !isSupportedType(bundle, (Type) arg)) {
                return false;
            }
            if (arg instanceof Handle && !isSupportedClass(bundle, ((Handle) arg).getOwner())) return false;
        }
        return true;
    }

    /// Whether every type in a method descriptor is supported.
    @Contract(pure = true)
    boolean isSupportedMethod(@NotNull Set<String> bundle, @NotNull String desc) {
        for (Type t : methodTypes(desc)) {
            if (!isSupportedType(bundle, t)) return false;
        }
        return true;
    }

    private static List<Type> methodTypes(String desc) {
        Type method = Type.getMethodType(desc);
        List<Type> types = new ArrayList<>(Arrays.asList(method.getArgumentTypes()));
        types.add(method.getReturnType());
        return types;
    }

    private static String typeName(Type type) {
        Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;
        return element.getSort() == Type.OBJECT ? element.getInternalName() : "";
    }

    /// Whether a lambda whose implementation method names `name` (a vanilla internal name) in its owner or descriptor
    /// becomes null instead of making its method unsupported.
    @Contract(pure = true)
    boolean nullsLambdasOver(@NotNull String name) {
        return nullLambdaTargets.contains(name);
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
        private final Map<String, String> instanceRedirects = new HashMap<>();
        private final Map<String, Function<String, Object>> constantFields = new HashMap<>();
        private final Map<String, List<String>> addedInterfaces = new HashMap<>();
        private final Set<String> nullLambdaTargets = new HashSet<>();
        private final List<String> inertPrefixes = new ArrayList<>();
        private boolean modernRuntime = isModernRuntime();

        private Builder() {
            passthrough.add("java/lang/Object");
            passthrough.add("java/lang/String");
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

        /// Calls to the instance methods `owner.name` (every descriptor) go to the static method of the same name on
        /// `target`, which takes the receiver (as `owner`, mapped) first. As for [#redirectStatic], vanilla classes
        /// (the receiver too) are [Object] to `target`. For JDK methods newer than the runtime, and for hooks.
        @NotNull
        @Contract("_, _, _ -> this")
        public Builder redirectInstance(@NotNull String owner, @NotNull String name, @NotNull Class<?> target) {
            instanceRedirects.put(owner + "." + name, Type.getInternalName(target));
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

        /// Lambdas whose implementation method involves `names` (unsupported vanilla classes, as owner or in the
        /// descriptor) evaluate to null instead. Only for lambdas handed to replacements that ignore them.
        @NotNull
        @Contract("_ -> this")
        public Builder nullLambdasOver(@NotNull String... names) {
            Collections.addAll(nullLambdaTargets, names);
            return this;
        }

        /// Makes the classes whose internal names start with one of `prefixes` inert: code that only builds things
        /// nobody uses (codecs, registries). Their types become [Object], reading their static fields or calling
        /// their methods (constructors included) gives null, zero or nothing, and they are dropped from interface
        /// lists. Such classes must never be what a supported computation depends on.
        @NotNull
        @Contract("_ -> this")
        public Builder inert(@NotNull String... prefixes) {
            Collections.addAll(inertPrefixes, prefixes);
            return this;
        }

        /// Whether the JVM running the rewritten classes has records and indified string concatenation (Java 16+).
        /// Detected by default; set only to test either case.
        @NotNull
        @Contract("_ -> this")
        public Builder modernRuntime(boolean modern) {
            this.modernRuntime = modern;
            return this;
        }

        private static boolean isModernRuntime() {
            try {
                Class.forName("java.lang.Record");
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        @NotNull
        @Contract("-> new")
        public VanillaMappings build() {
            return new VanillaMappings(this);
        }
    }
}
