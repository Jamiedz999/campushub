package com.campushub.registration.internal;

import com.campushub.event.EventModule;
import com.campushub.event.EventModule.FormField;
import com.campushub.event.EventModule.MultipleChoiceField;
import com.campushub.event.EventModule.OfficerEnrollment;
import com.campushub.event.EventModule.OfficerRegistrationEvent;
import com.campushub.event.EventModule.SeatRequestOutcome;
import com.campushub.event.EventModule.SeatRequestResult;
import com.campushub.event.EventModule.SingleChoiceField;
import com.campushub.event.EventModule.StudentRegistrationEvent;
import com.campushub.event.EventModule.WithdrawalOutcome;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.registration.RegistrationModule;
import com.campushub.registration.domain.Registration;
import com.campushub.registration.persistence.RegistrationRepository;
import com.campushub.shared.ConflictException;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class RegistrationModuleImpl implements RegistrationModule {

    private static final int MAX_PAGE_SIZE = 100;

    private final EventModule eventModule;
    private final RegistrationRepository repository;
    private final IdentityAccessModule identityAccessModule;

    RegistrationModuleImpl(
            EventModule eventModule,
            RegistrationRepository repository,
            IdentityAccessModule identityAccessModule) {
        this.eventModule = eventModule;
        this.repository = repository;
        this.identityAccessModule = identityAccessModule;
    }

    @Override
    public StudentRegistrationView findForStudent(String eventId, String studentId) {
        return toStudentView(currentEvent(eventId, studentId), studentId);
    }

    @Override
    public StudentRegistrationView register(
            String eventId, String studentId, Map<String, Object> submittedAnswers) {
        while (true) {
            StudentRegistrationEvent event = currentEvent(eventId, studentId);
            Map<String, Object> answers =
                    FormAnswersValidator.validate(event.registrationForm(), submittedAnswers);
            SeatRequestResult result =
                    eventModule.requestSeat(
                            eventId, studentId, event.registrationFormRevision());
            if (result.outcome() == SeatRequestOutcome.FORM_CHANGED) {
                continue;
            }
            handle(result.outcome());

            StudentRegistrationEvent current = currentEvent(eventId, studentId);
            if (current.enrolled()
                    && Objects.equals(current.enrollmentVersion(), result.enrollmentVersion())) {
                try {
                    if (repository.upsertAnswers(
                            eventId, studentId, result.enrollmentVersion(), answers)) {
                        return studentView(current, true, answers);
                    }
                    return findForStudent(eventId, studentId);
                } catch (org.springframework.dao.DataAccessException ignored) {
                    return studentView(current, false, Map.of());
                }
            }
            return toStudentView(current, studentId);
        }
    }

    @Override
    public StudentRegistrationView retryAnswers(
            String eventId, String studentId, Map<String, Object> submittedAnswers) {
        StudentRegistrationEvent event = currentEvent(eventId, studentId);
        if (!event.enrolled()) {
            throw new NotFoundException("The Student does not hold a Seat for this Event.");
        }
        Map<String, Object> answers =
                FormAnswersValidator.validate(event.registrationForm(), submittedAnswers);
        if (repository.upsertAnswers(eventId, studentId, event.enrollmentVersion(), answers)) {
            return studentView(event, true, answers);
        }
        return findForStudent(eventId, studentId);
    }

    @Override
    public StudentRegistrationView withdraw(String eventId, String studentId) {
        handle(eventModule.withdraw(eventId, studentId));
        return findForStudent(eventId, studentId);
    }

    @Override
    public StudentRegistrationPage findEnrolled(String studentId, int page, int size) {
        EventModule.StudentRegistrationEventPage result =
                eventModule.findEnrolledRegistrations(studentId, page, size);
        return new StudentRegistrationPage(
                result.items().stream()
                        .map(event -> toStudentView(event, studentId))
                        .toList(),
                result.page(),
                result.size(),
                result.total());
    }

    @Override
    public OfficerAnswersView findAnswersForOfficer(
            String eventId, Set<String> callerOfficerClubIds, int page, int size) {
        OfficerRegistrationEvent event = officerEvent(eventId, callerOfficerClubIds);
        ReportData report = reportData(event);
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        int from = Math.min(normalizedPage * normalizedSize, event.enrolled().size());
        int to = Math.min(from + normalizedSize, event.enrolled().size());
        List<OfficerAnswer> items = event.enrolled().subList(from, to).stream()
                .map(enrollment -> officerAnswer(enrollment, report))
                .toList();
        return new OfficerAnswersView(
                event.id(),
                event.title(),
                event.registrationForm(),
                items,
                normalizedPage,
                normalizedSize,
                event.enrolled().size(),
                optionCounts(event.registrationForm().fields(), report.registrationsByStudentId()));
    }

    @Override
    public String exportAnswersCsv(String eventId, Set<String> callerOfficerClubIds) {
        OfficerRegistrationEvent event = officerEvent(eventId, callerOfficerClubIds);
        ReportData report = reportData(event);
        List<String> headers = new ArrayList<>(List.of("Student", "Route in", "Answers status"));
        event.registrationForm().fields().stream().map(FormField::label).forEach(headers::add);

        StringBuilder csv = new StringBuilder(csvRow(headers)).append('\n');
        for (OfficerEnrollment enrollment : event.enrolled()) {
            OfficerAnswer answer = officerAnswer(enrollment, report);
            List<String> values = new ArrayList<>(List.of(
                    answer.studentDisplayName(),
                    displayRoute(answer.enrollmentVia()),
                    answer.answersSaved() ? "Saved" : "Missing"));
            for (FormField field : event.registrationForm().fields()) {
                values.add(csvAnswer(answer.answers().get(field.fieldId())));
            }
            csv.append(csvRow(values)).append('\n');
        }
        return csv.toString();
    }

    private OfficerRegistrationEvent officerEvent(
            String eventId, Set<String> callerOfficerClubIds) {
        return eventModule
                .findRegistrationForOfficer(eventId, callerOfficerClubIds)
                .orElseThrow(() -> new NotFoundException(
                        "No such Event, or the caller is not its Club's Officer."));
    }

    private ReportData reportData(OfficerRegistrationEvent event) {
        List<String> studentIds = event.enrolled().stream()
                .map(OfficerEnrollment::studentId)
                .toList();
        Map<String, OfficerEnrollment> currentEnrollments = event.enrolled().stream()
                .collect(Collectors.toUnmodifiableMap(
                        OfficerEnrollment::studentId, Function.identity()));
        Map<String, Registration> registrationsByStudentId = repository
                .findByEventAndStudents(event.id(), studentIds)
                .stream()
                .filter(registration -> matchesCurrentEnrollment(registration, currentEnrollments))
                .collect(Collectors.toUnmodifiableMap(Registration::getStudentId, Function.identity()));
        Map<String, String> displayNames = identityAccessModule.displayNames(Set.copyOf(studentIds));
        return new ReportData(registrationsByStudentId, displayNames);
    }

    private static OfficerAnswer officerAnswer(OfficerEnrollment enrollment, ReportData report) {
        Registration registration = report.registrationsByStudentId().get(enrollment.studentId());
        return new OfficerAnswer(
                enrollment.studentId(),
                report.displayNames().getOrDefault(enrollment.studentId(), enrollment.studentId()),
                enrollment.enrollmentVia(),
                enrollment.enrolledAt(),
                registration != null,
                registration == null ? Map.of() : registration.getAnswers());
    }

    private static List<OptionCount> optionCounts(
            List<FormField> fields, Map<String, Registration> registrations) {
        List<OptionCount> counts = new ArrayList<>();
        for (FormField field : fields) {
            if (field instanceof SingleChoiceField choice) {
                for (String option : choice.options()) {
                    long count = registrations.values().stream()
                            .map(Registration::getAnswers)
                            .map(answers -> answers.get(choice.fieldId()))
                            .filter(option::equals)
                            .count();
                    counts.add(new OptionCount(choice.fieldId(), option, count));
                }
            } else if (field instanceof MultipleChoiceField choice) {
                for (String option : choice.options()) {
                    long count = registrations.values().stream()
                            .map(Registration::getAnswers)
                            .map(answers -> answers.get(choice.fieldId()))
                            .filter(List.class::isInstance)
                            .map(List.class::cast)
                            .filter(options -> options.contains(option))
                            .count();
                    counts.add(new OptionCount(choice.fieldId(), option, count));
                }
            }
        }
        return List.copyOf(counts);
    }

    private static String displayRoute(String route) {
        return switch (route) {
            case "DIRECT" -> "Direct";
            case "PROMOTED" -> "Promoted";
            default -> route;
        };
    }

    private static String csvAnswer(Object answer) {
        if (answer == null) {
            return "";
        }
        if (answer instanceof List<?> answers) {
            return answers.stream().map(Object::toString).collect(Collectors.joining("; "));
        }
        return answer.toString();
    }

    private static String csvRow(List<String> values) {
        return values.stream().map(RegistrationModuleImpl::escapeCsv).collect(Collectors.joining(","));
    }

    private static String escapeCsv(String value) {
        if (value.contains(",")
                || value.contains(";")
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r")) {
            return forceQuote(value);
        }
        return value;
    }

    private static String forceQuote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private record ReportData(
            Map<String, Registration> registrationsByStudentId, Map<String, String> displayNames) {

        private ReportData {
            registrationsByStudentId = new LinkedHashMap<>(registrationsByStudentId);
            displayNames = Map.copyOf(displayNames);
        }
    }

    private StudentRegistrationEvent currentEvent(String eventId, String studentId) {
        return eventModule
                .findRegistrationForStudent(eventId, studentId)
                .orElseThrow(() -> new NotFoundException("No such Event."));
    }

    private StudentRegistrationView toStudentView(
            StudentRegistrationEvent event, String studentId) {
        java.util.Optional<Registration> registration = event.enrolled()
                ? repository.find(event.id(), studentId)
                        .filter(saved -> matchesEnrollment(saved, event.enrollmentVersion()))
                : java.util.Optional.empty();
        Boolean answersSaved = event.enrolled() ? registration.isPresent() : null;
        Map<String, Object> answers = registration
                .map(Registration::getAnswers)
                .orElseGet(Map::of);
        return studentView(event, answersSaved, answers);
    }

    private static StudentRegistrationView studentView(
            StudentRegistrationEvent event,
            Boolean answersSaved,
            Map<String, Object> answers) {
        return new StudentRegistrationView(
                event.id(),
                event.clubId(),
                event.title(),
                event.description(),
                event.phase(),
                event.registrationOpensAt(),
                event.registrationClosesAt(),
                event.startsAt(),
                event.endsAt(),
                event.capacity(),
                event.enrolledCount(),
                event.waitlistCount(),
                event.enrolled(),
                event.enrollmentVia(),
                event.waitlistPosition(),
                event.registrationForm(),
                answersSaved,
                answers);
    }

    private static boolean matchesEnrollment(
            Registration registration, Long enrollmentVersion) {
        return Objects.equals(registration.getEnrollmentVersion(), enrollmentVersion);
    }

    private static boolean matchesCurrentEnrollment(
            Registration registration,
            Map<String, OfficerEnrollment> currentEnrollments) {
        OfficerEnrollment enrollment = currentEnrollments.get(registration.getStudentId());
        return enrollment != null && matchesEnrollment(registration, enrollment.enrollmentVersion());
    }

    private static void handle(SeatRequestOutcome outcome) {
        switch (outcome) {
            case SUCCESS, FORM_CHANGED -> {
                // FORM_CHANGED is consumed by the retry loop before this method.
            }
            case NOT_FOUND -> throw new NotFoundException("No such Event.");
            case EVENT_CANCELLED -> conflict(ErrorCode.EVENT_CANCELLED, "This Event was cancelled.");
            case EVENT_STARTED -> conflict(ErrorCode.EVENT_STARTED, "This Event has already started.");
            case REGISTRATION_NOT_OPEN ->
                conflict(ErrorCode.REGISTRATION_NOT_OPEN, "Registration is not open yet.");
            case REGISTRATION_CLOSED ->
                conflict(ErrorCode.REGISTRATION_CLOSED, "Registration has closed.");
            case ALREADY_ENROLLED ->
                conflict(ErrorCode.ALREADY_ENROLLED, "You are already enrolled in this Event.");
            case ALREADY_WAITLISTED ->
                conflict(ErrorCode.ALREADY_WAITLISTED, "You are already on the Waitlist for this Event.");
            case EVENT_FULL -> conflict(ErrorCode.EVENT_FULL, "This Event is full.");
        }
    }

    private static void handle(WithdrawalOutcome outcome) {
        switch (outcome) {
            case SUCCESS -> {
                // Nothing to do — the caller re-reads the current view.
            }
            case NOT_FOUND -> throw new NotFoundException("No such Event.");
            case EVENT_CANCELLED -> conflict(ErrorCode.EVENT_CANCELLED, "This Event was cancelled.");
            case EVENT_STARTED -> conflict(ErrorCode.EVENT_STARTED, "This Event has already started.");
        }
    }

    private static void conflict(ErrorCode code, String detail) {
        throw new ConflictException(code, detail);
    }
}
