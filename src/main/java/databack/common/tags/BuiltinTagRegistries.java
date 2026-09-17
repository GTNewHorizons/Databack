package databack.common.tags;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import databack.common.handlers.DatapackHandlerRegistry;
import databack.common.handlers.ResourceType;
import databack.common.interop.modern_block.BlockIdentity;
import databack.common.interop.modern_block.ItemIdentity;
import databack.common.interop.registry.BlockIdentityRegistry;
import databack.common.interop.registry.ItemIdentityRegistry;
import databack.common.interop.registry.ProxyBiomeRegistry;
import databack.common.loader.DatapackEvent.DatapackFinishedLoadingEvent;
import databack.common.tags.TagEvent.RegisterBiomeTagsEvent;
import databack.common.tags.TagEvent.RegisterBlockTagsEvent;
import databack.common.tags.TagEvent.RegisterItemTagsEvent;
import databack.common.tags.TagEvent.TagReloadEvent;

/// The primary tag registries needed for parity with modern.
@EventBusSubscriber
public class BuiltinTagRegistries {

    public static final ResourceType<TagRegistry<BlockIdentity>> BLOCK_TAGS = ResourceType.withPath("tags/block");
    public static final ResourceType<TagRegistry<ItemIdentity>> ITEM_TAGS = ResourceType.withPath("tags/item");
    public static final ResourceType<TagRegistry<BiomeGenBase>> BIOME_TAGS = ResourceType.withPath("tags/worldgen/biome");
    public static final ResourceType<EntityTagRegistry> ENTITY_TAGS = ResourceType.withPath("tags/entity_type");

    public static TagRegistry<BlockIdentity> blocks() {
        return BLOCK_TAGS.getHandler();
    }

    public static TagRegistry<ItemIdentity> items() {
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

    public static class BlockTagRegistry extends TagRegistry<BlockIdentity> {

        public BlockTagRegistry() {
            super("block", "tags/block", BlockIdentity.class, new BlockIdentity[0]);
        }

        @Override
        protected List<BlockIdentity> getDomain() {
            return BlockIdentityRegistry.INSTANCE.domain();
        }

        @Override
        protected void postEvent() {
            MinecraftForge.EVENT_BUS.post(new RegisterBlockTagsEvent(this));
        }

        @Override
        protected ResourceLocation getIdForTarget(BlockIdentity identity) {
            return identity.identityId;
        }

        @Override
        protected BlockIdentity getTarget(ResourceLocation id) {
            return BlockIdentityRegistry.INSTANCE.getObject(id);
        }
    }

    public static class ItemTagRegistry extends TagRegistry<ItemIdentity> {

        public ItemTagRegistry() {
            super("item", "tags/item", ItemIdentity.class, new ItemIdentity[0]);
        }

        @Override
        protected List<ItemIdentity> getDomain() {
            return ItemIdentityRegistry.INSTANCE.domain();
        }

        @Override
        protected void postEvent() {
            MinecraftForge.EVENT_BUS.post(new RegisterItemTagsEvent(this));
        }

        @Override
        protected ResourceLocation getIdForTarget(ItemIdentity identity) {
            return identity.identityId;
        }

        @Override
        protected ItemIdentity getTarget(ResourceLocation id) {
            return ItemIdentityRegistry.INSTANCE.getObject(id);
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
