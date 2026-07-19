package databack.common.dto.worldgen.processor_list;

/** A {@link IProcessorListRef} that points to a named processor list by resource ID. */
public class ProcessorListIdRef implements IProcessorListRef {

    public final String id;

    public ProcessorListIdRef(String id) {
        this.id = id;
    }
}
