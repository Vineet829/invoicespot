package com.invoicespot.auth.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.auth.twofactor.dto.TwoFactorSetupResponse;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TwoFactorIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng!pass";

    private record Enabled(User user, byte[] secret, List<String> recoveryCodes, long step) {}

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TwoFactorService twoFactorService;

    private User withPassword() {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(PASSWORD));
        return userRepository.save(user);
    }

    private ResultActions account(String path, String authorization, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/account/two-factor" + path)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions login(User user) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(user.getEmail(), PASSWORD)));
    }

    private ResultActions secondStep(String challenge, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login/two-factor")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challenge\": \"%s\", \"code\": \"%s\"}".formatted(challenge, code)));
    }

    private String challenge(User user) throws Exception {
        return JsonPath.read(
                login(user).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.challenge");
    }

    // Sets it up and turns it on with the code for this step, so a sign-in
    // right after needs the next step's code.
    private Enabled enabled() throws Exception {
        User user = withPassword();
        String authorization = bearer(user);
        String setup = account("/setup", authorization, "{\"password\": \"%s\"}".formatted(PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpauthUri").value(startsWith("otpauth://totp/")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        byte[] secret = new Base32().decode((String) JsonPath.read(setup, "$.secret"));
        long step = Totp.step(Instant.now());
        String enable = account("/enable", authorization, "{\"code\": \"%s\"}".formatted(Totp.code(secret, step)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new Enabled(user, secret, JsonPath.read(enable, "$.recoveryCodes"), step);
    }

    @Test
    @DisplayName("turning it on takes the password and a code from the app, and gives ten recovery codes")
    void turnOn() throws Exception {
        Enabled enabled = enabled();

        assertThat(enabled.recoveryCodes()).hasSize(10).allMatch(code -> code.matches("[a-z2-9]{5}-[a-z2-9]{5}"));
        mockMvc.perform(get("/api/v1/account/two-factor").header(HttpHeaders.AUTHORIZATION, bearer(enabled.user())))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(10));
        account("/setup", bearer(withPassword()), "{\"password\": \"Wrong!pass1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your password is wrong"));
    }

    @Test
    @DisplayName("a password then earns only a challenge; a code completes it, and never twice")
    void signIn() throws Exception {
        Enabled enabled = enabled();
        String challenge = challenge(enabled.user());
        login(enabled.user())
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(cookie().doesNotExist("jwt"));
        mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + challenge))
                .andExpect(status().isForbidden());

        secondStep(challenge, Totp.code(enabled.secret(), enabled.step()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That code didn't work"));
        String token = JsonPath.read(
                secondStep(challenge, Totp.code(enabled.secret(), enabled.step() + 1))
                        .andExpect(status().isOk())
                        .andExpect(cookie().exists("jwt"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.accessToken");
        mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        secondStep("not-a-challenge", "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your sign-in timed out. Log in again"));
    }

    @Test
    @DisplayName("a recovery code works once, and wrong codes are limited")
    void recoveryCodes() throws Exception {
        Enabled enabled = enabled();
        String code = enabled.recoveryCodes().get(0);

        secondStep(challenge(enabled.user()), code.toUpperCase()).andExpect(status().isOk());
        secondStep(challenge(enabled.user()), code).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/account/two-factor").header(HttpHeaders.AUTHORIZATION, bearer(enabled.user())))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(9));
        String challenge = challenge(enabled.user());
        for (int attempt = 1; attempt <= 4; attempt++) {
            secondStep(challenge, "00000" + attempt).andExpect(status().isBadRequest());
        }
        secondStep(challenge, enabled.recoveryCodes().get(1)).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("two sign-ins racing with one code, from the app or a recovery code, take turns: one gets in")
    void oneCodeOnce() throws Exception {
        Enabled enabled = enabled();

        assertThat(race(enabled.user(), Totp.code(enabled.secret(), enabled.step() + 1)))
                .containsExactlyInAnyOrder(200, 400);
        assertThat(race(enabled.user(), enabled.recoveryCodes().get(0))).containsExactlyInAnyOrder(200, 400);
    }

    // Two sign-ins send the same code while the account is held locked, and
    // go on together once it is let go.
    private List<Integer> race(User user, String code) throws Exception {
        List<String> challenges = List.of(challenge(user), challenge(user));
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(tx -> {
                userRepository.findForUpdateByPkid(user.getPkid());
                held.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            held.await();
            List<Future<Integer>> results = new ArrayList<>();
            for (String challenge : challenges) {
                results.add(pool.submit(() -> secondStep(challenge, code).andReturn().getResponse().getStatus()));
            }
            Thread.sleep(500);
            release.countDown();
            holder.get();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("turning it off takes the password and a code; then a password signs in again")
    void turnOff() throws Exception {
        Enabled enabled = enabled();
        String authorization = bearer(enabled.user());
        String recovery = enabled.recoveryCodes().get(0);
        account("/disable", authorization, "{\"password\": \"Wrong!pass1\", \"code\": \"%s\"}".formatted(recovery))
                .andExpect(status().isBadRequest());
        account("/disable", authorization, "{\"password\": \"%s\", \"code\": \"%s\"}".formatted(PASSWORD, recovery))
                .andExpect(status().isOk());

        login(enabled.user()).andExpect(jsonPath("$.accessToken").exists()).andExpect(cookie().exists("jwt"));
    }

    @Test
    @DisplayName("with it on, deleting the account takes a code as well as the password")
    void deletingTakesACode() throws Exception {
        Enabled enabled = enabled();
        String authorization = bearer(enabled.user());

        mockMvc.perform(delete("/api/v1/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That code didn't work"));
        assertThat(userRepository.findById(enabled.user().getPkid())).isPresent();
        mockMvc.perform(delete("/api/v1/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"%s\", \"code\": \"%s\"}"
                                .formatted(PASSWORD, enabled.recoveryCodes().get(0))))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(enabled.user().getPkid())).isEmpty();
    }

    @Test
    @DisplayName("turning it on in a change that rolls back emails nobody; turned on, the owner is told")
    void turnOnRolledBack() {
        User user = withPassword();
        UUID session = sessionService.start(user).session().getId();
        TwoFactorSetupResponse setup = twoFactorService.setup(user, PASSWORD);
        String code = Totp.code(new Base32().decode(setup.secret()), Totp.step(Instant.now()));

        // As when saving it fails at the very end.
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            twoFactorService.enable(user, session, code);
            transaction.setRollbackOnly();
        });
        verify(mailSender, after(1000).never()).send(any(SimpleMailMessage.class));

        twoFactorService.enable(user, session, code);
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly(user.getEmail());
        assertThat(mail.getValue().getSubject()).isEqualTo("Two-factor sign-in was turned on");
    }

    @Test
    @DisplayName("turning it on, or off, signs out every other device and keeps this one signed in")
    void signsOutOtherDevices() throws Exception {
        User user = withPassword();
        String here = bearer(user);
        String elsewhere = bearer(user);
        String setup = account("/setup", here, "{\"password\": \"%s\"}".formatted(PASSWORD))
                .andReturn()
                .getResponse()
                .getContentAsString();
        byte[] secret = new Base32().decode((String) JsonPath.read(setup, "$.secret"));
        long step = Totp.step(Instant.now());
        account("/enable", here, "{\"code\": \"%s\"}".formatted(Totp.code(secret, step)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Two-factor sign-in is on, and your other devices were signed out. Keep your recovery codes"
                                + " somewhere safe"));
        profile(elsewhere).andExpect(status().isForbidden());
        profile(here).andExpect(status().isOk());

        String another = bearer(user);
        account("/disable", here, "{\"password\": \"%s\", \"code\": \"%s\"}".formatted(
                        PASSWORD, Totp.code(secret, step + 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Two-factor sign-in is off, and your other devices were signed out"));
        profile(another).andExpect(status().isForbidden());
        profile(here).andExpect(status().isOk());
    }

    private ResultActions profile(String authorization) throws Exception {
        return mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, authorization));
    }

    @Test
    @DisplayName("the shared demo account can't turn it on")
    void demo() throws Exception {
        User demo = demoUser();
        demo.setPassword(passwordEncoder.encode(PASSWORD));
        userRepository.save(demo);

        account("/setup", bearer(demo), "{\"password\": \"%s\"}".formatted(PASSWORD))
                .andExpect(status().isForbidden());
    }
}
