package databack.common.handlers;

import java.util.Arrays;
import java.util.List;

public interface ResourceType<H extends IDatapackTypeHandler> {

    String getResourcePath();

    H getHandler();

    static <H extends IDatapackTypeHandler> ResourceType<H> withPath(String resourcePath) {
        List<String> split = Arrays.asList(resourcePath.split("/"));

        return new ResourceType<>() {

            @Override
            public String getResourcePath() {
                return resourcePath;
            }

            @Override
            public H getHandler() {
                //noinspection unchecked
                return (H) DatapackHandlerRegistry.getTypeHandler(split);
            }
        };
    }
}
