package databack.common.dto.worldgen.template_pool;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({ "unused", "NotNullFieldNotInitialized" })
public class TemplatePool {

    @Nullable
    public String name;

    @NotNull
    public String fallback;

    @NotNull
    public WeightedElement[] elements;

    public static class WeightedElement {

        public int weight;
        public IPoolElement element;
    }
}
