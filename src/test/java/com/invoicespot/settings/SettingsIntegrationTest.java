package com.invoicespot.settings;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class SettingsIntegrationTest extends IntegrationTest {

    private static final String VALID = """
            {"defaultCurrency": "INR", "paymentTermsDays": 15, "defaultTaxRate": 18, "taxLabel": "GST",
             "defaultNotes": "Thanks for your business.", "defaultTerms": "Due in 15 days.",
             "emailMessage": "Hello {client}, here is {document}.", "timeZone": "Asia/Kolkata"}
            """;

    private ResultActions save(User user, String json) throws Exception {
        return mockMvc.perform(put("/api/v1/settings")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    @DisplayName("a new user gets the defaults")
    void defaults() throws Exception {
        mockMvc.perform(get("/api/v1/settings").header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.defaultCurrency").value("USD"))
                .andExpect(jsonPath("$.settings.paymentTermsDays").value(7))
                .andExpect(jsonPath("$.settings.defaultTaxRate").value(0))
                .andExpect(jsonPath("$.settings.taxLabel").value("Tax"))
                .andExpect(jsonPath("$.settings.timeZone").value("UTC"))
                .andExpect(jsonPath("$.settings.remindersEnabled").value(false))
                .andExpect(jsonPath("$.settings.reminderDaysBefore").value(3))
                .andExpect(jsonPath("$.settings.reminderOnDue").value(true))
                .andExpect(jsonPath("$.settings.reminderEveryDays").value(7))
                .andExpect(jsonPath("$.settings.reminderMax").value(3));
    }

    @Test
    @DisplayName("automatic reminders are saved as chosen, within their limits")
    void reminders() throws Exception {
        User user = createUser();
        String reminders = VALID.replace("\"timeZone\": \"Asia/Kolkata\"}", """
                "timeZone": "Asia/Kolkata", "remindersEnabled": true, "reminderDaysBefore": 0,
                 "reminderOnDue": false, "reminderEveryDays": 14, "reminderMax": 5}""");

        save(user, reminders)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.remindersEnabled").value(true))
                .andExpect(jsonPath("$.settings.reminderDaysBefore").value(0))
                .andExpect(jsonPath("$.settings.reminderOnDue").value(false))
                .andExpect(jsonPath("$.settings.reminderEveryDays").value(14))
                .andExpect(jsonPath("$.settings.reminderMax").value(5));
        save(user, reminders.replace("\"reminderMax\": 5", "\"reminderMax\": 11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Send from 1 to 10 reminders an invoice"));
        save(user, reminders.replace("\"reminderEveryDays\": 14", "\"reminderEveryDays\": 61"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Repeat reminders every 0 to 60 days"));
    }

    @Test
    @DisplayName("saved settings come back on the next read, for that user only")
    void savesPerUser() throws Exception {
        User user = createUser();
        save(user, VALID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.taxLabel").value("GST"));
        save(user, VALID.replace("\"paymentTermsDays\": 15", "\"paymentTermsDays\": 30")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/settings").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.settings.defaultCurrency").value("INR"))
                .andExpect(jsonPath("$.settings.paymentTermsDays").value(30))
                .andExpect(jsonPath("$.settings.defaultTaxRate").value(18))
                .andExpect(jsonPath("$.settings.timeZone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.settings.emailMessage").value("Hello {client}, here is {document}."));
        mockMvc.perform(get("/api/v1/settings").header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(jsonPath("$.settings.defaultCurrency").value("USD"));
    }

    @Test
    @DisplayName("unknown time zones, bad currencies and tax rates over 100 are refused")
    void validation() throws Exception {
        User user = createUser();

        save(user, VALID.replace("Asia/Kolkata", "Mars/Olympus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a time zone from the list"));
        save(user, VALID.replace("\"INR\"", "\"rupees\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Currency must be a three-letter code such as USD"));
        save(user, VALID.replace("\"defaultTaxRate\": 18", "\"defaultTaxRate\": 101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The tax rate must be from 0 to 100"));
    }
}
