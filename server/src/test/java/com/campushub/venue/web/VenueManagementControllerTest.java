package com.campushub.venue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.shared.NotFoundException;
import com.campushub.venue.VenueModule;
import com.campushub.venue.VenueModule.VenuePage;
import com.campushub.venue.VenueModule.VenueSummary;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VenueManagementControllerTest {

    @Mock
    private IdentityAccessModule identityAccessModule;

    @Mock
    private VenueModule venueModule;

    private VenueManagementController controller;

    @BeforeEach
    void setUp() {
        controller = new VenueManagementController(identityAccessModule, venueModule);
    }

    @Test
    void aUniversityAdminCreatesAVenue() {
        when(identityAccessModule.currentActor()).thenReturn(admin());
        when(venueModule.createVenue("Sports Hall 2")).thenReturn("venue-1");
        when(venueModule.findVenue("venue-1")).thenReturn(java.util.Optional.of(
                new VenueSummary("venue-1", "Sports Hall 2")));

        VenueResponse response = controller.create(new SaveVenueRequest("Sports Hall 2"));

        assertThat(response).isEqualTo(new VenueResponse("venue-1", "Sports Hall 2"));
    }

    @Test
    void aStudentCannotCreateAVenue() {
        when(identityAccessModule.currentActor()).thenReturn(student());

        assertThatThrownBy(() -> controller.create(new SaveVenueRequest("Hidden Hall")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void anOfficerCanListVenuesForTheBookingSurface() {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        when(venueModule.listVenues(0, 20))
                .thenReturn(new VenuePage(List.of(new VenueSummary("venue-1", "Hall")), 0, 20, 1));

        var response = controller.list(0, 20);

        assertThat(response.items()).containsExactly(new VenueResponse("venue-1", "Hall"));
    }

    @Test
    void renamingAnUnknownVenueIsNotFound() {
        when(identityAccessModule.currentActor()).thenReturn(admin());
        when(venueModule.renameVenue("missing", "New name")).thenReturn(false);

        assertThatThrownBy(() -> controller.rename("missing", new SaveVenueRequest("New name")))
                .isInstanceOf(NotFoundException.class);
        verify(venueModule).renameVenue("missing", "New name");
    }

    private static CurrentActor admin() {
        return new CurrentActor("admin", "admin@example.edu", "Admin", SystemRole.UNIVERSITY_ADMIN, Set.of());
    }

    private static CurrentActor officer() {
        return new CurrentActor("officer", "officer@example.edu", "Officer", SystemRole.STUDENT, Set.of("club-a"));
    }

    private static CurrentActor student() {
        return new CurrentActor("student", "student@example.edu", "Student", SystemRole.STUDENT, Set.of());
    }
}
