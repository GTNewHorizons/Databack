package databack.mixins;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@SuppressWarnings("unused")
@Getter
@RequiredArgsConstructor
public enum Mixins implements IMixins {

    DATAPACK(Side.COMMON, "MixinWorldInfo_Datapacks"),
    NBT_ACCESSOR(Side.COMMON, "AccessorNBTTagList", "AccessorNBTTagCompound"),
    HEIGHTMAPS(Side.COMMON, "MixinWorld_HeightmapExt"),
    EXT(Side.COMMON, "MixinWorld_Ext"),
    TAGS(Side.COMMON, "Mixin_InjectTaggable"),
    PROXY_REGISTRY(Side.COMMON, "MixinBiomeGenBase_Registry", "MixinEntityList_Registry", "MixinEntityRegistry_Registry"),
    REGISTRY_ACCESSOR(Side.COMMON, "AccessorRegistrySimple"),
    //
    ;

    private final MixinBuilder builder;

    Mixins(Side side, String... mixins) {
        builder = new MixinBuilder().addSidedMixins(side, mixins).setPhase(Phase.EARLY);
    }
}
