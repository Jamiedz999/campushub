package com.campushub.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// The seam the technical baseline names, exercised as the baseline describes it: a recording no-op in
// place of the socket. What a caller of this module can observe is one hint per publish, carrying one
// scope — proven here without a server, a client or a wait.
class RealtimeModuleImplTest {

    private final List<String> published = new ArrayList<>();
    private final RealtimeModuleImpl realtime = new RealtimeModuleImpl(published::add);

    @Test
    void publishingAHintFansItOutToTheScopeItNames() {
        realtime.publishAttendanceChanged("event-1");
        realtime.publishAttendanceChanged("event-2");

        assertThat(published).containsExactly("event-1", "event-2");
    }
}
