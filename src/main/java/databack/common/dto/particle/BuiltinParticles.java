package databack.common.dto.particle;

import cpw.mods.fml.common.FMLLog;
import com.google.gson.JsonElement;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

public class BuiltinParticles {

    public static void init() {
        TaggedUnionLoader<IParticle> particles = DatapackSerialization.createTaggedUnionLoader("particle", IParticle.class);

        particles.addVariant("minecraft:block", BlockParticle.class);
        particles.addVariant("minecraft:falling_dust", BlockParticle.class);
        particles.addVariant("minecraft:block_marker", BlockParticle.class);
        particles.addVariant("minecraft:dust_pillar", BlockParticle.class);
        particles.addVariant("minecraft:block_crumble", BlockParticle.class);

        particles.addVariant("minecraft:dust", DustParticle.class);
        particles.addVariant("minecraft:dust_color_transition", DustColorTransitionParticle.class);
        particles.addVariant("minecraft:entity_effect", EntityEffectParticle.class);
        particles.addVariant("minecraft:item", ItemParticle.class);
        particles.addVariant("minecraft:sculk_charge", SculkChargeParticle.class);
        particles.addVariant("minecraft:shriek", ShriekParticle.class);
        particles.addVariant("minecraft:trail", TrailParticle.class);
        particles.addVariant("minecraft:vibration", VibrationParticle.class);

        particles.addVariant("minecraft:angry_villager", EmptyParticle.class);
        particles.addVariant("minecraft:ash", EmptyParticle.class);
        particles.addVariant("minecraft:barrier", EmptyParticle.class);
        particles.addVariant("minecraft:bubble", EmptyParticle.class);
        particles.addVariant("minecraft:bubble_column_up", EmptyParticle.class);
        particles.addVariant("minecraft:bubble_pop", EmptyParticle.class);
        particles.addVariant("minecraft:campfire_cosy_smoke", EmptyParticle.class);
        particles.addVariant("minecraft:campfire_signal_smoke", EmptyParticle.class);
        particles.addVariant("minecraft:cherry_leaves", EmptyParticle.class);
        particles.addVariant("minecraft:cloud", EmptyParticle.class);
        particles.addVariant("minecraft:composter", EmptyParticle.class);
        particles.addVariant("minecraft:crimson_spore", EmptyParticle.class);
        particles.addVariant("minecraft:crit", EmptyParticle.class);
        particles.addVariant("minecraft:current_down", EmptyParticle.class);
        particles.addVariant("minecraft:damage_indicator", EmptyParticle.class);
        particles.addVariant("minecraft:dolphin", EmptyParticle.class);
        particles.addVariant("minecraft:dragon_breath", EmptyParticle.class);
        particles.addVariant("minecraft:dripping_dripstone_lava", EmptyParticle.class);
        particles.addVariant("minecraft:dripping_dripstone_water", EmptyParticle.class);
        particles.addVariant("minecraft:dripping_honey", EmptyParticle.class);
        particles.addVariant("minecraft:dripping_lava", EmptyParticle.class);
        particles.addVariant("minecraft:dripping_obsidian_tear", EmptyParticle.class);
        particles.addVariant("minecraft:dripping_water", EmptyParticle.class);
        particles.addVariant("minecraft:dust_plume", EmptyParticle.class);
        particles.addVariant("minecraft:effect", EmptyParticle.class);
        particles.addVariant("minecraft:egg_crack", EmptyParticle.class);
        particles.addVariant("minecraft:elder_guardian", EmptyParticle.class);
        particles.addVariant("minecraft:electric_spark", EmptyParticle.class);
        particles.addVariant("minecraft:enchant", EmptyParticle.class);
        particles.addVariant("minecraft:enchanted_hit", EmptyParticle.class);
        particles.addVariant("minecraft:end_rod", EmptyParticle.class);
        particles.addVariant("minecraft:explosion", EmptyParticle.class);
        particles.addVariant("minecraft:explosion_emitter", EmptyParticle.class);
        particles.addVariant("minecraft:falling_dripstone_lava", EmptyParticle.class);
        particles.addVariant("minecraft:falling_dripstone_water", EmptyParticle.class);
        particles.addVariant("minecraft:falling_honey", EmptyParticle.class);
        particles.addVariant("minecraft:falling_lava", EmptyParticle.class);
        particles.addVariant("minecraft:falling_nectar", EmptyParticle.class);
        particles.addVariant("minecraft:falling_obsidian_tear", EmptyParticle.class);
        particles.addVariant("minecraft:falling_spore_blossom", EmptyParticle.class);
        particles.addVariant("minecraft:falling_water", EmptyParticle.class);
        particles.addVariant("minecraft:firefly", EmptyParticle.class);
        particles.addVariant("minecraft:firework", EmptyParticle.class);
        particles.addVariant("minecraft:fishing", EmptyParticle.class);
        particles.addVariant("minecraft:flame", EmptyParticle.class);
        particles.addVariant("minecraft:flash", EmptyParticle.class);
        particles.addVariant("minecraft:glow", EmptyParticle.class);
        particles.addVariant("minecraft:glow_squid_ink", EmptyParticle.class);
        particles.addVariant("minecraft:happy_villager", EmptyParticle.class);
        particles.addVariant("minecraft:heart", EmptyParticle.class);
        particles.addVariant("minecraft:instant_effect", EmptyParticle.class);
        particles.addVariant("minecraft:item_cobweb", EmptyParticle.class);
        particles.addVariant("minecraft:item_slime", EmptyParticle.class);
        particles.addVariant("minecraft:item_snowball", EmptyParticle.class);
        particles.addVariant("minecraft:landing_honey", EmptyParticle.class);
        particles.addVariant("minecraft:landing_lava", EmptyParticle.class);
        particles.addVariant("minecraft:landing_obsidian_tear", EmptyParticle.class);
        particles.addVariant("minecraft:large_smoke", EmptyParticle.class);
        particles.addVariant("minecraft:lava", EmptyParticle.class);
        particles.addVariant("minecraft:light", EmptyParticle.class);
        particles.addVariant("minecraft:mycelium", EmptyParticle.class);
        particles.addVariant("minecraft:nautilus", EmptyParticle.class);
        particles.addVariant("minecraft:note", EmptyParticle.class);
        particles.addVariant("minecraft:ominous_spawning", EmptyParticle.class);
        particles.addVariant("minecraft:poof", EmptyParticle.class);
        particles.addVariant("minecraft:portal", EmptyParticle.class);
        particles.addVariant("minecraft:rain", EmptyParticle.class);
        particles.addVariant("minecraft:reverse_portal", EmptyParticle.class);
        particles.addVariant("minecraft:scrape", EmptyParticle.class);
        particles.addVariant("minecraft:small_flame", EmptyParticle.class);
        particles.addVariant("minecraft:small_gust", EmptyParticle.class);
        particles.addVariant("minecraft:smoke", EmptyParticle.class);
        particles.addVariant("minecraft:sneeze", EmptyParticle.class);
        particles.addVariant("minecraft:snowflake", EmptyParticle.class);
        particles.addVariant("minecraft:sonic_boom", EmptyParticle.class);
        particles.addVariant("minecraft:soul", EmptyParticle.class);
        particles.addVariant("minecraft:soul_fire_flame", EmptyParticle.class);
        particles.addVariant("minecraft:spit", EmptyParticle.class);
        particles.addVariant("minecraft:splash", EmptyParticle.class);
        particles.addVariant("minecraft:spore_blossom_air", EmptyParticle.class);
        particles.addVariant("minecraft:squid_ink", EmptyParticle.class);
        particles.addVariant("minecraft:suspended", EmptyParticle.class);
        particles.addVariant("minecraft:sweep_attack", EmptyParticle.class);
        particles.addVariant("minecraft:tinted_leaves", EmptyParticle.class);
        particles.addVariant("minecraft:totem_of_undying", EmptyParticle.class);
        particles.addVariant("minecraft:underwater", EmptyParticle.class);
        particles.addVariant("minecraft:warped_spore", EmptyParticle.class);
        particles.addVariant("minecraft:wax_off", EmptyParticle.class);
        particles.addVariant("minecraft:wax_on", EmptyParticle.class);
        particles.addVariant("minecraft:white_ash", EmptyParticle.class);
        particles.addVariant("minecraft:white_smoke", EmptyParticle.class);
        particles.addVariant("minecraft:witch", EmptyParticle.class);

        particles.setFallback((json, typeOfT, context) -> {
            String type = json.isJsonObject() && json.getAsJsonObject().has("type")
                ? json.getAsJsonObject().get("type").getAsString()
                : "<unknown>";
            FMLLog.warning("[Databack] Unknown particle type '%s', ignoring", type);
            return new EmptyParticle();
        });
    }

    private static class EmptyParticle implements IParticle {}

    public static class BlockParticle implements IParticle {
        public JsonElement block_state;
    }

    public static class DustParticle implements IParticle {
        public float[] color;
        public float scale;
    }

    public static class DustColorTransitionParticle implements IParticle {
        public float[] from_color;
        public float[] to_color;
        public float scale;
    }

    public static class EntityEffectParticle implements IParticle {
        public float[] color;
    }

    public static class ItemParticle implements IParticle {
        public JsonElement item;
    }

    public static class SculkChargeParticle implements IParticle {
        public float roll;
    }

    public static class ShriekParticle implements IParticle {
        public int delay;
    }

    public static class TrailParticle implements IParticle {
        public double[] target;
        public int color;
        public int duration;
    }

    public static class VibrationParticle implements IParticle {
        public JsonElement destination;
        public int arrival_in_ticks;
    }
}
