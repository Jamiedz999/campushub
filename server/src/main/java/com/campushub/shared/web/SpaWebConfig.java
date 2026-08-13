package com.campushub.shared.web;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

// Browser-history client routes (e.g. /events/123) must resolve to the app shell on a full
// navigation, not 404. Any path that isn't a real static asset falls back to index.html; a
// RequestMappingHandlerMapping (API controllers, actuator) is tried first and always wins over
// this resource handler, so /api and /actuator paths are never shadowed by the fallback.
@Configuration
class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = super.getResource(resourcePath, location);
                        return requested != null ? requested : new ClassPathResource("/static/index.html");
                    }
                });
    }
}
