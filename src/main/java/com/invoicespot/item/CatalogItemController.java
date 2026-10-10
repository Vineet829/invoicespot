package com.invoicespot.item;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Requires;
import com.invoicespot.business.Permission;
import com.invoicespot.item.dto.CreateItemRequest;
import com.invoicespot.item.dto.ItemListResponse;
import com.invoicespot.item.dto.ItemResultResponse;
import com.invoicespot.item.dto.UpdateItemRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/item")
public class CatalogItemController {

    private final CatalogItemService itemService;

    public CatalogItemController(CatalogItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public ItemListResponse list(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return itemService.list(access, query, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResultResponse create(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access, @Valid @RequestBody CreateItemRequest request) {
        return new ItemResultResponse(true, "Item saved", itemService.create(access, request));
    }

    @PatchMapping("/{id}")
    public ItemResultResponse update(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @Valid @RequestBody UpdateItemRequest request) {
        return new ItemResultResponse(true, "Item updated", itemService.update(access, id, request));
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        itemService.delete(access, id);
        return new MessageResponse(true, "Item deleted");
    }
}
