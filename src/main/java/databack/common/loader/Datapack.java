package databack.common.loader;

import java.io.File;

import javax.annotation.Nonnull;

import databack.common.command.IDatapackOwner;
import databack.common.loader.io.IDatapackSource;
import databack.common.meta.PackMetadata;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents one validated, fully initialised datapack candidate.
 *
 * <p>Groups together the pack's name, disk path, parsed metadata, and source abstraction.
 * Holds no mutable state after construction; it is effectively a value holder for the duration
 * of one loading run. The held {@link IDatapackSource} remains open until explicitly closed
 * by the loading pipeline.
 */
@Getter
public final class Datapack {

    @Nonnull
    private final IDatapackOwner owner;

    @Nonnull
    private final String name;

    @Nonnull
    private final File sourcePath;

    @Nonnull
    private final PackMetadata metadata;

    @Nonnull
    private final IDatapackSource source;

    @Setter
    private boolean enabled = true;

    public Datapack(
        @Nonnull IDatapackOwner owner,
        @Nonnull String name,
        @Nonnull File sourcePath,
        @Nonnull PackMetadata metadata,
        @Nonnull IDatapackSource source) {
        this.owner = owner;
        this.name = name;
        this.sourcePath = sourcePath;
        this.metadata = metadata;
        this.source = source;
    }

    public String getPackId() {
        return owner.getName() + "/" + name;
    }
}
