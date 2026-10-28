package databack.common.worldgen.biome.preset;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for vanilla's climate parameter list in extracted preset code: an ordered list of [PresetPair]s.
@ApiStatus.Internal
public final class PresetParameterList {

    @NotNull
    private final List<?> values;

    public PresetParameterList(@NotNull List<?> values) {
        this.values = new ArrayList<>(values);
    }

    @NotNull
    public List<?> values() {
        return values;
    }
}
