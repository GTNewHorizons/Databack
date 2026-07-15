package databack.common.loader;

import org.jetbrains.annotations.NotNull;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record ResourceId(String namespace, String resourceType, String id, String fullName) {

    public String fqid() {
        return namespace + ":" + id;
    }

    @Override
    public @NotNull String toString() {
        return namespace + ":" + resourceType + "/" + id;
    }
}
