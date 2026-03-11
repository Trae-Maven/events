package io.github.trae.events.exceptions;

/**
 * Thrown when an event handler fails during invocation.
 */
public class EventException extends RuntimeException {

    public EventException() {
        super();
    }

    public EventException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public EventException(final String message) {
        super(message);
    }

    public EventException(final Throwable cause) {
        super(cause);
    }
}