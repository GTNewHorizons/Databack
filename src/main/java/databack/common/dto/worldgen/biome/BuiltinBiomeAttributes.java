package databack.common.dto.worldgen.biome;

import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

public class BuiltinBiomeAttributes {

    public static void init() {
        TaggedUnionLoader<BiomeAttribute<?>> biomeAttributes = DatapackSerialization
            .getTaggedUnionLoader("builtin/biome_attributes");
    }

    public static class BooleanBiomeAttribute implements BiomeAttribute<Boolean> {

        public boolean value;

        @Override
        public Class<Boolean> getAttributeType() {
            return null;
        }

        @Override
        public Boolean get() {
            return null;
        }
    }

}
