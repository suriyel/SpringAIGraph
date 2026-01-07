# AiGraph Checkpoint JDBC

JDBC-based checkpoint implementation for AiGraph, providing persistent storage for graph execution checkpoints.

## Features

- **Database Support**: PostgreSQL, MySQL, H2, and other JDBC-compatible databases
- **Spring JDBC Integration**: Built on Spring's JdbcTemplate for reliable database operations
- **JSON Serialization**: Uses Jackson for serializing complex data structures
- **Base64 Encoding**: Binary data (byte arrays) stored as Base64-encoded strings
- **Metadata Indexing**: Optimized indexes for efficient queries
- **Connection Pooling**: Compatible with HikariCP and other connection pools

## Installation

Add to your Maven `pom.xml`:

```xml
<dependency>
    <groupId>com.aigraph</groupId>
    <artifactId>aigraph-checkpoint-jdbc</artifactId>
    <version>0.0.8</version>
</dependency>
```

You'll also need a JDBC driver for your database:

```xml
<!-- For PostgreSQL -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.1</version>
</dependency>

<!-- For MySQL -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.3.0</version>
</dependency>

<!-- For H2 (testing/development) -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.2.224</version>
</dependency>
```

## Database Setup

### Schema Creation

SQL schema files are provided for different databases:

- `schema-h2.sql` - H2 Database
- `schema-postgresql.sql` - PostgreSQL
- `schema-mysql.sql` - MySQL

Run the appropriate schema file to create the `aigraph_checkpoints` table:

**PostgreSQL:**
```bash
psql -U username -d database -f src/main/resources/schema-postgresql.sql
```

**MySQL:**
```bash
mysql -u username -p database < src/main/resources/schema-mysql.sql
```

**H2 (embedded):**
```java
// Schema is automatically created by JdbcTemplate
jdbcTemplate.execute(Files.readString(Path.of("schema-h2.sql")));
```

### Table Structure

The `aigraph_checkpoints` table stores:

| Column | Type | Description |
|--------|------|-------------|
| checkpoint_id | VARCHAR(255) | Primary key, unique checkpoint identifier |
| thread_id | VARCHAR(255) | Thread/conversation identifier |
| step_number | INT | Execution step number |
| channel_states | TEXT/CLOB | JSON-serialized channel states (Base64-encoded) |
| node_states | TEXT/CLOB | JSON-serialized node states (Base64-encoded) |
| metadata_source | VARCHAR(255) | Source of the checkpoint |
| metadata_step_number | INT | Metadata step number |
| metadata_executed_nodes | TEXT/CLOB | JSON array of executed node names |
| metadata_parent_checkpoint_id | VARCHAR(255) | Parent checkpoint reference |
| metadata_tags | TEXT/CLOB | JSON object of custom tags |
| created_at | TIMESTAMP | Creation timestamp |

**Indexes:**
- `idx_thread_id`: Fast lookup by thread ID
- `idx_thread_step`: Fast lookup by thread and step number
- `idx_created_at`: Fast chronological queries

## Usage

### Basic Usage

```java
import com.aigraph.checkpoint.jdbc.JdbcCheckpointer;
import com.zaxxer.hikari.HikariDataSource;

// Create DataSource
HikariDataSource dataSource = new HikariDataSource();
dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
dataSource.setUsername("username");
dataSource.setPassword("password");

// Create checkpointer
JdbcCheckpointer checkpointer = new JdbcCheckpointer(dataSource);

// Save checkpoint
CheckpointData data = new CheckpointData(
    "cp-123",
    "thread-1",
    5,
    Map.of("channel1", someBytes),
    Map.of("node1", moreBytes),
    new CheckpointMetadata("source", 5, List.of("node1", "node2"), null, Map.of()),
    Instant.now()
);
checkpointer.save("thread-1", data);

// Load checkpoint
Optional<CheckpointData> loaded = checkpointer.load("cp-123");

// Load latest for thread
Optional<CheckpointData> latest = checkpointer.loadLatest("thread-1");

// List checkpoints
List<CheckpointMetadata> history = checkpointer.list("thread-1", 10);

// Delete checkpoint
checkpointer.delete("cp-123");
```

### Custom Table Name

```java
JdbcCheckpointer checkpointer = new JdbcCheckpointer(dataSource, "my_checkpoints");
```

### Custom ObjectMapper

```java
ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new JavaTimeModule());
mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

JdbcCheckpointer checkpointer = new JdbcCheckpointer(dataSource, "aigraph_checkpoints", mapper);
```

### Spring Boot Integration

```java
@Configuration
public class CheckpointConfig {

    @Bean
    public Checkpointer checkpointer(DataSource dataSource) {
        return new JdbcCheckpointer(dataSource);
    }
}
```

With `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: username
    password: password
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
```

### Using with PregelGraph

```java
// Configure Pregel with JDBC checkpointer
PregelConfig config = PregelConfig.builder()
    .inputChannels(List.of("input"))
    .outputChannels(List.of("output"))
    .checkpointer(checkpointer)
    .enableCheckpointing(true)
    .build();

Pregel<String, String> pregel = graph.compile(config);

// Execute with checkpoint
ExecutionResult result = pregel.invoke("input data",
    RuntimeConfig.builder()
        .threadId("user-session-123")
        .build());
```

## Performance Considerations

### Connection Pooling

Always use a connection pool like HikariCP:

```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
config.setMaximumPoolSize(20);
config.setMinimumIdle(5);
config.setConnectionTimeout(30000);
config.setIdleTimeout(600000);
config.setMaxLifetime(1800000);

HikariDataSource dataSource = new HikariDataSource(config);
```

### Batch Operations

For multiple checkpoints, consider batch operations:

```java
List<CheckpointData> checkpoints = ...;
for (CheckpointData cp : checkpoints) {
    checkpointer.save(cp.threadId(), cp);
}
```

### Cleanup Strategy

Implement periodic cleanup of old checkpoints:

```java
@Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
public void cleanupOldCheckpoints() {
    // Delete checkpoints older than 30 days
    jdbcTemplate.update(
        "DELETE FROM aigraph_checkpoints WHERE created_at < ?",
        Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS))
    );
}
```

## Error Handling

All methods throw `CheckpointException` (extends `LangGraphException`) on errors:

```java
try {
    checkpointer.save("thread-1", data);
} catch (CheckpointException e) {
    logger.error("Failed to save checkpoint", e);
    // Handle error (retry, fallback, etc.)
}
```

## Testing

Use H2 in-memory database for testing:

```java
@BeforeEach
void setUp() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
    config.setUsername("sa");
    config.setPassword("");

    DataSource dataSource = new HikariDataSource(config);
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    // Create schema
    jdbcTemplate.execute(Files.readString(Path.of("schema-h2.sql")));

    checkpointer = new JdbcCheckpointer(dataSource);
}
```

## Thread Safety

`JdbcCheckpointer` is thread-safe. Spring's `JdbcTemplate` handles connection management and is safe for concurrent use.

## Migration from MemoryCheckpointer

```java
// Old: In-memory
Checkpointer memoryCheckpointer = new MemoryCheckpointer();

// New: JDBC-backed
Checkpointer jdbcCheckpointer = new JdbcCheckpointer(dataSource);

// API remains the same!
```

## License

Same as parent project.
