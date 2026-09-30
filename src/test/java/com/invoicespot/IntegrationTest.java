package com.invoicespot;

import com.invoicespot.auth.JwtService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(
        properties = {
            "app.jwt.access-secret=integration-test-access-secret-0123456789abcdef",
            "app.jwt.refresh-secret=integration-test-refresh-secret-0123456789abcdef",
            "management.health.mail.enabled=false"
        })
@AutoConfigureMockMvc
@Import(IntegrationTest.Database.class)
public abstract class IntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Database {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16");
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JwtService jwtService;

    @MockBean
    protected JavaMailSender mailSender;

    protected User createUser() {
        String handle = "user-" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setEmail(handle + "@example.test");
        user.setUsername(handle);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    protected String bearer(User user) {
        return "Bearer " + jwtService.issueAccessToken(user);
    }
}
