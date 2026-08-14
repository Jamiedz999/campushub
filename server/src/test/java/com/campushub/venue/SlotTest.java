package com.campushub.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campushub.venue.VenueModule.Slot;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SlotTest {

    private static final Instant ALIGNED_START = Instant.parse("2026-03-20T10:00:00Z");
    private static final Instant ALIGNED_END = Instant.parse("2026-03-20T11:00:00Z");

    @Test
    void acceptsTimesAlignedToWholeMinutes() {
        Slot slot = new Slot("venue-1", ALIGNED_START, ALIGNED_END);

        assertThat(slot.startsAt()).isEqualTo(ALIGNED_START);
        assertThat(slot.endsAt()).isEqualTo(ALIGNED_END);
    }

    @Test
    void rejectsAnAlignedStartWithAMisalignedEnd() {
        assertThatThrownBy(() -> new Slot("venue-1", ALIGNED_START, ALIGNED_END.plusSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole minutes");
    }

    @Test
    void rejectsSubSecondPrecision() {
        assertThatThrownBy(() -> new Slot("venue-1", ALIGNED_START.plusNanos(1), ALIGNED_END))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole minutes");
    }

    @Test
    void rejectsANullStart() {
        assertThatThrownBy(() -> new Slot("venue-1", null, ALIGNED_END))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole minutes");
    }
}
