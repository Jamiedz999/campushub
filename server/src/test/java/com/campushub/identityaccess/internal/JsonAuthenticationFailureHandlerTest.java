package com.campushub.identityaccess.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

class JsonAuthenticationFailureHandlerTest {

    private final JsonAuthenticationFailureHandler handler =
            new JsonAuthenticationFailureHandler(new ProblemResponseWriter(new ObjectMapper()));

    @Test
    void respondsWithUnauthorizedAndInvalidCredentialsCodeInsteadOfRedirecting() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                new MockHttpServletRequest("POST", "/api/auth/login"), response, new BadCredentialsException("bad"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getContentAsString()).contains("\"code\":\"INVALID_CREDENTIALS\"");
    }
}
