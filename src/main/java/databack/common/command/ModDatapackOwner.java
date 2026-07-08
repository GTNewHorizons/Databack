package databack.common.command;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record ModDatapackOwner(String modid) implements IDatapackOwner {

    @Override
    public String getName() {
        return modid;
    }
}
