package com.invoicespot.customer;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Requires;
import com.invoicespot.business.Permission;
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
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse created = customerService.create(access, request);
        return new CreatedCustomerResponse(
                true,
                "Your customer named: " + created.name() + ", was created successfully",
                created);
    }

    @GetMapping("/all")
    public CustomerListResponse listMine(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "sort", defaultValue = "createdAt") String sort,
            @RequestParam(name = "dir", defaultValue = "desc") String direction) {
        return customerService.listMine(
                access, CustomerSearch.of(query, sort, direction, page, size));
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> csv(
            @Requires(Permission.EXPORT_LISTS) BusinessAccess access,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "sort", defaultValue = "createdAt") String sort,
            @RequestParam(name = "dir", defaultValue = "desc") String direction) {
        return customerService.csv(access, CustomerSearch.of(query, sort, direction, 1, 1)).download();
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreviewResponse previewImport(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @RequestParam(name = "file", required = false) MultipartFile file) {
        return customerImport.preview(access, file);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultResponse importClients(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @RequestParam(name = "file", required = false) MultipartFile file) {
        return customerImport.importAll(access, file);
    }

    @GetMapping("/{id}")
    public SingleCustomerResponse getOne(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access, @PathVariable String id) {
        return new SingleCustomerResponse(true, customerService.getOne(access, id));
    }

    @GetMapping("/{id}/summary")
    public CustomerSummaryResponse summary(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access, @PathVariable String id) {
        return customerService.summary(access, id);
    }

    @PatchMapping("/{id}")
    public UpdatedCustomerResponse update(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @Valid @RequestBody UpdateCustomerRequest request) {
        CustomerResponse updated = customerService.update(access, id, request);
        return new UpdatedCustomerResponse(
                true, updated.name() + "'s info was successfully updated", updated);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(
            @Requires(Permission.DELETE_RECORDS) BusinessAccess access, @PathVariable String id) {
        customerService.delete(access, id);
        return new MessageResponse(true, "Your customer has been deleted");
    }
}
