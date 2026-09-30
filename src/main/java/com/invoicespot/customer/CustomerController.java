package com.invoicespot.customer;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.customer.dto.CreateCustomerRequest;
import com.invoicespot.customer.dto.CreatedCustomerResponse;
import com.invoicespot.customer.dto.CustomerListResponse;
import com.invoicespot.customer.dto.CustomerResponse;
import com.invoicespot.customer.dto.CustomerSummaryResponse;
import com.invoicespot.customer.dto.ImportPreviewResponse;
import com.invoicespot.customer.dto.ImportResultResponse;
import com.invoicespot.customer.dto.SingleCustomerResponse;
import com.invoicespot.customer.dto.UpdateCustomerRequest;
import com.invoicespot.customer.dto.UpdatedCustomerResponse;
import com.invoicespot.user.User;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/customer")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerImport customerImport;

    public CustomerController(CustomerService customerService, CustomerImport customerImport) {
        this.customerService = customerService;
        this.customerImport = customerImport;
    }

    @PostMapping("/create")
    public CreatedCustomerResponse create(
            @AuthenticationPrincipal User principal, @Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse created = customerService.create(principal.getPkid(), request);
        return new CreatedCustomerResponse(
                true,
                "Your customer named: " + created.name() + ", was created successfully",
                created);
    }

    @GetMapping("/all")
    public CustomerListResponse listMine(
            @AuthenticationPrincipal User principal,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "sort", defaultValue = "createdAt") String sort,
            @RequestParam(name = "dir", defaultValue = "desc") String direction) {
        return customerService.listMine(
                principal.getPkid(), CustomerSearch.of(query, sort, direction, page, size));
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> csv(
            @AuthenticationPrincipal User principal,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "sort", defaultValue = "createdAt") String sort,
            @RequestParam(name = "dir", defaultValue = "desc") String direction) {
        return customerService.csv(principal.getPkid(), CustomerSearch.of(query, sort, direction, 1, 1)).download();
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreviewResponse previewImport(
            @AuthenticationPrincipal User principal,
            @RequestParam(name = "file", required = false) MultipartFile file) {
        return customerImport.preview(principal.getPkid(), file);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultResponse importClients(
            @AuthenticationPrincipal User principal,
            @RequestParam(name = "file", required = false) MultipartFile file) {
        return customerImport.importAll(principal.getPkid(), file);
    }

    @GetMapping("/{id}")
    public SingleCustomerResponse getOne(
            @AuthenticationPrincipal User principal, @PathVariable String id) {
        return new SingleCustomerResponse(true, customerService.getOne(principal.getPkid(), id));
    }

    @GetMapping("/{id}/summary")
    public CustomerSummaryResponse summary(@AuthenticationPrincipal User principal, @PathVariable String id) {
        return customerService.summary(principal.getPkid(), id);
    }

    @PatchMapping("/{id}")
    public UpdatedCustomerResponse update(
            @AuthenticationPrincipal User principal,
            @PathVariable String id,
            @Valid @RequestBody UpdateCustomerRequest request) {
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
