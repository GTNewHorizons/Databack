package databack.common.dto;

import java.util.Map;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

public class BlockStateDTO {

    public ResourceLocation Name;

    @Nullable
    public Map<String, String> Properties;
}
