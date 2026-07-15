package databack.common.dto.dimension_type;

import javax.annotation.Nonnegative;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import databack.common.annotation.RangeFloat;
import databack.common.dto.worldgen.biome.EnvironmentEffects;
import databack.common.dto.worldgen.int_provider.IIntProvider;

@SuppressWarnings("unused")
public class DimensionType {

    /** GlobalEnvironmentAttributeMap */
    @Nullable
    public EnvironmentEffects attributes;

    /** Resource ID of a world_clock. */
    @Nullable
    public String default_clock;

    /** Tag string (e.g. {@code "#minecraft:some_tag"}) or list of timeline resource IDs. */
    @Nullable
    public JsonElement timelines;

    /** Affects weather, lighting engine, and respawning rules. */
    public boolean has_skylight;

    /** Affects weather, map items, and respawning rules. */
    public boolean has_ceiling;

    public boolean has_ender_dragon_fight;

    /** @ 0.00001..30000000 */
    public double coordinate_scale;

    @RangeFloat(min = 0, max = 1)
    public float ambient_light;

    /** If true, the sun is fixed in position. */
    @Nullable
    public Boolean has_fixed_time;

    /** Maximum height for portals and chorus fruit teleportation. @ 0..4064 */
    @Nonnegative
    public int logical_height;

    @Nullable
    public SkyboxType skybox;

    @Nullable
    public CardinalLightType cardinal_light;

    /** Block tag defining blocks that keep fire burning infinitely, e.g. {@code "#minecraft:infiniburn_overworld"}. */
    public String infiniburn;

    /** Minimum Y-level where blocks can exist. Must be divisible by 16. @ -2032..2031 */
    public int min_y;

    /** Total height where blocks can exist; max Y = min_y + height. Must be divisible by 16. @ 16..4064 */
    @Nonnegative
    public int height;

    /** @ 0..15 */
    public IIntProvider monster_spawn_light_level;

    /** @ 0..15 */
    public int monster_spawn_block_light_limit;

    public enum SkyboxType {
        none,
        overworld,
        end
    }

    public enum CardinalLightType {
        @SerializedName("default")
        DEFAULT,
        nether
    }

}
