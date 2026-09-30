package com.invoicespot.ratelimit;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

class RateLimitIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng!password";

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static String address() {
        return "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private User userWithPassword() {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(PASSWORD));
        return userRepository.save(user);
    }

    private ResultActions login(String address, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    @Test
    @DisplayName("five failed sign-ins lock that email from that address, even with the right password")
    void failedSignIns() throws Exception {
        User user = userWithPassword();
        String address = address();
        for (int attempt = 0; attempt < 5; attempt++) {
            login(address, user.getEmail(), "Wrong!password1").andExpect(status().isUnauthorized());
        }

        login(address, user.getEmail(), PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.message").value(startsWith("Too many sign-in attempts")));
        login(address(), user.getEmail(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a successful sign-in clears the failures before it")
    void successResets() throws Exception {
        User user = userWithPassword();
        String address = address();
        for (int attempt = 0; attempt < 4; attempt++) {
            login(address, user.getEmail(), "Wrong!password1").andExpect(status().isUnauthorized());
        }
        login(address, user.getEmail(), PASSWORD).andExpect(status().isOk());

        for (int attempt = 0; attempt < 4; attempt++) {
            login(address, user.getEmail(), "Wrong!password1").andExpect(status().isUnauthorized());
        }
        login(address, user.getEmail(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an address can sign up at most five times an hour")
    void signUps() throws Exception {
        String address = address();
        for (int attempt = 0; attempt < 6; attempt++) {
            String handle = "limit" + UUID.randomUUID().toString().substring(0, 8);
            ResultActions result = mockMvc.perform(post("/api/v1/auth/register")
                    .with(request -> {
                        request.setRemoteAddr(address);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"email": "%s@example.test", "username": "%s", "firstName": "Rate", "lastName": "Limit",
                             "password": "Str0ng!password", "passwordConfirm": "Str0ng!password"}
                            """.formatted(handle, handle)));
            if (attempt < 5) {
                result.andExpect(status().isOk());
            } else {
                result.andExpect(status().isTooManyRequests())
                        .andExpect(jsonPath("$.message").value(startsWith("Too many sign-ups from this network")));
            }
        }
    }
}
