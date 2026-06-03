package io.github.trae.events.event;

import lombok.Getter;

/**
 * Base class for all events in the system.
 *
 * <p>To define a custom event, simply extend this class and add your fields.</p>
 * <p>To make an event cancellable, implement {@link io.github.trae.events.interfaces.Cancellable}.</p>
 * <p>To mark an event as asynchronous, implement
 * {@link io.github.trae.events.event.types.impl.AsynchronousEvent}.</p>
 *
 * @see io.github.trae.events.event.types.CancellableEvent
 * @see io.github.trae.events.event.types.AsyncEvent
 * @see io.github.trae.events.event.types.CancellableAsyncEvent
 */
@Getter
public abstract class Event {

    private final String eventName;

    public Event() {
        this.eventName = this.getClass().getSimpleName();
    }
}