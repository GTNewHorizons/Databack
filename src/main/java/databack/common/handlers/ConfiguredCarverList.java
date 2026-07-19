package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.carver.IConfiguredCarver;

public class ConfiguredCarverList extends JsonDatapackTypeHandler<IConfiguredCarver> {

    public static final ResourceType<ConfiguredCarverList> RT = ResourceType.withPath("worldgen/configured_carver");

    public ConfiguredCarverList() {
        super(RT, IConfiguredCarver.class);
    }

    @Nullable
    public IConfiguredCarver getConfiguredCarver(String id) {
        return super.getObject(id);
    }
}
