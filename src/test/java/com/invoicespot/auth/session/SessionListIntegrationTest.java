package com.invoicespot.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

class SessionListIntegrationTest extends IntegrationTest {

    @Autowired
    private UserSessionRepository sessionRepository;

    private String headerFor(User user, UUID session) {
        return "Bearer " + jwtService.issueAccessToken(user, session);
    }

    private UUID session(User user) {
        return sessionService.start(user).session().getId();
    }

    private ResultActions list(String authorization) throws Exception {
        return mockMvc.perform(get("/api/v1/account/sessions").header(HttpHeaders.AUTHORIZATION, authorization));
    }

    private ResultActions signOut(String authorization, UUID session) throws Exception {
        return mockMvc.perform(
                delete("/api/v1/account/sessions/{id}", session).header(HttpHeaders.AUTHORIZATION, authorization));
    }

    private ResultActions signOutOthers(String authorization) throws Exception {
        return mockMvc.perform(
                post("/api/v1/account/sessions/sign-out-others").header(HttpHeaders.AUTHORIZATION, authorization));
    }

    private boolean open(UUID session) {
        return sessionRepository.findByExternalId(session).map(found -> found.isActiveAt(Instant.now())).orElse(false);
    }

    @Test
    @DisplayName("an account's sessions are listed newest first, this one marked; others can be signed out at once")
    void listAndSignOut() throws Exception {
        User user = createUser();
        UUID older = session(user);
        UUID other = session(user);
        UUID mine = session(user);
        String authorization = headerFor(user, mine);

        list(authorization)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(3))
                .andExpect(jsonPath("$.sessions[0]._id").value(mine.toString()))
                .andExpect(jsonPath("$.sessions[0].current").value(true))
                .andExpect(jsonPath("$.sessions[1].current").value(false))
                .andExpect(jsonPath("$.sessions[0].device").exists());

        String otherHeader = headerFor(user, other);
        signOut(authorization, other).andExpect(status().isOk());
        assertThat(open(other)).isFalse();
        list(otherHeader).andExpect(status().isForbidden());

        signOut(authorization, mine)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Use Log out to sign out of this device"));
        signOutOthers(authorization)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("1 other device was signed out"));
        assertThat(open(older)).isFalse();
        assertThat(open(mine)).isTrue();
    }

    @Test
    @DisplayName("no one can sign out another account's session")
    void othersSessions() throws Exception {
        UUID someoneElses = session(createUser());

        signOut(bearer(createUser()), someoneElses)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("That device is already signed out"));
        assertThat(open(someoneElses)).isTrue();
    }

    @Test
    @DisplayName("the shared demo account sees only the visitor's own session and signs out no one")
    void demo() throws Exception {
        User demo = demoUser();
        UUID visitor = session(demo);
        UUID another = session(demo);
        String authorization = headerFor(demo, visitor);

        list(authorization)
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0]._id").value(visitor.toString()));
        signOut(authorization, another).andExpect(status().isForbidden());
        signOutOthers(authorization).andExpect(status().isForbidden());
        assertThat(open(another)).isTrue();
    }
}
