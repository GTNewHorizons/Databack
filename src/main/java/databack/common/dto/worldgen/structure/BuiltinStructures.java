package databack.common.dto.worldgen.structure;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import databack.common.dto.worldgen.height_provider.IHeightProvider;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

@SuppressWarnings("unused")
public class BuiltinStructures {

    public static void init() {
        TaggedUnionLoader<IStructure> loader = DatapackSerialization
            .createTaggedUnionLoader("worldgen/structure", IStructure.class);

        // Jigsaw-based structures
        loader.addVariant("minecraft:jigsaw", JigsawStructure.class);

        // Structures with no extra config fields in 26.1
        loader.addVariant("minecraft:buried_treasure", EmptyStructure.class);
        loader.addVariant("minecraft:desert_pyramid", EmptyStructure.class);
        loader.addVariant("minecraft:end_city", EmptyStructure.class);
        loader.addVariant("minecraft:fortress", EmptyStructure.class);
        loader.addVariant("minecraft:igloo", EmptyStructure.class);
        loader.addVariant("minecraft:jungle_temple", EmptyStructure.class);
        loader.addVariant("minecraft:ocean_monument", EmptyStructure.class);
        loader.addVariant("minecraft:stronghold", EmptyStructure.class);
        loader.addVariant("minecraft:swamp_hut", EmptyStructure.class);
        loader.addVariant("minecraft:woodland_mansion", EmptyStructure.class);

        // Structures with type-specific config fields
        loader.addVariant("minecraft:mineshaft", MineshaftStructure.class);
        loader.addVariant("minecraft:nether_fossil", NetherFossilStructure.class);
        loader.addVariant("minecraft:ocean_ruin", OceanRuinStructure.class);
        loader.addVariant("minecraft:ruined_portal", RuinedPortalStructure.class);
        loader.addVariant("minecraft:shipwreck", ShipwreckStructure.class);
    }

    // ---- Common base ----------------------------------------------------------

    /** Common fields present on every structure. */
    static abstract class StructureBase implements IStructure {

        /** Biome tag or list of biome IDs where this structure can generate. */
        public JsonElement biomes;

        public String step;

        @Nullable
        public String terrain_adaptation;

        @Nullable
        public Map<String, SpawnOverride> spawn_overrides;
    }

    static class SpawnOverride {

        public String bounding_box;
        public List<SpawnerData> spawns;
    }

    static class SpawnerData {

        public String type;
        public int weight;
        public int minCount;
        public int maxCount;
    }

    // ---- Jigsaw ---------------------------------------------------------------

    static class JigsawStructure extends StructureBase {

        public String start_pool;
        public int size;
        public int max_distance_from_center;
        public IHeightProvider start_height;
        @Nullable
        public String start_jigsaw_name;
        @Nullable
        public String project_start_to_heightmap;
        public boolean use_expansion_hack;
        /** {@code int | {bottom, top}} — stubbed. */
        @Nullable
        public JsonElement dimension_padding;
        @Nullable
        public String liquid_settings;
        /** Complex pool alias bindings — stubbed. */
        @Nullable
        public JsonElement pool_aliases;
    }

    // ---- Simple typed structures ----------------------------------------------

    private static class EmptyStructure extends StructureBase {}

    static class MineshaftStructure extends StructureBase {

        public String mineshaft_type;
    }

    static class NetherFossilStructure extends StructureBase {

        public IHeightProvider height;
    }

    static class OceanRuinStructure extends StructureBase {

        public String biome_temp;
        public float large_probability;
        public float cluster_probability;
    }

    static class RuinedPortalStructure extends StructureBase {

        public List<RuinedPortalSetup> setups;
    }

    static class RuinedPortalSetup {

        public String placement;
        public float air_pocket_probability;
        public float mossiness;
        public boolean overgrown;
        public boolean vines;
        public boolean can_be_cold;
        public boolean replace_with_blackstone;
        public float weight;
    }

    static class ShipwreckStructure extends StructureBase {

        @Nullable
        public Boolean is_beached;
    }
}
