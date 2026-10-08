package com.invoicespot.business;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class BusinessSwitchIntegrationTest extends IntegrationTest {

    private ResultActions send(MockHttpServletRequestBuilder request, String session, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String start(String session, String name) throws Exception {
        String body = send(post("/api/v1/businesses"), session, "{\"name\": \"" + name + "\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$._id");
    }

    private String client(String session) throws Exception {
        String body = send(post("/api/v1/customer/create"), session, """
                        {"name": "Acme", "email": "acme-%s@clients.test", "phoneNumber": "555 0100"}
                        """.formatted(UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.createdCustomer._id");
    }

    @Test
    @DisplayName("a new business becomes the session's, and its records stay apart from the first one's")
    void startsAndSwitches() throws Exception {
        User user = createUser();
        String session = bearer(user);
        String first = businessOf(user).getId().toString();
        mockMvc.perform(get("/api/v1/businesses").header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businesses", hasSize(1)))
                .andExpect(jsonPath("$.businesses[0]._id").value(first))
                .andExpect(jsonPath("$.businesses[0].role").value("OWNER"))
                .andExpect(jsonPath("$.businesses[0].active").value(true));

        String second = start(session, "Side Studio");
        String secondClient = client(session);
        mockMvc.perform(get("/api/v1/businesses").header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(jsonPath("$.businesses[0].name").value("Side Studio"))
                .andExpect(jsonPath("$.businesses[0].active").value(true))
                .andExpect(jsonPath("$.businesses[1].active").value(false));

        send(put("/api/v1/businesses/active"), session, "{\"businessId\": \"" + first + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._id").value(first));
        mockMvc.perform(get("/api/v1/customer/all").header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(jsonPath("$.myCustomers", hasSize(0)));
        mockMvc.perform(get("/api/v1/customer/" + secondClient).header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isForbidden());

        send(put("/api/v1/businesses/active"), session, "{\"businessId\": \"" + second + "\"}")
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/customer/" + secondClient).header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("each business says the time zone its days are counted in, UTC until it chooses one")
    void timeZones() throws Exception {
        User user = createUser();
        String session = bearer(user);
        String first = businessOf(user).getId().toString();
        send(put("/api/v1/settings"), session, """
                {"defaultCurrency": "USD", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "America/Los_Angeles"}
                """)
                .andExpect(status().isOk());

        String body = send(post("/api/v1/businesses"), session, "{\"name\": \"Side Studio\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeZone").value("UTC"))
                .andReturn().getResponse().getContentAsString();
        String second = JsonPath.read(body, "$._id");
        mockMvc.perform(get("/api/v1/businesses").header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(jsonPath("$.businesses[?(@._id == '%s')].timeZone".formatted(first))
                        .value(contains("America/Los_Angeles")))
                .andExpect(jsonPath("$.businesses[?(@._id == '%s')].timeZone".formatted(second))
                        .value(contains("UTC")));
        send(put("/api/v1/businesses/active"), session, "{\"businessId\": \"" + first + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("America/Los_Angeles"));
    }

    @Test
    @DisplayName("each session keeps its own business, and a new one starts in the last one used")
    void perSession() throws Exception {
        User user = createUser();
        String first = businessOf(user).getId().toString();
        String laptop = bearer(user);
        String second = start(laptop, "Side Studio");
        String phone = bearer(user);

        send(put("/api/v1/businesses/active"), phone, "{\"businessId\": \"" + first + "\"}")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/business").header(HttpHeaders.AUTHORIZATION, laptop))
                .andExpect(jsonPath("$.business._id").value(second));
        mockMvc.perform(get("/api/v1/business").header(HttpHeaders.AUTHORIZATION, phone))
                .andExpect(jsonPath("$.business._id").value(first));
        mockMvc.perform(get("/api/v1/business").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.business._id").value(first));
    }

    @Test
    @DisplayName("a tab still showing another business is told which one the session is in now")
    void staleTab() throws Exception {
        User user = createUser();
        String session = bearer(user);
        String first = businessOf(user).getId().toString();
        String second = start(session, "Side Studio");

        mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, session)
                        .header("X-Business-Id", first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Acme\", \"email\": \"acme@clients.test\", \"phoneNumber\": \"555\"}"))
                .andExpect(status().isConflict())
                .andExpect(header().string(BusinessChangedException.HEADER, second))
                .andExpect(jsonPath("$.message").value("You switched business in another tab, so this tab switched too."));
        mockMvc.perform(get("/api/v1/customer/all")
                        .header(HttpHeaders.AUTHORIZATION, session)
                        .header("X-Business-Id", second))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("nobody can switch to a business they aren't in, and the demo account can't start one")
    void refusals() throws Exception {
        User user = createUser();
        String stranger = businessOf(createUser()).getId().toString();

        send(put("/api/v1/businesses/active"), bearer(user), "{\"businessId\": \"" + stranger + "\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("You aren't a member of that business"));
        send(post("/api/v1/businesses"), bearer(user), "{\"name\": \"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A business needs a name"));
        send(post("/api/v1/businesses"), bearer(demoUser()), "{\"name\": \"Demo Two\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("This is a shared demo account, so starting a business is turned off"));
    }

    @Test
    @DisplayName("an account owns at most ten businesses")
    void cap() throws Exception {
        User user = createUser();
        String session = bearer(user);
        for (int business = 2; business <= 10; business++) {
            start(session, "Business " + business);
        }
        send(post("/api/v1/businesses"), session, "{\"name\": \"One too many\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You can own at most 10 businesses"));
    }
}
