package com.aigraph.pregel;

import com.aigraph.channels.Channel;
import com.aigraph.channels.ChannelManager;
import com.aigraph.checkpoint.CheckpointData;
import com.aigraph.checkpoint.CheckpointMetadata;
import com.aigraph.checkpoint.Checkpointer;
import com.aigraph.checkpoint.JsonSerializer;
import com.aigraph.checkpoint.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Utility class for checkpoint save/resume operations.
 * <p>
 * Handles the serialization and restoration of execution state including:
 * <ul>
 *   <li>Channel states</li>
 *   <li>Message context</li>
 *   <li>Execution metadata</li>
 * </ul>
 *
 * @author AIGraph Team
 * @since 0.0.9
 */
public class CheckpointSupport {

    private static final Logger log = LoggerFactory.getLogger(CheckpointSupport.class);
    private static final Serializer serializer = JsonSerializer.create();

    /**
     * Creates a checkpoint from current execution context.
     *
     * @param context       the execution context
     * @param channelManager the channel manager
     * @return checkpoint data
     */
    public static CheckpointData createCheckpoint(ExecutionContext context, ChannelManager channelManager) {
        String checkpointId = UUID.randomUUID().toString();
        String threadId = context.getThreadId();
        int stepNumber = context.getStepNumber();

        log.debug("Creating checkpoint {} for thread {} at step {}", checkpointId, threadId, stepNumber);

        // Serialize channel states
        Map<String, byte[]> channelStates = new HashMap<>();
        channelManager.getAll().forEach((name, channel) -> {
            try {
                Object checkpoint = channel.checkpoint();
                if (checkpoint != null) {
                    // Wrap checkpoint with type information for deserialization
                    ChannelCheckpoint wrapper = new ChannelCheckpoint(
                        channel.getClass().getName(),
                        channel.getValueType().getName(),
                        checkpoint
                    );
                    byte[] serialized = serializer.serialize(wrapper);
                    channelStates.put(name, serialized);
                    log.trace("Serialized channel {} ({} bytes)", name, serialized.length);
                }
            } catch (Exception e) {
                log.warn("Failed to checkpoint channel {}: {}", name, e.getMessage());
            }
        });

        // Serialize MessageContext
        Map<String, byte[]> nodeStates = new HashMap<>();
        try {
            MessageContextSerializer.addToCheckpoint(nodeStates, context.getMessageContext());
        } catch (Exception e) {
            log.error("Failed to serialize MessageContext", e);
        }

        // Create metadata
        List<String> executedNodes = context.getStepHistory().stream()
            .flatMap(step -> step.executedNodes().stream())
            .distinct()
            .toList();

        CheckpointMetadata metadata = new CheckpointMetadata(
            "pregel-execution",
            stepNumber,
            executedNodes,
            null,
            Map.of("updatedChannels", context.getUpdatedChannels().toString())
        );

        return new CheckpointData(
            checkpointId,
            threadId,
            stepNumber,
            channelStates,
            nodeStates,
            metadata,
            Instant.now()
        );
    }

    /**
     * Saves a checkpoint using the provided checkpointer.
     *
     * @param checkpointer the checkpointer
     * @param context      the execution context
     * @param channelManager the channel manager
     * @return the checkpoint ID
     */
    public static String saveCheckpoint(Checkpointer checkpointer, ExecutionContext context,
                                       ChannelManager channelManager) {
        CheckpointData checkpoint = createCheckpoint(context, channelManager);
        String checkpointId = checkpointer.save(checkpoint.threadId(), checkpoint);

        log.info("Saved checkpoint {} for thread {} at step {}",
            checkpointId, checkpoint.threadId(), checkpoint.stepNumber());

        return checkpointId;
    }

    /**
     * Restores execution context from a checkpoint.
     *
     * @param checkpointData the checkpoint data
     * @param channelManager the channel manager to restore into
     * @return the restored message context
     */
    @SuppressWarnings("unchecked")
    public static MessageContext restoreFromCheckpoint(CheckpointData checkpointData,
                                                       ChannelManager channelManager) {
        log.info("Restoring from checkpoint {} at step {}",
            checkpointData.checkpointId(), checkpointData.stepNumber());

        // Restore channel states
        int restoredCount = 0;
        for (Map.Entry<String, byte[]> entry : checkpointData.channelStates().entrySet()) {
            String channelName = entry.getKey();
            byte[] serialized = entry.getValue();

            try {
                // Deserialize channel checkpoint wrapper
                ChannelCheckpoint wrapper = serializer.deserialize(serialized, ChannelCheckpoint.class);

                // Get the channel from manager
                Channel<?, ?, ?> channel = channelManager.get(channelName);

                // Restore channel from checkpoint
                Channel<?, ?, ?> restored = ((Channel<Object, Object, Object>) channel)
                    .fromCheckpoint(wrapper.checkpointData());

                // Replace channel in manager
                channelManager.remove(channelName);
                channelManager.register(channelName, restored);

                restoredCount++;
                log.trace("Restored channel {}", channelName);
            } catch (Exception e) {
                log.warn("Failed to restore channel {}: {}", channelName, e.getMessage());
            }
        }

        log.info("Restored {} channel states", restoredCount);

        // Restore MessageContext
        MessageContext messageContext = MessageContextSerializer.extractFromCheckpoint(
            checkpointData.nodeStates()
        );

        log.info("Restored MessageContext with {} messages", messageContext.size());

        return messageContext;
    }

    /**
     * Resumes execution from a checkpoint.
     *
     * @param checkpointer    the checkpointer
     * @param threadId        the thread ID
     * @param checkpointId    the checkpoint ID (null for latest)
     * @param channelManager  the channel manager
     * @return the restored execution context
     */
    public static ExecutionContext resumeFromCheckpoint(Checkpointer checkpointer,
                                                       String threadId,
                                                       String checkpointId,
                                                       ChannelManager channelManager,
                                                       com.aigraph.nodes.NodeRegistry nodeRegistry,
                                                       PregelConfig config) {
        log.info("Resuming execution for thread {} from checkpoint {}", threadId, checkpointId);

        // Load checkpoint
        Optional<CheckpointData> checkpointOpt;
        if (checkpointId != null) {
            checkpointOpt = checkpointer.load(checkpointId);
        } else {
            checkpointOpt = checkpointer.loadLatest(threadId);
        }

        if (checkpointOpt.isEmpty()) {
            throw new IllegalStateException("Checkpoint not found: " + checkpointId);
        }

        CheckpointData checkpoint = checkpointOpt.get();

        // Restore state
        MessageContext messageContext = restoreFromCheckpoint(checkpoint, channelManager);

        // Create execution context
        // Start from next step after checkpoint
        Set<String> updatedChannels = new HashSet<>(); // Will be determined by planning

        return new ExecutionContext(
            threadId,
            channelManager,
            nodeRegistry,
            updatedChannels,
            config,
            messageContext
        );
    }

    /**
     * Wrapper for channel checkpoint data with type information.
     * Used for JSON serialization/deserialization.
     */
    public static class ChannelCheckpoint {
        private String channelType;
        private String valueType;
        private Object checkpointData;

        // Default constructor for Jackson
        public ChannelCheckpoint() {
        }

        public ChannelCheckpoint(String channelType, String valueType, Object checkpointData) {
            this.channelType = channelType;
            this.valueType = valueType;
            this.checkpointData = checkpointData;
        }

        public String getChannelType() {
            return channelType;
        }

        public void setChannelType(String channelType) {
            this.channelType = channelType;
        }

        public String getValueType() {
            return valueType;
        }

        public void setValueType(String valueType) {
            this.valueType = valueType;
        }

        public Object getCheckpointData() {
            return checkpointData;
        }

        public void setCheckpointData(Object checkpointData) {
            this.checkpointData = checkpointData;
        }

        // Convenience method
        public Object checkpointData() {
            return checkpointData;
        }
    }
}
