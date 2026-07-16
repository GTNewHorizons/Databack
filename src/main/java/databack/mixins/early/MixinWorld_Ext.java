package databack.mixins.early;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import databack.common.context.WorldContext;
import databack.common.context.WorldContextImpl;
import databack.common.mixinext.WorldExt;

@Mixin(World.class)
public class MixinWorld_Ext implements WorldExt {

    @Unique
    private WorldContext db$context;

    @Override
    public WorldContext db$getContext() {
        if (db$context == null) {
            db$context = new WorldContextImpl((World) (Object) this);
        }

        return db$context;
    }
}
