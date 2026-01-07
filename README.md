# Spring AI Graph

A Java implementation of LangGraph - a stateful multi-actor graph execution framework based on the Pregel/BSP model.

## Features

- **Pregel/BSP Execution Model**: Deterministic, parallel execution with bulk synchronous updates
- **Channel-based Communication**: Type-safe message passing between nodes
- **Cyclic Graph Support**: Build graphs with loops and conditional flows
- **Checkpoint & Resume**: Save and restore execution state
- **Spring Boot Integration**: Auto-configuration and seamless Spring AI integration
- **Java 21**: Leverages Records, Sealed Classes, and modern Java features

## Quick Start

### Maven Dependencies

```xml
<dependency>
    <groupId>com.aigraph</groupId>
    <artifactId>aigraph-spring-boot-starter</artifactId>
    <version>0.0.8</version>
</dependency>
```

### Simple Example

```java
import com.aigraph.channels.LastValueChannel;
import com.aigraph.graph.*;
import com.aigraph.nodes.*;
import com.aigraph.pregel.Pregel;

// Create a node
Node<String, String> uppercaseNode = NodeBuilder.<String, String>create("uppercase")
    .subscribeOnly("input")
    .process(String::toUpperCase)
    .writeTo("output")
    .build();

// Build graph
Graph<String, String> graph = GraphBuilder.<String, String>create()
    .name("simple-pipeline")
    .addNode("uppercase", uppercaseNode)
    .addChannel("input", new LastValueChannel<>("input", String.class))
    .addChannel("output", new LastValueChannel<>("output", String.class))
    .setInput("input")
    .setOutput("output")
    .build();

// Execute
Pregel<String, String> pregel = graph.compile();
String result = pregel.invoke("hello");
System.out.println(result); // "HELLO"
```

## Module Overview

| Module | Description |
|--------|-------------|
| `aigraph-core` | Core exceptions and functional interfaces |
| `aigraph-channels` | Channel implementations (LastValue, Topic, BinaryOperator, Ephemeral) |
| `aigraph-nodes` | Node system with builders and registry |
| `aigraph-pregel` | Pregel execution engine |
| `aigraph-graph` | Graph builder and validator |
| `aigraph-checkpoint` | Checkpoint interfaces and JSON serializer |
| `aigraph-checkpoint-memory` | In-memory checkpoint storage |
| `aigraph-spring-boot-starter` | Spring Boot auto-configuration |
| `aigraph-examples` | Example applications |

## Configuration

```yaml
spring:
  ai:
    graph:
      enabled: true
      default-max-steps: 100
      default-timeout: 5m
      thread-pool-size: 8
      checkpoint:
        enabled: false
        type: memory
```

## Architecture

### Channel Types

- **LastValueChannel**: Stores only the most recent value
- **TopicChannel**: Accumulates values in a list (Pub/Sub pattern)
- **BinaryOperatorChannel**: Aggregates values using a binary operator (sum, max, etc.)
- **EphemeralChannel**: Values consumed once and cleared

### Execution Flow

```
1. Plan: Find nodes subscribed to updated channels
2. Execute: Run nodes in parallel (BSP synchronization point)
3. Update: Collect writes and batch update channels
4. Repeat: Until no updates or max steps reached
```

## Building from Source

```bash
mvn clean install
```

## Requirements

- Java 21+
- Spring Boot 3.x
- Spring AI 1.1.2

## License

Apache License 2.0

## Status

**Version**: 0.0.8
**Status**: Beta - Core functionality implemented

**Implemented**:
- Core framework (Channels, Nodes, Pregel, Graph)
- Spring Boot integration
- Checkpoint system
- Basic examples

**Roadmap**:
- Advanced node types (Composite, Conditional)
- More checkpoint backends (JDBC, Redis)
- Monitoring and metrics (Actuator integration)
- Graph visualization
- Advanced examples and documentation

## Contributing

Contributions welcome! Please open an issue or submit a pull request.

## References

- [LangGraph Python](https://github.com/langchain-ai/langgraph) - Original implementation
- [Pregel Paper](https://kowshik.github.io/JPregel/pregel_paper.pdf) - Google's Pregel system
