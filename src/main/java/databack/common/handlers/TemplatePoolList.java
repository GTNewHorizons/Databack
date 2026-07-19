package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.template_pool.TemplatePool;

public class TemplatePoolList extends JsonDatapackTypeHandler<TemplatePool> {

    public TemplatePoolList() {
        super("worldgen/template_pool", TemplatePool.class);
    }

    @Nullable
    public TemplatePool getTemplatePool(String id) {
        return super.getObject(id);
    }
}
