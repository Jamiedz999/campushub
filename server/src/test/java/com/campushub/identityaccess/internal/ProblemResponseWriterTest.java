package com.campushub.identityaccess.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.shared.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class ProblemResponseWriterTest {

    private final ProblemResponseWriter writer = new ProblemResponseWriter(new ObjectMapper());

    @Test
    void writesAProblemJsonBodyMatchingTheContract() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, HttpStatus.UNAUTHORIZED, "Unauthenticated", "boom", ErrorCode.UNAUTHENTICATED);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        String body = response.getContentAsString();
        assertThat(body).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(body).contains("\"title\":\"Unauthenticated\"");
        assertThat(body).contains("\"detail\":\"boom\"");
        assertThat(body).contains("\"instance\":\"/api/auth/me\"");
    }
}
