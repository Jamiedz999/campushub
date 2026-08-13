package com.campushub.registration;

import com.campushub.event.EventModule.RegistrationForm;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns Registration documents and the full answer-validation/write workflow. */
public interface RegistrationModule {

    record StudentRegistrationView(
            String id,
            String clubId,
            String title,
            String description,
            String phase,
            Instant registrationOpensAt,
            Instant registrationClosesAt,
            Instant startsAt,
            Instant endsAt,
            int capacity,
            int enrolledCount,
            int waitlistCount,
            boolean enrolled,
            String enrollmentVia,
            Integer waitlistPosition,
            RegistrationForm registrationForm,
            Boolean answersSaved,
            Map<String, Object> answers) {

        public StudentRegistrationView {
            answers = Map.copyOf(answers);
        }
    }

    record StudentRegistrationPage(
            List<StudentRegistrationView> items, int page, int size, long total) {

        public StudentRegistrationPage {
            items = List.copyOf(items);
        }
    }

    StudentRegistrationView findForStudent(String eventId, String studentId);

    StudentRegistrationView register(String eventId, String studentId, Map<String, Object> answers);

    StudentRegistrationView retryAnswers(String eventId, String studentId, Map<String, Object> answers);

    StudentRegistrationView withdraw(String eventId, String studentId);

    StudentRegistrationPage findEnrolled(String studentId, int page, int size);

    OfficerAnswersView findAnswersForOfficer(
            String eventId, Set<String> callerOfficerClubIds, int page, int size);

    String exportAnswersCsv(String eventId, Set<String> callerOfficerClubIds);

    record OfficerAnswer(
            String studentId,
            String studentDisplayName,
            String enrollmentVia,
            Instant enrolledAt,
            boolean answersSaved,
            Map<String, Object> answers) {

        public OfficerAnswer {
            answers = Map.copyOf(answers);
        }
    }

    record OptionCount(String fieldId, String option, long count) {}

    record OfficerAnswersView(
            String eventId,
            String eventTitle,
            RegistrationForm registrationForm,
            List<OfficerAnswer> items,
            int page,
            int size,
            long total,
            List<OptionCount> optionCounts) {

        public OfficerAnswersView {
            items = List.copyOf(items);
            optionCounts = List.copyOf(optionCounts);
        }
    }
}
