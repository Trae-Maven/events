package io.github.trae.events.event.types.impl;

/**
 * Marker interface indicating that an event should be dispatched asynchronously.
 *
 * <p>Events implementing this interface are automatically dispatched on the
 * {@link io.github.trae.events.event.EventBus}'s async thread pool rather than
 * synchronously on the calling thread.</p>
 *
 * <p>Most consumers should extend {@link io.github.trae.events.event.types.AsyncEvent}
 * or {@link io.github.trae.events.event.types.CancellableAsyncEvent} rather than
 * implementing this interface directly.</p>
 */
public interface AsynchronousEvent {
}