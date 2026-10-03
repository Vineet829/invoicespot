package com.invoicespot.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.audit.AuditEvent;
import com.invoicespot.audit.AuditEventRepository;
import com.invoicespot.audit.AuditEventType;
import com.invoicespot.user.User;
import com.invoicespot.user.UserIdentity;
import com.invoicespot.user.UserIdentityRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Google sign-in end to end, against a stand-in for Google. The start sets a
// signed cookie and sends the browser to Google with PKCE; the callback
// links or makes the account by OAuthSignIn's rules and hands over to the
// app, or sends the browser back to sign-in saying why not.
class OAuthGoogleSignInIntegrationTest extends IntegrationTest {

    // app.site.client-url in tests.
    private static final String CLIENT = "http://localhost:3000";
    private static final String PASSWORD = "Str0ng!pass";

    private final OAuthStub stub = OAuthStub.get();

    @Autowired
    private UserIdentityRepository identities;

    @Autowired
    private AuditEventRepository auditEvents;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private record Started(Cookie cookie, String setCookie, Map<String, String> query) {}

    private Started start() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/auth/oauth2/authorization/google"))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse();
        return new Started(
                response.getCookie(CookieAuthorizationRequests.COOKIE),
                response.getHeader(HttpHeaders.SET_COOKIE),
                query(response.getHeader(HttpHeaders.LOCATION)));
    }

    // Comes back from Google as the given person, with the cookies given.
    private MockHttpServletResponse finish(Started started, OAuthStub.GoogleUser user, Cookie... cookies)
            throws Exception {
        stub.nextGoogle(user, started.query().get("nonce"));
        return callback(started, "code", "code-" + UUID.randomUUID(), cookies);
    }

    // Comes back from Google with the start's state, the one parameter given
    // (a code, or an error) and the cookies given.
    private MockHttpServletResponse callback(Started started, String name, String value, Cookie... cookies)
            throws Exception {
        MockHttpServletRequestBuilder callback = get("/api/v1/auth/oauth2/callback/google")
                .param(name, value)
                .param("state", started.query().get("state"));
        if (cookies.length > 0) {
            callback.cookie(cookies);
        }
        return mockMvc.perform(callback)
                .andExpect(status().isFound())
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse signIn(OAuthStub.GoogleUser user) throws Exception {
        Started started = start();
        return finish(started, user, started.cookie());
    }

    private static OAuthStub.GoogleUser google(String email) {
        return new OAuthStub.GoogleUser("google-" + UUID.randomUUID(), email, true, "Ada", "Lovelace");
    }

    // Someone at a Google Workspace domain, the email's own: Google hosts it.
    private static OAuthStub.GoogleUser workspace(String email) {
        return new OAuthStub.GoogleUser(
                "google-" + UUID.randomUUID(), email, true, "Ada", "Lovelace", email.substring(email.indexOf('@') + 1));
    }

    private static String freshEmail() {
        return "ada-" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.test";
    }

    private static Map<String, String> query(String url) {
        Map<String, String> values = new LinkedHashMap<>();
        String query = URI.create(url).getRawQuery();
        for (String pair : query.split("&")) {
            int at = pair.indexOf('=');
            values.put(pair.substring(0, at), URLDecoder.decode(pair.substring(at + 1), StandardCharsets.UTF_8));
        }
        return values;
    }

    private static String refused(String code) {
        return CLIENT + "/login?oauth_error=" + code + "&provider=google";
    }

    private List<AuditEvent> eventsOf(User user) {
        return auditEvents.findAll().stream()
                .filter(event -> event.getUser() != null && event.getUser().getPkid().equals(user.getPkid()))
                .sorted(Comparator.comparing(AuditEvent::getPkid))
                .toList();
    }

    private AuditEvent lastEventOf(User user) {
        List<AuditEvent> events = eventsOf(user);
        return events.get(events.size() - 1);
    }

    private User reloaded(User user) {
        return userRepository.findById(user.getPkid()).orElseThrow();
    }

    @Test
    @DisplayName("a first sign-in makes a verified account with a business of its own, through PKCE, with a session")
    void newAccount() throws Exception {
        Started started = start();
        assertThat(started.query())
                .containsEntry("client_id", OAuthStub.GOOGLE_CLIENT_ID)
                .containsEntry("response_type", "code")
                .containsEntry("scope", "openid profile email")
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("redirect_uri", CLIENT + "/api/v1/auth/oauth2/callback/google")
                .containsKeys("state", "nonce", "code_challenge");
        assertThat(started.setCookie())
                .startsWith(CookieAuthorizationRequests.COOKIE + "=")
                .contains("Path=/api/v1/auth/oauth2", "Max-Age=600", "HttpOnly", "SameSite=Lax");

        String email = freshEmail();
        OAuthStub.GoogleUser person = google(email);
        MockHttpServletResponse done = finish(started, person, started.cookie());

        assertThat(done.getHeader(HttpHeaders.LOCATION)).isEqualTo(CLIENT + "/auth/callback");
        Cookie session = done.getCookie("jwt");
        assertThat(session).isNotNull();
        mockMvc.perform(get("/api/v1/auth/new_access_token").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.provider").value("google"));
        // The code went back with the verifier whose hash the start sent.
        Map<String, String> exchange = stub.lastTokenRequest();
        assertThat(exchange).containsEntry("grant_type", "authorization_code");
        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                        .digest(exchange.get("code_verifier").getBytes(StandardCharsets.US_ASCII))))
                .isEqualTo(started.query().get("code_challenge"));

        User account = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(account.isEmailVerified()).isTrue();
        assertThat(account.getPassword()).isNull();
        assertThat(account.getProvider()).isEqualTo("google");
        assertThat(account.getLastName()).isEqualTo("Lovelace");
        assertThat(account.getUsername()).startsWith(email.substring(0, email.indexOf('@')));
        assertThat(identities.findByProviderAndSubject("google", person.sub())).isPresent();
        assertThat(businessOf(account).getName()).isEqualTo("Ada Lovelace");
        assertThat(lastEventOf(account).getType()).isEqualTo(AuditEventType.SIGN_IN);
        assertThat(lastEventOf(account).getDetail()).isEqualTo("With Google");
    }

    @Test
    @DisplayName("an account with the email Google verified is linked, keeps its password, and its owner is told")
    void linksVerifiedAccount() throws Exception {
        User existing = createUser();
        existing.setPassword(passwordEncoder.encode(PASSWORD));
        userRepository.save(existing);

        MockHttpServletResponse done = signIn(workspace(existing.getEmail()));

        assertThat(done.getHeader(HttpHeaders.LOCATION)).isEqualTo(CLIENT + "/auth/callback");
        User linked = reloaded(existing);
        assertThat(linked.getPassword()).isEqualTo(existing.getPassword());
        assertThat(linked.getProvider()).isEqualTo("email");
        assertThat(eventsOf(linked)).extracting(AuditEvent::getType)
                .containsSubsequence(AuditEventType.PROVIDER_LINKED, AuditEventType.SIGN_IN);
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000).atLeastOnce()).send(mail.capture());
        assertThat(mail.getAllValues())
                .anySatisfy(message -> {
                    assertThat(message.getTo()).containsExactly(existing.getEmail());
                    assertThat(message.getSubject()).isEqualTo("Google sign-in was added to your Invoice Spot account");
                    assertThat(message.getText())
                            .contains("Whoever holds that Google account can sign in to yours.")
                            .contains("If that wasn't you, reply to this email right away.")
                            .doesNotContain("reset_password");
                });
    }

    @Test
    @DisplayName("an account nobody verified goes to the email's verified owner: its password and sessions end")
    void takesOverUnverifiedAccount() throws Exception {
        User pending = createUser();
        pending.setEmailVerified(false);
        pending.setPassword(passwordEncoder.encode(PASSWORD));
        userRepository.save(pending);
        String earlierSession = sessionService.start(pending).refreshToken();

        signIn(workspace(pending.getEmail()));

        User owned = reloaded(pending);
        assertThat(owned.isEmailVerified()).isTrue();
        assertThat(owned.getPassword()).isNull();
        assertThat(owned.getPasswordChangedAt()).isNotNull();
        mockMvc.perform(get("/api/v1/auth/new_access_token").cookie(new Cookie("jwt", earlierSession)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an email Google doesn't host opens no existing account: Google checked it once, it may have moved")
    void notHostedEmailOpensNoAccount() throws Exception {
        User existing = createUser();
        existing.setPassword(passwordEncoder.encode(PASSWORD));
        userRepository.save(existing);

        MockHttpServletResponse done = signIn(google(existing.getEmail()));

        assertThat(done.getHeader(HttpHeaders.LOCATION)).isEqualTo(refused("email_not_hosted"));
        assertThat(done.getCookie("jwt")).isNull();
        assertThat(identities.findAll())
                .noneMatch(identity -> identity.getUser().getPkid().equals(existing.getPkid()));
        assertThat(eventsOf(existing)).extracting(AuditEvent::getType)
                .doesNotContain(AuditEventType.PROVIDER_LINKED, AuditEventType.SIGN_IN);
    }

    @Test
    @DisplayName("an email Google doesn't host takes no unverified account over: its password and sessions stay")
    void notHostedEmailTakesNothingOver() throws Exception {
        User pending = createUser();
        pending.setEmailVerified(false);
        pending.setPassword(passwordEncoder.encode(PASSWORD));
        userRepository.save(pending);
        String earlierSession = sessionService.start(pending).refreshToken();

        MockHttpServletResponse done = signIn(google(pending.getEmail()));

        assertThat(done.getHeader(HttpHeaders.LOCATION)).isEqualTo(refused("email_not_hosted"));
        User unchanged = reloaded(pending);
        assertThat(unchanged.isEmailVerified()).isFalse();
        assertThat(unchanged.getPassword()).isEqualTo(pending.getPassword());
        assertThat(identities.findAll())
                .noneMatch(identity -> identity.getUser().getPkid().equals(pending.getPkid()));
        mockMvc.perform(get("/api/v1/auth/new_access_token").cookie(new Cookie("jwt", earlierSession)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a Gmail address opens the account that has it, as a Workspace one does")
    void linksGmailAccount() throws Exception {
        User existing = createUser();
        existing.setEmail(existing.getUsername() + "@gmail.com");
        userRepository.save(existing);

        MockHttpServletResponse done = signIn(google(existing.getEmail()));

        assertThat(done.getHeader(HttpHeaders.LOCATION)).isEqualTo(CLIENT + "/auth/callback");
        assertThat(eventsOf(existing)).extracting(AuditEvent::getType)
                .containsSubsequence(AuditEventType.PROVIDER_LINKED, AuditEventType.SIGN_IN);
    }

    @Test
    @DisplayName("a linked Google account opens its own account even after its email changed to someone else's")
    void identityBeforeEmail() throws Exception {
        OAuthStub.GoogleUser person = google(freshEmail());
        signIn(person);
        User account = userRepository.findByEmailIgnoreCase(person.email()).orElseThrow();
        User someoneElse = createUser();

        MockHttpServletResponse done = signIn(new OAuthStub.GoogleUser(
                person.sub(), someoneElse.getEmail(), true, "Ada", "Lovelace"));

        mockMvc.perform(get("/api/v1/auth/new_access_token").cookie(done.getCookie("jwt")))
                .andExpect(jsonPath("$.username").value(account.getUsername()));
        assertThat(eventsOf(someoneElse)).extracting(AuditEvent::getType).doesNotContain(AuditEventType.SIGN_IN);
    }

    @Test
    @DisplayName("an unverified email, the demo account, a deactivated account and a second Google account are refused")
    void refused() throws Exception {
        String unverified = freshEmail();
        assertThat(signIn(new OAuthStub.GoogleUser("google-" + UUID.randomUUID(), unverified, false, "Ada", null))
                        .getHeader(HttpHeaders.LOCATION))
                .isEqualTo(refused("unverified_email"));
        assertThat(userRepository.findByEmailIgnoreCase(unverified)).isEmpty();

        assertThat(signIn(google(DEMO_EMAIL)).getHeader(HttpHeaders.LOCATION)).isEqualTo(refused("demo_account"));

        User deactivated = createUser();
        deactivated.setActive(false);
        userRepository.save(deactivated);
        assertThat(signIn(google(deactivated.getEmail())).getHeader(HttpHeaders.LOCATION))
                .isEqualTo(refused("deactivated"));
        assertThat(lastEventOf(deactivated).getType()).isEqualTo(AuditEventType.SIGN_IN_FAILED);
        assertThat(lastEventOf(deactivated).getDetail()).isEqualTo("With Google: account deactivated");
        assertThat(identities.findAll())
                .noneMatch(identity -> identity.getUser().getPkid().equals(deactivated.getPkid()));

        OAuthStub.GoogleUser first = google(freshEmail());
        signIn(first);
        assertThat(signIn(google(first.email())).getHeader(HttpHeaders.LOCATION))
                .isEqualTo(refused("already_linked"));
    }

    @Test
    @DisplayName("a refused Google sign-in is logged as a wrong password is, for the account with the email if any")
    void refusalsLogged() throws Exception {
        OAuthStub.GoogleUser first = google(freshEmail());
        signIn(first);
        User linked = userRepository.findByEmailIgnoreCase(first.email()).orElseThrow();
        assertThat(signIn(google(first.email())).getHeader(HttpHeaders.LOCATION))
                .isEqualTo(refused("already_linked"));
        assertThat(lastEventOf(linked).getType()).isEqualTo(AuditEventType.SIGN_IN_FAILED);
        assertThat(lastEventOf(linked).getDetail()).isEqualTo("With Google: linked to a different Google account");

        User notHosted = createUser();
        assertThat(signIn(google(notHosted.getEmail())).getHeader(HttpHeaders.LOCATION))
                .isEqualTo(refused("email_not_hosted"));
        assertThat(eventsOf(notHosted))
                .extracting(AuditEvent::getType, AuditEvent::getDetail)
                .containsExactly(tuple(AuditEventType.SIGN_IN_FAILED, "With Google: email not hosted by Google"));

        User existing = createUser();
        OAuthStub.GoogleUser unverified =
                new OAuthStub.GoogleUser("google-" + UUID.randomUUID(), existing.getEmail(), false, "Ada", null);
        assertThat(signIn(unverified).getHeader(HttpHeaders.LOCATION)).isEqualTo(refused("unverified_email"));
        assertThat(eventsOf(existing))
                .extracting(AuditEvent::getType, AuditEvent::getDetail)
                .containsExactly(tuple(AuditEventType.SIGN_IN_FAILED, "With Google: email not verified by Google"));

        String nobody = freshEmail();
        signIn(new OAuthStub.GoogleUser("google-" + UUID.randomUUID(), nobody, false, "Ada", null));
        assertThat(auditEvents.findAll())
                .filteredOn(event -> nobody.equals(event.getEmail()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getType()).isEqualTo(AuditEventType.SIGN_IN_FAILED);
                    assertThat(event.getUser()).isNull();
                });

        // The demo account is shared by every test, so only its new entries.
        User demo = demoUser();
        int before = eventsOf(demo).size();
        assertThat(signIn(google(DEMO_EMAIL)).getHeader(HttpHeaders.LOCATION)).isEqualTo(refused("demo_account"));
        assertThat(eventsOf(demo).stream().skip(before))
                .extracting(AuditEvent::getType, AuditEvent::getDetail)
                .containsExactly(tuple(AuditEventType.SIGN_IN_FAILED, "With Google: demo account"));
    }

    @Test
    @DisplayName("with two-factor sign-in on, Google is only the first step, and the code still has to follow")
    void twoFactor() throws Exception {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(PASSWORD));
        userRepository.save(user);
        String authorization = bearer(user);
        String setup = mockMvc.perform(post("/api/v1/account/two-factor/setup")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        byte[] secret = new Base32().decode((String) JsonPath.read(setup, "$.secret"));
        String enabled = mockMvc.perform(post("/api/v1/account/two-factor/enable")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(totp(secret, Instant.now().getEpochSecond() / 30))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> recoveryCodes = JsonPath.read(enabled, "$.recoveryCodes");

        MockHttpServletResponse done = signIn(workspace(user.getEmail()));
        String location = done.getHeader(HttpHeaders.LOCATION);

        assertThat(location).startsWith(CLIENT + "/auth/callback#challenge=");
        // No session before the code.
        assertThat(done.getCookie("jwt")).isNull();
        String challenge = URLDecoder.decode(location.substring(location.indexOf('=') + 1), StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/auth/login/two-factor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challenge\": \"%s\", \"code\": \"%s\"}"
                                .formatted(challenge, recoveryCodes.get(0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        assertThat(lastEventOf(user).getType()).isEqualTo(AuditEventType.SIGN_IN);
        assertThat(lastEventOf(user).getDetail()).isEqualTo("With Google and a recovery code");
    }

    @Test
    @DisplayName("a missing or altered cookie, or another state, ends the sign-in as expired")
    void expired() throws Exception {
        Started started = start();
        OAuthStub.GoogleUser person = google(freshEmail());

        assertThat(finish(started, person).getHeader(HttpHeaders.LOCATION)).isEqualTo(refused("expired"));
        Cookie altered = new Cookie(
                CookieAuthorizationRequests.COOKIE,
                CookieAuthorizationRequestsTest.tampered(started.cookie().getValue()));
        assertThat(finish(started, person, altered).getHeader(HttpHeaders.LOCATION)).isEqualTo(refused("expired"));
        Started other = start();
        assertThat(finish(started, person, other.cookie()).getHeader(HttpHeaders.LOCATION))
                .isEqualTo(refused("expired"));
        assertThat(userRepository.findByEmailIgnoreCase(person.email())).isEmpty();
    }

    @Test
    @DisplayName("a callback from an earlier start is refused as expired, and the newer sign-in still finishes")
    void staleCallback() throws Exception {
        Started earlier = start();
        Started newer = start();
        OAuthStub.GoogleUser person = google(freshEmail());

        MockHttpServletResponse stale = finish(earlier, person, newer.cookie());

        assertThat(stale.getHeader(HttpHeaders.LOCATION)).isEqualTo(refused("expired"));
        // The browser keeps the newer sign-in's cookie.
        assertThat(stale.getCookie(CookieAuthorizationRequests.COOKIE)).isNull();
        assertThat(finish(newer, person, newer.cookie()).getHeader(HttpHeaders.LOCATION))
                .isEqualTo(CLIENT + "/auth/callback");
        assertThat(userRepository.findByEmailIgnoreCase(person.email())).isPresent();
    }

    @Test
    @DisplayName("cancelled at Google, the sign-in page says so")
    void cancelled() throws Exception {
        Started started = start();

        MockHttpServletResponse done = callback(started, "error", "access_denied", started.cookie());

        assertThat(done.getHeader(HttpHeaders.LOCATION)).isEqualTo(refused("cancelled"));
    }

    @Test
    @DisplayName("an ID token for another sign-in's nonce is refused, and no account is made")
    void wrongNonce() throws Exception {
        Started started = start();
        OAuthStub.GoogleUser person = google(freshEmail());
        stub.nextGoogle(person, "nonce-of-another-sign-in");

        MockHttpServletResponse done = callback(started, "code", "code-" + UUID.randomUUID(), started.cookie());

        assertThat(done.getHeader(HttpHeaders.LOCATION)).isEqualTo(refused("failed"));
        assertThat(userRepository.findByEmailIgnoreCase(person.email())).isEmpty();
        assertThat(identities.findByProviderAndSubject("google", person.sub())).isEmpty();
    }

    @Test
    @DisplayName("a linked Google account is refused once its account is deactivated, and when it opens the demo")
    void linkedButRefused() throws Exception {
        OAuthStub.GoogleUser person = google(freshEmail());
        signIn(person);
        User account = userRepository.findByEmailIgnoreCase(person.email()).orElseThrow();
        account.setActive(false);
        userRepository.save(account);

        assertThat(signIn(person).getHeader(HttpHeaders.LOCATION)).isEqualTo(refused("deactivated"));
        assertThat(lastEventOf(account).getType()).isEqualTo(AuditEventType.SIGN_IN_FAILED);
        assertThat(lastEventOf(account).getDetail()).isEqualTo("With Google: account deactivated");

        UserIdentity demoIdentity = new UserIdentity();
        demoIdentity.setUser(demoUser());
        demoIdentity.setProvider("google");
        demoIdentity.setSubject("google-" + UUID.randomUUID());
        demoIdentity.setEmail(DEMO_EMAIL);
        demoIdentity.setLinkedAt(Instant.now());
        identities.save(demoIdentity);

        int before = eventsOf(demoUser()).size();
        assertThat(signIn(new OAuthStub.GoogleUser(demoIdentity.getSubject(), freshEmail(), true, "Ada", "Lovelace"))
                        .getHeader(HttpHeaders.LOCATION))
                .isEqualTo(refused("demo_account"));
        assertThat(eventsOf(demoUser()).stream().skip(before))
                .extracting(AuditEvent::getType, AuditEvent::getDetail)
                .containsExactly(tuple(AuditEventType.SIGN_IN_FAILED, "With Google: demo account"));
    }

    @Test
    @DisplayName("a taken handle gets a short suffix, and without a name the handle is the first name")
    void takenHandleAndNoName() throws Exception {
        User existing = createUser();
        String email = existing.getUsername() + "@gmail.test";

        signIn(new OAuthStub.GoogleUser("google-" + UUID.randomUUID(), email, true, null, null));

        User account = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(account.getUsername()).matches(Pattern.quote(existing.getUsername()) + "-[a-z2-9]{4}");
        assertThat(account.getFirstName()).isEqualTo(existing.getUsername());
        assertThat(account.getLastName()).isEmpty();
    }

    @Test
    @DisplayName("the sign-in page offers the providers the server is set up for, and no other can be started")
    void providers() throws Exception {
        mockMvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.length()").value(1))
                .andExpect(jsonPath("$.providers[0].id").value("google"))
                .andExpect(jsonPath("$.providers[0].name").value("Google"));
        mockMvc.perform(get("/api/v1/auth/oauth2/authorization/github")).andExpect(status().isNotFound());
    }

    // A code from an authenticator app (RFC 6238), for the step given.
    private static String totp(byte[] secret, long step) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret, "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        return String.format("%06d", binary % 1_000_000);
    }
}
