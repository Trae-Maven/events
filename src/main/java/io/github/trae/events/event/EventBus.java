package io.github.trae.events.event;

import io.github.trae.events.annotations.AsynchronousEvent;
import io.github.trae.events.annotations.EventHandler;
import io.github.trae.events.exceptions.EventException;
import io.github.trae.events.handler.HandlerList;
import io.github.trae.events.handler.RegisteredHandler;
import io.github.trae.events.interfaces.Cancellable;
import io.github.trae.events.interfaces.IEventBus;
import io.github.trae.events.interfaces.Listener;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central event bus responsible for registering listeners and dispatching events.
 * Manages all {@link HandlerList} instances internally and supports hierarchical event dispatch.
 *
 * <p>Events annotated with {@link AsynchronousEvent} are automatically dispatched on a
 * separate thread pool. All other events are dispatched synchronously on the calling thread.</p>
 *
 * <h3>Hierarchy Dispatch:</h3>
 * <p>When a parent event is called, the bus automatically instantiates and dispatches
 * all registered child events using their no-arg constructors. When a child event is
 * called directly, handlers registered for every parent type in the hierarchy are also invoked.</p>
 */
public final class EventBus implements IEventBus {

    private static final Logger LOGGER = Logger.getLogger(EventBus.class.getName());

    private final ConcurrentHashMap<Class<? extends Event>, HandlerList> handlerListMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<? extends Event>, Set<Class<? extends Event>>> childRegistryMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<? extends Event>, Constructor<? extends Event>> childConstructorMap = new ConcurrentHashMap<>();

    private final ExecutorService asyncExecutor;

    /**
     * Creates an event bus with a custom {@link ExecutorService} for async event dispatch.
     *
     * @param asyncExecutor the executor to use for asynchronous events
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

            registerHierarchy(eventClass);
        }
    }

    /**
     * Unregisters all handlers belonging to the given listener from all event types.
     */
    @Override
    public void unregister(final Listener listener) {
        for (final HandlerList handlerList : this.handlerListMap.values()) {
            handlerList.unregisterAll(listener);
        }
    }

    /**
     * Unregisters all handlers from all events and clears the internal registry.
     */
    @Override
    public void unregisterAll() {
        this.handlerListMap.values().forEach(HandlerList::clear);
        this.handlerListMap.clear();
        this.childRegistryMap.clear();
        this.childConstructorMap.clear();
    }

    /**
     * Dispatches the given event to all registered handlers in priority order.
     *
     * <p>If the event is annotated with {@link AsynchronousEvent}, dispatch happens
     * on the async thread pool. Otherwise, dispatch happens synchronously on the
     * calling thread.</p>
     *
     * <p>If the event has registered child types, each child is instantiated via its
     * no-arg constructor and dispatched as well. When a child event is called directly,
     * handlers for every parent type in the hierarchy are also invoked.</p>
     *
     * @param event the event to fire
     * @param <T>   the event type
     * @return the same event instance (for chaining / inspection)
     */
    @Override
    public <T extends Event> T call(final T event) {
        if (isAsynchronous(event.getClass())) {
            this.asyncExecutor.execute(() -> dispatchAll(event));
        } else {
            dispatchAll(event);
        }
        return event;
    }

    /**
     * Checks whether the given event class is marked as asynchronous
     * via {@link AsynchronousEvent}. The annotation is {@link java.lang.annotation.Inherited},
     * so child classes inherit it from annotated parents.
     */
    @Override
    public boolean isAsynchronous(final Class<? extends Event> eventClass) {
        return eventClass.isAnnotationPresent(AsynchronousEvent.class);
    }

    /**
     * Shuts down the async executor. Should be called when the event bus is no longer needed
     * to prevent thread leaks.
     */
    @Override
    public void shutdown() {
        this.asyncExecutor.shutdown();
    }

    /**
     * Performs the full dispatch cycle: exact type, parent hierarchy, and child events.
     */
    private <T extends Event> void dispatchAll(final T event) {
        final Class<? extends Event> eventClass = event.getClass();

        // 1. Dispatch to handlers registered for the exact type
        this.dispatch(event, eventClass);

        // 2. Walk up the hierarchy and dispatch to parent handlers
        Class<?> parentClass = eventClass.getSuperclass();
        while (parentClass != null && Event.class.isAssignableFrom(parentClass) && parentClass != Event.class) {
            this.dispatch(event, parentClass.asSubclass(Event.class));

            parentClass = parentClass.getSuperclass();
        }

        // 3. Dispatch to child events (instantiated via no-arg constructor)
        final Set<Class<? extends Event>> childrenSet = this.childRegistryMap.get(eventClass);
        if (childrenSet != null) {
            for (final Class<? extends Event> childClass : childrenSet) {
                if (childClass == eventClass) {
                    continue;
                }

                final Constructor<? extends Event> declaredConstructor = this.childConstructorMap.get(childClass);
                if (declaredConstructor == null) {
                    continue;
                }

                try {
                    this.call(declaredConstructor.newInstance());
                } catch (final Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to instantiate child event %s".formatted(childClass.getName()), e);
                }
            }
        }
    }

    /**
     * Dispatches handlers registered for a specific class against the given event instance.
     */
    private void dispatch(final Event event, final Class<? extends Event> targetClass) {
        final HandlerList handlerList = this.handlerListMap.get(targetClass);
        if (handlerList == null) {
            return;
        }

        final RegisteredHandler[] registeredHandlers = handlerList.getBakedRegisteredHandlers();

        for (final RegisteredHandler handler : registeredHandlers) {
            if (event instanceof final Cancellable cancellable && handler.isIgnoreCancelled() && cancellable.isCancelled()) {
                continue;
            }

            try {
                handler.invoke(event);
            } catch (final EventException e) {
                LOGGER.log(Level.SEVERE, "Error dispatching event %s to handler %s in %s".formatted(event.getEventName(), handler.getMethod().getName(), handler.getListener().getClass().getName()), e);
            }
        }
    }

    /**
     * Registers the parent-child hierarchy for the given event class.
     * Walks up from the class to {@link Event} and registers it as a child
     * of every parent along the way. Also caches its no-arg constructor if available.
     */
    private void registerHierarchy(final Class<? extends Event> eventClass) {
        if (!(Modifier.isAbstract(eventClass.getModifiers())) && !(this.childConstructorMap.containsKey(eventClass))) {
            try {
                final Constructor<? extends Event> declaredConstructor = eventClass.getDeclaredConstructor();

                if (!(declaredConstructor.canAccess(null))) {
                    declaredConstructor.setAccessible(true);
                }

                this.childConstructorMap.put(eventClass, declaredConstructor);
            } catch (final NoSuchMethodException e) {
                LOGGER.warning("Event class %s has no no-arg constructor and cannot be auto-dispatched as a child event".formatted(eventClass.getName()));
            }
        }

        Class<?> current = eventClass.getSuperclass();
        while (current != null && Event.class.isAssignableFrom(current) && current != Event.class) {
            final Class<? extends Event> parentClass = current.asSubclass(Event.class);

            this.childRegistryMap.computeIfAbsent(parentClass, __ -> ConcurrentHashMap.newKeySet()).add(eventClass);

            current = current.getSuperclass();
        }
    }
}