package io.github.trae.events.event.interfaces;

import io.github.trae.events.event.Event;
import io.github.trae.events.handler.HandlerList;
import io.github.trae.events.interfaces.Listener;

public interface IEventBus {

    HandlerList getHandlerList(final Class<? extends Event> eventClass);

    void register(final Listener listener);

    void unregister(final Listener listener);

    void unregisterAll();

    <T extends Event> T call(final T event);

    void shutdown();
}