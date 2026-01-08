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
    <version>0.0.9</version>
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

### NEW: Context-Aware AI Agent Example

```java
// Create a context-aware node that accesses conversation history
Node<String, String> chatNode = NodeBuilder.<String, String>create("ai-chat")
    .subscribeOnly("input")
    .processWithContext((input, ctx) -> {
        ExecutionContext execCtx = (ExecutionContext) ctx;
        MessageContext msgCtx = execCtx.getMessageContext();

        // Access conversation history
        List<Message> history = msgCtx.getMessages();

        // Process with context
        return generateResponse(input, history);
    })
    .writeTo("output")
    .build();

Graph<String, String> graph = GraphBuilder.<String, String>create()
    .name("ai-agent")
    .addNode("ai-chat", chatNode)
    .setInput("input")
    .setOutput("output")
    .build();

Pregel<String, String> pregel = graph.compile();
String response = pregel.invoke("Hello!");
```

### NEW: Reactive Execution Example

```java
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

Pregel<String, String> pregel = graph.compile();

// Reactive single value execution
Mono<String> resultMono = pregel.invokeReactive("input");
resultMono.subscribe(
    result -> System.out.println("Result: " + result),
    error -> System.err.println("Error: " + error),
    () -> System.out.println("Complete")
);

// Reactive streaming of execution steps
Flux<ExecutionStep> steps = pregel.streamReactive("input");
steps
    .doOnNext(step -> System.out.println("Step " + step.stepNumber()))
    .take(10) // Cancellation support
    .timeout(Duration.ofSeconds(30)) // Timeout support
    .subscribe();
```

### NEW: Checkpoint and Resume Example

```java
import com.aigraph.checkpoint.memory.MemoryCheckpointer;

Pregel<String, String> pregel = graph.compile();
pregel.setCheckpointer(new MemoryCheckpointer());

// Execute with automatic checkpointing
String result = pregel.invoke("input");

// Resume from checkpoint
String resumed = pregel.resumeFrom(threadId, checkpointId);
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

**Version**: 0.0.9
**Status**: Beta - Core functionality + Advanced features

**Implemented** ✅:
- Core framework (Channels, Nodes, Pregel, Graph)
- Spring Boot integration
- Checkpoint system with resume support
- **NEW**: Spring AI deep integration with MessageContext
- **NEW**: Context-aware nodes with execution context access
- **NEW**: Reactive programming with Mono/Flux (Project Reactor)
- **NEW**: Stop/Resume execution with full state persistence
- Comprehensive examples and documentation

**What's New in v0.0.9**:
- 🎯 **MessageContext**: Stateful conversation management for AI agents
- 🔄 **Reactive APIs**: Non-blocking execution with Mono/Flux
- 💾 **Checkpoint/Resume**: Full state persistence and restoration
- 🧠 **Context-Aware Nodes**: Access message history and execution state
- 📊 **Stream Monitoring**: Real-time execution step streaming

**Roadmap**:
- Direct Spring AI ChatClient integration
- Distributed execution support
- Hot observable streams
- Enhanced monitoring and metrics
- Graph visualization tools

## Contributing

Contributions welcome! Please open an issue or submit a pull request.

## References

- [LangGraph Python](https://github.com/langchain-ai/langgraph) - Original implementation
- [Pregel Paper](https://kowshik.github.io/JPregel/pregel_paper.pdf) - Google's Pregel system
