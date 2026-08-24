package databack.common.tags;

import net.minecraft.util.ResourceLocation;

public interface ITag<Target> extends Taggable {

    ResourceLocation getId();

    boolean includes(ITag<Target> tag);
    ITag<Target>[] getChildTags();

    boolean includes(Target target);
    Target[] getContents();
}
