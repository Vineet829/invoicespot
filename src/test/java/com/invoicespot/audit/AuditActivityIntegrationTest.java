package com.invoicespot.audit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

class AuditActivityIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng!pass";
    private static final String CHROME_ON_MAC =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/129.0 Safari/537.36";

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User withPassword(User user) {
        user.setPassword(passwordEncoder.encode(PASSWORD));
        return userRepository.save(user);
    }

    private ResultActions login(User user, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .header(HttpHeaders.USER_AGENT, CHROME_ON_MAC)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(user.getEmail(), password)));
    }

    private String signIn(User user) throws Exception {
        return "Bearer " + JsonPath.read(
                login(user, PASSWORD).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private ResultActions activity(String authorization, int size) throws Exception {
        return mockMvc.perform(get("/api/v1/account/activity")
                .param("size", String.valueOf(size))
                .header(HttpHeaders.AUTHORIZATION, authorization));
    }

    @Test
    @DisplayName("an account's own sign-ins and changes are listed newest first, this device marked")
    void mine() throws Exception {
        User user = withPassword(createUser());
        login(user, "Wrong!pass1").andExpect(status().isUnauthorized());
        String authorization = signIn(user);

        activity(authorization, 10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.events[0].type").value("SIGN_IN"))
                .andExpect(jsonPath("$.events[0].device").value("Chrome on macOS"))
                .andExpect(jsonPath("$.events[0].ip").value("127.0.0.1"))
                .andExpect(jsonPath("$.events[0].thisDevice").value(true))
                .andExpect(jsonPath("$.events[1].type").value("SIGN_IN_FAILED"))
                .andExpect(jsonPath("$.events[1].detail").value("Wrong password"))
                .andExpect(jsonPath("$.events[1].thisDevice").value(false));
        activity(authorization, 1)
                .andExpect(jsonPath("$.pages").value(2))
                .andExpect(jsonPath("$.events.length()").value(1));
        activity(bearer(createUser()), 10).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("each visitor of the shared demo account sees only what they did")
    void demo() throws Exception {
        User demo = withPassword(demoUser());
        String first = signIn(demo);
        String second = signIn(demo);

        activity(first, 10)
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.events[0].thisDevice").value(true));
        activity(second, 10)
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.events[0].thisDevice").value(true));
    }

    @Test
    @DisplayName("admins read the whole log by email and kind of event; no one else can")
    void adminLog() throws Exception {
        User user = withPassword(createUser());
        login(user, "Wrong!pass1");
        signIn(user);
        String admin = bearer(createAdmin());

        mockMvc.perform(get("/api/v1/admin/audit")
                        .param("q", user.getEmail().toUpperCase())
                        .param("type", "sign_in_failed")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.events[0].email").value(user.getEmail()))
                .andExpect(jsonPath("$.events[0].type").value("SIGN_IN_FAILED"));
        mockMvc.perform(get("/api/v1/admin/audit").param("type", "EVERYTHING").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a kind of event from the list"));
        mockMvc.perform(get("/api/v1/admin/audit").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
    }
}
