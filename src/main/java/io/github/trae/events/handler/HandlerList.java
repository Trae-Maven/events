package io.github.trae.events.handler;

import io.github.trae.events.event.EventBus;
import io.github.trae.events.exceptions.EventException;
import io.github.trae.events.handler.interfaces.IHandlerList;
import io.github.trae.events.interfaces.Listener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stores all {@link RegisteredHandler}s for a specific event type.
 * Handlers are sorted by priority and baked into a flat array for fast iteration during dispatch.
 *
 * <p>Instances are created and managed by {@link EventBus} — event
 * subclasses do not need to interact with this class directly.</p>
 */
public final class HandlerList implements IHandlerList {

    private final Object LOCK = new Object();

    private final List<RegisteredHandler> handlerList = new ArrayList<>();
    private RegisteredHandler[] bakedHandlers;

    /**
     * Returns the baked handler array sorted by priority (ascending).
     * Rebuilds the array if the list has been modified since the last bake.
     *
     * @return the sorted handler array
     */
    @Override
    public RegisteredHandler[] getBakedRegisteredHandlers() {
        synchronized (this.LOCK) {
            if (this.bakedHandlers == null) {
                this.bake();
            }
            return this.bakedHandlers;
        }
    }

    /**
     * Registers a handler and invalidates the baked cache.
     *
     * @param registeredHandler the handler to register
     */
    @Override
    public void register(final RegisteredHandler registeredHandler) {
        if (registeredHandler == null) {
            throw new EventException("Registered Handler cannot be null.");
        }

        synchronized (this.LOCK) {
            this.handlerList.add(registeredHandler);
            this.bakedHandlers = null;
        }
    }

    /**
     * Unregisters a specific handler.
     *
     * @param registeredHandler the handler to remove
     */
    @Override
    public void unregister(final RegisteredHandler registeredHandler) {
        if (registeredHandler == null) {
            throw new EventException("Registered Handler cannot be null.");
        }

        synchronized (this.LOCK) {
            if (this.handlerList.remove(registeredHandler)) {
                this.bakedHandlers = null;
            }
        }
    }

    /**
     * Unregisters all handlers belonging to the given listener.
     *
     * @param listener the listener whose handlers should be removed
     */
    @Override
    public void unregisterAll(final Listener listener) {
        if (listener == null) {
            throw new EventException("Listener cannot be null.");
        }

        synchronized (this.LOCK) {
            if (this.handlerList.removeIf(h -> h.getListener() == listener)) {
                this.bakedHandlers = null;
            }
        }
    }

    /**
     * Clears all handlers.
     */
    @Override
    public void clear() {
        synchronized (this.LOCK) {
            this.handlerList.clear();
            this.bakedHandlers = null;
        }
    }

    /**
     * Returns the total number of registered handlers.
     *
     * @return the handler count
     */
    @Override
    public int size() {
        synchronized (this.LOCK) {
            return this.handlerList.size();
        }
    }

    /**
     * Sorts handlers by priority and flattens into a fixed array.
     */
    private void bake() {
        this.handlerList.sort(Comparator.comparingInt(RegisteredHandler::getPriority));
        this.bakedHandlers = this.handlerList.toArray(RegisteredHandler[]::new);
    }
}