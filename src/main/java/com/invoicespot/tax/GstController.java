package com.invoicespot.tax;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Permission;
import com.invoicespot.business.Requires;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The GST state codes, so forms and the server use one list.
@RestController
@RequestMapping("/api/v1/gst")
public class GstController {

    public record StatesResponse(boolean success, List<GstStates.State> states) {}

    @GetMapping("/states")
    public StatesResponse states(@Requires(Permission.VIEW_RECORDS) BusinessAccess access) {
        return new StatesResponse(true, GstStates.places());
    }
}
