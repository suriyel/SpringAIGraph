package com.aigraph.core.exceptions;

/**
 * Exception thrown when attempting to read from an empty channel.
 * <p>
 * This occurs when:
 * <ul>
 *   <li>A channel has never been updated</li>
 *   <li>An ephemeral channel has already been consumed</li>
 *   <li>A checkpoint operation is attempted on a channel that doesn't support it</li>
 * </ul>
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public final class EmptyChannelException extends LangGraphException {

    private final String channelName;

    public EmptyChannelException(String channelName) {
        super("Channel '" + channelName + "' is empty");
        this.channelName = channelName;
    }

    public EmptyChannelException(String channelName, String reason) {
        super("Channel '" + channelName + "' is empty: " + reason);
        this.channelName = channelName;
    }

    public String getChannelName() {
        return channelName;
    }
}
