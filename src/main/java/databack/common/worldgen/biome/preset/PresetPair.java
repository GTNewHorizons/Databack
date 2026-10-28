package databack.common.worldgen.biome.preset;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/// Stand-in for DataFixerUpper's pair in extracted preset code. Preset code only pairs a point with a biome id.
@ApiStatus.Internal
public final class PresetPair {

    private final Object first;
    private final Object second;

    private PresetPair(Object first, Object second) {
        this.first = first;
        this.second = second;
    }

    @NotNull
    @Contract("_, _ -> new")
    public static PresetPair of(Object first, Object second) {
        return new PresetPair(first, second);
    }

    public Object first() {
        return first;
    }

    public Object second() {
        return second;
    }
}
