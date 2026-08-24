package databack.common.tags;

import net.minecraft.entity.Entity;

public interface IEntityTag extends ITag<Class<? extends Entity>> {

    boolean includes(Entity entity);

}
