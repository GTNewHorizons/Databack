package databack.common.tags;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import databack.common.handlers.DatapackHandlerRegistry;
import databack.common.handlers.ResourceType;
import databack.common.interop.registry.ProxyBiomeRegistry;
import databack.common.loader.DatapackEvent.DatapackFinishedLoadingEvent;
import databack.common.tags.TagEvent.RegisterBiomeTagsEvent;
import databack.common.tags.TagEvent.RegisterBlockTagsEvent;
import databack.common.tags.TagEvent.RegisterItemTagsEvent;
import databack.common.tags.TagEvent.TagReloadEvent;
import it.unimi.dsi.fastutil.objects.ObjectIterators;

/// The primary tag registries needed for parity with modern.
@EventBusSubscriber
public class BuiltinTagRegistries {

    public static final ResourceType<TagRegistry<Block>> BLOCK_TAGS = ResourceType.withPath("tags/block");
    public static final ResourceType<TagRegistry<Item>> ITEM_TAGS = ResourceType.withPath("tags/item");
    public static final ResourceType<TagRegistry<BiomeGenBase>> BIOME_TAGS = ResourceType.withPath("tags/worldgen/biome");
    public static final ResourceType<EntityTagRegistry> ENTITY_TAGS = ResourceType.withPath("tags/entity_type");

    public static TagRegistry<Block> blocks() {
        return BLOCK_TAGS.getHandler();
    }

    public static TagRegistry<Item> items() {
        return ITEM_TAGS.getHandler();
    }

    public static TagRegistry<BiomeGenBase> biomes() {
        return BIOME_TAGS.getHandler();
    }

    public static EntityTagRegistry entities() {
        return ENTITY_TAGS.getHandler();
    }

    public static void reloadTags() {
        MinecraftForge.EVENT_BUS.post(new TagReloadEvent());
    }

    @SubscribeEvent
    public static void onDatapackLoadFinished(DatapackFinishedLoadingEvent event) {
        reloadTags();
    }

    @SubscribeEvent
    public static void onTagsReloaded(TagReloadEvent event) {
        DatapackHandlerRegistry.forEach((path, handler) -> {
            if (handler instanceof ITagHandler tagHandler) {
                tagHandler.gatherTags();
            }
        });
    }

    public static class BlockTagRegistry extends TagRegistry<Block> {

        public BlockTagRegistry() {
            super("block", "tags/block", Block.class, new Block[0]);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected List<Block> getDomain() {
            return ObjectIterators.pour((Iterator<Block>) Block.blockRegistry.iterator());
        }

        @Override
        protected void postEvent() {
            MinecraftForge.EVENT_BUS.post(new RegisterBlockTagsEvent(this));
        }

        @Override
        protected ResourceLocation getIdForTarget(Block block) {
            return new ResourceLocation(Block.blockRegistry.getNameForObject(block));
        }

        @Override
        protected Block getTarget(ResourceLocation id) {
            return (Block) Block.blockRegistry.getObject(id.toString());
        }
    }

    public static class ItemTagRegistry extends TagRegistry<Item> {

        public ItemTagRegistry() {
            super("item", "tags/item", Item.class, new Item[0]);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected List<Item> getDomain() {
            return ObjectIterators.pour((Iterator<Item>) Item.itemRegistry.iterator());
        }

        @Override
        protected void postEvent() {
            MinecraftForge.EVENT_BUS.post(new RegisterItemTagsEvent(this));
        }

        @Override
        protected ResourceLocation getIdForTarget(Item item) {
            return new ResourceLocation(Item.itemRegistry.getNameForObject(item));
        }

        @Override
        protected Item getTarget(ResourceLocation id) {
            return (Item) Item.itemRegistry.getObject(id.toString());
        }
    }

    public static class BiomeGenBaseTagRegistry extends TagRegistry<BiomeGenBase> {

        public BiomeGenBaseTagRegistry() {
            super("biome", "tags/worldgen/biome", BiomeGenBase.class, new BiomeGenBase[0]);
        }

        @Override
        protected List<BiomeGenBase> getDomain() {
            return Arrays.asList(BiomeGenBase.getBiomeGenArray()).stream().filter(Objects::nonNull).collect(Collectors.toList());
        }

        @Override
        protected void postEvent() {
            MinecraftForge.EVENT_BUS.post(new RegisterBiomeTagsEvent(this));
        }

        @Override
        protected ResourceLocation getIdForTarget(BiomeGenBase biome) {
            return ProxyBiomeRegistry.INSTANCE.getIdForObject(biome);
        }

        @Override
        protected BiomeGenBase getTarget(ResourceLocation id) {
            return ProxyBiomeRegistry.INSTANCE.getObject(id);
        }
    }
}
