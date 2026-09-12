package com.invoicespot.customer;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.customer.dto.CreateCustomerRequest;
import com.invoicespot.customer.dto.CreatedCustomerResponse;
import com.invoicespot.customer.dto.CustomerListResponse;
import com.invoicespot.customer.dto.CustomerResponse;
import com.invoicespot.customer.dto.SingleCustomerResponse;
import com.invoicespot.customer.dto.UpdateCustomerRequest;
import com.invoicespot.customer.dto.UpdatedCustomerResponse;
import com.invoicespot.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping("/create")
    public CreatedCustomerResponse create(
            @AuthenticationPrincipal User principal, @RequestBody CreateCustomerRequest request) {
        CustomerResponse created = customerService.create(principal.getPkid(), request);
        return new CreatedCustomerResponse(
                true,
                "Your customer named: " + created.name() + ", was created successfully",
                created);
    }

    @GetMapping("/all")
    public CustomerListResponse listMine(
            @AuthenticationPrincipal User principal,
            @RequestParam(name = "page", defaultValue = "1") int page) {
        return customerService.listMine(principal.getPkid(), page);
    }

    @GetMapping("/{id}")
    public SingleCustomerResponse getOne(
            @AuthenticationPrincipal User principal, @PathVariable String id) {
        return new SingleCustomerResponse(true, customerService.getOne(principal.getPkid(), id));
    }

    @PatchMapping("/{id}")
    public UpdatedCustomerResponse update(
            @AuthenticationPrincipal User principal,
            @PathVariable String id,
            @RequestBody UpdateCustomerRequest request) {
        CustomerResponse updated = customerService.update(principal.getPkid(), id, request);
        return new UpdatedCustomerResponse(
                true, updated.name() + "'s info was successfully updated", updated);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(
            @AuthenticationPrincipal User principal, @PathVariable String id) {
        customerService.delete(principal.getPkid(), id);
        return new MessageResponse(true, "Your customer has been deleted");
    }
}
