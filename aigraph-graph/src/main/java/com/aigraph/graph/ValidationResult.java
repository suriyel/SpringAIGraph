package com.aigraph.graph;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of graph validation.
 */
public record ValidationResult(
    boolean valid,
    List<ValidationIssue> issues
) {
    public ValidationResult {
        issues = issues != null ? List.copyOf(issues) : List.of();
    }

    public static ValidationResult valid() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult invalid(List<ValidationIssue> issues) {
        return new ValidationResult(false, issues);
    }

    public List<ValidationIssue> getErrors() {
        return issues.stream()
            .filter(issue -> issue.level() == ValidationIssue.Level.ERROR)
            .collect(Collectors.toList());
    }

    public List<ValidationIssue> getWarnings() {
        return issues.stream()
            .filter(issue -> issue.level() == ValidationIssue.Level.WARNING)
            .collect(Collectors.toList());
    }
}
