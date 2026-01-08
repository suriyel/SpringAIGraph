# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring AI Graph is a Java implementation of LangGraph - a stateful multi-actor graph execution framework based on the Pregel/BSP (Bulk Synchronous Parallel) model. It provides a framework for building and executing computational graphs with channel-based communication between nodes.

**Key Technologies:**
- Java 21 (uses Records, Sealed Classes, Pattern Matching)
- Spring Boot 3.4.1
- Spring AI 1.1.2
- Maven multi-module project

**Current Version:** 0.0.8 (Beta)

## Build Commands

```bash
# Build entire project
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run tests
mvn test

# Run tests for a specific module
mvn test -pl aigraph-pregel

# Package the project
mvn clean package

# Generate Javadocs
mvn javadoc:javadoc
```

## Module Architecture

This is a **multi-module Maven project** with the following structure:

### Core Modules (Bottom-Up Dependency Order)

1. **aigraph-core** - Core exceptions, functional interfaces, and utilities
   - No external dependencies beyond JDK
   - Contains `LangGraphException` hierarchy
   - Defines base functional interfaces like `NodeFunction`

2. **aigraph-channels** - Channel implementations for inter-node communication
   - Depends on: `aigraph-core`
   - Key classes: `Channel`, `LastValueChannel`, `TopicChannel`, `BinaryOperatorChannel`, `EphemeralChannel`
   - `ChannelManager` handles channel lifecycle and updates

3. **aigraph-nodes** - Node system with builders and registry
   - Depends on: `aigraph-core`
   - Key classes: `Node`, `NodeBuilder`, `FunctionalNode`, `CompositeNode`, `NodeRegistry`
   - Nodes are executable units that subscribe to channels and write results

4. **aigraph-pregel** - Pregel execution engine (BSP model)
   - Depends on: `aigraph-core`, `aigraph-channels`, `aigraph-nodes`
   - Key classes: `Pregel`, `PregelExecutor`, `ExecutionContext`, `ExecutionStep`
   - Internal services: `PlanningService`, `ExecutionService`, `UpdateService`
   - **This is the execution heart of the framework**

5. **aigraph-graph** - Graph builder and validator
   - Depends on: `aigraph-core`, `aigraph-channels`, `aigraph-nodes`, `aigraph-pregel`
   - Key classes: `Graph`, `GraphBuilder`, `GraphValidator`, `GraphVisualizer`

6. **aigraph-checkpoint** - Checkpoint interfaces and serialization
   - Depends on: `aigraph-core`
   - Key classes: `Checkpointer`, `CheckpointData`, `Serializer`, `JsonSerializer`

7. **aigraph-checkpoint-memory** - In-memory checkpoint implementation
   - Depends on: `aigraph-checkpoint`

8. **aigraph-checkpoint-jdbc** - JDBC-based checkpoint implementation
   - Depends on: `aigraph-checkpoint`

9. **aigraph-spring-boot-starter** - Spring Boot auto-configuration
   - Integrates all modules with Spring Boot

10. **aigraph-actuator** - Metrics and monitoring integration
    - Depends on: `aigraph-pregel`

11. **aigraph-examples** - Example applications

## Core Execution Model: Pregel/BSP

The framework uses the **Pregel (Bulk Synchronous Parallel)** execution model:

### Execution Loop (Plan-Execute-Update)

1. **Plan Phase** (`PlanningService`)
   - Identifies which channels were updated in the previous step
   - Finds all nodes subscribed to those updated channels
   - Returns list of nodes to execute

2. **Execute Phase** (`ExecutionService`)
   - Executes all selected nodes **in parallel** using thread pool
   - Each node reads from its subscribed channels
   - Nodes produce outputs and specify write targets
   - All nodes must complete before proceeding (BSP synchronization barrier)

3. **Update Phase** (`UpdateService`)
   - Collects all write operations from executed nodes
   - Applies updates to channels in batch
   - Tracks which channels were updated
   - If any channels were updated, loop continues; otherwise execution completes

### Key Concepts

- **Channels**: Message passing abstractions between nodes. Types:
  - `LastValueChannel`: Stores only the most recent value
  - `TopicChannel`: Accumulates values in a list (Pub/Sub)
  - `BinaryOperatorChannel`: Aggregates values using operators (sum, max, etc.)
  - `EphemeralChannel`: Values consumed once then cleared

- **Nodes**: Executable units with:
  - Subscribed channels (trigger execution when updated)
  - Read channels (read but don't trigger)
  - Write targets (channels to write results to)
  - Processor function (business logic)

- **Graph**: Container for nodes and channels with defined input/output channels

- **Pregel**: Compiled executable graph with configuration

## Common Code Patterns

### Creating a Simple Pipeline

```java
// 1. Create a node
Node<String, String> node = NodeBuilder.<String, String>create("nodeName")
    .subscribeOnly("inputChannel")     // Subscribe to channel
    .process(input -> processLogic(input))  // Business logic
    .writeTo("outputChannel")           // Write result
    .build();

// 2. Build graph
Graph<String, String> graph = GraphBuilder.<String, String>create()
    .name("graphName")
    .addNode("nodeName", node)
    .addChannel("inputChannel", new LastValueChannel<>("inputChannel", String.class))
    .addChannel("outputChannel", new LastValueChannel<>("outputChannel", String.class))
    .setInput("inputChannel")
    .setOutput("outputChannel")
    .build();

// 3. Compile and execute
Pregel<String, String> pregel = graph.compile();
String result = pregel.invoke("input");
```

### Multiple Nodes with Parallel Execution

```java
// Nodes subscribed to the same channel execute in parallel
Node<String, String> branchA = NodeBuilder.<String, String>create("branchA")
    .subscribeOnly("input")
    .process(s -> "A:" + s)
    .writeTo("resultA")
    .build();

Node<String, String> branchB = NodeBuilder.<String, String>create("branchB")
    .subscribeOnly("input")
    .process(s -> "B:" + s)
    .writeTo("resultB")
    .build();

// Merge node waits for both branches
Node<Map<String, String>, String> merge = NodeBuilder.<Map<String, String>, String>create("merge")
    .subscribeTo("resultA", "resultB")  // Multiple subscriptions
    .process(inputs -> inputs.get("resultA") + " | " + inputs.get("resultB"))
    .writeTo("output")
    .build();
```

### Conditional Writes (Loops)

```java
Node<String, String> conditionalNode = NodeBuilder.<String, String>create("grow")
    .subscribeOnly("value")
    .process(str -> str + str)
    // Write only if condition met (null writes are ignored)
    .writeTo("value", str -> str.length() < 10 ? str : null)
    .build();
```

## Important Implementation Details

### Type Safety with Generics

Java's type erasure is handled using explicit `Class<T>` parameters:

```java
new LastValueChannel<String>("channelName", String.class)
```

The second parameter preserves runtime type information needed for serialization and validation.

### Channel Update Semantics

- `LastValueChannel.update(List<V>)`: Accepts exactly ONE value, throws `InvalidUpdateException` if multiple
- `TopicChannel.update(List<V>)`: Accepts multiple values, can accumulate or replace
- `BinaryOperatorChannel.update(List<V>)`: Reduces all values using operator (e.g., `Integer::sum`)
- Null writes are filtered out before channel updates

### Node Execution

- Nodes are **thread-safe** - execution service handles concurrent invocation
- Node outputs can be mapped to different channels using write target mappers
- If a node returns null for a write target, that channel is not updated

### Auto-Channel Creation

`GraphBuilder` has `autoCreateChannels(true)` by default, which automatically creates `LastValueChannel<Object>` for any channel referenced by nodes but not explicitly added. Disable for stricter validation.

## Testing

Tests use JUnit 5 + AssertJ + Mockito:

```bash
# Run all tests
mvn test

# Run tests for specific module
mvn test -pl aigraph-channels

# Run specific test class
mvn test -Dtest=LastValueChannelTest

# Run with debug output
mvn test -X
```

## Configuration (Spring Boot)

```yaml
spring:
  ai:
    graph:
      enabled: true
      default-max-steps: 100        # Max Pregel iterations
      default-timeout: 5m           # Execution timeout
      thread-pool-size: 8           # Parallel execution threads
      checkpoint:
        enabled: false
        type: memory                # memory | jdbc
```

## Code Style Notes

- Java 21 features are heavily used:
  - **Records** for immutable data: `ExecutionStep`, `NodeResult`, `PregelConfig`
  - **Pattern matching** in switch expressions
  - **Text blocks** for multi-line strings
- Functional interfaces are preferred (`Function`, `Supplier`, `Predicate`)
- Immutability: Most data structures are immutable; `ChannelManager` and `NodeRegistry` use concurrent collections for thread safety
- Builder pattern for complex objects: `NodeBuilder`, `GraphBuilder`, `PregelConfig.Builder`

## Key Design Principles from Specifications

1. **Type Safety First**: Extensive use of Java generics for compile-time type checking
2. **Immutable by Default**: State objects use Records where possible
3. **Functional Friendly**: Lambda support throughout the API
4. **Concurrent Safe**: Thread-safe collections and BSP synchronization barrier ensure correctness
5. **Zero Dependencies for Core**: `aigraph-core`, `aigraph-channels`, `aigraph-nodes` have no external dependencies

## Debugging

Enable debug mode for detailed execution logging:

```java
PregelConfig config = PregelConfig.builder()
    .debug(true)
    .maxSteps(50)
    .build();

Pregel<I, O> pregel = graph.compile(config);
```

Debug mode logs:
- Each execution step
- Nodes executed in each step
- Channel updates
- Execution timing

## Known Limitations (v0.0.8)

- Checkpoint resume is not yet implemented (`resumeFrom` throws `UnsupportedOperationException`)
- `ConditionalNode` and `RetryNode` are defined but not fully integrated
- `GraphVisualizer` is partially implemented
- Stream execution (`Pregel.stream()`) executes the full graph then returns steps (not true streaming)

## References

- Architecture diagrams: `LangGraph Java 架构图集.md`
- Requirements specification: `LangGraph Java 实现需求规格说明书.md`
- LangGraph Python (original): https://github.com/langchain-ai/langgraph
- Pregel Paper: https://kowshik.github.io/JPregel/pregel_paper.pdf
