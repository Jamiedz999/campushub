package com.campushub.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One id per request, on every log line the request produces and on the response it returns.
 *
 * <p>It is the handle that makes the redacted logs usable: once identifiers are masked, a support
 * conversation cannot start from "the Student whose id is…", so it starts from the id the browser was
 * handed instead. Nothing about a correlation id identifies anybody — it is minted per request and
 * stored nowhere.
 *
 * <p>Ordered ahead of everything, Spring Security included, so that a 401 from the entry point, a 403
 * from CSRF and a 500 from a filter all carry the header too. Anything that logged before this filter
 * ran would log without an id, which is exactly the request you would want to trace.
 *
 * <p>An inbound {@code X-Correlation-Id} is deliberately ignored rather than honoured. Core is one
 * instance with nothing in front of it, so there is no upstream whose id would be worth keeping — and
 * an id that a caller chooses is an id that goes into log lines, which is how a caller writes their
 * own. Putting a proxy in front of this would be a reason to revisit that, and a deliberate one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Correlation-Id";
    static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = UUID.randomUUID().toString();
        // Set before the chain rather than after it: by the time an error response has been committed,
        // adding a header no longer does anything.
        response.setHeader(HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
