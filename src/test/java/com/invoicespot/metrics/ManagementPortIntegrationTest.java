package com.invoicespot.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.auth.JwtService;
import com.invoicespot.auth.session.SessionService;
import com.invoicespot.business.BusinessService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;

// The app on real ports, with the management port apart as MANAGEMENT_PORT
// sets it: Prometheus reads the metrics there, and the app's port refuses
// them. Tests leave metrics off unless asked, so they are turned on here.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.jwt.access-secret=integration-test-access-secret-0123456789abcdef",
            "app.encryption.key=aW50ZWdyYXRpb24tdGVzdC1rZXktMzItYnl0ZXMhISE=",
            "management.health.mail.enabled=false",
            "app.jobs.enabled=false",
            "management.server.port=0"
        })
@AutoConfigureObservability(tracing = false)
class ManagementPortIntegrationTest {

    private static final String PASSWORD = "metrics-test-password";

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

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SessionService sessionService;

    // Nothing here sends an email, and none could reach a real mail server.
    @MockBean
    private JavaMailSender mailSender;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("sign-ins and an issued invoice are counted in the metrics read on the management port")
    void scraped() throws Exception {
        User user = createUser();
        assertThat(post(appPort, "/api/v1/auth/login", null, signIn(user, "not-the-password")).statusCode())
                .isEqualTo(401);
        HttpResponse<String> signedIn = post(appPort, "/api/v1/auth/login", null, signIn(user, PASSWORD));
        assertThat(signedIn.statusCode()).isEqualTo(200);
        String token = JsonPath.read(signedIn.body(), "$.accessToken");
        String customer = JsonPath.read(
                post(appPort, "/api/v1/customer/create", token, """
                                {"name": "Beta LLC", "email": "beta@clients.test", "phoneNumber": "+1"}
                                """)
                        .body(),
                "$.createdCustomer._id");
        HttpResponse<String> issued = post(appPort, "/api/v1/document/create", token, """
                {"customerId": "%s", "documentType": "Invoice", "currency": "USD",
                 "billingItems": [{"itemName": "Design", "unitPrice": 100, "quantity": 2}]}
                """.formatted(customer));
        assertThat(issued.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(issued.body(), "$.newDocument.documentNumber")).isNotBlank();

        HttpResponse<String> scrape = get(managementPort, "/actuator/prometheus", null);

        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.body())
                .contains("invoicespot_signins_total{method=\"password\",outcome=\"success\"} 1.0")
                .contains("invoicespot_signins_total{method=\"password\",outcome=\"failure\"} 1.0")
                .contains("invoicespot_documents_issued_total{type=\"invoice\"} 1.0");
    }

    @Test
    @DisplayName("the app's port refuses the metrics, even to someone signed in")
    void refusedOnAppPort() throws Exception {
        User user = createUser();
        String token = jwtService.issueAccessToken(user, sessionService.start(user).session().getId());

        assertThat(get(appPort, "/actuator/prometheus", null).statusCode()).isEqualTo(401);
        assertThat(get(appPort, "/actuator/prometheus", token).statusCode()).isEqualTo(403);
        assertThat(get(managementPort, "/actuator/health", null).statusCode()).isEqualTo(200);
    }

    private User createUser() {
        String handle = "metrics-" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setEmail(handle + "@example.test");
        user.setUsername(handle);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmailVerified(true);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        userRepository.save(user);
        businessService.createPersonal(user);
        return user;
    }

    private static String signIn(User user, String password) {
        return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(user.getEmail(), password);
    }

    private HttpResponse<String> get(int port, String path, String token) throws Exception {
        return send(request(port, path, token).GET());
    }

    private HttpResponse<String> post(int port, String path, String token, String json) throws Exception {
        return send(request(port, path, token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    private static HttpRequest.Builder request(int port, String path, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
