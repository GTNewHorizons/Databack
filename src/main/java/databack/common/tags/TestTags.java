package databack.common.tags;

import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import databack.DatabackConfig;
import databack.common.tags.TagEvent.RegisterBiomeTagsEvent;
import databack.common.tags.TagEvent.RegisterBlockTagsEvent;
import databack.common.tags.TagEvent.RegisterEntityTagsEvent;
import databack.common.tags.TagEvent.RegisterItemTagsEvent;

@EventBusSubscriber
public class TestTags {

    @SubscribeEvent
    public static void registerBlockTags(RegisterBlockTagsEvent event) {
        if (!DatabackConfig.enableTagDebugMode) return;

        var tag = event.registry.getOrCreateTag(new ResourceLocation("minecraft", "test"));
        var tag2 = event.registry.getOrCreateTag(new ResourceLocation("minecraft", "test2"));

        event.registry.addToTag(tag, tag2);
        event.registry.addToTag(tag, Blocks.grass);
        event.registry.addToTag(tag2, Blocks.dirt);
    }

    @SubscribeEvent
    public static void registerItemTags(RegisterItemTagsEvent event) {
        if (!DatabackConfig.enableTagDebugMode) return;

        var tag = event.registry.getOrCreateTag(new ResourceLocation("minecraft", "test"));
        var tag2 = event.registry.getOrCreateTag(new ResourceLocation("minecraft", "test2"));

        event.registry.addToTag(tag, tag2);
        event.registry.addToTag(tag, Items.beef);
        event.registry.addToTag(tag2, Items.cooked_beef);
    }

    @SubscribeEvent
    public static void registerEntityTags(RegisterEntityTagsEvent event) {
        if (!DatabackConfig.enableTagDebugMode) return;

        var tag = event.registry.getOrCreateTag(new ResourceLocation("minecraft", "test"));
        var tag2 = event.registry.getOrCreateTag(new ResourceLocation("minecraft", "test2"));

        event.registry.addToTag(tag, tag2);
        event.registry.addToTag(tag, EntityPig.class);
        event.registry.addToTag(tag2, EntitySheep.class);
    }

    @SubscribeEvent
    public static void registerBiomeTags(RegisterBiomeTagsEvent event) {
        if (!DatabackConfig.enableTagDebugMode) return;

        var forest = event.registry.getOrCreateTag(new ResourceLocation("minecraft", "forest"));
        var temperate = event.registry.getOrCreateTag(new ResourceLocation("minecraft", "temperate"));

        event.registry.addToTag(temperate, forest);

        event.registry.addToTag(forest, BiomeGenBase.forest);
        event.registry.addToTag(forest, BiomeGenBase.forestHills);
        event.registry.addToTag(forest, BiomeGenBase.birchForest);
        event.registry.addToTag(forest, BiomeGenBase.birchForestHills);
        event.registry.addToTag(forest, BiomeGenBase.roofedForest);
    }
}
