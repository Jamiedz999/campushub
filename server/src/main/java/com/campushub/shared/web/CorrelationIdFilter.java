package com.campushub.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One id per request, on every log line the request produces and on the response it returns.
 *
 * <p>It is the handle that makes {@link com.campushub.shared.logging.LogRedaction redacted logs}
 * usable: once identifiers are masked, a support conversation cannot start from "the Student whose id
 * is…", so it starts from the id the browser was handed instead. Nothing about a correlation id
 * identifies anybody — it is minted per request and stored nowhere.
 *
 * <p>Ordered ahead of everything, Spring Security included, so that a 401 from the entry point, a 403
 * from CSRF and a 500 from a filter all carry the header too. Anything that logged before this filter
 * ran would log without an id, which is exactly the request you would want to trace.
 *
 * <p>An inbound id is honoured so that a request keeps one id across a proxy or a load test, but only
 * after it is checked against {@link #ACCEPTABLE}: an id goes into log lines, and a header holding a
 * newline is how a caller writes their own. An id that fails the check is replaced rather than
 * cleaned, because a half-scrubbed id is neither the caller's nor traceable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Correlation-Id";
    static final String MDC_KEY = "correlationId";

    private static final Pattern ACCEPTABLE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = correlationIdOf(request);
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

    private static String correlationIdOf(HttpServletRequest request) {
        String supplied = request.getHeader(HEADER);
        if (supplied != null && ACCEPTABLE.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }
}
