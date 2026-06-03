package io.github.trae.events;

import io.github.trae.events.event.Event;
import io.github.trae.events.event.EventBus;
import io.github.trae.events.event.types.impl.AsynchronousEvent;
import io.github.trae.events.exceptions.EventException;
import io.github.trae.events.interfaces.Cancellable;
import io.github.trae.events.interfaces.Listener;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Static API for common event operations.
 *
 * <p>Provides convenience access to a default {@link EventBus} instance without requiring
 * consumers to manage one directly. The backing {@link EventBus} and {@link ExecutorService}
 * are lazily and thread-safely initialized on first use.</p>
 *
 * <p>Synchronous methods ({@link #dispatchEvent}, {@link #supplyEvent}) require that the event does
 * not implement {@link AsynchronousEvent}. Asynchronous methods ({@link #dispatchAsynchronousEvent},
 * {@link #supplyAsynchronousEvent}) require that it does, and submit the (synchronous) dispatch to
 * the executor so completion can be awaited.</p>
 */
public final class EventApi {

    private static volatile EventBus eventBus;

    @Getter
    @Setter
    private static volatile ExecutorService asynchronousExecutorService;

    private EventApi() {
    }

    /**
     * Returns the backing {@link EventBus} instance, creating a default one if none exists.
     * Also initializes the async {@link ExecutorService} with a cached daemon thread pool
     * if one has not been set. Initialization is thread-safe.
     *
     * @return the event bus
     */
    public static EventBus getEventBus() {
        EventBus localBus = eventBus;
        if (localBus == null) {
            synchronized (EventApi.class) {
                localBus = eventBus;
                if (localBus == null) {
                    if (asynchronousExecutorService == null) {
                        asynchronousExecutorService = Executors.newCachedThreadPool(runnable -> {
                            final Thread thread = new Thread(runnable);
                            thread.setName("EventBus-Async-%s".formatted(thread.threadId()));
                            thread.setDaemon(true);
                            return thread;
                        });
                    }

                    localBus = new EventBus(asynchronousExecutorService);
                    eventBus = localBus;
                }
            }
        }

        return localBus;
    }

    /**
     * Dispatches the given event synchronously on the calling thread.
     * Throws if the event implements {@link AsynchronousEvent}.
     *
     * @param event the event to fire
     * @param <T>   the event type
     * @throws IllegalArgumentException if the event is null
     * @throws EventException           if the event is asynchronous
     */
    public static <T extends Event> void dispatchEvent(final T event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null.");
        }

        if (event instanceof AsynchronousEvent) {
            throw new EventException("Cannot dispatch asynchronous event synchronously.");
        }

        getEventBus().call(event);
    }

    /**
     * Dispatches the given event asynchronously on the bus executor (fire-and-forget).
     * Throws if the event does not implement {@link AsynchronousEvent}.
     *
     * @param event the event to fire
     * @param <T>   the event type
     * @throws IllegalArgumentException if the event is null
     * @throws EventException           if the event is not asynchronous
     */
    public static <T extends Event> void dispatchAsynchronousEvent(final T event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null.");
        }

        if (!(event instanceof AsynchronousEvent)) {
            throw new EventException("Cannot dispatch synchronous event asynchronously.");
        }

        final EventBus bus = getEventBus();
        getAsynchronousExecutorService().execute(() -> bus.call(event));
    }

    /**
     * Dispatches the given event synchronously and returns the event instance.
     * Throws if the event implements {@link AsynchronousEvent}.
     *
     * @param event the event to fire
     * @param <R>   the event type
     * @return the same event instance after all handlers have been invoked
     * @throws IllegalArgumentException if the event is null
     * @throws EventException           if the event is asynchronous
     */
    public static <R extends Event> R supplyEvent(final R event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null.");
        }

        if (event instanceof AsynchronousEvent) {
            throw new EventException("Cannot dispatch asynchronous event synchronously.");
        }

        dispatchEvent(event);
        return event;
    }

    /**
     * Dispatches the given event asynchronously on the bus executor and returns a future that
     * completes with the event after all handlers have been invoked.
     * Throws if the event does not implement {@link AsynchronousEvent}.
     *
     * @param event the event to fire
     * @param <R>   the event type
     * @return a {@link CompletableFuture} that completes with the event once dispatch finishes
     * @throws IllegalArgumentException if the event is null
     * @throws EventException           if the event is not asynchronous
     */
    public static <R extends Event> CompletableFuture<R> supplyAsynchronousEvent(final R event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null.");
        }

        if (!(event instanceof AsynchronousEvent)) {
            throw new EventException("Cannot dispatch synchronous event asynchronously.");
        }

        final EventBus bus = getEventBus();
        return CompletableFuture.supplyAsync(() -> bus.call(event), getAsynchronousExecutorService());
    }

    /**
     * Registers a listener with the backing event bus.
     *
     * @param listener the listener to register
     * @throws IllegalArgumentException if the listener is null
     */
    public static void registerListener(final Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null.");
        }

        getEventBus().register(listener);
    }

    /**
     * Unregisters a listener from the backing event bus.
     *
     * @param listener the listener to unregister
     * @throws IllegalArgumentException if the listener is null
     */
    public static void unregisterListener(final Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null.");
        }

        getEventBus().unregister(listener);
    }

    /**
     * Unregisters all listeners from the backing event bus.
     */
    public static void unregisterAllListeners() {
        getEventBus().unregisterAll();
    }

    /**
     * Checks whether the given event is cancellable.
     *
     * @param event the event to check
     * @return {@code true} if the event implements {@link Cancellable}, {@code false} otherwise
     * @throws IllegalArgumentException if the event is null
     */
    public static boolean isCancellableEvent(final Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null.");
        }

        return event instanceof Cancellable;
    }

    /**
     * Checks whether the given event has been cancelled.
     * Returns {@code false} if the event does not implement {@link Cancellable}.
     *
     * @param event the event to check
     * @return {@code true} if the event is cancellable and has been cancelled, {@code false} otherwise
     * @throws IllegalArgumentException if the event is null
     */
    public static boolean isEventCancelled(final Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null.");
        }

        return event instanceof final Cancellable cancellable && cancellable.isCancelled();
    }
}