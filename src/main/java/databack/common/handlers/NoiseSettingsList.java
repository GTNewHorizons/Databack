package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings;

public class NoiseSettingsList extends JsonDatapackTypeHandler<NoiseGeneratorSettings> {

    public static final ResourceType<NoiseSettingsList> RT = ResourceType.withPath("worldgen/noise_settings");

    public NoiseSettingsList() {
        super(RT, NoiseGeneratorSettings.class);
    }

    @Nullable
    public NoiseGeneratorSettings getNoiseSettings(String id) {
        return super.getObject(id);
    }
}
