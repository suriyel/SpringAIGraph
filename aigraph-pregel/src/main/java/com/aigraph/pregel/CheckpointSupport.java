package com.aigraph.pregel;

import com.aigraph.channels.Channel;
import com.aigraph.channels.ChannelManager;
import com.aigraph.checkpoint.CheckpointData;
import com.aigraph.checkpoint.CheckpointMetadata;
import com.aigraph.checkpoint.Checkpointer;
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
                    // Serialize channel checkpoint (implementation depends on serializer)
                    // For now, we'll store a placeholder
                    channelStates.put(name, new byte[0]);
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
    public static MessageContext restoreFromCheckpoint(CheckpointData checkpointData,
                                                       ChannelManager channelManager) {
        log.info("Restoring from checkpoint {} at step {}",
            checkpointData.checkpointId(), checkpointData.stepNumber());

        // Restore channel states
        // (Implementation depends on channel serialization)

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
}
