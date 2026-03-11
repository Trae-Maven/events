package io.github.trae.events.interfaces;

/**
 * Events that implement this interface can be cancelled by handlers.
 * When an event is cancelled, subsequent handlers with {@code ignoreCancelled = true}
 * will not receive the event.
 */
public interface Cancellable {

    /**
     * Returns whether this event has been cancelled.
     *
     * @return {@code true} if the event is cancelled, {@code false} otherwise
     */
    boolean isCancelled();

    /**
     * Sets the cancellation state of this event.
     *
     * @param cancelled {@code true} to cancel the event, {@code false} to uncancel it
     */
    void setCancelled(final boolean cancelled);

    /**
     * Returns the reason this event was cancelled, or {@code null} if no reason was provided.
     *
     * @return the cancellation reason, or {@code null}
     */
    String getCancelledReason();

    /**
     * Cancels this event with a reason. Automatically sets the cancelled state to {@code true}.
     *
     * @param cancelledReason the reason for cancellation
     */
    void setCancelledWithReason(final String cancelledReason);
}