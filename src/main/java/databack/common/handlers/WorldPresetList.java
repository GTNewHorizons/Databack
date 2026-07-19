package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.world_preset.WorldPreset;

public class WorldPresetList extends JsonDatapackTypeHandler<WorldPreset> {

    public static final ResourceType<WorldPresetList> RT = ResourceType.withPath("worldgen/world_preset");

    public WorldPresetList() {
        super(RT, WorldPreset.class);
    }

    @Nullable
    public WorldPreset getWorldPreset(String id) {
        return super.getObject(id);
    }
}
