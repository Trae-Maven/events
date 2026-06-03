package io.github.trae.events.event;

import io.github.trae.events.annotations.EventHandler;
import io.github.trae.events.event.interfaces.IEventBus;
import io.github.trae.events.exceptions.EventException;
import io.github.trae.events.handler.HandlerList;
import io.github.trae.events.handler.RegisteredHandler;
import io.github.trae.events.interfaces.Cancellable;
import io.github.trae.events.interfaces.Listener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central event bus responsible for registering listeners and dispatching events.
 * Manages all {@link HandlerList} instances internally and supports hierarchical event dispatch.
 *
 * <p>{@link #call(Event)} dispatches synchronously on the calling thread and is the single
 * dispatch primitive for the whole library. Asynchronous behaviour is owned by callers
 * (for example {@link io.github.trae.events.EventApi}), which submit {@code call} to an
 * executor — keeping {@code call} predictable and allowing completion to be awaited.</p>
 *
 * <h3>Hierarchy dispatch</h3>
 * <p>When an event is fired, handlers registered for its exact runtime type are invoked first,
 * followed by handlers registered for each supertype up the chain (excluding {@link Event}
 * itself), in priority order within each type. The resolved chain of {@link HandlerList}s for
 * a given concrete event type is cached and reused; the cache is invalidated automatically
 * whenever listeners are registered or unregistered.</p>
 */
public final class EventBus implements IEventBus {

    private static final Logger LOGGER = Logger.getLogger(EventBus.class.getName());

    private final ConcurrentHashMap<Class<? extends Event>, HandlerList> handlerListMap = new ConcurrentHashMap<>();

    /**
     * Per-concrete-type cache of the ordered {@link HandlerList} chain to dispatch to.
     * Each entry is tagged with the {@link #generation} it was built under; a stale entry
     * (generation mismatch) is transparently rebuilt on the next dispatch.
     */
    private final ConcurrentHashMap<Class<? extends Event>, CachedDispatch> dispatchCache = new ConcurrentHashMap<>();

    /**
     * Monotonic registration generation. Incremented on every register/unregister so that
     * cached dispatch chains built under an older generation are rebuilt on next use.
     */
    private final AtomicInteger generation = new AtomicInteger();

    private final ExecutorService asyncExecutor;

    /**
     * Creates an event bus with an associated executor. The executor is not used by
     * {@link #call(Event)} directly; it is provided for callers that dispatch asynchronously
     * and is shut down by {@link #shutdown()}.
     *
     * @param asyncExecutor the executor associated with this bus
     */
    public EventBus(final ExecutorService asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * Returns the {@link HandlerList} for the given event class, or {@code null}
     * if no handlers have been registered for it.
     */
    @Override
    public HandlerList getHandlerList(final Class<? extends Event> eventClass) {
        return this.handlerListMap.get(eventClass);
    }

    /**
     * Registers all {@link EventHandler}-annotated methods in the given listener.
     *
     * @param listener the listener instance to scan and register
     * @throws IllegalArgumentException if a handler method has an invalid signature
     */
    @Override
    public void register(final Listener listener) {
        for (final Method method : listener.getClass().getDeclaredMethods()) {
            final EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) {
                continue;
            }

            final Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1) {
                throw new IllegalArgumentException("EventHandler method %s in %s must have exactly 1 parameter, found %s".formatted(method.getName(), listener.getClass().getName(), parameterTypes.length));
            }

            final Class<?> parameterType = parameterTypes[0];
            if (!(Event.class.isAssignableFrom(parameterType))) {
                throw new IllegalArgumentException("EventHandler method %s in %s parameter must extend Event, found %s".formatted(method.getName(), listener.getClass().getName(), parameterType.getName()));
            }

            final Class<? extends Event> eventClass = parameterType.asSubclass(Event.class);

            method.trySetAccessible();

            this.handlerListMap.computeIfAbsent(eventClass, __ -> new HandlerList()).register(new RegisteredHandler(listener, method, annotation.priority(), annotation.ignoreCancelled()));
        }

        this.generation.incrementAndGet();
    }

    /**
     * Unregisters all handlers belonging to the given listener from all event types,
     * pruning any event types left without handlers.
     */
    @Override
    public void unregister(final Listener listener) {
        for (final HandlerList handlerList : this.handlerListMap.values()) {
            handlerList.unregisterAll(listener);
        }

        this.handlerListMap.values().removeIf(handlerList -> handlerList.size() == 0);
        this.generation.incrementAndGet();
    }

    /**
     * Unregisters all handlers from all events and clears the internal registry.
     */
    @Override
    public void unregisterAll() {
        this.handlerListMap.values().forEach(HandlerList::clear);
        this.handlerListMap.clear();
        this.dispatchCache.clear();
        this.generation.incrementAndGet();
    }

    /**
     * Dispatches the given event synchronously on the calling thread to all registered
     * handlers, in supertype order (exact type first, then each parent up to but excluding
     * {@link Event}) and priority order within each type.
     *
     * @param event the event to fire
     * @param <T>   the event type
     * @return the same event instance (for chaining / inspection)
     */
    @Override
    public <T extends Event> T call(final T event) {
        this.dispatchAll(event);
        return event;
    }

    /**
     * Shuts down the associated executor. Should be called when the event bus is no longer
     * needed to prevent thread leaks.
     */
    @Override
    public void shutdown() {
        this.asyncExecutor.shutdown();
    }

    /**
     * Resolves (using the cache) and walks the handler chain for the event's runtime type,
     * invoking each handler in order.
     */
    private void dispatchAll(final Event event) {
        final Class<? extends Event> eventClass = event.getClass();
        final int currentGeneration = this.generation.get();

        CachedDispatch cached = this.dispatchCache.get(eventClass);
        if (cached == null || cached.generation() != currentGeneration) {
            cached = new CachedDispatch(currentGeneration, this.resolveChain(eventClass));
            this.dispatchCache.put(eventClass, cached);
        }

        final HandlerList[] chain = cached.handlerLists();
        if (chain.length == 0) {
            return;
        }

        final Cancellable cancellable = (event instanceof final Cancellable c) ? c : null;

        for (final HandlerList handlerList : chain) {
            for (final RegisteredHandler handler : handlerList.getBakedRegisteredHandlers()) {
                if (cancellable != null && handler.isIgnoreCancelled() && cancellable.isCancelled()) {
                    continue;
                }

                try {
                    handler.invoke(event);
                } catch (final EventException e) {
                    LOGGER.log(Level.SEVERE, "Error dispatching event %s to handler %s in %s".formatted(event.getEventName(), handler.getMethod().getName(), handler.getListener().getClass().getName()), e);
                }
            }
        }
    }

    /**
     * Builds the ordered {@link HandlerList} chain for a concrete event type: the exact type
     * first, then each supertype up the hierarchy that has registered handlers, stopping
     * before {@link Event} itself. Types without handlers are skipped.
     */
    private HandlerList[] resolveChain(final Class<? extends Event> eventClass) {
        final List<HandlerList> chain = new ArrayList<>();

        Class<?> current = eventClass;
        while (current != null && current != Event.class && Event.class.isAssignableFrom(current)) {
            final HandlerList handlerList = this.handlerListMap.get(current.asSubclass(Event.class));
            if (handlerList != null) {
                chain.add(handlerList);
            }

            current = current.getSuperclass();
        }

        return chain.toArray(HandlerList[]::new);
    }

    /**
     * Immutable cache entry: the registration generation it was built under and the
     * resolved, ordered handler chain.
     */
    private record CachedDispatch(int generation, HandlerList[] handlerLists) {
    }
}