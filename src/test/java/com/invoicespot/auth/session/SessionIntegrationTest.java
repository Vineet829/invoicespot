package com.invoicespot.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

class SessionIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng!pass";

    private record SignedIn(String access, String refresh) {}

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User withPassword() {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(PASSWORD));
        return userRepository.save(user);
    }

    private static SignedIn read(MockHttpServletResponse response) throws Exception {
        Cookie cookie = response.getCookie("jwt");
        return new SignedIn(
                "Bearer " + JsonPath.read(response.getContentAsString(), "$.accessToken"),
                cookie == null ? null : cookie.getValue());
    }

    private SignedIn signIn(User user) throws Exception {
        return read(mockMvc.perform(post("/api/v1/auth/login")
                        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh) Chrome/129.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse());
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/new_access_token").cookie(new Cookie("jwt", token)));
    }

    private ResultActions profile(String access) throws Exception {
        return mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, access));
    }

    private List<UserSession> active(User user) {
        return sessionRepository.findActive(user.getPkid(), Instant.now());
    }

    @Test
    @DisplayName("signing in starts a session with the browser it came from, and a refresh swaps its token")
    void signInAndRefresh() throws Exception {
        User user = withPassword();
        SignedIn first = signIn(user);
        profile(first.access()).andExpect(status().isOk());
        assertThat(active(user)).singleElement().satisfies(session -> {
            assertThat(session.getUserAgent()).isEqualTo("Mozilla/5.0 (Macintosh) Chrome/129.0");
            assertThat(session.getIp()).isEqualTo("127.0.0.1");
        });

        SignedIn next = read(refresh(first.refresh()).andExpect(status().isOk()).andReturn().getResponse());

        assertThat(next.refresh()).isNotNull().isNotEqualTo(first.refresh());
        profile(next.access()).andExpect(status().isOk());
        assertThat(active(user)).hasSize(1);
    }

    @Test
    @DisplayName("a second tab refreshing with the same token a moment later still gets in, and keeps the new cookie")
    void concurrentRefresh() throws Exception {
        User user = withPassword();
        SignedIn first = signIn(user);
        SignedIn rotated = read(refresh(first.refresh()).andReturn().getResponse());

        MockHttpServletResponse late = refresh(first.refresh())
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn()
                .getResponse();

        profile(read(late).access()).andExpect(status().isOk());
        refresh(rotated.refresh()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a token handed out a moment ago isn't swapped again, so a tab still sending the one before gets in")
    void noSecondSwapWithinGrace() throws Exception {
        User user = withPassword();
        SignedIn first = signIn(user);
        SignedIn rotated = read(refresh(first.refresh()).andExpect(status().isOk()).andReturn().getResponse());

        // A tab that already has the new cookie refreshes a moment later.
        refresh(rotated.refresh())
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        // A tab that sent the first cookie before either answer came back.
        MockHttpServletResponse late = refresh(first.refresh())
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn()
                .getResponse();

        profile(read(late).access()).andExpect(status().isOk());
        assertThat(active(user)).hasSize(1);
    }

    @Test
    @DisplayName("once that moment has passed, the token is swapped again")
    void swapsAgainAfterGrace() throws Exception {
        User user = withPassword();
        SignedIn first = signIn(user);
        SignedIn rotated = read(refresh(first.refresh()).andReturn().getResponse());
        jdbcTemplate.update(
                "update user_sessions set rotated_at = now() - interval '1 minute' where user_pkid = ?",
                user.getPkid());

        SignedIn next = read(refresh(rotated.refresh()).andExpect(status().isOk()).andReturn().getResponse());

        assertThat(next.refresh()).isNotNull().isNotEqualTo(rotated.refresh());
        refresh(next.refresh()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an old token coming back after that signs out every session of the account")
    void reusedToken() throws Exception {
        User user = withPassword();
        SignedIn first = signIn(user);
        SignedIn other = signIn(user);
        refresh(first.refresh()).andExpect(status().isOk());
        jdbcTemplate.update(
                "update user_sessions set rotated_at = now() - interval '1 minute' where user_pkid = ?",
                user.getPkid());

        refresh(first.refresh()).andExpect(status().isForbidden());

        assertThat(active(user)).isEmpty();
        profile(other.access()).andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForList(
                        "select type from audit_events where user_pkid = ? order by pkid",
                        String.class,
                        user.getPkid()))
                .endsWith("SESSION_TOKEN_REUSED");
    }

    @Test
    @DisplayName("signing out ends the session at once, even for an access token that hasn't run out")
    void signOut() throws Exception {
        User user = withPassword();
        SignedIn session = signIn(user);

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("jwt", session.refresh())))
                .andExpect(status().isOk());

        profile(session.access()).andExpect(status().isForbidden());
        refresh(session.refresh()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("signing out with the cookie a refresh has just replaced still ends the session")
    void signOutWithReplacedCookie() throws Exception {
        User user = withPassword();
        SignedIn first = signIn(user);
        SignedIn rotated = read(refresh(first.refresh()).andExpect(status().isOk()).andReturn().getResponse());

        // The tab signing out sent its cookie before the refresh's answer came back.
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("jwt", first.refresh())))
                .andExpect(status().isOk());

        profile(rotated.access()).andExpect(status().isForbidden());
        refresh(rotated.refresh()).andExpect(status().isForbidden());
        assertThat(active(user)).isEmpty();
    }

    @Test
    @DisplayName("a GET can't sign anyone out, so a link or an image on another site can't either")
    void signOutTakesPost() throws Exception {
        User user = withPassword();
        SignedIn session = signIn(user);

        mockMvc.perform(get("/api/v1/auth/logout").cookie(new Cookie("jwt", session.refresh())))
                .andExpect(status().isMethodNotAllowed());

        profile(session.access()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a session that ran out can't be refreshed")
    void expired() throws Exception {
        User user = withPassword();
        SignedIn session = signIn(user);
        jdbcTemplate.update(
                "update user_sessions set expires_at = now() - interval '1 second' where user_pkid = ?",
                user.getPkid());

        refresh(session.refresh()).andExpect(status().isForbidden());
        profile(session.access()).andExpect(status().isForbidden());
    }
}
