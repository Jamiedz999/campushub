package com.campushub.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

// Exercises SpaWebConfig in isolation with a plain Spring MVC context rather than the full
// application — CampusHubApplication carries @EnableMongock, which needs a real Mongo connection
// bean even under Spring Boot's web-only test slices, and none of that machinery is relevant here.
class SpaWebConfigTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.register(SpaWebConfig.class, MvcInfrastructure.class);
        context.setServletContext(new MockServletContext());
        context.refresh();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void servesTheAppShellForARealStaticAsset() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("campushub-spa-fixture")));
    }

    @Test
    void fallsBackToTheAppShellForAnUnmatchedBrowserHistoryRoute() throws Exception {
        mockMvc.perform(get("/events/123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("campushub-spa-fixture")));
    }

    @EnableWebMvc
    static class MvcInfrastructure {}
}
