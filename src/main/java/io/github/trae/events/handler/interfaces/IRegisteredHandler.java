package io.github.trae.events.handler.interfaces;

import io.github.trae.events.event.Event;

public interface IRegisteredHandler {

    void invoke(final Event event);
}