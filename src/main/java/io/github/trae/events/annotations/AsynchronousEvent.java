package io.github.trae.events.annotations;

import java.lang.annotation.*;

/**
 * Marks an event class as asynchronous, meaning it fires outside the main thread.
 *
 * <p>This annotation is {@link Inherited}, so any subclass of an annotated event
 * is also considered asynchronous.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface AsynchronousEvent {
}