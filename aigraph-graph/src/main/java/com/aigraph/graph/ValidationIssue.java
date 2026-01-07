package com.aigraph.graph;

/**
 * A single validation issue.
 */
public record ValidationIssue(
    Level level,
    String code,
    String message,
    String location
) {
    public enum Level {
        ERROR, WARNING, INFO
    }

    public ValidationIssue {
        if (level == null) level = Level.ERROR;
        if (code == null) code = "UNKNOWN";
        if (message == null) message = "";
    }
}
