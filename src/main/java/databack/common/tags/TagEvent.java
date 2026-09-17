package databack.common.tags;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.world.biome.BiomeGenBase;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.relauncher.Side;
import databack.common.interop.modern_block.BlockIdentity;
import databack.common.interop.modern_block.ItemIdentity;

public class TagEvent<Target> extends Event {

    public final ITagRegistry<Target> registry;

    public TagEvent(ITagRegistry<Target> registry) {
        this.registry = registry;
    }

    public static class TagReloadEvent extends Event {

    }

    public static class RegisterBlockTagsEvent extends TagEvent<BlockIdentity> {

        public RegisterBlockTagsEvent(ITagRegistry<BlockIdentity> registry) {
            super(registry);
        }
    }

    public static class RegisterItemTagsEvent extends TagEvent<ItemIdentity> {

        public RegisterItemTagsEvent(ITagRegistry<ItemIdentity> registry) {
            super(registry);
        }
    }

    public static class RegisterBiomeTagsEvent extends TagEvent<BiomeGenBase> {

        public RegisterBiomeTagsEvent(ITagRegistry<BiomeGenBase> registry) {
            super(registry);
        }
    }

    public static class RegisterEntityTagsEvent extends TagEvent<Class<? extends Entity>> {

        public RegisterEntityTagsEvent(ITagRegistry<Class<? extends Entity>> registry) {
            super(registry);
        }
    }
}
