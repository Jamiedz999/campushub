package com.campushub.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// The acceptance criterion that outlives every other test here: no authoritative state travels over
// the socket. A count added to this payload would work — the screen would even show it — and it would
// be wrong for every client that missed a frame, silently, until someone reloaded the page.
class AttendanceHintTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void theHintCarriesWhatChangedAndWhereAndNothingElse() {
        String json = objectMapper.writeValueAsString(AttendanceHint.attendanceChanged("event-1"));

        assertThat(json).isEqualTo("{\"type\":\"attendance-changed\",\"eventId\":\"event-1\"}");
    }

    @Test
    void nothingInTheHintIsANumber() {
        // Stated as a property rather than as a list of forbidden field names, so a later "attended",
        // "enrolled" or "remaining" fails this test without anyone having to remember to add it.
        String json = objectMapper.writeValueAsString(AttendanceHint.attendanceChanged("event-1"));

        Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

        assertThat(parsed.values()).allSatisfy(value -> assertThat(value).isNotInstanceOf(Number.class));
    }
}
