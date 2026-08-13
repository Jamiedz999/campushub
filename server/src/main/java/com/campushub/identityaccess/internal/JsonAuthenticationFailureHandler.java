package com.campushub.identityaccess.internal;

import com.campushub.shared.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

// Spring's DaoAuthenticationProvider hides UsernameNotFoundException behind BadCredentialsException by
// default, so this single handler never distinguishes "no such account" from "wrong password" —
// deliberately, to avoid leaking which emails are registered.
@Component
class JsonAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final ProblemResponseWriter problemResponseWriter;

    JsonAuthenticationFailureHandler(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws java.io.IOException {
        problemResponseWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "Invalid Credentials",
                "The email or password was incorrect.",
                ErrorCode.INVALID_CREDENTIALS);
    }
}
