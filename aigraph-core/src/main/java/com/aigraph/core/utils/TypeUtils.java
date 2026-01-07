package com.aigraph.core.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Utility class for working with Java types and generics.
 * <p>
 * Provides helper methods to extract runtime type information
 * from generic classes, which is useful for channel and node
 * type verification.
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public final class TypeUtils {

    private TypeUtils() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Checks if a value is an instance of the specified type.
     *
     * @param value the value to check
     * @param type  the expected type
     * @return true if value is an instance of type
     */
    public static boolean isInstance(Object value, Class<?> type) {
        if (value == null) {
            return !type.isPrimitive();
        }
        return type.isInstance(value);
    }

    /**
     * Validates that a value is an instance of the specified type.
     *
     * @param value     the value to validate
     * @param type      the expected type
     * @param fieldName the name of the field (for error messages)
     * @throws IllegalArgumentException if validation fails
     */
    public static void validateType(Object value, Class<?> type, String fieldName) {
        if (value == null) {
            if (type.isPrimitive()) {
                throw new IllegalArgumentException(
                    fieldName + " cannot be null for primitive type " + type.getName()
                );
            }
            return;
        }

        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(
                fieldName + " must be of type " + type.getName() +
                " but got " + value.getClass().getName()
            );
        }
    }

    /**
     * Extracts the generic type argument at the specified index.
     *
     * @param clazz the class to extract from
     * @param index the index of the type parameter
     * @return the type argument, if present
     */
    public static Optional<Class<?>> getGenericTypeArgument(Class<?> clazz, int index) {
        Type genericSuperclass = clazz.getGenericSuperclass();

        if (genericSuperclass instanceof ParameterizedType parameterizedType) {
            Type[] typeArguments = parameterizedType.getActualTypeArguments();
            if (index >= 0 && index < typeArguments.length) {
                Type typeArgument = typeArguments[index];
                if (typeArgument instanceof Class<?> actualClass) {
                    return Optional.of(actualClass);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Safely casts an object to the specified type.
     *
     * @param <T>   the target type
     * @param value the value to cast
     * @param type  the target type class
     * @return the casted value
     * @throws ClassCastException if cast fails
     */
    @SuppressWarnings("unchecked")
    public static <T> T safeCast(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException(
                "Cannot cast " + value.getClass().getName() + " to " + type.getName()
            );
        }
        return (T) value;
    }

    /**
     * Gets the simple name of a type for error messages.
     *
     * @param type the type
     * @return the simple name
     */
    public static String getSimpleName(Class<?> type) {
        return type == null ? "null" : type.getSimpleName();
    }
}
