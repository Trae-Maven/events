package io.github.trae.events.event.types;

import io.github.trae.events.event.types.impl.AsynchronousEvent;

/**
 * Convenience base class for asynchronous cancellable events.
 * Combines {@link CancellableEvent} with {@link AsynchronousEvent}, so subclasses
 * are both cancellable and automatically dispatched on the async thread pool.
 *
 * <p>Extend this instead of {@link CancellableEvent} when your cancellable event
 * should always be dispatched asynchronously.</p>
 */
public abstract class CancellableAsyncEvent extends CancellableEvent implements AsynchronousEvent {
}