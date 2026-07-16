package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.carver.IConfiguredCarver;

public class ConfiguredCarverList extends JsonDatapackTypeHandler<IConfiguredCarver> {

    public static final ConfiguredCarverList INSTANCE = new ConfiguredCarverList();

    public ConfiguredCarverList() {
        super("worldgen/configured_carver", IConfiguredCarver.class);
    }

    @Nullable
    public IConfiguredCarver getConfiguredCarver(String id) {
        return super.getObject(id);
    }
}
