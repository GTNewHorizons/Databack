package databack.common.meta;

import javax.annotation.Nullable;

/**
 * Represents one entry in a filter block list.
 * Each field is a Java regex pattern string matched against the corresponding component of a resource location.
 * Immutable value class.
 */
public final class ResourceLocationPattern {

    @Nullable
    private final String namespace;

    @Nullable
    private final String path;

    /**
     * Either or both fields may be null. A null field matches any value.
     */
    public ResourceLocationPattern(@Nullable String namespace, @Nullable String path) {
        this.namespace = namespace;
        this.path = path;
    }

    /**
     * The namespace regex pattern, or {@code null} to match any namespace.
     */
    @Nullable
    public String getNamespace() {
        return namespace;
    }

    /**
     * The path regex pattern, or {@code null} to match any path.
     */
    @Nullable
    public String getPath() {
        return path;
    }
}
