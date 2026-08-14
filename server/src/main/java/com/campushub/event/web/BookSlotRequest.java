package com.campushub.event.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

record BookSlotRequest(
        @NotBlank String venueId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt) {}
