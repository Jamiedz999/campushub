package com.campushub.system.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SystemControllerIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO_DB.getConnectionString() + "/campushub-test");
    }

    @LocalServerPort
    private int port;

    private final HttpClient client =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    @Test
    void returnsRoleFreeSystemStatus() throws Exception {
        HttpResponse<String> response = get("/api/system");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        assertThat(response.body()).contains("\"serverTime\"");
    }

    @Test
    void generatesTheOpenApiDocument() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"openapi\"");
        assertThat(response.body()).contains("/api/system");
    }

    @Test
    void servesSwaggerUi() throws Exception {
        HttpResponse<String> response = get("/swagger-ui.html");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).containsIgnoringCase("swagger");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
