package com.invoicespot.customer;

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

class CustomerOwnershipIntegrationTest extends IntegrationTest {

    private ResultActions create(User owner, String email, String vatTinNo) throws Exception {
        return mockMvc.perform(post("/api/v1/customer/create")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Northwind", "email": "%s", "phoneNumber": "+1 555 0100", "vatTinNo": %s}
                        """.formatted(email, vatTinNo)));
    }

    @Test
    @DisplayName("two businesses can each have the same client")
    void sameClientForTwoUsers() throws Exception {
        create(createUser(), "shared@northwind.test", "null").andExpect(status().isOk());
        create(createUser(), "shared@northwind.test", "null").andExpect(status().isOk());
    }

    @Test
    @DisplayName("one user can't have two clients with the same email, in any letter case")
    void duplicateForOneUser() throws Exception {
        User owner = createUser();
        create(owner, "twice@northwind.test", "null").andExpect(status().isOk());

        create(owner, "Twice@Northwind.test", "null")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have a customer with that email address"));
    }

    @Test
    @DisplayName("renaming a client's email onto another of the user's clients is refused")
    void duplicateOnUpdate() throws Exception {
        User owner = createUser();
        create(owner, "first@northwind.test", "null").andExpect(status().isOk());
        String body = create(owner, "second@northwind.test", "null")
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(body, "$.createdCustomer._id");

        mockMvc.perform(patch("/api/v1/customer/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"first@northwind.test\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a VAT or tax number keeps its letters, and a zero means none")
    void vatNumbers() throws Exception {
        User owner = createUser();

        create(owner, "gst@northwind.test", "\"29ABCDE1234F1Z5\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCustomer.vatTinNo").value("29ABCDE1234F1Z5"));
        create(owner, "zero@northwind.test", "0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCustomer.vatTinNo").doesNotExist());
    }
}
