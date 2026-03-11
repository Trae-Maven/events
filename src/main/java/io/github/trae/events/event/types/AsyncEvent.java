package io.github.trae.events.event.types;

import io.github.trae.events.annotations.AsynchronousEvent;
import io.github.trae.events.event.Event;

/**
 * Convenience base class for asynchronous events.
 * Pre-annotated with {@link AsynchronousEvent}, so subclasses are automatically
 * dispatched on the async thread pool without needing the annotation themselves.
 *
 * <p>Extend this instead of {@link Event} when your event should always be dispatched asynchronously.</p>
 */
@AsynchronousEvent
public abstract class AsyncEvent extends Event {
}