package com.campushub.event.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

record BookSlotRequest(
        @NotBlank String venueId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt) {

    @AssertTrue(message = "Slot times must be aligned to whole minutes.")
    public boolean isMinuteAligned() {
        return isMinuteAligned(startsAt) && isMinuteAligned(endsAt);
    }

    private static boolean isMinuteAligned(Instant value) {
        return value == null || value.getEpochSecond() % 60 == 0 && value.getNano() == 0;
    }
}
