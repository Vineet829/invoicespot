package com.invoicespot.upload;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class UploadAccessIntegrationTest extends IntegrationTest {

    private static final MockMultipartFile LOGO =
            new MockMultipartFile("logo", "logo.png", "image/png", TestImages.image("png", 1, 1));

    private ResultActions upload(String authorization) throws Exception {
        return mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/upload")
                .file(LOGO)
                .header(HttpHeaders.AUTHORIZATION, authorization));
    }

    @Test
    @DisplayName("only the owner and admins upload a logo, as only they change the business's details")
    void ownersAndAdmins() throws Exception {
        User owner = createUser();
        Business business = businessOf(owner);

        upload(bearer(owner, business)).andExpect(status().isOk());
        upload(bearer(createMember(business, BusinessRole.ADMIN), business)).andExpect(status().isOk());
        upload(bearer(createMember(business, BusinessRole.MEMBER), business))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("Only the owner and admins can change the business's details and settings"));
        upload(bearer(createMember(business, BusinessRole.VIEWER), business)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("one person uploads at most twenty logos an hour")
    void twentyAnHour() throws Exception {
        User owner = createUser();
        String authorization = bearer(owner);

        for (int upload = 0; upload < 20; upload++) {
            upload(authorization).andExpect(status().isOk());
        }
        upload(authorization)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", startsWith("Too many logos uploaded.")));
    }
}
