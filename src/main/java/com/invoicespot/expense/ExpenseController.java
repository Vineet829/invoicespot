package com.invoicespot.expense;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Permission;
import com.invoicespot.business.Requires;
import com.invoicespot.expense.dto.ExpenseCategoriesResponse;
import com.invoicespot.expense.dto.ExpenseListResponse;
import com.invoicespot.expense.dto.ExpenseRequest;
import com.invoicespot.expense.dto.ExpenseResultResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expense")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    public ExpenseListResponse list(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return expenseService.list(access, ExpenseSearch.of(query, category, from, to, page, size));
    }

    // The categories the business has used, for picking one again.
    @GetMapping("/categories")
    public ExpenseCategoriesResponse categories(@Requires(Permission.VIEW_RECORDS) BusinessAccess access) {
        return new ExpenseCategoriesResponse(true, expenseService.categories(access));
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> csv(
            @Requires(Permission.EXPORT_LISTS) BusinessAccess access,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return expenseService.csv(access, ExpenseSearch.of(query, category, from, to, 1, 1)).download();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResultResponse create(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access, @Valid @RequestBody ExpenseRequest request) {
        return new ExpenseResultResponse(true, "Expense saved", expenseService.create(access, request));
    }

    @PatchMapping("/{id}")
    public ExpenseResultResponse update(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @Valid @RequestBody ExpenseRequest request) {
        return new ExpenseResultResponse(true, "Expense updated", expenseService.update(access, id, request));
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        expenseService.delete(access, id);
        return new MessageResponse(true, "Expense deleted");
    }
}
