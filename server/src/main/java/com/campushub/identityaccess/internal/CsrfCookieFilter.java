package com.campushub.identityaccess.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

// Spring Security defers CSRF token generation until something actually reads it, so a plain GET that
// never touches a form would otherwise never cause the XSRF-TOKEN cookie to be written. This forces
// that read on every request, which is what lets the SPA read the cookie before the user's very first
// login attempt. See Spring Security's documented "Single Page Application" CSRF integration.
class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
