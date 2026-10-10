package com.invoicespot.upload;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.auth.JwtService;
import com.invoicespot.auth.session.SessionService;
import com.invoicespot.business.BusinessService;
import com.invoicespot.config.AppProperties;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.PostgreSQLContainer;

// Tomcat parses a multipart body when Spring asks for its parts before
// picking a controller (after the app's security filters), which is why
// these tests go over a real connection: MockMvc, which the other upload
// tests use, never reaches Tomcat's parser.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.jwt.access-secret=integration-test-access-secret-0123456789abcdef",
            "app.encryption.key=aW50ZWdyYXRpb24tdGVzdC1rZXktMzItYnl0ZXMhISE=",
            "management.health.mail.enabled=false",
            "app.jobs.enabled=false"
        })
class MultipartLimitsIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Database {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16");
        }
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final String BOUNDARY = "invoicespotTestBoundary";

    @LocalServerPort
    private int port;

    @Autowired
    private AppProperties properties;

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
    @DisplayName("a logo sent as the settings page sends it, in a part of its own, is kept")
    void keepsALogo() throws Exception {
        HttpResponse<String> response = upload(token(), body(0));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).startsWith("/uploads/");
        Path logo = Path.of(properties.upload().dir()).resolve(response.body().substring("/uploads/".length()));
        assertThat(logo).exists();
        // Tests share the folder a local run of the app keeps logos in.
        Files.delete(logo);
    }

    // No form here sends more than one part; a body cut into many is only a
    // way to make the server hold on to them all (CVE-2025-48988).
    @Test
    @DisplayName("a logo sent among more parts than the server takes is refused, and nothing is kept")
    void refusesTooManyParts() throws Exception {
        String token = token();
        Set<Path> before = uploads();

        HttpResponse<String> response = upload(token, body(59));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"message\":\"Malformed request body\"");
        assertThat(uploads()).isEqualTo(before);
    }

    // Parts are read before the route is, so a route anyone can reach takes
    // them too; one cut short is the sender's to fix.
    @Test
    @DisplayName("a multipart body cut short is a 400, even sent without signing in")
    void refusesABodyCutShort() throws Exception {
        byte[] body = ("--" + BOUNDARY + "\r\n"
                        + "Content-Disposition: form-data; name=\"email\"\r\n\r\nsomeone@example.test")
                .getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"message\":\"Malformed request body\"");
    }

    private String token() {
        String handle = "multipart-" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setEmail(handle + "@example.test");
        user.setUsername(handle);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmailVerified(true);
        userRepository.save(user);
        businessService.createPersonal(user);
        return jwtService.issueAccessToken(user, sessionService.start(user).session().getId());
    }

    // The logo's part, as a browser sends it, then the given number of others.
    private static byte[] body(int others) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\n"
                        + "Content-Disposition: form-data; name=\"logo\"; filename=\"company-logo.png\"\r\n"
                        + "Content-Type: image/png\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(PNG);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < others; i++) {
            body.write(("--" + BOUNDARY + "\r\n"
                            + "Content-Disposition: form-data; name=\"part" + i + "\"\r\n\r\nx\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private HttpResponse<String> upload(String token, byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/upload"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Set<Path> uploads() throws Exception {
        Path dir = Path.of(properties.upload().dir());
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.collect(Collectors.toSet());
        }
    }
}
