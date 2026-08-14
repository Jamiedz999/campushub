package com.campushub.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void aRequestWithoutOneIsGivenOneAndCarriesItBackOnTheResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        String correlationId = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(correlationId).isNotBlank();
    }

    @Test
    void twoRequestsWithoutOneAreNotGivenTheSameOne() throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), first, new MockFilterChain());
        filter.doFilter(new MockHttpServletRequest(), second, new MockFilterChain());

        assertThat(first.getHeader(CorrelationIdFilter.HEADER))
                .isNotEqualTo(second.getHeader(CorrelationIdFilter.HEADER));
    }

    // The id is always this server's, never the caller's. An id chosen by a caller is an id that goes
    // straight into log lines, which is how a caller writes their own — and there is no proxy in front
    // of this application whose id would be worth keeping instead.
    @Test
    void anIdSuppliedByTheCallerIsIgnoredRatherThanEchoedBack() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "abc\nINFO  Nothing to see here");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER))
                .doesNotContain("Nothing to see here")
                .isNotBlank();
    }

    @Test
    void everyLogLineWrittenWhileTheRequestRunsCanCarryTheSameId() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> seenByTheChain = new ArrayList<>();
        FilterChain chain = (request, ignored) -> seenByTheChain.add(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(seenByTheChain).containsExactly(response.getHeader(CorrelationIdFilter.HEADER));
    }

    @Test
    void theIdIsClearedAfterwardsSoItCannotLeakOntoTheNextRequestOnTheSameThread() throws Exception {
        FilterChain explodes = (request, response) -> {
            throw new IllegalStateException("the handler blew up");
        };

        try {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), explodes);
        } catch (IllegalStateException expected) {
            // The point of the test is what is left behind, not what was thrown.
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void theHeaderIsSetBeforeTheChainRunsSoAnErrorResponseStillCarriesIt() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> seenByTheChain = new ArrayList<>();
        FilterChain chain =
                (request, ignored) -> seenByTheChain.add(response.getHeader(CorrelationIdFilter.HEADER));

        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(seenByTheChain).doesNotContainNull();
    }
}
