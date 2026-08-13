package com.campushub.identityaccess.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

class JsonAuthenticationSuccessHandlerTest {

    private final JsonAuthenticationSuccessHandler handler = new JsonAuthenticationSuccessHandler();

    @Test
    void respondsWithNoContentInsteadOfRedirecting() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, mock(Authentication.class));

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getRedirectedUrl()).isNull();
    }
}
