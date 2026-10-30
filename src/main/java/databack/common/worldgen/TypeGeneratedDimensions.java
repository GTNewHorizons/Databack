package databack.common.worldgen;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;

import org.jetbrains.annotations.NotNull;

/// The dimensions 1.7 has the world type generate: those whose providers leave making their generator to the type,
/// as the surface provider does, by not overriding [WorldProvider#createChunkGenerator()] (the Nether's and End's
/// override it).
public final class TypeGeneratedDimensions {

    private TypeGeneratedDimensions() {}

    /// Found by signature, the only no-argument method giving an [IChunkProvider], so it works with obfuscated names.
    public static boolean includes(@NotNull Class<? extends WorldProvider> provider) {
        return DEFERS_TO_TYPE.get(provider);
    }

    private static final ClassValue<Boolean> DEFERS_TO_TYPE = new ClassValue<Boolean>() {

        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != WorldProvider.class && c != null; c = c.getSuperclass()) {
                for (Method method : c.getDeclaredMethods()) {
                    boolean generator = method.getParameterCount() == 0 && method.getReturnType() == IChunkProvider.class;
                    if (generator && !Modifier.isStatic(method.getModifiers()) && !method.isBridge()) return false;
                }
            }
            return true;
        }
    };
}
