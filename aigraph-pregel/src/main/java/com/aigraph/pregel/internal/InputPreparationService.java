package com.aigraph.pregel.internal;

import com.aigraph.channels.Channel;
import com.aigraph.channels.ChannelManager;
import com.aigraph.nodes.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Service for preparing node inputs from channel values.
 * <p>
 * This service handles the logic of reading channel values and
 * mapping them to node inputs based on subscription patterns.
 * <p>
 * Input mapping rules:
 * <ul>
 *   <li>Single subscription, no read channels: pass channel value directly</li>
 *   <li>Multiple subscriptions or has read channels: pass Map&lt;String, Object&gt;</li>
 * </ul>
 *
 * @author AIGraph Team
 * @since 0.0.9
 */
public class InputPreparationService {
    private static final Logger log = LoggerFactory.getLogger(InputPreparationService.class);

    /**
     * Prepares inputs for a list of nodes based on their channel subscriptions.
     *
     * @param nodes          the nodes to prepare inputs for
     * @param channelManager the channel manager containing channel values
     * @return map of node name to input value
     */
    public Map<String, Object> prepareNodeInputs(List<Node<?, ?>> nodes, ChannelManager channelManager) {
        Map<String, Object> nodeInputs = new HashMap<>();

        for (Node<?, ?> node : nodes) {
            String nodeName = node.getName();
            Set<String> subscribedChannels = node.getSubscribedChannels();
            Set<String> readChannels = node.getReadChannels();

            try {
                Object input;

                if (subscribedChannels.size() == 1 && readChannels.isEmpty()) {
                    // Single subscription: pass channel value directly
                    String channelName = subscribedChannels.iterator().next();
                    input = readChannelValue(channelManager, channelName);
                } else {
                    // Multiple subscriptions or has read channels: build a map
                    Map<String, Object> inputMap = new LinkedHashMap<>();

                    // Add subscribed channel values
                    for (String channelName : subscribedChannels) {
                        Object value = readChannelValue(channelManager, channelName);
                        inputMap.put(channelName, value);
                    }

                    // Add additional read channel values
                    for (String channelName : readChannels) {
                        Object value = readChannelValue(channelManager, channelName);
                        inputMap.put(channelName, value);
                    }

                    input = inputMap;
                }

                nodeInputs.put(nodeName, input);
                log.trace("Prepared input for node '{}': {}", nodeName, input);

            } catch (Exception e) {
                log.warn("Failed to prepare input for node '{}': {}", nodeName, e.getMessage());
                // Pass null if we can't read the input - node will handle it
                nodeInputs.put(nodeName, null);
            }
        }

        return nodeInputs;
    }

    /**
     * Reads a value from a channel, returning null if the channel is empty.
     *
     * @param channelManager the channel manager
     * @param channelName    the channel name
     * @return the channel value, or null if empty
     */
    private Object readChannelValue(ChannelManager channelManager, String channelName) {
        try {
            Channel<?, ?, ?> channel = channelManager.get(channelName);
            if (channel.isEmpty()) {
                return null;
            }
            return channel.get();
        } catch (Exception e) {
            log.trace("Channel '{}' is empty or unreadable: {}", channelName, e.getMessage());
            return null;
        }
    }
}
