package com.invoicespot.item;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class CatalogItemIntegrationTest extends IntegrationTest {

    private ResultActions create(User owner, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/item")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String createdId(User owner, String json) throws Exception {
        return JsonPath.read(create(owner, json).andReturn().getResponse().getContentAsString(), "$.item._id");
    }

    @Test
    @DisplayName("items are created, listed by name, searched, updated and deleted")
    void lifecycle() throws Exception {
        User owner = createUser();
        String id =
                createdId(owner, "{\"name\": \"Logo design\", \"description\": \"Two concepts\", \"unitPrice\": 450}");
        createdId(owner, "{\"name\": \"Brand audit\", \"unitPrice\": 1200.5}");

        mockMvc.perform(get("/api/v1/item").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Brand audit"))
                .andExpect(jsonPath("$.items[1].unitPrice").value(450));
        mockMvc.perform(get("/api/v1/item").param("q", "CONCEPT").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("Logo design"));

        mockMvc.perform(patch("/api/v1/item/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPrice\": 500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.unitPrice").value(500));
        mockMvc.perform(delete("/api/v1/item/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/item").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    @DisplayName("% and _ in a search match themselves, not any text")
    void searchWildcards() throws Exception {
        User owner = createUser();
        createdId(owner, "{\"name\": \"50% deposit\", \"unitPrice\": 100}");
        createdId(owner, "{\"name\": \"500 deposit\", \"unitPrice\": 500}");
        createdId(owner, "{\"name\": \"site_audit\", \"unitPrice\": 80}");
        createdId(owner, "{\"name\": \"site audit\", \"unitPrice\": 80}");

        mockMvc.perform(get("/api/v1/item").param("q", "50%").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("50% deposit"));
        mockMvc.perform(get("/api/v1/item").param("q", "site_").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("site_audit"));
    }

    @Test
    @DisplayName("names are unique per user in any letter case, on create and on rename")
    void uniqueNames() throws Exception {
        User owner = createUser();
        createdId(owner, "{\"name\": \"Hosting\", \"unitPrice\": 20}");
        String other = createdId(owner, "{\"name\": \"Support\", \"unitPrice\": 30}");

        create(owner, "{\"name\": \"hosting\", \"unitPrice\": 25}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have an item with that name"));
        mockMvc.perform(patch("/api/v1/item/{id}", other)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"HOSTING\"}"))
                .andExpect(status().isConflict());
        create(createUser(), "{\"name\": \"Hosting\", \"unitPrice\": 20}").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("names are required and prices can't be negative")
    void validation() throws Exception {
        User owner = createUser();

        create(owner, "{\"name\": \" \", \"unitPrice\": 1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An item needs a name"));
        create(owner, "{\"name\": \"Refund\", \"unitPrice\": -5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Prices can't be negative"));
    }

    @Test
    @DisplayName("another user's items are neither listed nor changeable")
    void ownership() throws Exception {
        User owner = createUser();
        String id = createdId(owner, "{\"name\": \"Private\", \"unitPrice\": 10}");
        User stranger = createUser();

        mockMvc.perform(get("/api/v1/item").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(delete("/api/v1/item/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden());
    }
}
