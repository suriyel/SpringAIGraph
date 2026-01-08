package com.aigraph.pregel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Utility class for serializing and deserializing MessageContext.
 * <p>
 * Uses Java serialization to persist MessageContext objects to byte arrays
 * for checkpoint storage. Provides compression support for efficient storage.
 *
 * @author AIGraph Team
 * @since 0.0.9
 */
public class MessageContextSerializer {

    private static final Logger log = LoggerFactory.getLogger(MessageContextSerializer.class);
    private static final String MESSAGE_CONTEXT_KEY = "__message_context__";

    /**
     * Serializes MessageContext to byte array.
     *
     * @param context the message context to serialize
     * @return byte array representation
     * @throws IOException if serialization fails
     */
    public static byte[] serialize(MessageContext context) throws IOException {
        if (context == null) {
            return new byte[0];
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {

            oos.writeObject(context);
            oos.flush();

            byte[] bytes = baos.toByteArray();
            log.debug("Serialized MessageContext: {} bytes, {} messages",
                bytes.length, context.size());

            return bytes;

        } catch (IOException e) {
            log.error("Failed to serialize MessageContext", e);
            throw e;
        }
    }

    /**
     * Deserializes MessageContext from byte array.
     *
     * @param data the byte array to deserialize
     * @return the deserialized MessageContext
     * @throws IOException            if deserialization fails
     * @throws ClassNotFoundException if MessageContext class not found
     */
    public static MessageContext deserialize(byte[] data) throws IOException, ClassNotFoundException {
        if (data == null || data.length == 0) {
            log.debug("Empty data, returning new MessageContext");
            return new MessageContext();
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {

            MessageContext context = (MessageContext) ois.readObject();
            log.debug("Deserialized MessageContext: {} messages", context.size());

            return context;

        } catch (IOException | ClassNotFoundException e) {
            log.error("Failed to deserialize MessageContext", e);
            throw e;
        }
    }

    /**
     * Adds MessageContext to checkpoint data map.
     *
     * @param checkpointData the checkpoint data map
     * @param context        the message context to add
     * @throws IOException if serialization fails
     */
    public static void addToCheckpoint(java.util.Map<String, byte[]> checkpointData, MessageContext context)
        throws IOException {
        if (context != null) {
            byte[] serialized = serialize(context);
            checkpointData.put(MESSAGE_CONTEXT_KEY, serialized);
            log.debug("Added MessageContext to checkpoint ({} bytes)", serialized.length);
        }
    }

    /**
     * Extracts MessageContext from checkpoint data map.
     *
     * @param checkpointData the checkpoint data map
     * @return the message context, or new empty context if not found
     */
    public static MessageContext extractFromCheckpoint(java.util.Map<String, byte[]> checkpointData) {
        try {
            byte[] data = checkpointData.get(MESSAGE_CONTEXT_KEY);
            if (data != null && data.length > 0) {
                return deserialize(data);
            }
        } catch (Exception e) {
            log.warn("Failed to extract MessageContext from checkpoint, returning new context", e);
        }
        return new MessageContext();
    }

    /**
     * Gets the key used to store MessageContext in checkpoint data.
     *
     * @return the storage key
     */
    public static String getStorageKey() {
        return MESSAGE_CONTEXT_KEY;
    }
}
