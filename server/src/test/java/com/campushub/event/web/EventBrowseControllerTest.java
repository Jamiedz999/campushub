package com.campushub.event.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campushub.event.EventModule;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventBrowseQuery;
import com.campushub.event.domain.EventPage;
import com.campushub.shared.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventBrowseControllerTest {

    private static final Instant NOW = Instant.parse("2026-03-05T00:00:00Z");

    @Mock
    private EventModule eventModule;

    private EventBrowseController controller;

    @BeforeEach
    void setUp() {
        controller = new EventBrowseController(eventModule, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void buildsTheQueryFromRequestParametersAndMapsTheResultIntoTheEnvelope() {
        Event event = new Event(
                "club-a", "Robotics", "build things", NOW, NOW.plusSeconds(10), NOW.plusSeconds(20),
                NOW.plusSeconds(30), 5);
        when(eventModule.browse(org.mockito.ArgumentMatchers.any(EventBrowseQuery.class)))
                .thenReturn(new EventPage(List.of(event), 0, 20, 1));

        PageResponse<EventBrowseItemResponse> response =
                controller.browse("robot", "club-a", true, null, null, true, null, 0, 20);

        ArgumentCaptor<EventBrowseQuery> captor = ArgumentCaptor.forClass(EventBrowseQuery.class);
        verify(eventModule).browse(captor.capture());
        EventBrowseQuery query = captor.getValue();
        assertThat(query.searchTerm()).isEqualTo("robot");
        assertThat(query.clubId()).isEqualTo("club-a");
        assertThat(query.openForRegistration()).isTrue();
        assertThat(query.hasFreeSeat()).isTrue();

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).title()).isEqualTo("Robotics");
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
    }
}
