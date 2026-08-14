package com.campushub.identityaccess.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

// Form login with opaque, same-origin, Spring-Session-Mongo-backed sessions and CSRF enabled — no JWT.
// See docs/adr/12-lock-core-technical-baseline.md. Authorization (which role may do what) is
// deliberately NOT expressed here with hasAuthority()/hasRole(): every business authorization failure
// in this app is 404, never 403 (docs/adr/08-define-roles-and-resource-authorization.md), so role and
// ownership checks live in application code, which can throw NotFoundException. This filter chain only
// answers "is there a session at all" (401 UNAUTHENTICATED when not) and CSRF (Spring's own default
// 403, never seen by a well-behaved frontend that reads the XSRF-TOKEN cookie back).
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JsonAuthenticationSuccessHandler successHandler,
            JsonAuthenticationFailureHandler failureHandler,
            JsonLogoutSuccessHandler logoutSuccessHandler,
            ProblemDetailAuthenticationEntryPoint authenticationEntryPoint)
            throws Exception {
        http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, "/api/auth/login")
                        .permitAll()
                        // Build/version/server-time only — the same operational-status category as
                        // /actuator/health, not a business route, and the Core build contract's own
                        // same-origin smoke test (TECHNICAL-BASELINE.md, CI) curls it unauthenticated.
                        .requestMatchers(HttpMethod.GET, "/api/system")
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        // The WebSocket handshake is an ordinary GET, so it is the last chance to
                        // require a session before a connection outlives the request that opened it.
                        // Which scope that session may then watch is decided in realtime's handshake
                        // interceptor, which can answer 404 the way the rest of the system does.
                        .requestMatchers("/ws/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                .logout(logout ->
                        logout.logoutUrl("/api/auth/logout").logoutSuccessHandler(logoutSuccessHandler))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint));
        return http.build();
    }

    // The XSRF-TOKEN cookie is readable by script on purpose — that is the whole double-submit
    // mechanism, and the frontend's axios instance reads it back into the X-XSRF-TOKEN header. Its
    // other two attributes are stated rather than left to the default: SameSite=Lax for the same
    // reason as the session cookie (application.yml explains it), and Secure mirroring the request's
    // scheme, which is Spring's own default and is repeated here so that a reader of this file sees
    // both cookies decided in the same place.
    private static CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }
}
