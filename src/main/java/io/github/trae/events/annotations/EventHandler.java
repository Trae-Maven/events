package io.github.trae.events.annotations;

import io.github.trae.events.constants.EventPriority;
import io.github.trae.events.event.Event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event handler.
 * The method must have exactly one parameter which extends {@link Event}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {

    /**
     * The priority of this handler. Handlers with lower values execute first.
     *
     * <p>Defaults to {@link EventPriority#NORMAL}. Use constants from {@link EventPriority}
     * or any custom integer value for fine-grained ordering.</p>
     *
     * @return the priority value
     */
    int priority() default EventPriority.NORMAL;

    /**
     * Whether this handler should skip cancelled events.
     *
     * <p>When set to {@code true}, this handler will not be invoked if a previous handler
     * has already cancelled the event. When set to {@code false}, this handler will always
     * be invoked regardless of the event's cancellation state.</p>
     *
     * @return {@code true} to skip cancelled events, {@code false} to receive all events
     */
    boolean ignoreCancelled() default false;
}