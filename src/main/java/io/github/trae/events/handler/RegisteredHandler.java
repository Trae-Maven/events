package io.github.trae.events.handler;

import io.github.trae.events.event.Event;
import io.github.trae.events.exceptions.EventException;
import io.github.trae.events.handler.interfaces.IRegisteredHandler;
import io.github.trae.events.interfaces.Listener;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.lang.reflect.Method;

/**
 * Represents a single resolved event handler — a {@link Listener} instance,
 * the reflective {@link Method}, and the annotation metadata.
 * Immutable after construction.
 */
@AllArgsConstructor
@Getter
public final class RegisteredHandler implements IRegisteredHandler {

    private final Listener listener;
    private final Method method;
    private final int priority;
    private final boolean ignoreCancelled;

    /**
     * Invokes this handler with the given event.
     *
     * @param event the event to pass to the handler method
     * @throws EventException if the handler method throws an exception
     */
    @Override
    public void invoke(final Event event) throws EventException {
        if (event == null) {
            throw new EventException("Event cannot be null.");
        }

        try {
            this.getMethod().invoke(this.getListener(), event);
        } catch (final Exception e) {
            throw new EventException("Failed to invoke handler %s in %s".formatted(this.getMethod().getName(), this.getListener().getClass().getName()), e);
        }
    }
}