package databack.common.tags;

import java.util.BitSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.hash.Fnv1a32;
import lombok.Getter;

public class TagImpl<Target> implements ITag<Target> {

    static final AtomicInteger CURRENT_GENERATION = new AtomicInteger(0);

    @Getter
    private final ResourceLocation id;
    @Getter
    private final int generation, bit;

    private final BitSet parentTags = new BitSet();
    private TagImpl<Target>[] childTags;
    private Target[] contents;

    public TagImpl(ResourceLocation id, int bit) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.generation = CURRENT_GENERATION.get();
        this.bit = bit;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof TagImpl<?> tagImpl)) {
            return false;
        }

        return generation == tagImpl.generation
            && bit == tagImpl.bit
            && id.equals(tagImpl.id);
    }

    @Override
    public int hashCode() {
        int hash = Fnv1a32.initialState();
        hash = Fnv1a32.hashStep(hash, id.getResourceDomain());
        hash = Fnv1a32.hashStep(hash, id.getResourcePath());
        hash = Fnv1a32.hashStep(hash, generation);
        hash = Fnv1a32.hashStep(hash, bit);
        return hash;
    }

    @Override
    public String toString() {
        return "#" + id.getResourceDomain() + ":" + id.getResourcePath();
    }

    void finish(TagImpl<Target>[] childTags, Target[] contents) {
        this.childTags = childTags;
        this.contents = contents;
    }

    @Override
    public @NotNull BitSet db$getTagBitSet() {
        return parentTags;
    }

    @Override
    public boolean includes(ITag<Target> tag) {
        return tag.db$getTagBitSet().get(bit);
    }

    @Override
    public ITag<Target>[] getChildTags() {
        return childTags;
    }

    @Override
    public boolean includes(Target target) {
        return ((Taggable) target).db$getTagBitSet().get(bit);
    }

    @Override
    public Target[] getContents() {
        return contents;
    }
}
