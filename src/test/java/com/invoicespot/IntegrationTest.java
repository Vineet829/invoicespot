package com.invoicespot;

import com.invoicespot.auth.JwtService;
import com.invoicespot.auth.oauth.OAuthStub;
import com.invoicespot.auth.session.SessionService;
import com.invoicespot.business.ActiveBusinesses;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessMember;
import com.invoicespot.business.BusinessMemberRepository;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.business.BusinessService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(
        properties = {
            "app.jwt.access-secret=integration-test-access-secret-0123456789abcdef",
            "app.encryption.key=aW50ZWdyYXRpb24tdGVzdC1rZXktMzItYnl0ZXMhISE=",
            "management.health.mail.enabled=false",
            "app.jobs.enabled=false",
            "app.demo.email=" + IntegrationTest.DEMO_EMAIL
        })
@AutoConfigureMockMvc
@Import(IntegrationTest.Database.class)
public abstract class IntegrationTest {

    protected static final String DEMO_EMAIL = "demo@invoicespot.test";

    // The password withAccountPassword gives an account, which steps that
    // can't be undone, such as deleting it, ask for again.
    protected static final String ACCOUNT_PASSWORD = "Acc0unt!pass";
    protected static final String CONFIRMATION = "{\"password\": \"" + ACCOUNT_PASSWORD + "\"}";

    @TestConfiguration(proxyBeanMethods = false)
    static class Database {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16");
        }
    }

    // Google sign-in, pointed at a stand-in, set up the same for every test
    // so they all share one context.
    @DynamicPropertySource
    static void signInProviders(DynamicPropertyRegistry registry) {
        OAuthStub stub = OAuthStub.get();
        registry.add("app.oauth.google.client-id", () -> OAuthStub.GOOGLE_CLIENT_ID);
        registry.add("app.oauth.google.client-secret", () -> OAuthStub.GOOGLE_CLIENT_SECRET);
        registry.add("app.oauth.google.endpoints.authorization-uri", () -> stub.url() + "/google/authorize");
        registry.add("app.oauth.google.endpoints.token-uri", () -> stub.url() + "/google/token");
        registry.add("app.oauth.google.endpoints.user-info-uri", () -> stub.url() + "/google/userinfo");
        registry.add("app.oauth.google.endpoints.jwk-set-uri", () -> stub.url() + "/google/jwks");
        registry.add("app.oauth.google.endpoints.issuer-uri", stub::googleIssuer);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected SessionService sessionService;

    @Autowired
    protected BusinessService businessService;

    @Autowired
    protected BusinessMemberRepository businessMemberRepository;

    @Autowired
    protected ActiveBusinesses activeBusinesses;

    @MockBean
    protected JavaMailSender mailSender;

    @Autowired
    private PasswordEncoder accountPasswords;

    protected User createUser() {
        String handle = "user-" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setEmail(handle + "@example.test");
        user.setUsername(handle);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmailVerified(true);
        return withBusiness(userRepository.save(user));
    }

    protected User demoUser() {
        return userRepository.findByEmailIgnoreCase(DEMO_EMAIL).orElseGet(() -> {
            User user = new User();
            user.setEmail(DEMO_EMAIL);
            user.setUsername("demo-" + UUID.randomUUID().toString().substring(0, 8));
            user.setFirstName("Demo");
            user.setLastName("User");
            user.setEmailVerified(true);
            return withBusiness(userRepository.save(user));
        });
    }

    protected User withAccountPassword(User user) {
        user.setPassword(accountPasswords.encode(ACCOUNT_PASSWORD));
        return userRepository.save(user);
    }

    protected User createAdmin() {
        User user = createUser();
        user.setRoles(new HashSet<>(Set.of("User", "Admin")));
        return userRepository.save(user);
    }

    // Every account starts with a business of its own, as a sign-up does.
    private User withBusiness(User user) {
        businessService.createPersonal(user);
        return user;
    }

    protected Business businessOf(User user) {
        return businessService.ownedBy(user.getPkid());
    }

    // A header for a new session of the user's.
    protected String bearer(User user) {
        return "Bearer " + jwtService.issueAccessToken(user, sessionService.start(user).session().getId());
    }

    // A header for a new session of the user's, working in the given business.
    protected String bearer(User user, Business business) {
        UUID session = sessionService.start(user).session().getId();
        activeBusinesses.use(user, session, business.getId());
        return "Bearer " + jwtService.issueAccessToken(user, session);
    }

    // Someone with a business of their own who also works in this one.
    protected User createMember(Business business, BusinessRole role) {
        User user = createUser();
        BusinessMember member = new BusinessMember();
        member.setBusiness(business);
        member.setUser(user);
        member.setRole(role);
        member.setJoinedAt(Instant.now());
        businessMemberRepository.save(member);
        return user;
    }
}
