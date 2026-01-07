package com.aigraph.checkpoint;

/**
 * Interface for object serialization.
 */
public interface Serializer {

    byte[] serialize(Object obj);

    <T> T deserialize(byte[] data, Class<T> type);

    String serializeToString(Object obj);

    <T> T deserializeFromString(String data, Class<T> type);
}
