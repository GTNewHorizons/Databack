package databack.common.loader;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.github.bsideup.jabel.Desugar;
import com.google.common.collect.ImmutableList;

@Desugar
public record ResourceId(String namespace, ImmutableList<String> resourceType, String id, String fullName) {

    public String fqid() {
        return namespace + ":" + id;
    }

    @Override
    public @NotNull String toString() {
        return namespace + ":" + String.join("/", resourceType) + "/" + id;
    }
}
