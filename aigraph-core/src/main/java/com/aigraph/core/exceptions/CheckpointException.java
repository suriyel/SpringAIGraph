package com.aigraph.core.exceptions;

/**
 * Exception thrown during checkpoint operations.
 * <p>
 * This includes errors in:
 * <ul>
 *   <li>Checkpoint serialization</li>
 *   <li>Checkpoint deserialization</li>
 *   <li>Checkpoint storage operations</li>
 *   <li>Checkpoint restoration</li>
 * </ul>
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public final class CheckpointException extends LangGraphException {

    private final String checkpointId;

    public CheckpointException(String message) {
        super(message);
        this.checkpointId = null;
    }

    public CheckpointException(String message, Throwable cause) {
        super(message, cause);
        this.checkpointId = null;
    }

    public CheckpointException(String checkpointId, String message) {
        super("Checkpoint error [" + checkpointId + "]: " + message);
        this.checkpointId = checkpointId;
    }

    public CheckpointException(String checkpointId, String message, Throwable cause) {
        super("Checkpoint error [" + checkpointId + "]: " + message, cause);
        this.checkpointId = checkpointId;
    }

    public String getCheckpointId() {
        return checkpointId;
    }
}
