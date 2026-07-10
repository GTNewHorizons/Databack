package databack.common.dto.worldgen.biome;

import java.util.function.Supplier;

public interface BiomeAttribute<T> extends Supplier<T> {

    Class<T> getAttributeType();

}
