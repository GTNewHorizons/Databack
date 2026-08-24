package databack.common.tags;

import net.minecraft.util.ResourceLocation;

import com.google.common.collect.SetMultimap;
import databack.common.dto.tag.TagEntry;

public interface ITagStagingReceiver {

    void receiveTagStaging(SetMultimap<ResourceLocation, TagEntry> staging);
}
