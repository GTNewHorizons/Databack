package databack.common.dto.worldgen.processor_list;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * An inline processor list.  In the datapack format this is either:
 * <ul>
 *   <li>an array: {@code [ {processor_type: "..."}, ... ]}</li>
 *   <li>an object: {@code { "processors": [ ... ] }}</li>
 * </ul>
 * A custom deserializer registered in {@link BuiltinProcessors} handles both forms.
 */
@SuppressWarnings({ "unused", "NotNullFieldNotInitialized" })
public class ProcessorList implements IProcessorListRef {

    @NotNull
    public List<IProcessor> processors;
}
