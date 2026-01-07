package com.aigraph.examples;

import com.aigraph.channels.BinaryOperatorChannel;
import com.aigraph.channels.LastValueChannel;
import com.aigraph.channels.TopicChannel;
import com.aigraph.graph.Graph;
import com.aigraph.graph.GraphBuilder;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeBuilder;
import com.aigraph.pregel.Pregel;

/**
 * Example: Aggregate using BinaryOperatorChannel
 * <p>
 * Multiple nodes write to a sum channel that aggregates values.
 */
public class AggregateExample {

    public static void main(String[] args) {
        // Node that emits 10
        Node<Void, Integer> emitTen = NodeBuilder.<Void, Integer>create("emit10")
            .subscribeOnly("trigger")
            .process(v -> 10)
            .writeTo("sum")
            .build();

        // Node that emits 20
        Node<Void, Integer> emitTwenty = NodeBuilder.<Void, Integer>create("emit20")
            .subscribeOnly("trigger")
            .process(v -> 20)
            .writeTo("sum")
            .build();

        // Node that emits 30
        Node<Void, Integer> emitThirty = NodeBuilder.<Void, Integer>create("emit30")
            .subscribeOnly("trigger")
            .process(v -> 30)
            .writeTo("sum")
            .build();

        // Build graph with aggregation channel
        Graph<Void, Integer> graph = GraphBuilder.<Void, Integer>create()
            .name("aggregate-example")
            .addNode("emit10", emitTen)
            .addNode("emit20", emitTwenty)
            .addNode("emit30", emitThirty)
            .addChannel("trigger", new LastValueChannel<>("trigger", Void.class))
            .addChannel("sum", new BinaryOperatorChannel<>(
                "sum",
                Integer.class,
                Integer::sum,
                0
            ))
            .setInput("trigger")
            .setOutput("sum")
            .build();

        // Execute
        Pregel<Void, Integer> pregel = graph.compile();
        Integer result = pregel.invoke(null);

        System.out.println("Sum of 10 + 20 + 30 = " + result);
        System.out.println("Expected: 60");
    }
}
