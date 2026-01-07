package com.aigraph.checkpoint;

import java.util.List;
import java.util.Optional;

/**
 * Interface for checkpoint storage.
 */
public interface Checkpointer {

    String save(String threadId, CheckpointData data);

    Optional<CheckpointData> load(String checkpointId);

    Optional<CheckpointData> loadByThread(String threadId);

    Optional<CheckpointData> loadLatest(String threadId);

    List<CheckpointMetadata> list(String threadId);

    List<CheckpointMetadata> list(String threadId, int limit);

    boolean delete(String checkpointId);

    int deleteByThread(String threadId);

    boolean exists(String checkpointId);
}
