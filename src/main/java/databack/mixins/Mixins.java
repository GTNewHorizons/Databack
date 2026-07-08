package databack.mixins;

import com.gtnewhorizon.gtnhlib.GTNHLibConfig;
import com.gtnewhorizon.gtnhlib.mixin.Phase;
import com.gtnewhorizon.gtnhlib.mixins.TargetMods;
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
    //
    ;

    private final MixinBuilder builder;

    Mixins(Side side, String... mixins) {
        builder = new MixinBuilder().addSidedMixins(side, mixins).setPhase(Phase.EARLY);
    }
}
