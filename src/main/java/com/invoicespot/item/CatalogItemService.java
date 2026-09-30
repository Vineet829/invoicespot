package com.invoicespot.item;

import com.invoicespot.common.ApiException;
import com.invoicespot.common.SearchText;
import com.invoicespot.item.dto.CreateItemRequest;
import com.invoicespot.item.dto.ItemListResponse;
import com.invoicespot.item.dto.ItemResponse;
import com.invoicespot.item.dto.UpdateItemRequest;
import com.invoicespot.user.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogItemService {

    private static final int MAX_PAGE_SIZE = 500;

    private final CatalogItemRepository itemRepository;
    private final UserRepository userRepository;

    public CatalogItemService(CatalogItemRepository itemRepository, UserRepository userRepository) {
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ItemListResponse list(Long ownerPkid, String query, int pageNumber, int pageSize) {
        int page = Math.max(pageNumber, 1);
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Page<CatalogItem> result = itemRepository.search(
                ownerPkid,
                SearchText.containing(query),
                PageRequest.of(page - 1, size, Sort.by("name").ascending().and(Sort.by("pkid"))));
        return new ItemListResponse(
                true,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getContent().stream().map(ItemResponse::from).toList());
    }

    @Transactional
    public ItemResponse create(Long ownerPkid, CreateItemRequest request) {
        String name = requireName(request.name());
        if (itemRepository.existsByCreatedByPkidAndNameIgnoreCase(ownerPkid, name)) {
            throw duplicateName();
        }
        CatalogItem item = new CatalogItem();
        item.setCreatedBy(userRepository.getReferenceById(ownerPkid));
        item.setName(name);
        item.setDescription(blankToNull(request.description()));
        item.setUnitPrice(request.unitPrice() == null ? BigDecimal.ZERO : request.unitPrice());
        itemRepository.save(item);
        return ItemResponse.from(item);
    }

    @Transactional
    public ItemResponse update(Long ownerPkid, String id, UpdateItemRequest request) {
        CatalogItem item = requireOwned(ownerPkid, id);
        if (request.name() != null) {
            String name = requireName(request.name());
            if (itemRepository.existsByCreatedByPkidAndNameIgnoreCaseAndPkidNot(ownerPkid, name, item.getPkid())) {
                throw duplicateName();
            }
            item.setName(name);
        }
        if (request.description() != null) {
            item.setDescription(blankToNull(request.description()));
        }
        if (request.unitPrice() != null) {
            item.setUnitPrice(request.unitPrice());
        }
        return ItemResponse.from(item);
    }

    @Transactional
    public void delete(Long ownerPkid, String id) {
        itemRepository.delete(requireOwned(ownerPkid, id));
    }

    private CatalogItem requireOwned(Long ownerPkid, String id) {
        CatalogItem item = externalId(id)
                .flatMap(itemRepository::findByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That item does not exist"));
        if (!item.getCreatedBy().getPkid().equals(ownerPkid)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not authorized to change this item. It's not yours");
        }
        return item;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "An item needs a name");
        }
        return name.trim();
    }

    private static ApiException duplicateName() {
        return new ApiException(HttpStatus.CONFLICT, "You already have an item with that name");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Optional<UUID> externalId(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
