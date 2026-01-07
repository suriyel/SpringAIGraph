# LangGraph Java 架构图集

## 1. 系统分层架构图

```mermaid
graph TB
    subgraph "用户接口层 User Interface Layer"
        direction LR
        API1[GraphBuilder API]
        API2[Fluent Chain API]
        API3[Annotation API]
    end
    
    subgraph "应用服务层 Application Service Layer"
        direction LR
        PREGEL[Pregel<br/>执行引擎]
        GRAPH[Graph<br/>图定义]
        STREAM[StreamHandler<br/>流处理器]
    end
    
    subgraph "领域层 Domain Layer"
        direction TB
        subgraph "节点域 Node Domain"
            NODE[Node]
            NB[NodeBuilder]
            NR[NodeRegistry]
        end
        subgraph "通道域 Channel Domain"
            CH[Channel]
            CM[ChannelManager]
            CW[ChannelWrite]
            CR[ChannelRead]
        end
        subgraph "执行域 Execution Domain"
            EX[Executor]
            CTX[ExecutionContext]
            STEP[ExecutionStep]
        end
    end
    
    subgraph "基础设施层 Infrastructure Layer"
        direction LR
        CP[Checkpointer]
        SER[Serializer]
        POOL[ThreadPool]
        LOG[Logger]
    end
    
    API1 --> GRAPH
    API2 --> PREGEL
    API3 --> GRAPH
    GRAPH --> PREGEL
    PREGEL --> EX
    PREGEL --> STREAM
    EX --> NODE
    EX --> CM
    EX --> CTX
    NODE --> CH
    CM --> CH
    EX --> CP
    CP --> SER
    EX --> POOL
```

## 2. 执行引擎状态机

```mermaid
stateDiagram-v2
    [*] --> Initialized: create()
    
    Initialized --> Planning: invoke(input)
    
    Planning --> Executing: nodes found
    Planning --> Completed: no nodes
    
    Executing --> Updating: all nodes done
    Executing --> Failed: node error
    Executing --> TimedOut: timeout
    
    Updating --> Checkpointing: checkpoint enabled
    Updating --> Planning: has updates
    Updating --> Completed: no updates
    
    Checkpointing --> Planning: saved
    Checkpointing --> Failed: save error
    
    Completed --> [*]: return result
    Failed --> [*]: throw exception
    TimedOut --> [*]: throw timeout
    
    note right of Planning
        Plan Phase:
        1. 获取已更新通道
        2. 查找订阅节点
        3. 返回待执行列表
    end note
    
    note right of Executing
        Execute Phase:
        1. 并行执行节点
        2. 收集输出结果
        3. 等待全部完成
    end note
    
    note right of Updating
        Update Phase:
        1. 写入通道值
        2. 记录更新通道
        3. 检查终止条件
    end note
```

## 3. 完整类继承关系图

```mermaid
classDiagram
    %% 异常体系
    class LangGraphException {
        <<abstract>>
        +getMessage() String
        +getCause() Throwable
    }
    class EmptyChannelException
    class InvalidUpdateException
    class ExecutionException
    class GraphValidationException
    class CheckpointException
    
    LangGraphException <|-- EmptyChannelException
    LangGraphException <|-- InvalidUpdateException
    LangGraphException <|-- ExecutionException
    LangGraphException <|-- GraphValidationException
    LangGraphException <|-- CheckpointException
    
    %% 函数式接口
    class NodeFunction~I,O~ {
        <<interface>>
        +apply(I input) O
    }
    class ChannelMapper~T,R~ {
        <<interface>>
        +map(T value) R
    }
    class UpdateValidator~U~ {
        <<interface>>
        +validate(List~U~ updates) boolean
    }
    
    %% Channel 体系
    class Channel~V,U,C~ {
        <<interface>>
    }
    class BaseChannel~V,U,C~ {
        <<abstract>>
    }
    class LastValueChannel~V~
    class TopicChannel~V~
    class BinaryOperatorChannel~V~
    class EphemeralChannel~V~
    class ContextChannel~V~
    
    Channel <|.. BaseChannel
    BaseChannel <|-- LastValueChannel
    BaseChannel <|-- TopicChannel
    BaseChannel <|-- BinaryOperatorChannel
    BaseChannel <|-- EphemeralChannel
    BaseChannel <|-- ContextChannel
    
    %% Node 体系
    class Node~I,O~ {
        <<interface>>
    }
    class AbstractNode~I,O~ {
        <<abstract>>
    }
    class FunctionalNode~I,O~
    class CompositeNode~I,O~
    class ConditionalNode~I,O~
    class RetryNode~I,O~
    
    Node <|.. AbstractNode
    AbstractNode <|-- FunctionalNode
    AbstractNode <|-- CompositeNode
    AbstractNode <|-- ConditionalNode
    AbstractNode <|-- RetryNode
    
    %% Checkpointer 体系
    class Checkpointer {
        <<interface>>
    }
    class AbstractCheckpointer {
        <<abstract>>
    }
    class MemoryCheckpointer
    class FileCheckpointer
    class JdbcCheckpointer
    
    Checkpointer <|.. AbstractCheckpointer
    AbstractCheckpointer <|-- MemoryCheckpointer
    AbstractCheckpointer <|-- FileCheckpointer
    AbstractCheckpointer <|-- JdbcCheckpointer
```

## 4. Channel 详细类图

```mermaid
classDiagram
    class Channel~V,U,C~ {
        <<interface>>
        +getName() String
        +getValueType() Class~V~
        +getUpdateType() Class~U~
        +update(List~U~ values) boolean
        +get() V
        +checkpoint() C
        +fromCheckpoint(C checkpoint) Channel
        +consume() boolean
        +finish() boolean
        +copy() Channel
        +isEmpty() boolean
    }
    
    class BaseChannel~V,U,C~ {
        <<abstract>>
        #name: String
        #valueType: Class~V~
        #updateType: Class~U~
        #updated: boolean
        +getName() String
        +getValueType() Class~V~
        +getUpdateType() Class~U~
        +isUpdated() boolean
        #markUpdated() void
        #resetUpdated() void
        #validateUpdate(List~U~) void
    }
    
    class LastValueChannel~V~ {
        -value: V
        -hasValue: boolean
        +LastValueChannel(Class~V~ type)
        +update(List~V~ values) boolean
        +get() V
        +checkpoint() V
        +fromCheckpoint(V) LastValueChannel~V~
        +copy() LastValueChannel~V~
        +isEmpty() boolean
    }
    
    class TopicChannel~V~ {
        -values: List~V~
        -accumulate: boolean
        -unique: boolean
        -seen: Set~V~
        +TopicChannel(Class~V~ type)
        +TopicChannel(Class~V~ type, boolean accumulate, boolean unique)
        +update(List~V~ values) boolean
        +get() List~V~
        +checkpoint() List~V~
        +fromCheckpoint(List~V~) TopicChannel~V~
        +copy() TopicChannel~V~
        +clear() void
    }
    
    class BinaryOperatorChannel~V~ {
        -value: V
        -operator: BinaryOperator~V~
        -identity: V
        -initialized: boolean
        +BinaryOperatorChannel(Class~V~, BinaryOperator~V~, V identity)
        +update(List~V~ values) boolean
        +get() V
        +checkpoint() V
        +fromCheckpoint(V) BinaryOperatorChannel~V~
        +reset() void
    }
    
    class EphemeralChannel~V~ {
        -value: V
        -consumed: boolean
        +EphemeralChannel(Class~V~ type)
        +update(List~V~ values) boolean
        +get() V
        +consume() boolean
        +isConsumed() boolean
        +checkpoint() void
    }
    
    class ChannelConfig {
        <<record>>
        +name: String
        +channelType: Class~Channel~
        +valueType: Class~?~
        +options: Map~String,Object~
    }
    
    class ChannelManager {
        -channels: ConcurrentMap~String, Channel~
        -updatedChannels: Set~String~
        +register(String, Channel) void
        +get(String) Channel
        +getOrCreate(String, Supplier~Channel~) Channel
        +update(String, List~?~) boolean
        +batchUpdate(Map~String, List~) Set~String~
        +getUpdatedChannels() Set~String~
        +clearUpdatedFlags() void
        +checkpoint() Map~String, Object~
        +restore(Map~String, Object~) void
        +getAll() Map~String, Channel~
    }
    
    Channel <|.. BaseChannel
    BaseChannel <|-- LastValueChannel
    BaseChannel <|-- TopicChannel
    BaseChannel <|-- BinaryOperatorChannel
    BaseChannel <|-- EphemeralChannel
    ChannelManager o-- "*" Channel
    ChannelManager ..> ChannelConfig
```

## 5. Node 详细类图

```mermaid
classDiagram
    class Node~I,O~ {
        <<interface>>
        +getName() String
        +getSubscribedChannels() Set~String~
        +getReadChannels() Set~String~
        +getWriteTargets() Map~String, Function~
        +invoke(I input) O
        +invokeAsync(I input) CompletableFuture~O~
        +getMetadata() NodeMetadata
    }
    
    class NodeMetadata {
        <<record>>
        +name: String
        +description: String
        +tags: Set~String~
        +retryPolicy: RetryPolicy
        +timeout: Duration
    }
    
    class RetryPolicy {
        <<record>>
        +maxAttempts: int
        +backoffMillis: long
        +retryableExceptions: Set~Class~
        +static none() RetryPolicy
        +static fixed(int attempts, long backoff) RetryPolicy
        +static exponential(int attempts, long initial) RetryPolicy
    }
    
    class NodeBuilder~I,O~ {
        -name: String
        -subscribedChannels: LinkedHashSet~String~
        -readChannels: LinkedHashSet~String~
        -writeTargets: LinkedHashMap~String, Function~
        -processor: Function~I, O~
        -metadata: NodeMetadata.Builder
        +static create(String name) NodeBuilder
        +subscribeOnly(String channel) NodeBuilder
        +subscribeTo(String... channels) NodeBuilder
        +alsoRead(String... channels) NodeBuilder
        +process(Function~I,O~ func) NodeBuilder
        +writeTo(String channel) NodeBuilder
        +writeTo(String channel, Function~O,?~ mapper) NodeBuilder
        +writeToConditional(String channel, Predicate~O~, Function~O,?~) NodeBuilder
        +withRetry(RetryPolicy policy) NodeBuilder
        +withTimeout(Duration timeout) NodeBuilder
        +withDescription(String desc) NodeBuilder
        +withTags(String... tags) NodeBuilder
        +build() Node~I, O~
    }
    
    class FunctionalNode~I,O~ {
        -name: String
        -subscribedChannels: Set~String~
        -readChannels: Set~String~
        -writeTargets: Map~String, Function~
        -processor: Function~I, O~
        -metadata: NodeMetadata
        -executor: ExecutorService
        +invoke(I input) O
        +invokeAsync(I input) CompletableFuture~O~
        -processWithRetry(I input) O
    }
    
    class CompositeNode~I,O~ {
        -name: String
        -stages: List~Node~
        +addStage(Node stage) CompositeNode
        +invoke(I input) O
        +getStages() List~Node~
    }
    
    class ConditionalNode~I,O~ {
        -name: String
        -condition: Predicate~I~
        -trueNode: Node~I, O~
        -falseNode: Node~I, O~
        +invoke(I input) O
    }
    
    class NodeRegistry {
        -nodes: ConcurrentMap~String, Node~
        -subscriptionIndex: Map~String, Set~String~~
        +register(Node node) void
        +register(String name, Node node) void
        +get(String name) Optional~Node~
        +getBySubscription(String channel) Set~Node~
        +getBySubscriptions(Set~String~ channels) Set~Node~
        +getAll() Collection~Node~
        +remove(String name) boolean
        +contains(String name) boolean
        +clear() void
        -updateIndex(Node node) void
    }
    
    Node <|.. FunctionalNode
    Node <|.. CompositeNode
    Node <|.. ConditionalNode
    NodeBuilder ..> FunctionalNode : creates
    NodeRegistry o-- "*" Node
    Node --> NodeMetadata
    NodeMetadata --> RetryPolicy
```

## 6. Pregel 执行引擎详细类图

```mermaid
classDiagram
    class PregelGraph~I,O~ {
        <<interface>>
        +invoke(I input) O
        +invoke(I input, RuntimeConfig config) O
        +invokeAsync(I input) CompletableFuture~O~
        +stream(I input) Stream~ExecutionStep~
        +streamAsync(I input) Flux~ExecutionStep~
        +resumeFrom(String threadId, String checkpointId) O
        +getGraph() Graph~I, O~
        +getConfig() PregelConfig
    }
    
    class Pregel~I,O~ {
        -graph: Graph~I, O~
        -config: PregelConfig
        -executor: PregelExecutor
        -checkpointer: Checkpointer
        -eventListeners: List~ExecutionListener~
        +static builder() PregelBuilder
        +invoke(I input) O
        +invokeAsync(I input) CompletableFuture~O~
        +stream(I input) Stream~ExecutionStep~
        +addEventListener(ExecutionListener listener) void
        +removeEventListener(ExecutionListener listener) void
        -createContext(I input) ExecutionContext
        -extractOutput(ExecutionContext ctx) O
    }
    
    class PregelBuilder~I,O~ {
        -chains: Map~String, Node~
        -channels: Map~String, Channel~
        -inputChannels: List~String~
        -outputChannels: List~String~
        -config: PregelConfig.Builder
        -checkpointer: Checkpointer
        +addChain(String name, Node node) PregelBuilder
        +addChannel(String name, Channel channel) PregelBuilder
        +input(String... channels) PregelBuilder
        +output(String... channels) PregelBuilder
        +maxSteps(int steps) PregelBuilder
        +timeout(Duration timeout) PregelBuilder
        +debug(boolean debug) PregelBuilder
        +checkpointer(Checkpointer cp) PregelBuilder
        +build() Pregel~I, O~
    }
    
    class PregelConfig {
        <<record>>
        +inputChannels: List~String~
        +outputChannels: List~String~
        +maxSteps: int
        +timeout: Duration
        +debug: boolean
        +threadPoolSize: int
        +checkpointEnabled: boolean
        +static builder() Builder
        +static defaults() PregelConfig
    }
    
    class RuntimeConfig {
        <<record>>
        +threadId: String
        +checkpointId: String
        +tags: Map~String, String~
        +timeout: Duration
        +static defaults() RuntimeConfig
    }
    
    class PregelExecutor {
        -planningService: PlanningService
        -executionService: ExecutionService
        -updateService: UpdateService
        -checkpointer: Checkpointer
        -threadPool: ExecutorService
        +execute(ExecutionContext ctx) ExecutionResult
        +executeAsync(ExecutionContext ctx) CompletableFuture~ExecutionResult~
        +executeStep(ExecutionContext ctx) StepResult
        -shouldTerminate(ExecutionContext ctx) boolean
    }
    
    class ExecutionContext {
        -threadId: String
        -stepNumber: int
        -channelManager: ChannelManager
        -nodeRegistry: NodeRegistry
        -updatedChannels: Set~String~
        -config: PregelConfig
        -startTime: Instant
        -stepHistory: List~ExecutionStep~
        +nextStep() ExecutionContext
        +isTerminated() boolean
        +isTimedOut() boolean
        +getElapsedTime() Duration
        +recordStep(ExecutionStep step) void
    }
    
    class ExecutionStep {
        <<record>>
        +stepNumber: int
        +executedNodes: List~String~
        +updatedChannels: Set~String~
        +channelSnapshots: Map~String, Object~
        +startTime: Instant
        +endTime: Instant
        +duration() Duration
    }
    
    class ExecutionResult {
        <<record>>
        +success: boolean
        +output: Object
        +steps: List~ExecutionStep~
        +totalSteps: int
        +totalDuration: Duration
        +error: Throwable
        +static success(Object output, List~ExecutionStep~ steps) ExecutionResult
        +static failure(Throwable error, List~ExecutionStep~ steps) ExecutionResult
    }
    
    class PlanningService {
        +plan(ExecutionContext ctx) List~Node~
        -findInputSubscribers(ExecutionContext ctx) List~Node~
        -findChannelSubscribers(ExecutionContext ctx) List~Node~
    }
    
    class ExecutionService {
        -threadPool: ExecutorService
        -timeout: Duration
        +executeNodes(List~Node~ nodes, Map~String,Object~ inputs) Map~String, NodeResult~
        +executeNodesAsync(List~Node~, Map~String,Object~) CompletableFuture~Map~
        -invokeNode(Node node, Object input) NodeResult
    }
    
    class UpdateService {
        +collectWrites(Map~String, NodeResult~ results) Map~String, List~Object~~
        +applyUpdates(ChannelManager manager, Map~String, List~Object~~ updates) Set~String~
        -filterNullWrites(Map~String, List~Object~~ writes) Map~String, List~Object~~
    }
    
    class NodeResult {
        <<record>>
        +nodeName: String
        +output: Object
        +writes: Map~String, Object~
        +success: boolean
        +error: Throwable
        +duration: Duration
    }
    
    PregelGraph <|.. Pregel
    Pregel *-- PregelConfig
    Pregel *-- PregelExecutor
    Pregel o-- Checkpointer
    PregelBuilder ..> Pregel : creates
    PregelExecutor *-- PlanningService
    PregelExecutor *-- ExecutionService
    PregelExecutor *-- UpdateService
    PregelExecutor ..> ExecutionContext
    PregelExecutor ..> ExecutionResult
    ExecutionService ..> NodeResult
    ExecutionContext o-- ExecutionStep
```

## 7. Graph 构建器详细类图

```mermaid
classDiagram
    class Graph~I,O~ {
        -name: String
        -nodeRegistry: NodeRegistry
        -channelManager: ChannelManager
        -inputChannels: List~String~
        -outputChannels: List~String~
        -metadata: GraphMetadata
        +getName() String
        +getNodes() Collection~Node~
        +getChannels() Map~String, Channel~
        +getInputChannels() List~String~
        +getOutputChannels() List~String~
        +compile() Pregel~I, O~
        +compile(PregelConfig config) Pregel~I, O~
        +validate() ValidationResult
        +visualize() String
    }
    
    class GraphBuilder~I,O~ {
        -name: String
        -nodes: LinkedHashMap~String, Node~
        -channels: LinkedHashMap~String, Channel~
        -inputChannels: List~String~
        -outputChannels: List~String~
        -autoCreateChannels: boolean
        +static create() GraphBuilder
        +name(String name) GraphBuilder
        +addNode(String name, Node node) GraphBuilder
        +addNode(String name, Function~?,?~ func) GraphBuilder
        +addChannel(String name, Channel channel) GraphBuilder
        +addLastValueChannel(String name, Class type) GraphBuilder
        +addTopicChannel(String name, Class type) GraphBuilder
        +setInput(String... channels) GraphBuilder
        +setOutput(String... channels) GraphBuilder
        +autoCreateChannels(boolean auto) GraphBuilder
        +build() Graph~I, O~
        -validateAndCreateChannels() void
    }
    
    class GraphMetadata {
        <<record>>
        +name: String
        +description: String
        +version: String
        +createdAt: Instant
        +tags: Set~String~
    }
    
    class GraphValidator {
        +validate(Graph graph) ValidationResult
        -checkOrphanNodes(Graph graph) List~ValidationIssue~
        -checkUnreachableOutputs(Graph graph) List~ValidationIssue~
        -checkMissingChannels(Graph graph) List~ValidationIssue~
        -checkCyclicDependencies(Graph graph) List~ValidationIssue~
        -checkTypeCompatibility(Graph graph) List~ValidationIssue~
    }
    
    class ValidationResult {
        <<record>>
        +valid: boolean
        +issues: List~ValidationIssue~
        +getErrors() List~ValidationIssue~
        +getWarnings() List~ValidationIssue~
        +static valid() ValidationResult
        +static invalid(List~ValidationIssue~ issues) ValidationResult
    }
    
    class ValidationIssue {
        <<record>>
        +level: Level
        +code: String
        +message: String
        +location: String
    }
    
    class Level {
        <<enumeration>>
        ERROR
        WARNING
        INFO
    }
    
    class GraphVisualizer {
        +toMermaid(Graph graph) String
        +toDot(Graph graph) String
        +toAscii(Graph graph) String
        -renderNodes(Graph graph, StringBuilder sb) void
        -renderEdges(Graph graph, StringBuilder sb) void
    }
    
    GraphBuilder ..> Graph : creates
    Graph --> GraphMetadata
    Graph ..> Pregel : compiles to
    GraphValidator ..> ValidationResult
    ValidationResult *-- ValidationIssue
    ValidationIssue --> Level
    GraphVisualizer ..> Graph
```

## 8. Checkpoint 详细类图

```mermaid
classDiagram
    class Checkpointer {
        <<interface>>
        +save(CheckpointData data) String
        +load(String checkpointId) Optional~CheckpointData~
        +loadByThread(String threadId) Optional~CheckpointData~
        +loadLatest(String threadId) Optional~CheckpointData~
        +list(String threadId) List~CheckpointMetadata~
        +list(String threadId, int limit) List~CheckpointMetadata~
        +delete(String checkpointId) boolean
        +deleteByThread(String threadId) int
        +exists(String checkpointId) boolean
    }
    
    class CheckpointData {
        <<record>>
        +checkpointId: String
        +threadId: String
        +stepNumber: int
        +channelStates: Map~String, byte[]~
        +nodeStates: Map~String, byte[]~
        +metadata: CheckpointMetadata
        +createdAt: Instant
        +toBytes(Serializer ser) byte[]
        +static fromBytes(byte[] data, Serializer ser) CheckpointData
    }
    
    class CheckpointMetadata {
        <<record>>
        +source: String
        +stepNumber: int
        +executedNodes: List~String~
        +parentCheckpointId: String
        +tags: Map~String, String~
    }
    
    class CheckpointConfig {
        <<record>>
        +enabled: boolean
        +interval: Duration
        +maxCheckpoints: int
        +encryption: boolean
        +compressionEnabled: boolean
    }
    
    class AbstractCheckpointer {
        <<abstract>>
        #serializer: Serializer
        #config: CheckpointConfig
        +save(CheckpointData data) String
        +load(String checkpointId) Optional~CheckpointData~
        #generateId() String
        #serialize(CheckpointData data) byte[]
        #deserialize(byte[] data) CheckpointData
        #doSave(String id, byte[] data)* void
        #doLoad(String id)* Optional~byte[]~
    }
    
    class MemoryCheckpointer {
        -storage: ConcurrentMap~String, byte[]~
        -threadIndex: ConcurrentMap~String, List~String~~
        #doSave(String id, byte[] data) void
        #doLoad(String id) Optional~byte[]~
        +clear() void
        +size() int
    }
    
    class FileCheckpointer {
        -basePath: Path
        -fileExtension: String
        #doSave(String id, byte[] data) void
        #doLoad(String id) Optional~byte[]~
        -getFilePath(String id) Path
        -ensureDirectory() void
    }
    
    class JdbcCheckpointer {
        -dataSource: DataSource
        -tableName: String
        #doSave(String id, byte[] data) void
        #doLoad(String id) Optional~byte[]~
        +migrate() void
        -createTableIfNotExists() void
    }
    
    class Serializer {
        <<interface>>
        +serialize(Object obj) byte[]
        +deserialize(byte[] data, Class~T~ type) T
        +serializeToString(Object obj) String
        +deserializeFromString(String data, Class~T~ type) T
    }
    
    class JsonSerializer {
        -objectMapper: ObjectMapper
        +serialize(Object obj) byte[]
        +deserialize(byte[] data, Class~T~ type) T
        +static create() JsonSerializer
        +static createPretty() JsonSerializer
    }
    
    class MsgPackSerializer {
        -messagePack: MessagePack
        +serialize(Object obj) byte[]
        +deserialize(byte[] data, Class~T~ type) T
    }
    
    Checkpointer <|.. AbstractCheckpointer
    AbstractCheckpointer <|-- MemoryCheckpointer
    AbstractCheckpointer <|-- FileCheckpointer
    AbstractCheckpointer <|-- JdbcCheckpointer
    AbstractCheckpointer o-- Serializer
    AbstractCheckpointer o-- CheckpointConfig
    Serializer <|.. JsonSerializer
    Serializer <|.. MsgPackSerializer
    Checkpointer ..> CheckpointData
    CheckpointData *-- CheckpointMetadata
```

## 9. 数据流图

```mermaid
flowchart LR
    subgraph Input["输入层"]
        I1[用户输入]
    end
    
    subgraph Channels["通道层"]
        IC[Input Channel]
        C1[Channel A]
        C2[Channel B]
        C3[Channel C]
        OC[Output Channel]
    end
    
    subgraph Nodes["节点层"]
        N1[Node 1]
        N2[Node 2]
        N3[Node 3]
    end
    
    subgraph Output["输出层"]
        O1[执行结果]
    end
    
    I1 -->|write| IC
    IC -->|subscribe| N1
    IC -->|subscribe| N2
    N1 -->|write| C1
    N2 -->|write| C2
    C1 -->|subscribe| N3
    C2 -->|subscribe| N3
    N3 -->|write| OC
    OC -->|read| O1
    
    style IC fill:#e1f5fe
    style OC fill:#e8f5e9
    style C1 fill:#fff3e0
    style C2 fill:#fff3e0
    style C3 fill:#fff3e0
```

## 10. 并发执行模型

```mermaid
sequenceDiagram
    participant Main as Main Thread
    participant Pool as Thread Pool
    participant N1 as Node1 Task
    participant N2 as Node2 Task
    participant N3 as Node3 Task
    participant Barrier as Sync Barrier
    
    Main->>Pool: submit(node1, node2, node3)
    
    par 并行执行
        Pool->>N1: execute
        N1->>N1: process input
        N1-->>Pool: result1
    and
        Pool->>N2: execute
        N2->>N2: process input
        N2-->>Pool: result2
    and
        Pool->>N3: execute
        N3->>N3: process input
        N3-->>Pool: result3
    end
    
    Pool->>Barrier: await all
    Barrier-->>Main: all completed
    
    Main->>Main: collect results
    Main->>Main: update channels
    
    Note over Main,Barrier: BSP 同步点<br/>所有节点完成后<br/>才更新通道
```

## 11. 组件交互时序图

```mermaid
sequenceDiagram
    participant App as Application
    participant GB as GraphBuilder
    participant G as Graph
    participant P as Pregel
    participant PE as PregelExecutor
    participant CM as ChannelManager
    participant NR as NodeRegistry
    participant CP as Checkpointer
    
    App->>GB: create()
    App->>GB: addNode("n1", node1)
    App->>GB: addNode("n2", node2)
    App->>GB: setInput("input")
    App->>GB: setOutput("output")
    App->>GB: build()
    GB->>G: new Graph(...)
    GB-->>App: graph
    
    App->>G: compile(config)
    G->>P: new Pregel(graph, config)
    P->>PE: new PregelExecutor(...)
    P->>CM: new ChannelManager(channels)
    P->>NR: new NodeRegistry(nodes)
    G-->>App: pregel
    
    App->>P: invoke(input)
    P->>CM: update("input", [input])
    P->>PE: execute(context)
    
    loop until terminated
        PE->>NR: getBySubscriptions(updatedChannels)
        NR-->>PE: nodesToRun
        PE->>PE: executeNodes(nodesToRun)
        PE->>CM: batchUpdate(results)
        CM-->>PE: newUpdatedChannels
        
        opt checkpoint enabled
            PE->>CP: save(checkpoint)
        end
    end
    
    PE-->>P: ExecutionResult
    P->>CM: get("output")
    CM-->>P: outputValue
    P-->>App: output
```