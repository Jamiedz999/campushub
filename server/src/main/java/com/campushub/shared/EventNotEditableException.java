package com.campushub.shared;

// Thrown when the caller is entitled to an Event but its current Status or timestamps refuse the write
// — see docs/adr/03-define-event-lifecycle.md. Distinct from NotFoundException: the caller is allowed
// to know this resource exists, just not that this particular change is legal right now.
public class EventNotEditableException extends RuntimeException {

    public EventNotEditableException(String message) {
        super(message);
    }
}
