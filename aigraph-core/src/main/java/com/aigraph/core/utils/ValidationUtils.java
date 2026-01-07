package com.aigraph.core.utils;

import java.util.Collection;
import java.util.Objects;

/**
 * Utility class for common validation operations.
 * <p>
 * Provides fluent validation methods that throw {@link IllegalArgumentException}
 * or {@link NullPointerException} when validation fails.
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public final class ValidationUtils {

    private ValidationUtils() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Ensures that an object reference is not null.
     *
     * @param <T>       the type of the reference
     * @param reference the object reference to check
     * @param paramName the parameter name (for error messages)
     * @return the non-null reference
     * @throws NullPointerException if reference is null
     */
    public static <T> T requireNonNull(T reference, String paramName) {
        return Objects.requireNonNull(reference, paramName + " must not be null");
    }

    /**
     * Ensures that a string is not null or empty.
     *
     * @param string    the string to check
     * @param paramName the parameter name (for error messages)
     * @return the non-empty string
     * @throws IllegalArgumentException if string is null or empty
     */
    public static String requireNonEmpty(String string, String paramName) {
        requireNonNull(string, paramName);
        if (string.isEmpty()) {
            throw new IllegalArgumentException(paramName + " must not be empty");
        }
        return string;
    }

    /**
     * Ensures that a string is not null, empty, or blank.
     *
     * @param string    the string to check
     * @param paramName the parameter name (for error messages)
     * @return the non-blank string
     * @throws IllegalArgumentException if string is null, empty, or blank
     */
    public static String requireNonBlank(String string, String paramName) {
        requireNonEmpty(string, paramName);
        if (string.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be blank");
        }
        return string;
    }

    /**
     * Ensures that a collection is not null or empty.
     *
     * @param <T>        the type of collection
     * @param collection the collection to check
     * @param paramName  the parameter name (for error messages)
     * @return the non-empty collection
     * @throws IllegalArgumentException if collection is null or empty
     */
    public static <T extends Collection<?>> T requireNonEmpty(T collection, String paramName) {
        requireNonNull(collection, paramName);
        if (collection.isEmpty()) {
            throw new IllegalArgumentException(paramName + " must not be empty");
        }
        return collection;
    }

    /**
     * Ensures that a number is positive (greater than zero).
     *
     * @param value     the value to check
     * @param paramName the parameter name (for error messages)
     * @return the positive value
     * @throws IllegalArgumentException if value is not positive
     */
    public static int requirePositive(int value, String paramName) {
        if (value <= 0) {
            throw new IllegalArgumentException(paramName + " must be positive, got: " + value);
        }
        return value;
    }

    /**
     * Ensures that a number is non-negative (greater than or equal to zero).
     *
     * @param value     the value to check
     * @param paramName the parameter name (for error messages)
     * @return the non-negative value
     * @throws IllegalArgumentException if value is negative
     */
    public static int requireNonNegative(int value, String paramName) {
        if (value < 0) {
            throw new IllegalArgumentException(paramName + " must be non-negative, got: " + value);
        }
        return value;
    }

    /**
     * Ensures that a value is within the specified range (inclusive).
     *
     * @param value     the value to check
     * @param min       the minimum allowed value (inclusive)
     * @param max       the maximum allowed value (inclusive)
     * @param paramName the parameter name (for error messages)
     * @return the value if within range
     * @throws IllegalArgumentException if value is outside range
     */
    public static int requireInRange(int value, int min, int max, String paramName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                paramName + " must be between " + min + " and " + max + ", got: " + value
            );
        }
        return value;
    }

    /**
     * Ensures that a condition is true.
     *
     * @param condition the condition to check
     * @param message   the error message
     * @throws IllegalArgumentException if condition is false
     */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Ensures that a state condition is true.
     *
     * @param condition the condition to check
     * @param message   the error message
     * @throws IllegalStateException if condition is false
     */
    public static void requireState(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
