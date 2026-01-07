package com.aigraph.graph;

import java.time.Instant;
import java.util.Set;

/**
 * Metadata for a graph.
 */
public record GraphMetadata(
    String name,
    String description,
    String version,
    Instant createdAt,
    Set<String> tags
) {
    public GraphMetadata {
        if (name == null || name.isBlank()) {
            name = "unnamed-graph";
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (tags == null) {
            tags = Set.of();
        } else {
            tags = Set.copyOf(tags);
        }
    }

    public static GraphMetadata of(String name) {
        return new GraphMetadata(name, null, "0.0.1", Instant.now(), Set.of());
    }
}
