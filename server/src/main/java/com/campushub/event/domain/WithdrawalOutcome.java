package com.campushub.event.domain;

import java.time.Instant;

/** The stable outcomes of deleting the signed-in Student's registration sub-resource. */
public enum WithdrawalOutcome {
    SUCCESS,
    NOT_FOUND,
    EVENT_CANCELLED,
    EVENT_STARTED;

    public static WithdrawalOutcome classifyFailure(Event event, Instant now) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            return EVENT_CANCELLED;
        }
        if (!now.isBefore(event.getStartsAt())) {
            return EVENT_STARTED;
        }
        // DELETE is idempotent: before the freeze, an already-absent registration is the desired state.
        return SUCCESS;
    }
}
