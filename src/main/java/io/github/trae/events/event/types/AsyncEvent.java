package io.github.trae.events.event.types;

import io.github.trae.events.event.Event;
import io.github.trae.events.event.types.impl.AsynchronousEvent;

/**
 * Convenience base class for asynchronous events.
 * Implements {@link AsynchronousEvent}, so subclasses are automatically
 * dispatched on the async thread pool without implementing the interface themselves.
 *
 * <p>Extend this instead of {@link Event} when your event should always be dispatched asynchronously.</p>
 */
public abstract class AsyncEvent extends Event implements AsynchronousEvent {
}