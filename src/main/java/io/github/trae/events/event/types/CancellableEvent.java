package io.github.trae.events.event.types;

import io.github.trae.events.event.Event;
import io.github.trae.events.interfaces.Cancellable;
import lombok.Getter;
import lombok.Setter;

/**
 * Convenience base class for cancellable events.
 * Extends {@link Event} and implements {@link Cancellable} with a default
 * cancellation state and optional reason.
 *
 * <p>Extend this instead of {@link Event} when your event needs to be cancellable
 * without manually implementing the {@link Cancellable} interface.</p>
 */
@Getter
public abstract class CancellableEvent extends Event implements Cancellable {

    @Setter
    private boolean cancelled;

    private String cancelledReason;

    /**
     * Cancels this event and sets the reason. Equivalent to calling
     * {@code setCancelled(true)} followed by setting the reason.
     *
     * @param cancelledReason the reason for cancellation
     */
    @Override
    public void setCancelledWithReason(final String cancelledReason) {
        this.cancelledReason = cancelledReason;
        this.setCancelled(true);
    }
}