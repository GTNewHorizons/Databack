package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings;

public class NoiseSettingsList extends JsonDatapackTypeHandler<NoiseGeneratorSettings> {

    public static final NoiseSettingsList INSTANCE = new NoiseSettingsList();

    public NoiseSettingsList() {
        super("worldgen/noise_settings", NoiseGeneratorSettings.class);
    }

    @Nullable
    public NoiseGeneratorSettings getNoiseSettings(String id) {
        return super.getObject(id);
    }
}
