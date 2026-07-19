package databack.common.dto.worldgen.structure_set;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

@SuppressWarnings("unused")
public class BuiltinStructurePlacements {

    public static void init() {
        TaggedUnionLoader<IStructurePlacement> loader = DatapackSerialization
            .createTaggedUnionLoader("worldgen/structure_placement", IStructurePlacement.class);

        loader.addVariant("minecraft:random_spread", RandomSpreadPlacement.class);
        loader.addVariant("minecraft:concentric_rings", ConcentricRingsPlacement.class);
    }

    // ---- Common fields --------------------------------------------------------

    /** Fields shared by all placement types. */
    static abstract class PlacementBase implements IStructurePlacement {

        public int salt;
        @Nullable
        public String frequency_reduction_method;
        @Nullable
        public Float frequency;
        /** {@code {other_set, chunk_count}} — stubbed. */
        @Nullable
        public JsonElement exclusion_zone;
        @Nullable
        public int[] locate_offset;
    }

    // ---- Placement types ------------------------------------------------------

    static class RandomSpreadPlacement extends PlacementBase {

        public int spacing;
        public int separation;
        @Nullable
        public String spread_type;
    }

    static class ConcentricRingsPlacement extends PlacementBase {

        public int distance;
        public int spread;
        public int count;
        /** Tag string or biome ID list. */
        public JsonElement preferred_biomes;
    }
}
