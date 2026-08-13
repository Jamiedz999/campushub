package com.campushub.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.shared.ErrorCode;
import com.campushub.shared.NotFoundException;
import java.util.HashMap;
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
}
