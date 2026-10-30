package databack.common.worldgen.vanilla;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/// Defines a bundle of rewritten vanilla classes in isolation. Everything else, including the replacements their
/// [VanillaMappings] name, comes from the loader that loaded this class, so the bundle and Databack share them.
public final class VanillaClassLoader extends ClassLoader {

    /// Rewritten class files and generated bridges, by binary name (`a.b.C$D`).
    private final Map<String, byte[]> classes;
    private int bridges;

    private VanillaClassLoader(Map<String, byte[]> classes) {
        super(VanillaClassLoader.class.getClassLoader());
        this.classes = new ConcurrentHashMap<>(classes);
    }

    /// Rewrites `classFiles` (keyed by jar entry, e.g. `a/b/C.class`) together with `mappings` and returns a loader
    /// for them. Throws when a class cannot be parsed.
    @NotNull
    @Contract("_, _ -> new")
    public static VanillaClassLoader load(@NotNull Map<String, byte[]> classFiles, @NotNull VanillaMappings mappings) {
        Set<String> bundle = new HashSet<>();
        for (String entry : classFiles.keySet()) bundle.add(internalName(entry));

        Set<String> foreign = VanillaClassRewriter.failingMethods(classFiles.values(), mappings, bundle);

        Map<String, byte[]> rewritten = new HashMap<>();
        for (Map.Entry<String, byte[]> e : classFiles.entrySet()) {
            rewritten.put(binaryName(e.getKey()),
                VanillaClassRewriter.rewrite(e.getValue(), mappings, bundle, foreign));
        }

        return new VanillaClassLoader(rewritten);
    }

    /// The binary name (`a.b.C$D`) of the class in jar entry `entry`.
    @NotNull
    @Contract(pure = true)
    public static String binaryName(@NotNull String entry) {
        return internalName(entry).replace('/', '.');
    }

    private static String internalName(String entry) {
        if (!entry.endsWith(".class")) throw new IllegalArgumentException("Not a class file entry: " + entry);
        return entry.substring(0, entry.length() - ".class".length());
    }

    /// An instance of the functional interface `itf` whose method calls the static method `method` of the bundled
    /// class `owner` (a binary name), or its constructor for `"<init>"`, with the interface method's parameters. The
    /// call is direct, so going through the bridge costs a plain interface call. For hot calls; reflection is fine
    /// elsewhere. Throws [IllegalArgumentException] when `owner` has no such accessible target.
    @NotNull
    public synchronized <T> T bridge(@NotNull Class<T> itf, @NotNull String owner, @NotNull String method) {
        Method implemented = VanillaBridges.functionalMethod(itf);
        try {
            VanillaBridges.checkTarget(loadClass(owner), method, implemented);

            String name = owner + "$DatabackBridge" + bridges++;
            classes.put(name, VanillaBridges.generate(name.replace('.', '/'), itf, implemented, owner.replace('.', '/'),
                method));
            return itf.cast(loadClass(name).getConstructor().newInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Could not bridge " + owner + "." + method + " to " + itf.getName(), e);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] classFile = classes.get(name);
        if (classFile == null) throw new ClassNotFoundException(name);
        return defineClass(name, classFile, 0, classFile.length);
    }
}
