package databack.common.worldgen.vanilla.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Stand-in for a registry, as vanilla's static initializers use one: registering gives the value back, and nothing
/// is kept. The registries themselves are never built: `registry` is always null.
@ApiStatus.Internal
public final class VnRegistry {

    private VnRegistry() {}

    @NotNull
    @Contract("_, _, _ -> param3")
    public static Object register(@Nullable VnRegistry registry, @NotNull String name, @NotNull Object value) {
        return value;
    }
}
