package com.invoicespot.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.auth.JwtService;
import com.invoicespot.auth.session.SessionService;
import com.invoicespot.business.BusinessService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.PostgreSQLContainer;

// With no management port of its own, as in development, everything is on
// the app's port, which the public reaches. Spring then reports that port as
// the management port too, so this runs on a real port: the metrics must
// still be refused there, while health answers as before.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.jwt.access-secret=integration-test-access-secret-0123456789abcdef",
            "app.encryption.key=aW50ZWdyYXRpb24tdGVzdC1rZXktMzItYnl0ZXMhISE=",
            "management.health.mail.enabled=false",
            "app.jobs.enabled=false"
        })
@AutoConfigureObservability(tracing = false)
class MetricsOnAppPortIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Database {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16");
        }
    }

    @LocalServerPort
    private int appPort;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SessionService sessionService;

    // Nothing here sends an email, and none could reach a real mail server.
    @MockBean
    private JavaMailSender mailSender;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("on the app's own port the metrics are refused, even to someone signed in")
    void refused() throws Exception {
        String handle = "metrics-" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setEmail(handle + "@example.test");
        user.setUsername(handle);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmailVerified(true);
        userRepository.save(user);
        businessService.createPersonal(user);
        String token = jwtService.issueAccessToken(user, sessionService.start(user).session().getId());

        assertThat(get("/actuator/prometheus", null).statusCode()).isEqualTo(401);
        assertThat(get("/actuator/prometheus", token).statusCode()).isEqualTo(403);
        assertThat(get("/actuator/health", null).statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + appPort + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
