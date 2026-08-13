package com.campushub.identityaccess.internal;

import com.campushub.shared.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

// Replaces Spring Security's default "redirect to /login" entry point: this is an API with no login
// page to redirect to. Every unauthenticated request to a protected route gets this instead — see
// docs/planning/implementation/TECHNICAL-BASELINE.md: "Unauthenticated access to any internal route
// is refused."
@Component
class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemResponseWriter problemResponseWriter;

    ProblemDetailAuthenticationEntryPoint(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws java.io.IOException {
        problemResponseWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "Unauthenticated",
                "Authentication is required.",
                ErrorCode.UNAUTHENTICATED);
    }
}
