package databack.mixins.early;

import java.util.Map;

import net.minecraft.util.RegistrySimple;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegistrySimple.class)
public interface AccessorRegistrySimple {

    @Accessor("registryObjects")
    <T> Map<String, T> db$getRegistryObjects();

}
