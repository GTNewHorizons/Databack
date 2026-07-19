package databack.common.handlers;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public interface ResourceType<H extends IDatapackTypeHandler> {

    String getResourcePath();

    H getHandler(Side side);

    default H getHandler() {
        return getHandler(FMLCommonHandler.instance().getEffectiveSide());
    }

    static <H extends IDatapackTypeHandler> ResourceType<H> withPath(String resourcePath) {
        return new ResourceType<>() {

            @Override
            public String getResourcePath() {
                return resourcePath;
            }

            @Override
            public H getHandler(Side side) {
                //noinspection unchecked
                return (H) DatapackHandlerRegistry.getTypeHandler(resourcePath);
            }
        };
    }

}
