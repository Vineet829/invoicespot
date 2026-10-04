package com.invoicespot.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.invoicespot.IntegrationTest;
import com.invoicespot.Uncommitted;
import com.invoicespot.user.User;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

// A save of some of the business's details keeps the others as they are, even
// when another member's save lands while it is under way.
class BusinessProfileRaceIntegrationTest extends IntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("a save of the city while another member's phone number lands keeps both")
    void savedMeanwhile() throws Exception {
        User owner = createUser();
        Business business = businessOf(owner);
        String session = bearer(owner);

        int status = Uncommitted.change(
                dataSource, "update businesses set phone_number = '+91 20 5555 0199' where pkid = ?",
                business.getPkid(),
                () -> mockMvc.perform(patch("/api/v1/business")
                                .header(HttpHeaders.AUTHORIZATION, session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"city\": \"Pune\"}"))
                        .andReturn().getResponse().getStatus());

        assertThat(status).isEqualTo(200);
        Map<String, Object> row =
                jdbc.queryForMap("select phone_number, city from businesses where pkid = ?", business.getPkid());
        assertThat(row).containsEntry("phone_number", "+91 20 5555 0199").containsEntry("city", "Pune");
    }
}
