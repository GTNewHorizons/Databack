package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.processor_list.ProcessorList;

public class ProcessorListHandler extends JsonDatapackTypeHandler<ProcessorList> {

    public ProcessorListHandler() {
        super("worldgen/processor_list", ProcessorList.class);
    }

    @Nullable
    public ProcessorList getProcessorList(String id) {
        return super.getObject(id);
    }
}
