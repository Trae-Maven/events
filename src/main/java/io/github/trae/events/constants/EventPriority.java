package io.github.trae.events.constants;

import lombok.experimental.UtilityClass;

/**
 * Priority constants for event handler execution order.
 * Handlers are executed in ascending order.
 *
 * <p>Custom integer values between or beyond these constants are also supported
 * for fine-grained ordering.</p>
 */
@UtilityClass
public class EventPriority {

    /**
     * Executed before all others. Should only be used for observation and initial setup.
     * Handlers at this priority should NEVER modify the event.
     */
    public static final int BASELINE = Integer.MIN_VALUE;

    /**
     * Executed after BASELINE. For handlers that need to run before most others.
     */
    public static final int LOWEST = Integer.MIN_VALUE + 1;

    /**
     * Executed third. For early processing.
     */
    public static final int LOW = Integer.MIN_VALUE + 2;

    /**
     * Default priority. For standard handler logic.
     */
    public static final int NORMAL = 0;

    /**
     * Executed after NORMAL. For late processing such as validation and filtering.
     */
    public static final int HIGH = Integer.MAX_VALUE - 2;

    /**
     * Executed second-to-last. For handlers that need final say on cancellation.
     */
    public static final int HIGHEST = Integer.MAX_VALUE - 1;

    /**
     * Executed last. Should only be used for monitoring and logging. Handlers at this priority should NEVER modify the event.
     */
    public static final int MONITOR = Integer.MAX_VALUE;
}