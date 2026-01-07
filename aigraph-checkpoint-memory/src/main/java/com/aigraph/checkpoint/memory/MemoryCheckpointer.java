package com.aigraph.checkpoint.memory;

import com.aigraph.checkpoint.CheckpointData;
import com.aigraph.checkpoint.CheckpointMetadata;
import com.aigraph.checkpoint.Checkpointer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory checkpoint storage.
 */
public class MemoryCheckpointer implements Checkpointer {

    private final ConcurrentHashMap<String, CheckpointData> storage;
    private final ConcurrentHashMap<String, List<String>> threadIndex;

    public MemoryCheckpointer() {
        this.storage = new ConcurrentHashMap<>();
        this.threadIndex = new ConcurrentHashMap<>();
    }

    @Override
    public String save(String threadId, CheckpointData data) {
        String checkpointId = data.checkpointId();
        storage.put(checkpointId, data);
        threadIndex.computeIfAbsent(threadId, k -> new ArrayList<>()).add(checkpointId);
        return checkpointId;
    }

    @Override
    public Optional<CheckpointData> load(String checkpointId) {
        return Optional.ofNullable(storage.get(checkpointId));
    }

    @Override
    public Optional<CheckpointData> loadByThread(String threadId) {
        return loadLatest(threadId);
    }

    @Override
    public Optional<CheckpointData> loadLatest(String threadId) {
        List<String> checkpoints = threadIndex.get(threadId);
        if (checkpoints == null || checkpoints.isEmpty()) {
            return Optional.empty();
        }
        String latest = checkpoints.get(checkpoints.size() - 1);
        return Optional.ofNullable(storage.get(latest));
    }

    @Override
    public List<CheckpointMetadata> list(String threadId) {
        return list(threadId, Integer.MAX_VALUE);
    }

    @Override
    public List<CheckpointMetadata> list(String threadId, int limit) {
        List<String> checkpoints = threadIndex.getOrDefault(threadId, List.of());
        return checkpoints.stream()
            .limit(limit)
            .map(storage::get)
            .filter(Objects::nonNull)
            .map(CheckpointData::metadata)
            .collect(Collectors.toList());
    }

    @Override
    public boolean delete(String checkpointId) {
        CheckpointData removed = storage.remove(checkpointId);
        if (removed != null) {
            List<String> checkpoints = threadIndex.get(removed.threadId());
            if (checkpoints != null) {
                checkpoints.remove(checkpointId);
            }
            return true;
        }
        return false;
    }

    @Override
    public int deleteByThread(String threadId) {
        List<String> checkpoints = threadIndex.remove(threadId);
        if (checkpoints != null) {
            checkpoints.forEach(storage::remove);
            return checkpoints.size();
        }
        return 0;
    }

    @Override
    public boolean exists(String checkpointId) {
        return storage.containsKey(checkpointId);
    }

    public void clear() {
        storage.clear();
        threadIndex.clear();
    }

    public int size() {
        return storage.size();
    }
}
