package databack.mixins;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.util.data.IMod;
import com.gtnewhorizon.gtnhmixins.builders.ITargetMod;
import com.gtnewhorizon.gtnhmixins.builders.TargetModBuilder;
import cpw.mods.fml.common.Loader;

public enum DBMods implements ITargetMod, IMod {

    CUBIC_CHUNKS("cubicchunks", "com.cardinalstar.cubicchunks.CubicChunksCore"),
    //
    ;

    private final TargetModBuilder builder;
    private final String modId;

    private boolean isLoaded, checkedPresence;

    DBMods(String modId, @Nullable String coreModClass) {
        this.modId = modId;
        this.builder = new TargetModBuilder().setCoreModClass(coreModClass).setModId(modId);
    }

    @Nonnull
    @Override
    public TargetModBuilder getBuilder() {
        return builder;
    }

    @Override
    public boolean isModLoaded() {
        if (!checkedPresence) {
            isLoaded = Loader.isModLoaded(getInternalID());
        }

        return isLoaded;
    }

    @SuppressWarnings("ProtectedMemberInFinalClass")
    protected String getInternalID() {
        return getID();
    }

    @Override
    public String getID() {
        return modId;
    }

    @Override
    public String getResourceLocation() {
        return modId;
    }
}
