package com.aigraph.core.exceptions;

/**
 * Base exception for all LangGraph-related errors.
 * <p>
 * This is a sealed class that defines all possible exception types
 * in the LangGraph framework, ensuring exhaustive error handling.
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public sealed class LangGraphException extends RuntimeException
        permits EmptyChannelException,
                InvalidUpdateException,
                ExecutionException,
                GraphValidationException,
                CheckpointException {

    public LangGraphException(String message) {
        super(message);
    }

    public LangGraphException(String message, Throwable cause) {
        super(message, cause);
    }

    public LangGraphException(Throwable cause) {
        super(cause);
    }
}
