package com.campushub.registration.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.campushub.event.EventModule;
import com.campushub.event.EventModule.RegistrationForm;
import com.campushub.event.EventModule.SeatRequestOutcome;
import com.campushub.event.EventModule.SeatRequestResult;
import com.campushub.event.EventModule.LongTextField;
import com.campushub.event.EventModule.MultipleChoiceField;
import com.campushub.event.EventModule.NumberField;
import com.campushub.event.EventModule.OfficerEnrollment;
import com.campushub.event.EventModule.OfficerRegistrationEvent;
import com.campushub.event.EventModule.ShortTextField;
import com.campushub.event.EventModule.SingleChoiceField;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.registration.persistence.RegistrationRepository;
import com.campushub.registration.domain.Registration;
import com.campushub.shared.ConflictException;
import com.campushub.shared.FormValidationException;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class RegistrationModuleImplTest {

    private static final Instant NOW = Instant.parse("2026-03-05T00:00:00Z");
    private static final Long CURRENT_ENROLLMENT_VERSION = 2L;
    private static final Long NEWER_ENROLLMENT_VERSION = 3L;
    private static final Long OLD_ENROLLMENT_VERSION = 1L;

    @Mock
    private EventModule eventModule;

    @Mock
    private RegistrationRepository repository;

    @Mock
    private IdentityAccessModule identityAccessModule;

    private RegistrationModuleImpl module;

    @BeforeEach
    void setUp() {
        module = new RegistrationModuleImpl(eventModule, repository, identityAccessModule);
    }

    @Test
    void aMissingRequiredShortTextAnswerIsRejectedBeforeTheSeatWrite() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField("name", "Preferred name", null, true, 80)));
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(java.util.Optional.of(studentEvent(form, false)));

        assertThatThrownBy(() -> module.register("event-1", "student-1", Map.of()))
                .isInstanceOf(FormValidationException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                                ((FormValidationException) exception).fieldErrors())
                        .containsEntry("name", "Required."));
        verify(eventModule, never()).requestSeat(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void aValidShortTextAnswerIsSavedAfterTheSeatIsWon() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField("name", "Preferred name", null, true, 80)));
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(
                        java.util.Optional.of(studentEvent(form, false)),
                        java.util.Optional.of(studentEvent(form, true)));
        when(eventModule.requestSeat("event-1", "student-1", 0))
                .thenReturn(new SeatRequestResult(
                        SeatRequestOutcome.SUCCESS, CURRENT_ENROLLMENT_VERSION));
        when(repository.upsertAnswers(
                        "event-1",
                        "student-1",
                        CURRENT_ENROLLMENT_VERSION,
                        Map.of("name", "Jamie")))
                .thenReturn(true);

        var view = module.register("event-1", "student-1", Map.of("name", "Jamie"));

        assertThat(view.answersSaved()).isTrue();
        verify(repository).upsertAnswers(
                "event-1", "student-1", CURRENT_ENROLLMENT_VERSION, Map.of("name", "Jamie"));
    }

    @Test
    void aDelayedSeatWinnerCannotOverwriteAnswersForALaterEnrollment() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField("name", "Preferred name", null, true, 80)));
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(
                        java.util.Optional.of(studentEvent(form, false)),
                        java.util.Optional.of(studentEvent(
                                form, true, null, 0, NEWER_ENROLLMENT_VERSION)));
        when(eventModule.requestSeat("event-1", "student-1", 0))
                .thenReturn(new SeatRequestResult(
                        SeatRequestOutcome.SUCCESS, CURRENT_ENROLLMENT_VERSION));
        when(repository.find("event-1", "student-1"))
                .thenReturn(java.util.Optional.of(new Registration(
                        "event-1",
                        "student-1",
                        NEWER_ENROLLMENT_VERSION,
                        Map.of("name", "New answer"))));

        var view = module.register("event-1", "student-1", Map.of("name", "Old answer"));

        assertThat(view.answersSaved()).isTrue();
        assertThat(view.answers()).containsExactlyEntriesOf(Map.of("name", "New answer"));
        verify(repository, never()).upsertAnswers(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void longTextHonoursItsOwnMaximumLength() {
        RegistrationForm form = new RegistrationForm(List.of(
                new LongTextField("experience", "Experience", null, false, 5)));

        assertInvalid(
                form,
                Map.of("experience", "Too long"),
                ErrorCode.FORM_VALIDATION_FAILED,
                "experience",
                "Use 5 characters or fewer.");
    }

    @Test
    void singleChoiceNamesAnUndefinedOptionWithItsOwnStableCode() {
        RegistrationForm form = new RegistrationForm(List.of(
                new SingleChoiceField("shirt", "T-shirt", null, true, List.of("S", "M", "L"))));

        assertInvalid(
                form,
                Map.of("shirt", "XL"),
                ErrorCode.UNDEFINED_OPTION,
                "shirt",
                "The option 'XL' is not defined.");
    }

    @Test
    void multipleChoiceRejectsAnyUndefinedOption() {
        RegistrationForm form = new RegistrationForm(List.of(new MultipleChoiceField(
                "topics", "Topics", null, false, List.of("Robotics", "AI"))));

        assertInvalid(
                form,
                Map.of("topics", List.of("AI", "Biology")),
                ErrorCode.UNDEFINED_OPTION,
                "topics",
                "The option 'Biology' is not defined.");
    }

    @Test
    void numberHonoursItsMinimumAndMaximum() {
        RegistrationForm form = new RegistrationForm(List.of(new NumberField(
                "teamSize", "Team size", null, true, BigDecimal.ONE, BigDecimal.valueOf(4))));

        assertInvalid(
                form,
                Map.of("teamSize", 5),
                ErrorCode.FORM_VALIDATION_FAILED,
                "teamSize",
                "Enter 4 or less.");
    }

    @Test
    void anUnknownFieldIdGetsAPerFieldValidationError() {
        assertInvalid(
                RegistrationForm.empty(),
                Map.of("oldField", "stale answer"),
                ErrorCode.FORM_VALIDATION_FAILED,
                "oldField",
                "This field is not defined.");
    }

    @Test
    void anEventWithNoFormRegistersAndSavesAnEmptyRegistrationInOneAction() {
        RegistrationForm form = RegistrationForm.empty();
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(
                        java.util.Optional.of(studentEvent(form, false)),
                        java.util.Optional.of(studentEvent(form, true)));
        when(eventModule.requestSeat("event-1", "student-1", 0))
                .thenReturn(new SeatRequestResult(
                        SeatRequestOutcome.SUCCESS, CURRENT_ENROLLMENT_VERSION));
        when(repository.upsertAnswers(
                        "event-1", "student-1", CURRENT_ENROLLMENT_VERSION, Map.of()))
                .thenReturn(true);

        var view = module.register("event-1", "student-1", Map.of());

        assertThat(view.answersSaved()).isTrue();
        verify(repository).upsertAnswers(
                "event-1", "student-1", CURRENT_ENROLLMENT_VERSION, Map.of());
    }

    @Test
    void aFormRevisionRaceRevalidatesThePreservedAnswersBeforeTakingASeat() {
        RegistrationForm original = new RegistrationForm(List.of(
                new ShortTextField("name", "Preferred name", null, true, 80)));
        RegistrationForm changed = new RegistrationForm(List.of(
                new ShortTextField("name", "Display name", null, true, 40)));
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(
                        java.util.Optional.of(studentEvent(original, false, null, 0)),
                        java.util.Optional.of(studentEvent(changed, false, null, 1)),
                        java.util.Optional.of(studentEvent(changed, true, null, 1)));
        when(eventModule.requestSeat("event-1", "student-1", 0))
                .thenReturn(new SeatRequestResult(SeatRequestOutcome.FORM_CHANGED, null));
        when(eventModule.requestSeat("event-1", "student-1", 1))
                .thenReturn(new SeatRequestResult(
                        SeatRequestOutcome.SUCCESS, CURRENT_ENROLLMENT_VERSION));
        when(repository.upsertAnswers(
                        "event-1",
                        "student-1",
                        CURRENT_ENROLLMENT_VERSION,
                        Map.of("name", "Jamie")))
                .thenReturn(true);

        var view = module.register("event-1", "student-1", Map.of("name", "Jamie"));

        assertThat(view.answersSaved()).isTrue();
        verify(repository).upsertAnswers(
                "event-1", "student-1", CURRENT_ENROLLMENT_VERSION, Map.of("name", "Jamie"));
    }

    @ParameterizedTest
    @MethodSource("conflictingSeatOutcomes")
    void aSeatRefusalKeepsItsStableConflictCode(SeatRequestOutcome outcome, ErrorCode code) {
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(java.util.Optional.of(studentEvent(RegistrationForm.empty(), false)));
        when(eventModule.requestSeat("event-1", "student-1", 0))
                .thenReturn(new SeatRequestResult(outcome, null));

        assertThatThrownBy(() -> module.register("event-1", "student-1", Map.of()))
                .isInstanceOf(ConflictException.class)
                .extracting(exception -> ((ConflictException) exception).code())
                .isEqualTo(code);
    }

    @Test
    void aMissingSeatRequestOutcomeIsReportedAsNotFound() {
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(java.util.Optional.of(studentEvent(RegistrationForm.empty(), false)));
        when(eventModule.requestSeat("event-1", "student-1", 0))
                .thenReturn(new SeatRequestResult(SeatRequestOutcome.NOT_FOUND, null));

        assertThatThrownBy(() -> module.register("event-1", "student-1", Map.of()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void losingTheSeatRaceKeepsAnswersOutOfTheDatabaseWhileJoiningTheWaitlist() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField("name", "Preferred name", null, true, 80)));
        EventModule.StudentRegistrationEvent waiting = studentEvent(form, false, 1);
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(
                        java.util.Optional.of(studentEvent(form, false)),
                        java.util.Optional.of(waiting));
        when(eventModule.requestSeat("event-1", "student-1", 0))
                .thenReturn(new SeatRequestResult(SeatRequestOutcome.SUCCESS, null));

        var view = module.register("event-1", "student-1", Map.of("name", "Jamie"));

        assertThat(view.waitlistPosition()).isEqualTo(1);
        assertThat(view.answersSaved()).isNull();
        verify(repository, never()).upsertAnswers(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void anAnswersWriteFailureKeepsTheSeatAndReportsAnswersMissing() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField("name", "Preferred name", null, true, 80)));
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(
                        java.util.Optional.of(studentEvent(form, false)),
                        java.util.Optional.of(studentEvent(form, true)),
                        java.util.Optional.of(studentEvent(form, true)));
        when(eventModule.requestSeat("event-1", "student-1", 0))
                .thenReturn(new SeatRequestResult(
                        SeatRequestOutcome.SUCCESS, CURRENT_ENROLLMENT_VERSION));
        doThrow(new DataAccessResourceFailureException("forced answer write failure"))
                .when(repository)
                .upsertAnswers(
                        "event-1", "student-1", CURRENT_ENROLLMENT_VERSION, Map.of("name", "Jamie"));
        when(repository.find("event-1", "student-1"))
                .thenReturn(java.util.Optional.of(new Registration(
                        "event-1",
                        "student-1",
                        OLD_ENROLLMENT_VERSION,
                        Map.of("name", "Old answer"))));

        var view = module.register("event-1", "student-1", Map.of("name", "Jamie"));
        var refreshed = module.findForStudent("event-1", "student-1");

        assertThat(view.enrolled()).isTrue();
        assertThat(view.answersSaved()).isFalse();
        assertThat(view.answers()).isEmpty();
        assertThat(refreshed.answersSaved()).isFalse();
        assertThat(refreshed.answers()).isEmpty();
        verify(eventModule).requestSeat("event-1", "student-1", 0);
        verify(repository).upsertAnswers(
                "event-1", "student-1", CURRENT_ENROLLMENT_VERSION, Map.of("name", "Jamie"));
        verify(repository).find("event-1", "student-1");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void retryWritesOnlyTheMissingAnswersWithoutTouchingTheSeat() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField("name", "Preferred name", null, true, 80)));
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(java.util.Optional.of(studentEvent(form, true)));
        when(repository.upsertAnswers(
                        "event-1",
                        "student-1",
                        CURRENT_ENROLLMENT_VERSION,
                        Map.of("name", "Jamie")))
                .thenReturn(true);

        var view = module.retryAnswers("event-1", "student-1", Map.of("name", "Jamie"));

        assertThat(view.answersSaved()).isTrue();
        assertThat(view.answers()).containsEntry("name", "Jamie");
        verify(repository).upsertAnswers(
                "event-1", "student-1", CURRENT_ENROLLMENT_VERSION, Map.of("name", "Jamie"));
        verify(eventModule, never()).requestSeat(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void aDelayedRetryCannotOverwriteAnswersForALaterEnrollment() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField("name", "Preferred name", null, true, 80)));
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(
                        java.util.Optional.of(studentEvent(form, true)),
                        java.util.Optional.of(studentEvent(
                                form, true, null, 0, NEWER_ENROLLMENT_VERSION)));
        when(repository.upsertAnswers(
                        "event-1",
                        "student-1",
                        CURRENT_ENROLLMENT_VERSION,
                        Map.of("name", "Old retry")))
                .thenReturn(false);
        when(repository.find("event-1", "student-1"))
                .thenReturn(java.util.Optional.of(new Registration(
                        "event-1",
                        "student-1",
                        NEWER_ENROLLMENT_VERSION,
                        Map.of("name", "New answer"))));

        var view = module.retryAnswers(
                "event-1", "student-1", Map.of("name", "Old retry"));

        assertThat(view.answersSaved()).isTrue();
        assertThat(view.answers()).containsExactlyEntriesOf(Map.of("name", "New answer"));
        verify(repository).upsertAnswers(
                "event-1",
                "student-1",
                CURRENT_ENROLLMENT_VERSION,
                Map.of("name", "Old retry"));
    }

    @Test
    void aStudentCanReadTheirOwnSavedAnswers() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField("name", "Preferred name", null, true, 80)));
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(java.util.Optional.of(studentEvent(form, true)));
        when(repository.find("event-1", "student-1"))
                .thenReturn(java.util.Optional.of(
                        new Registration(
                                "event-1",
                                "student-1",
                                CURRENT_ENROLLMENT_VERSION,
                                Map.of("name", "Jamie"))));

        var view = module.findForStudent("event-1", "student-1");

        assertThat(view.answersSaved()).isTrue();
        assertThat(view.answers()).containsExactlyEntriesOf(Map.of("name", "Jamie"));
    }

    @Test
    void aConcurrentNewEnrollmentCannotLoseItsAnswersToTheOlderWithdrawal() {
        when(eventModule.withdraw("event-1", "student-1"))
                .thenReturn(EventModule.WithdrawalOutcome.SUCCESS);
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(java.util.Optional.of(studentEvent(RegistrationForm.empty(), true)));
        when(repository.find("event-1", "student-1"))
                .thenReturn(java.util.Optional.of(new Registration(
                        "event-1",
                        "student-1",
                        OLD_ENROLLMENT_VERSION,
                        Map.of("name", "Old answer"))));

        var view = module.withdraw("event-1", "student-1");

        assertThat(view.enrolled()).isTrue();
        assertThat(view.answersSaved()).isFalse();
        assertThat(view.answers()).isEmpty();
        verify(repository).find("event-1", "student-1");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void retryIsUnavailableWithoutAHeldSeat() {
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(java.util.Optional.of(studentEvent(RegistrationForm.empty(), false)));

        assertThatThrownBy(() -> module.retryAnswers("event-1", "student-1", Map.of()))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).upsertAnswers(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void officerAnswersDistinguishMissingFromEmptyAndCountEverySelectedOption() {
        RegistrationForm form = answersReportForm();
        OfficerRegistrationEvent event = officerEvent(form);
        when(eventModule.findRegistrationForOfficer("event-1", Set.of("club-a")))
                .thenReturn(java.util.Optional.of(event));
        when(repository.findByEventAndStudents(
                        "event-1", List.of("student-1", "student-2", "student-3")))
                .thenReturn(List.of(
                        new Registration("event-1", "student-1", Map.of(
                                "shirt", "M", "topics", List.of("AI", "Robotics"))),
                        new Registration("event-1", "student-2", Map.of())));
        when(identityAccessModule.displayNames(Set.of("student-1", "student-2", "student-3")))
                .thenReturn(Map.of(
                        "student-1", "Jamie Byrne",
                        "student-2", "Alex Chen",
                        "student-3", "Morgan Lee"));

        var view = module.findAnswersForOfficer("event-1", Set.of("club-a"), 0, 20);

        assertThat(view.items()).hasSize(3);
        assertThat(view.items().get(1).answersSaved()).isTrue();
        assertThat(view.items().get(1).answers()).isEmpty();
        assertThat(view.items().get(2).answersSaved()).isFalse();
        assertThat(view.optionCounts())
                .contains(
                        new com.campushub.registration.RegistrationModule.OptionCount("shirt", "M", 1),
                        new com.campushub.registration.RegistrationModule.OptionCount("topics", "AI", 1),
                        new com.campushub.registration.RegistrationModule.OptionCount("topics", "Robotics", 1));
    }

    @Test
    void officerDoesNotSeeAnswersFromAStudentsPreviousEnrollmentAsSaved() {
        OfficerRegistrationEvent event = new OfficerRegistrationEvent(
                "event-1",
                "Robotics Night",
                RegistrationForm.empty(),
                List.of(new OfficerEnrollment(
                        "student-1", "DIRECT", NOW, CURRENT_ENROLLMENT_VERSION)));
        when(eventModule.findRegistrationForOfficer("event-1", Set.of("club-a")))
                .thenReturn(java.util.Optional.of(event));
        when(repository.findByEventAndStudents("event-1", List.of("student-1")))
                .thenReturn(List.of(new Registration(
                        "event-1", "student-1", OLD_ENROLLMENT_VERSION, Map.of("name", "Old answer"))));
        when(identityAccessModule.displayNames(Set.of("student-1")))
                .thenReturn(Map.of("student-1", "Jamie Byrne"));

        var view = module.findAnswersForOfficer("event-1", Set.of("club-a"), 0, 20);

        assertThat(view.items()).singleElement().satisfies(answer -> {
            assertThat(answer.answersSaved()).isFalse();
            assertThat(answer.answers()).isEmpty();
        });
    }

    @Test
    void csvUsesTheBuiltFieldOrderAndEscapesAnswerValues() {
        RegistrationForm form = answersReportForm();
        when(eventModule.findRegistrationForOfficer("event-1", Set.of("club-a")))
                .thenReturn(java.util.Optional.of(officerEvent(form)));
        when(repository.findByEventAndStudents(
                        "event-1", List.of("student-1", "student-2", "student-3")))
                .thenReturn(List.of(new Registration(
                        "event-1",
                        "student-1",
                        Map.of("shirt", "M", "topics", List.of("AI", "Robotics")))));
        when(identityAccessModule.displayNames(Set.of("student-1", "student-2", "student-3")))
                .thenReturn(Map.of("student-1", "Byrne, Jamie"));

        String csv = module.exportAnswersCsv("event-1", Set.of("club-a"));

        assertThat(csv.lines().toList())
                .containsExactly(
                        "Student,Route in,Answers status,T-shirt,Topics",
                        "\"Byrne, Jamie\",Direct,Saved,M,\"AI; Robotics\"",
                        "student-2,Direct,Missing,,",
                        "student-3,Promoted,Missing,,");
    }

    @Test
    void csvEscapesQuotesAndLineBreaksAndKeepsAnUnknownRouteReadable() {
        OfficerRegistrationEvent event = new OfficerRegistrationEvent(
                "event-1",
                "Robotics Night",
                RegistrationForm.empty(),
                List.of(
                        new OfficerEnrollment("student-1", "LEGACY", NOW.minusSeconds(3)),
                        new OfficerEnrollment("student-2", "DIRECT", NOW.minusSeconds(2)),
                        new OfficerEnrollment("student-3", "DIRECT", NOW.minusSeconds(1))));
        when(eventModule.findRegistrationForOfficer("event-1", Set.of("club-a")))
                .thenReturn(java.util.Optional.of(event));
        when(repository.findByEventAndStudents(
                        "event-1", List.of("student-1", "student-2", "student-3")))
                .thenReturn(List.of());
        when(identityAccessModule.displayNames(Set.of("student-1", "student-2", "student-3")))
                .thenReturn(Map.of(
                        "student-1", "Jamie \"JJ\"",
                        "student-2", "Line\nbreak",
                        "student-3", "Carriage\rreturn"));

        String csv = module.exportAnswersCsv("event-1", Set.of("club-a"));

        assertThat(csv)
                .contains("\"Jamie \"\"JJ\"\"\",LEGACY,Missing")
                .contains("\"Line\nbreak\",Direct,Missing")
                .contains("\"Carriage\rreturn\",Direct,Missing");
    }

    private static RegistrationForm answersReportForm() {
        return new RegistrationForm(List.of(
                new SingleChoiceField("shirt", "T-shirt", null, false, List.of("S", "M", "L")),
                new MultipleChoiceField(
                        "topics", "Topics", null, false, List.of("AI", "Robotics"))));
    }

    private static Stream<Arguments> conflictingSeatOutcomes() {
        return Stream.of(
                Arguments.of(SeatRequestOutcome.EVENT_CANCELLED, ErrorCode.EVENT_CANCELLED),
                Arguments.of(SeatRequestOutcome.EVENT_STARTED, ErrorCode.EVENT_STARTED),
                Arguments.of(SeatRequestOutcome.REGISTRATION_NOT_OPEN, ErrorCode.REGISTRATION_NOT_OPEN),
                Arguments.of(SeatRequestOutcome.REGISTRATION_CLOSED, ErrorCode.REGISTRATION_CLOSED),
                Arguments.of(SeatRequestOutcome.ALREADY_ENROLLED, ErrorCode.ALREADY_ENROLLED),
                Arguments.of(SeatRequestOutcome.ALREADY_WAITLISTED, ErrorCode.ALREADY_WAITLISTED),
                Arguments.of(SeatRequestOutcome.EVENT_FULL, ErrorCode.EVENT_FULL));
    }

    private static OfficerRegistrationEvent officerEvent(RegistrationForm form) {
        return new OfficerRegistrationEvent(
                "event-1",
                "Robotics Night",
                form,
                List.of(
                        new OfficerEnrollment("student-1", "DIRECT", NOW.minusSeconds(3)),
                        new OfficerEnrollment("student-2", "DIRECT", NOW.minusSeconds(2)),
                        new OfficerEnrollment("student-3", "PROMOTED", NOW.minusSeconds(1))));
    }

    private void assertInvalid(
            RegistrationForm form,
            Map<String, Object> answers,
            ErrorCode code,
            String fieldId,
            String message) {
        when(eventModule.findRegistrationForStudent("event-1", "student-1"))
                .thenReturn(java.util.Optional.of(studentEvent(form, false)));

        assertThatThrownBy(() -> module.register("event-1", "student-1", answers))
                .isInstanceOf(FormValidationException.class)
                .satisfies(exception -> {
                    FormValidationException validation = (FormValidationException) exception;
                    assertThat(validation.code()).isEqualTo(code);
                    assertThat(validation.fieldErrors()).containsEntry(fieldId, message);
                });
        verify(eventModule, never()).requestSeat(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    private static EventModule.StudentRegistrationEvent studentEvent(
            RegistrationForm form, boolean enrolled) {
        return studentEvent(form, enrolled, null);
    }

    private static EventModule.StudentRegistrationEvent studentEvent(
            RegistrationForm form, boolean enrolled, Integer waitlistPosition) {
        return studentEvent(form, enrolled, waitlistPosition, 0);
    }

    private static EventModule.StudentRegistrationEvent studentEvent(
            RegistrationForm form, boolean enrolled, Integer waitlistPosition, int formRevision) {
        return studentEvent(
                form,
                enrolled,
                waitlistPosition,
                formRevision,
                enrolled ? CURRENT_ENROLLMENT_VERSION : null);
    }

    private static EventModule.StudentRegistrationEvent studentEvent(
            RegistrationForm form,
            boolean enrolled,
            Integer waitlistPosition,
            int formRevision,
            Long enrollmentVersion) {
        return new EventModule.StudentRegistrationEvent(
                "event-1",
                "club-a",
                "Robotics Night",
                "Build things",
                "REGISTRATION_OPEN",
                NOW.minusSeconds(10),
                NOW.plusSeconds(10),
                NOW.plusSeconds(20),
                NOW.plusSeconds(30),
                5,
                0,
                0,
                enrolled,
                enrolled ? "DIRECT" : null,
                enrollmentVersion,
                waitlistPosition,
                form,
                formRevision);
    }
}
