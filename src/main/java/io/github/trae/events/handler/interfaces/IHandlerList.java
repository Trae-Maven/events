package io.github.trae.events.handler.interfaces;

import io.github.trae.events.handler.RegisteredHandler;
import io.github.trae.events.interfaces.Listener;

public interface IHandlerList {

    RegisteredHandler[] getBakedRegisteredHandlers();

    void register(final RegisteredHandler registeredHandler);

    void unregister(final RegisteredHandler registeredHandler);

    void unregisterAll(final Listener listener);

    void clear();

    int size();
}