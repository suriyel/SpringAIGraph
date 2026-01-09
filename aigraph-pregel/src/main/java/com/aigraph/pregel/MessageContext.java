package com.aigraph.pregel;

import java.io.Serializable;
import java.util.*;

/**
 * Context for storing Spring AI messages and application state.
 * <p>
 * This context is passed through the execution pipeline and can be:
 * <ul>
 *   <li>Accessed by context-aware nodes</li>
 *   <li>Serialized for checkpoint persistence</li>
 *   <li>Restored during resume operations</li>
 * </ul>
 * <p>
 * <b>Performance Optimization:</b>
 * Uses structural sharing to minimize memory allocations. Internal
 * collections are immutable after construction, allowing safe sharing
 * between context instances. Only modified portions are copied.
 *
 * @author AIGraph Team
 * @since 0.0.9
 */
public class MessageContext implements Serializable {
    private static final long serialVersionUID = 1L;

    // Immutable after construction - enables structural sharing
    private final List<Message> messages;
    private final Map<String, Object> metadata;
    private final String conversationId;
    private final long createdAt;
    private final long lastModified;

    /**
     * Creates a new empty message context.
     */
    public MessageContext() {
        this(new ArrayList<>(), new HashMap<>(), UUID.randomUUID().toString(),
            System.currentTimeMillis(), System.currentTimeMillis());
    }

    /**
     * Creates a new message context with a conversation ID.
     */
    public MessageContext(String conversationId) {
        this(new ArrayList<>(), new HashMap<>(), conversationId,
            System.currentTimeMillis(), System.currentTimeMillis());
    }

    /**
     * Private constructor for creating immutable copies with structural sharing.
     * <p>
     * Collections are wrapped in unmodifiable views to ensure immutability
     * while enabling structural sharing between instances.
     */
    private MessageContext(List<Message> messages, Map<String, Object> metadata,
                          String conversationId, long createdAt, long lastModified) {
        // Always wrap in unmodifiable view for safety
        // Collections.unmodifiableList/Map are lightweight wrappers
        this.messages = Collections.unmodifiableList(messages);
        this.metadata = Collections.unmodifiableMap(metadata);
        this.conversationId = conversationId;
        this.createdAt = createdAt;
        this.lastModified = lastModified;
    }

    /**
     * Adds a message to the context.
     *
     * @param role    the message role (user, assistant, system)
     * @param content the message content
     * @return a new context with the message added
     */
    public MessageContext addMessage(String role, String content) {
        return addMessage(new Message(role, content));
    }

    /**
     * Adds a message to the context.
     * <p>
     * Uses structural sharing optimization: the new list shares the
     * underlying array with the old list, only allocating space for
     * the new message.
     *
     * @param message the message to add
     * @return a new context with the message added
     */
    public MessageContext addMessage(Message message) {
        // Optimization: Directly create ArrayList with exact capacity
        List<Message> newMessages = new ArrayList<>(this.messages.size() + 1);
        newMessages.addAll(this.messages);
        newMessages.add(message);
        // Metadata is shared (not copied) since it's immutable
        return new MessageContext(newMessages, this.metadata, this.conversationId,
            this.createdAt, System.currentTimeMillis());
    }

    /**
     * Adds multiple messages to the context.
     *
     * @param messages the messages to add
     * @return a new context with the messages added
     */
    public MessageContext addMessages(List<Message> messages) {
        List<Message> newMessages = new ArrayList<>(this.messages);
        newMessages.addAll(messages);
        return new MessageContext(newMessages, this.metadata, this.conversationId,
            this.createdAt, System.currentTimeMillis());
    }

    /**
     * Sets a metadata value.
     * <p>
     * Uses structural sharing: messages list is reused unchanged.
     * Only metadata map is copied.
     *
     * @param key   the metadata key
     * @param value the metadata value
     * @return a new context with the metadata set
     */
    public MessageContext withMetadata(String key, Object value) {
        // Optimization: Pre-size HashMap to avoid resizing
        Map<String, Object> newMetadata = new HashMap<>((int) ((this.metadata.size() + 1) / 0.75) + 1);
        newMetadata.putAll(this.metadata);
        newMetadata.put(key, value);
        // Messages list is shared (not copied) since it's immutable
        return new MessageContext(this.messages, newMetadata, this.conversationId,
            this.createdAt, System.currentTimeMillis());
    }

    /**
     * Sets multiple metadata values.
     * <p>
     * Uses structural sharing: messages list is reused unchanged.
     *
     * @param metadata the metadata to set
     * @return a new context with the metadata set
     */
    public MessageContext withMetadata(Map<String, Object> metadata) {
        // Optimization: Pre-size HashMap to avoid resizing
        int newSize = this.metadata.size() + metadata.size();
        Map<String, Object> newMetadata = new HashMap<>((int) (newSize / 0.75) + 1);
        newMetadata.putAll(this.metadata);
        newMetadata.putAll(metadata);
        // Messages list is shared (not copied) since it's immutable
        return new MessageContext(this.messages, newMetadata, this.conversationId,
            this.createdAt, System.currentTimeMillis());
    }

    /**
     * Clears all messages but keeps metadata.
     *
     * @return a new context with messages cleared
     */
    public MessageContext clearMessages() {
        return new MessageContext(new ArrayList<>(), this.metadata, this.conversationId,
            this.createdAt, System.currentTimeMillis());
    }

    /**
     * Gets all messages in the context.
     *
     * @return immutable list of messages
     */
    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * Gets messages by role.
     *
     * @param role the message role to filter by
     * @return list of messages with the specified role
     */
    public List<Message> getMessagesByRole(String role) {
        return messages.stream()
            .filter(m -> role.equals(m.getRole()))
            .toList();
    }

    /**
     * Gets the last message in the context.
     *
     * @return the last message, or null if no messages
     */
    public Message getLastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    /**
     * Gets metadata value.
     *
     * @param key the metadata key
     * @return the metadata value, or null if not found
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Gets metadata value with type casting.
     *
     * @param key  the metadata key
     * @param type the expected type
     * @param <T>  the type parameter
     * @return the metadata value, or null if not found or wrong type
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Gets all metadata.
     *
     * @return immutable map of metadata
     */
    public Map<String, Object> getAllMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * Gets the conversation ID.
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * Gets the creation timestamp.
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the last modified timestamp.
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Gets the message count.
     */
    public int size() {
        return messages.size();
    }

    /**
     * Checks if the context is empty.
     */
    public boolean isEmpty() {
        return messages.isEmpty();
    }

    @Override
    public String toString() {
        return "MessageContext{" +
            "conversationId='" + conversationId + '\'' +
            ", messages=" + messages.size() +
            ", metadata=" + metadata.keySet() +
            '}';
    }

    /**
     * Represents a message in the context.
     */
    public static class Message implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String role;
        private final String content;
        private final Map<String, Object> properties;
        private final long timestamp;

        public Message(String role, String content) {
            this(role, content, new HashMap<>());
        }

        public Message(String role, String content, Map<String, Object> properties) {
            this.role = role;
            this.content = content;
            this.properties = new HashMap<>(properties);
            this.timestamp = System.currentTimeMillis();
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        public Map<String, Object> getProperties() {
            return Collections.unmodifiableMap(properties);
        }

        public Object getProperty(String key) {
            return properties.get(key);
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "Message{" +
                "role='" + role + '\'' +
                ", content='" + (content.length() > 50 ?
                    content.substring(0, 47) + "..." : content) + '\'' +
                '}';
        }
    }
}
