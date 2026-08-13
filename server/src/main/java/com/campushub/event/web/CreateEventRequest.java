package com.campushub.event.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

record CreateEventRequest(
        @NotBlank String title,
        @NotBlank String description,
        @NotNull Instant registrationOpensAt,
        @NotNull Instant registrationClosesAt,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Positive int capacity) {}
