package com.invoicespot.user;

import com.invoicespot.common.ApiException;
import com.invoicespot.config.DemoProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DemoAccountGuard {

    private final String demoEmail;

    public DemoAccountGuard(DemoProperties properties) {
        this.demoEmail = properties.email() == null ? "" : properties.email().trim();
    }

    public boolean isDemo(User user) {
        return isDemoEmail(user.getEmail());
    }

    public boolean isDemoEmail(String email) {
        return !demoEmail.isEmpty() && email != null && email.trim().equalsIgnoreCase(demoEmail);
    }

    public void requireNotDemo(User user, String action) {
        if (isDemo(user)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "This is a shared demo account, so " + action + " is turned off");
        }
    }

    public void requireNotDemoTarget(User user, String action) {
        if (isDemo(user)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "The shared demo account can't be " + action);
        }
    }
}
