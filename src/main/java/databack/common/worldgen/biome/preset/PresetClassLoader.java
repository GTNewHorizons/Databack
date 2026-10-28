package databack.common.worldgen.biome.preset;

import org.jetbrains.annotations.NotNull;

/// Defines one rewritten preset class in isolation. Everything else, including the stand-ins, comes from the loader
/// that loaded the stand-ins, so the class and the extractor share them.
final class PresetClassLoader extends ClassLoader {

    @NotNull
    private final String className;
    private final byte @NotNull [] classFile;

    /// `className` is a binary name (`a.b.C$D`); `classFile` must already be rewritten.
    PresetClassLoader(@NotNull String className, byte @NotNull [] classFile) {
        super(PresetClassLoader.class.getClassLoader());
        this.className = className;
        this.classFile = classFile;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!name.equals(className)) throw new ClassNotFoundException(name);
        return defineClass(name, classFile, 0, classFile.length);
    }
}
