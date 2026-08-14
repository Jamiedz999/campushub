package com.campushub.event.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookSlotRequestTest {

    @Test
    void acceptsWholeMinutesAndRejectsFinerPrecisionBeforeBooking() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(new BookSlotRequest(
                            "venue-1",
                            Instant.parse("2026-03-20T10:00:00Z"),
                            Instant.parse("2026-03-20T11:00:00Z"))))
                    .isEmpty();
            assertThat(validator.validate(new BookSlotRequest(
                            "venue-1",
                            Instant.parse("2026-03-20T10:00:30Z"),
                            Instant.parse("2026-03-20T11:00:00Z"))))
                    .singleElement()
                    .satisfies(violation -> assertThat(violation.getMessage()).contains("whole minutes"));
            assertThat(validator.validate(new BookSlotRequest(
                            "venue-1",
                            Instant.parse("2026-03-20T10:00:00Z"),
                            Instant.parse("2026-03-20T11:00:00.500Z"))))
                    .singleElement()
                    .satisfies(violation -> assertThat(violation.getMessage()).contains("whole minutes"));
        }
    }

    @Test
    void aMissingStartIsAMissingValueNotAMisalignedOne() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(
                            new BookSlotRequest("venue-1", null, Instant.parse("2026-03-20T11:00:00Z"))))
                    .singleElement()
                    .satisfies(violation ->
                            assertThat(violation.getMessage()).doesNotContain("whole minutes"));
        }
    }
}
