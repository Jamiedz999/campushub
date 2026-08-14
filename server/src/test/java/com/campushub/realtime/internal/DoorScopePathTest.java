package com.campushub.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DoorScopePathTest {

    @Test
    void theEventIdIsTheScope() {
        assertThat(DoorScopePath.eventIdIn("/ws/events/65f0c0ffee0000000000abcd/attendance"))
                .contains("65f0c0ffee0000000000abcd");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/ws/events//attendance",
                "/ws/events/event-1",
                "/ws/events/event-1/attendance/extra",
                "/ws/events/event-1/roster",
                "/api/events/event-1/attendance",
                "/ws/events/one/two/attendance"
            })
    void anythingElseIsNoScopeAtAll(String path) {
        // An unparsed path is refused rather than defaulted. The alternative — treating a strange path
        // as some scope — is how a socket ends up subscribed to something nobody authorized.
        assertThat(DoorScopePath.eventIdIn(path)).isEmpty();
    }
}
