package com.aigraph.pregel.internal;

import com.aigraph.channels.ChannelManager;
import com.aigraph.pregel.NodeResult;

import java.util.*;

/**
 * Service for collecting and applying channel updates.
 */
public class UpdateService {

    public Map<String, List<Object>> collectWrites(Map<String, NodeResult> results) {
        Map<String, List<Object>> channelWrites = new HashMap<>();

        for (NodeResult result : results.values()) {
            if (result.success()) {
                result.writes().forEach((channel, value) -> {
                    channelWrites.computeIfAbsent(channel, k -> new ArrayList<>()).add(value);
                });
            }
        }

        return channelWrites;
    }

    @SuppressWarnings("unchecked")
    public Set<String> applyUpdates(ChannelManager manager, Map<String, List<Object>> updates) {
        return manager.batchUpdate((Map<String, List<?>>) (Map<?, ?>) updates);
    }
}
