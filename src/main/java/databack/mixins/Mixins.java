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
    PROXY_REGISTRY(Side.COMMON, "MixinBiomeGenBase_Registry", "MixinEntityList_Registry", "MixinEntityRegistry_Registry", "MixinBiomeGenMutated_Registry"),
    REGISTRY_ACCESSOR(Side.COMMON, "AccessorRegistrySimple"),
    BLOCK_VARIANT(Side.COMMON, "MixinBlock_Identity"),
    ITEM_VARIANT(Side.COMMON, "MixinItem_Identity"),
    DEBUG_OVERLAY_TOGGLE(Side.CLIENT, "MixinASMEventHandler_DebugToggle", "MixinStaticASMEventHandler_DebugToggle"),
    DEBUG_OVERLAY_ORDER(Side.CLIENT, "MixinEventBus_OverlayOrder"),
    //
    ;

    private final MixinBuilder builder;

    Mixins(Side side, String... mixins) {
        builder = new MixinBuilder().addSidedMixins(side, mixins).setPhase(Phase.EARLY);
    }
}
