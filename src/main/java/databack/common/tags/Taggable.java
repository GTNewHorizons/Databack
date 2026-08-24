package databack.common.tags;

import java.util.BitSet;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@Internal
public interface Taggable {

    @NotNull
    BitSet db$getTagBitSet();

}
