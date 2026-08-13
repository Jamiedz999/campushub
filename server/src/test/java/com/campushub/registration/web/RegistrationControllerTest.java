package com.campushub.registration.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campushub.event.EventModule.RegistrationForm;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.registration.RegistrationModule;
import com.campushub.registration.RegistrationModule.StudentRegistrationPage;
import com.campushub.registration.RegistrationModule.StudentRegistrationView;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrationControllerTest {

    private static final CurrentActor STUDENT =
            new CurrentActor("student-1", "student@campushub.test", "Student", SystemRole.STUDENT, Set.of());
    private static final CurrentActor OFFICER =
            new CurrentActor("officer-1", "officer@campushub.test", "Officer", SystemRole.STUDENT, Set.of("club-a"));

    @Mock
    private IdentityAccessModule identityAccessModule;

    @Mock
    private RegistrationModule registrationModule;

    private RegistrationController controller;

    @BeforeEach
    void setUp() {
        controller = new RegistrationController(identityAccessModule, registrationModule);
    }

    @Test
    void anEventWithNoFormStillRegistersWithNoRequestBody() {
        when(identityAccessModule.currentActor()).thenReturn(STUDENT);
        when(registrationModule.register("event-1", "student-1", Map.of()))
                .thenReturn(view(true, true));

        StudentRegistrationView response = controller.register("event-1", null);

        assertThat(response.enrolled()).isTrue();
        verify(registrationModule).register("event-1", "student-1", Map.of());
    }

    @Test
    void submittedAnswersAndRetryStayScopedToTheSignedInStudent() {
        when(identityAccessModule.currentActor()).thenReturn(STUDENT);
        SubmitRegistrationRequest request = new SubmitRegistrationRequest(Map.of("name", "Jamie"));
        when(registrationModule.register("event-1", "student-1", request.answerMap()))
                .thenReturn(view(true, false));
        when(registrationModule.retryAnswers("event-1", "student-1", request.answerMap()))
                .thenReturn(view(true, true));

        assertThat(controller.register("event-1", request).answersSaved()).isFalse();
        assertThat(controller.retryAnswers("event-1", request).answersSaved()).isTrue();
    }

    @Test
    void aJsonNullAnswerReachesFormValidationInsteadOfBecomingAnInternalError() {
        when(identityAccessModule.currentActor()).thenReturn(STUDENT);
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("name", null);
        SubmitRegistrationRequest request = new SubmitRegistrationRequest(answers);
        when(registrationModule.register("event-1", "student-1", answers))
                .thenReturn(view(false, null));

        controller.register("event-1", request);

        verify(registrationModule).register("event-1", "student-1", answers);
    }

    @Test
    void myEventsUsesTheSharedPagingContract() {
        when(identityAccessModule.currentActor()).thenReturn(STUDENT);
        StudentRegistrationPage page = new StudentRegistrationPage(List.of(view(true, true)), 0, 20, 1);
        when(registrationModule.findEnrolled("student-1", 0, 20)).thenReturn(page);

        assertThat(controller.mine(0, 20)).isEqualTo(page);
    }

    @Test
    void csvIsOfficerScopedAndDownloadedAsAnAttachment() {
        when(identityAccessModule.currentActor()).thenReturn(OFFICER);
        when(registrationModule.exportAnswersCsv("event-1", Set.of("club-a")))
                .thenReturn("Student\nJamie\n");

        var response = controller.answersCsv("event-1");

        assertThat(response.getBody()).isEqualTo("Student\nJamie\n");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/csv");
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("event-event-1-registration-answers.csv");
    }

    private static StudentRegistrationView view(boolean enrolled, Boolean answersSaved) {
        Instant now = Instant.parse("2026-03-05T00:00:00Z");
        return new StudentRegistrationView(
                "event-1",
                "club-a",
                "Robotics Night",
                "Build things",
                "REGISTRATION_OPEN",
                now.minusSeconds(10),
                now.plusSeconds(10),
                now.plusSeconds(20),
                now.plusSeconds(30),
                5,
                enrolled ? 1 : 0,
                0,
                enrolled,
                enrolled ? "DIRECT" : null,
                null,
                RegistrationForm.empty(),
                answersSaved,
                Map.of());
    }
}
