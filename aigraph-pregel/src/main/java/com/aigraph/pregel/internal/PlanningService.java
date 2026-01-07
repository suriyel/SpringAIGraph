package com.aigraph.pregel.internal;

import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeRegistry;
import com.aigraph.pregel.ExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service for planning which nodes to execute in each step.
 */
public class PlanningService {

    public List<Node<?, ?>> plan(ExecutionContext context) {
        Set<String> updatedChannels = context.getUpdatedChannels();
        NodeRegistry registry = context.getNodeRegistry();

        if (updatedChannels.isEmpty()) {
            return List.of();
        }

        // Find all nodes subscribed to updated channels
        return new ArrayList<>(registry.getBySubscriptions(updatedChannels));
    }
}
