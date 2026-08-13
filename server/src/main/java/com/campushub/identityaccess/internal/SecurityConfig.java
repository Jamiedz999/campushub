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
        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
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
}
