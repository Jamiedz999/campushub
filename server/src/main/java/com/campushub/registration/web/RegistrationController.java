package com.campushub.registration.web;

import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.registration.RegistrationModule;
import com.campushub.registration.RegistrationModule.OfficerAnswersView;
import com.campushub.registration.RegistrationModule.StudentRegistrationPage;
import com.campushub.registration.RegistrationModule.StudentRegistrationView;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RegistrationController {

    private static final MediaType CSV = new MediaType("text", "csv");

    private final IdentityAccessModule identityAccessModule;
    private final RegistrationModule registrationModule;

    RegistrationController(
            IdentityAccessModule identityAccessModule, RegistrationModule registrationModule) {
        this.identityAccessModule = identityAccessModule;
        this.registrationModule = registrationModule;
    }

    @GetMapping("/api/events/{eventId}/registration")
    StudentRegistrationView get(@PathVariable String eventId) {
        CurrentActor actor = identityAccessModule.currentActor();
        return registrationModule.findForStudent(eventId, actor.accountId());
    }

    @PostMapping("/api/events/{eventId}/registration")
    StudentRegistrationView register(
            @PathVariable String eventId,
            @RequestBody(required = false) SubmitRegistrationRequest request) {
        CurrentActor actor = identityAccessModule.currentActor();
        return registrationModule.register(eventId, actor.accountId(), answers(request));
    }

    @PutMapping("/api/events/{eventId}/registration/answers")
    StudentRegistrationView retryAnswers(
            @PathVariable String eventId, @RequestBody SubmitRegistrationRequest request) {
        CurrentActor actor = identityAccessModule.currentActor();
        return registrationModule.retryAnswers(eventId, actor.accountId(), answers(request));
    }

    @DeleteMapping("/api/events/{eventId}/registration")
    StudentRegistrationView withdraw(@PathVariable String eventId) {
        CurrentActor actor = identityAccessModule.currentActor();
        return registrationModule.withdraw(eventId, actor.accountId());
    }

    @GetMapping("/api/events/mine")
    StudentRegistrationPage mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentActor actor = identityAccessModule.currentActor();
        return registrationModule.findEnrolled(actor.accountId(), page, size);
    }

    @GetMapping("/api/events/{eventId}/registration-answers")
    OfficerAnswersView officerAnswers(
            @PathVariable String eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentActor actor = identityAccessModule.currentActor();
        return registrationModule.findAnswersForOfficer(
                eventId, actor.officerClubIds(), page, size);
    }

    @GetMapping(value = "/api/events/{eventId}/registration-answers/csv", produces = "text/csv")
    ResponseEntity<String> answersCsv(@PathVariable String eventId) {
        CurrentActor actor = identityAccessModule.currentActor();
        String csv = registrationModule.exportAnswersCsv(eventId, actor.officerClubIds());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("event-" + eventId + "-registration-answers.csv")
                .build());
        return ResponseEntity.ok().headers(headers).contentType(CSV).body(csv);
    }

    private static Map<String, Object> answers(SubmitRegistrationRequest request) {
        return request == null ? Map.of() : request.answerMap();
    }
}
