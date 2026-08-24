package databack.common.tags;

import java.util.BitSet;
import java.util.Objects;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.hash.Fnv1a32;
import lombok.Getter;

public class EntityTagImpl implements IEntityTag {

    @Getter
    private final ResourceLocation id;
    @Getter
    private final int generation, bit;

    private final BitSet parentTags = new BitSet();
    private EntityTagImpl[] childTags;
    private Class<? extends Entity>[] contents;

    public EntityTagImpl(ResourceLocation id, int bit) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.generation = TagImpl.CURRENT_GENERATION.get();
        this.bit = bit;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof EntityTagImpl tagImpl)) {
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

    void finish(EntityTagImpl[] childTags, Class<? extends Entity>[] contents) {
        this.childTags = childTags;
        this.contents = contents;
    }

    @Override
    public @NotNull BitSet db$getTagBitSet() {
        return parentTags;
    }

    @Override
    public boolean includes(ITag<Class<? extends Entity>> tag) {
        return tag.db$getTagBitSet().get(bit);
    }

    @Override
    public ITag<Class<? extends Entity>>[] getChildTags() {
        return childTags;
    }

    @Override
    public boolean includes(Class<? extends Entity> target) {
        return EntityTagRegistry.getEntityBits(target).get(bit);
    }

    @Override
    public Class<? extends Entity>[] getContents() {
        return contents;
    }

    @Override
    public boolean includes(Entity entity) {
        return includes(entity.getClass());
    }
}
