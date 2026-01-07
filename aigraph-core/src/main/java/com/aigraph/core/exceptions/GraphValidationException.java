package com.aigraph.core.exceptions;

import java.util.List;

/**
 * Exception thrown when graph validation fails.
 * <p>
 * Validation errors include:
 * <ul>
 *   <li>Orphaned nodes (no subscribed channels)</li>
 *   <li>Unreachable output channels</li>
 *   <li>Missing channel definitions</li>
 *   <li>Cyclic dependencies without termination conditions</li>
 * </ul>
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public final class GraphValidationException extends LangGraphException {

    private final List<String> validationErrors;

    public GraphValidationException(String message) {
        super(message);
        this.validationErrors = List.of(message);
    }

    public GraphValidationException(String message, List<String> validationErrors) {
        super(message + "\nErrors:\n  - " + String.join("\n  - ", validationErrors));
        this.validationErrors = List.copyOf(validationErrors);
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
