package com.campushub.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.shared.ConflictException;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.EventNotEditableException;
import com.campushub.shared.FormValidationException;
import com.campushub.shared.NotFoundException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundExceptionBecomesProblemDetailWithNotFoundCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clubs/abc/officers");

        ProblemDetail problem = handler.handleNotFound(new NotFoundException("no such club"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isNotBlank();
        assertThat(problem.getDetail()).isNotBlank();
        assertThat(problem.getInstance()).isEqualTo(java.net.URI.create("/api/clubs/abc/officers"));
        assertThat(problem.getProperties()).containsEntry("code", ErrorCode.NOT_FOUND);
    }

    @Test
    void eventNotEditableExceptionBecomesProblemDetailWithEventNotEditableCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/events/abc");

        ProblemDetail problem =
                handler.handleEventNotEditable(new EventNotEditableException("cannot lower capacity"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isNotBlank();
        assertThat(problem.getDetail()).isNotBlank();
        assertThat(problem.getInstance()).isEqualTo(java.net.URI.create("/api/events/abc"));
        assertThat(problem.getProperties()).containsEntry("code", ErrorCode.EVENT_NOT_EDITABLE);
    }

    @Test
    void conflictExceptionBecomesProblemDetailWithItsOwnCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events/abc/registration");

        ProblemDetail problem = handler.handleConflict(
                new ConflictException(ErrorCode.EVENT_FULL, "This Event is full."), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isNotBlank();
        assertThat(problem.getDetail()).isNotBlank();
        assertThat(problem.getInstance()).isEqualTo(java.net.URI.create("/api/events/abc/registration"));
        assertThat(problem.getProperties()).containsEntry("code", ErrorCode.EVENT_FULL);
    }

    @Test
    void aConflictCarriesTheExtraMembersItsRefusalNeedsAlongsideTheCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events/abc/attendance");

        ProblemDetail problem = handler.handleConflict(
                new ConflictException(
                        ErrorCode.ALREADY_CHECKED_IN,
                        "You are already checked in to this Event.",
                        Map.of("at", "2026-03-20T18:04:00Z", "method", "SCANNED")),
                request);

        // `code` stays the contract; these are the facts a refusal carries that the client cannot read
        // out of `detail` — a second scan being told when the first one was.
        assertThat(problem.getProperties())
                .containsEntry("code", ErrorCode.ALREADY_CHECKED_IN)
                .containsEntry("at", "2026-03-20T18:04:00Z")
                .containsEntry("method", "SCANNED");
    }

    @Test
    void unexpectedExceptionBecomesProblemDetailWithInternalErrorCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system");

        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("boom"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isNotBlank();
        assertThat(problem.getDetail()).isNotBlank();
        assertThat(problem.getInstance()).isEqualTo(java.net.URI.create("/api/system"));
        assertThat(problem.getProperties()).containsEntry("code", ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void validationExceptionBecomesProblemDetailWithValidationFailedCode() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod(
                        "validationExceptionBecomesProblemDetailWithValidationFailedCode"),
                -1);
        MapBindingResult bindingResult = new MapBindingResult(new HashMap<>(), "target");
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events");

        ProblemDetail problem = handler.handleValidation(exception, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getInstance()).isEqualTo(java.net.URI.create("/api/events"));
        assertThat(problem.getProperties()).containsEntry("code", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void formValidationCarriesItsStableCodeAndPerFieldBreakdown() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/events/abc/registration");
        FormValidationException exception = new FormValidationException(
                ErrorCode.UNDEFINED_OPTION, Map.of("shirt", "The option 'XL' is not defined."));

        ProblemDetail problem = handler.handleFormValidation(exception, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getProperties())
                .containsEntry("code", ErrorCode.UNDEFINED_OPTION)
                .containsEntry("fieldErrors", Map.of("shirt", "The option 'XL' is not defined."));
    }
}
