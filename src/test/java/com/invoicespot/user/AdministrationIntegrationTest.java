package com.invoicespot.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class AdministrationIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ResultActions action(String admin, String method, String path, String json) throws Exception {
        var request = "DELETE".equals(method) ? delete("/api/v1/user" + path) : patch("/api/v1/user" + path);
        request.header(HttpHeaders.AUTHORIZATION, admin);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private ResultActions stats(String authorization) throws Exception {
        return mockMvc.perform(get("/api/v1/user/stats").header(HttpHeaders.AUTHORIZATION, authorization));
    }

    @Test
    @DisplayName("accounts are searched by name, email or username, a page at a time; only admins may look")
    void search() throws Exception {
        String tag = "zq" + UUID.randomUUID().toString().substring(0, 6);
        for (int count = 0; count < 3; count++) {
            User user = createUser();
            user.setLastName("Tagged" + tag);
            userRepository.save(user);
        }
        String admin = bearer(createAdmin());

        mockMvc.perform(get("/api/v1/user/all")
                        .param("q", "TAGGED" + tag)
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.numberOfPages").value(2))
                .andExpect(jsonPath("$.users.length()").value(2));
        mockMvc.perform(get("/api/v1/user/all").header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the counts include every new account")
    void counts() throws Exception {
        String admin = bearer(createAdmin());
        int before = JsonPath.read(stats(admin).andReturn().getResponse().getContentAsString(), "$.accounts");
        createUser();

        stats(admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").value(before + 1))
                .andExpect(jsonPath("$.admins").isNumber())
                .andExpect(jsonPath("$.signedInLast30Days").isNumber());
        stats(bearer(createUser())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin can reactivate an account and make it an admin; the change counts at once")
    void reactivateAndPromote() throws Exception {
        User actor = createAdmin();
        String admin = bearer(actor);
        User user = createUser();
        String theirs = bearer(user);
        action(admin, "PATCH", "/" + user.getId() + "/deactivate", null).andExpect(status().isOk());
        action(admin, "PATCH", "/" + user.getId() + "/reactivate", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        action(admin, "PATCH", "/" + user.getId() + "/reactivate", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This account is already active"));

        String fresh = bearer(user);
        stats(fresh).andExpect(status().isForbidden());
        action(admin, "PATCH", "/" + user.getId() + "/admin", "{\"admin\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(hasItem("Admin")));
        stats(fresh).andExpect(status().isOk());
        action(admin, "PATCH", "/" + user.getId() + "/admin", "{\"admin\": false}").andExpect(status().isOk());
        stats(fresh).andExpect(status().isForbidden());
        stats(theirs).andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForList(
                        "select type from audit_events where user_pkid = ? and actor_pkid = ? order by pkid",
                        String.class,
                        user.getPkid(),
                        actor.getPkid()))
                .containsExactly("USER_DEACTIVATED", "USER_REACTIVATED", "ADMIN_GRANTED", "ADMIN_REVOKED");
    }

    @Test
    @DisplayName("an admin can't demote, deactivate or delete themselves, nor make the demo account an admin")
    void guards() throws Exception {
        User self = createAdmin();
        String admin = bearer(self);

        action(admin, "PATCH", "/" + self.getId() + "/admin", "{\"admin\": false}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You can't remove your own admin role"));
        action(admin, "PATCH", "/" + self.getId() + "/deactivate", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You can't deactivate your own account"));
        action(admin, "DELETE", "/" + self.getId(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Delete your own account from your profile"));
        action(admin, "PATCH", "/" + demoUser().getId() + "/admin", "{\"admin\": true}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("The shared demo account can't be made an admin"));
    }
}
