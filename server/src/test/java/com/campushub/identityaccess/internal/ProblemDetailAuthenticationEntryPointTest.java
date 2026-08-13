package com.campushub.identityaccess.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.ObjectMapper;

class ProblemDetailAuthenticationEntryPointTest {

    private final ProblemDetailAuthenticationEntryPoint entryPoint =
            new ProblemDetailAuthenticationEntryPoint(new ProblemResponseWriter(new ObjectMapper()));

    @Test
    void respondsWithUnauthorizedAndUnauthenticatedCodeInsteadOfRedirecting() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                new MockHttpServletRequest("GET", "/api/auth/me"),
                response,
                new InsufficientAuthenticationException("no session"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"");
    }
}
