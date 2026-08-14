package com.campushub.shared.web;

import com.campushub.shared.ConflictException;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.EventNotEditableException;
import com.campushub.shared.FormValidationException;
import com.campushub.shared.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
        problem.setTitle("Validation Failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ErrorCode.VALIDATION_FAILED);
        return problem;
    }

    @ExceptionHandler(FormValidationException.class)
    ProblemDetail handleFormValidation(FormValidationException exception, HttpServletRequest request) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Form Validation Failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", exception.code());
        problem.setProperty("fieldErrors", exception.fieldErrors());
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found.");
        problem.setTitle("Not Found");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ErrorCode.NOT_FOUND);
        return problem;
    }

    @ExceptionHandler(EventNotEditableException.class)
    ProblemDetail handleEventNotEditable(EventNotEditableException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Event Not Editable");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ErrorCode.EVENT_NOT_EDITABLE);
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", exception.code());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ErrorCode.INTERNAL_ERROR);
        return problem;
    }
}
