package com.aigraph.core.exceptions;

/**
 * Exception thrown during graph execution.
 * <p>
 * This exception wraps errors that occur during:
 * <ul>
 *   <li>Node execution</li>
 *   <li>Channel updates</li>
 *   <li>Timeout violations</li>
 *   <li>Max steps exceeded</li>
 * </ul>
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public final class ExecutionException extends LangGraphException {

    private final String nodeName;
    private final int stepNumber;

    public ExecutionException(String message) {
        super(message);
        this.nodeName = null;
        this.stepNumber = -1;
    }

    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.nodeName = null;
        this.stepNumber = -1;
    }

    public ExecutionException(String nodeName, int stepNumber, String message) {
        super("Execution failed at step " + stepNumber +
              (nodeName != null ? " in node '" + nodeName + "'" : "") + ": " + message);
        this.nodeName = nodeName;
        this.stepNumber = stepNumber;
    }

    public ExecutionException(String nodeName, int stepNumber, String message, Throwable cause) {
        super("Execution failed at step " + stepNumber +
              (nodeName != null ? " in node '" + nodeName + "'" : "") + ": " + message, cause);
        this.nodeName = nodeName;
        this.stepNumber = stepNumber;
    }

    public String getNodeName() {
        return nodeName;
    }

    public int getStepNumber() {
        return stepNumber;
    }
}
