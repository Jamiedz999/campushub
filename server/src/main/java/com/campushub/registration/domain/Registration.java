package com.campushub.registration.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Answers tied to one Seat incarnation. The Event module's Seat Ledger remains authoritative. */
@Document("registrations")
public class Registration {

    @Id
    private String id;

    private String eventId;

    private String studentId;

    private Long enrollmentVersion;

    private Map<String, Object> answers;

    public Registration() {}

    public Registration(String eventId, String studentId, Map<String, Object> answers) {
        this(eventId, studentId, null, answers);
    }

    public Registration(
            String eventId,
            String studentId,
            Long enrollmentVersion,
            Map<String, Object> answers) {
        this.eventId = eventId;
        this.studentId = studentId;
        this.enrollmentVersion = enrollmentVersion;
        this.answers = new LinkedHashMap<>(answers);
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getStudentId() {
        return studentId;
    }

    public Long getEnrollmentVersion() {
        return enrollmentVersion;
    }

    public Map<String, Object> getAnswers() {
        return Map.copyOf(answers);
    }
}
