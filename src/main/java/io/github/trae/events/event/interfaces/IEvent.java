package io.github.trae.events.event.interfaces;

public interface IEvent {

    boolean isEventCancellable();

    boolean isEventAsynchronous();
}