package databack.mixins.early;

import java.util.BitSet;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.world.biome.BiomeGenBase;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import databack.common.tags.Taggable;

@Mixin({ Block.class, Item.class, BiomeGenBase.class })
public class Mixin_InjectTaggable implements Taggable {

    @Unique
    private final BitSet db$presentTags = new BitSet();

    @Override
    public @NotNull BitSet db$getTagBitSet() {
        return db$presentTags;
    }
}
