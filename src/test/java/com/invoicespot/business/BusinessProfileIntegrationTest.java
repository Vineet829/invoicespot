package com.invoicespot.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class BusinessProfileIntegrationTest extends IntegrationTest {

    @Autowired
    private BusinessRepository businessRepository;

    private ResultActions send(MockHttpServletRequestBuilder request, User user, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String publicView(User owner) throws Exception {
        String customer = send(post("/api/v1/customer/create"), owner, """
                        {"name": "Acme", "email": "acme-%s@clients.test", "phoneNumber": "555 0100"}
                        """.formatted(UUID.randomUUID()))
                .andReturn().getResponse().getContentAsString();
        String document = send(post("/api/v1/document/create"), owner, """
                        {"customerId": "%s", "billingItems": [{"itemName": "Design", "unitPrice": 100, "quantity": 1}]}
                        """.formatted(JsonPath.<String>read(customer, "$.createdCustomer._id")))
                .andReturn().getResponse().getContentAsString();
        String link = send(post("/api/v1/document/%s/share".formatted(
                        JsonPath.<String>read(document, "$.newDocument._id"))), owner, "")
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(link, "$.url");
        return mockMvc.perform(get("/api/v1/public/share/" + url.substring(url.lastIndexOf('/') + 1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("an account starts with a business named after its owner, which it owns")
    void personalBusiness() throws Exception {
        User user = createUser();

        mockMvc.perform(get("/api/v1/business").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business.name").value("Test User"))
                .andExpect(jsonPath("$.business.email").value(user.getEmail()))
                .andExpect(jsonPath("$.business.role").value("OWNER"));
    }

    @Test
    @DisplayName("the business details are saved, blank ones cleared, and shown to clients as documents show them")
    void updates() throws Exception {
        User user = createUser();
        send(patch("/api/v1/business"), user, """
                        {"name": " Ada Analytics ", "legalName": "Ada Analytics Private Limited",
                         "phoneNumber": "+91 20 5555 0100", "address": "12 MG Road", "city": "Pune",
                         "state": "Maharashtra", "postalCode": "411001", "country": "India",
                         "taxId": "27AAPFU0939F1ZV"}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your business details were saved"))
                .andExpect(jsonPath("$.business.name").value("Ada Analytics"))
                .andExpect(jsonPath("$.business.state").value("Maharashtra"));
        send(patch("/api/v1/business"), user, """
                        {"phoneNumber": " "}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.business.city").value("Pune"));

        String view = publicView(user);
        assertThat(JsonPath.<String>read(view, "$.business.name")).isEqualTo("Ada Analytics");
        assertThat(JsonPath.<List<String>>read(view, "$.business.lines")).containsExactly(
                "Ada Analytics Private Limited",
                user.getEmail(),
                "12 MG Road, Pune, Maharashtra 411001, India",
                "Tax ID 27AAPFU0939F1ZV");
    }

    @Test
    @DisplayName("a business needs a name and a valid email address")
    void validation() throws Exception {
        User user = createUser();
        send(patch("/api/v1/business"), user, "{\"name\": \"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A business needs a name"));
        send(patch("/api/v1/business"), user, "{\"email\": \"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a valid email address"));
    }

    @Test
    @DisplayName("signing up makes a business, and deleting the account deletes it")
    void lifecycle() throws Exception {
        String handle = "signup-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(request -> {
                            request.setRemoteAddr("10.22.0." + (1 + (int) (Math.random() * 250)));
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s@example.test", "username": "%s", "firstName": "Ada",
                                 "lastName": "Lovelace", "password": "Str0ng!pw", "passwordConfirm": "Str0ng!pw"}
                                """.formatted(handle, handle)))
                .andExpect(status().isOk());
        User user = userRepository.findByEmailIgnoreCase(handle + "@example.test").orElseThrow();
        Business business = businessOf(user);
        assertThat(business.getName()).isEqualTo("Ada Lovelace");

        mockMvc.perform(delete("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk());

        assertThat(businessRepository.findById(business.getPkid())).isEmpty();
    }

    @Test
    @DisplayName("an account in no business is told to start one")
    void noBusiness() throws Exception {
        User user = new User();
        String handle = "alone-" + UUID.randomUUID().toString().substring(0, 8);
        user.setEmail(handle + "@example.test");
        user.setUsername(handle);
        user.setFirstName("Alone");
        user.setLastName("User");
        user.setEmailVerified(true);
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/business").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isConflict())
                .andExpect(header().string(BusinessChangedException.HEADER, "none"))
                .andExpect(jsonPath("$.message").value("You aren't part of a business yet. Start one to carry on."));
    }
}
