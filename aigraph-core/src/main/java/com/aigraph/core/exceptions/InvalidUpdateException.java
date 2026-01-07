package com.aigraph.core.exceptions;

/**
 * Exception thrown when an invalid update is attempted on a channel.
 * <p>
 * Common scenarios:
 * <ul>
 *   <li>LastValueChannel receives multiple updates in a single batch</li>
 *   <li>Update value type doesn't match channel's expected type</li>
 *   <li>Null value provided when not allowed</li>
 *   <li>Custom validation fails</li>
 * </ul>
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public final class InvalidUpdateException extends LangGraphException {

    private final String channelName;

    public InvalidUpdateException(String channelName, String message) {
        super("Invalid update to channel '" + channelName + "': " + message);
        this.channelName = channelName;
    }

    public InvalidUpdateException(String channelName, String message, Throwable cause) {
        super("Invalid update to channel '" + channelName + "': " + message, cause);
        this.channelName = channelName;
    }

    public String getChannelName() {
        return channelName;
    }
}
