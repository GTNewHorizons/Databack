package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the registries and codecs the carvers' static setup touches: registering returns the value, and
/// codecs are inert.
@ApiStatus.Internal
public final class CvRegistration {

    public static final CvRegistration CARVER = new CvRegistration();

    private CvRegistration() {}

    public static Object register(CvRegistration registry, String name, Object value) {
        return value;
    }

    public CvRegistration fieldOf(String name) {
        return this;
    }

    /// The functions are null ([databack.common.worldgen.vanilla.VanillaMappings.Builder#nullLambdasOver]).
    public CvRegistration xmap(java.util.function.Function<?, ?> to, java.util.function.Function<?, ?> from) {
        return this;
    }
}
