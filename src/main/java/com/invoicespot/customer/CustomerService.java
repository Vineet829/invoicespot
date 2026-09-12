package com.invoicespot.customer;

import com.invoicespot.common.ApiException;
import com.invoicespot.customer.dto.CreateCustomerRequest;
import com.invoicespot.customer.dto.CustomerListResponse;
import com.invoicespot.customer.dto.CustomerResponse;
import com.invoicespot.customer.dto.UpdateCustomerRequest;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private static final int PAGE_SIZE = 10;

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public CustomerService(CustomerRepository customerRepository, UserRepository userRepository) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CustomerResponse create(Long ownerPkid, CreateCustomerRequest request) {
        if (isBlank(request.name()) || isBlank(request.email()) || isBlank(request.phoneNumber())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "A Customer must have at least a name, email and phone number");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (customerRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That Customer already exists");
        }

        User owner = userRepository
                .findById(ownerPkid)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));

        Customer customer = new Customer();
        customer.setCreatedBy(owner);
        customer.setName(request.name().trim());
        customer.setEmail(email);
        customer.setPhoneNumber(request.phoneNumber().trim());
        customer.setVatTinNo(request.vatTinNo() != null ? request.vatTinNo() : 0L);
        customer.setAddress(request.address());
        customer.setCity(request.city());
        customer.setCountry(request.country());
        customer.setAccountNo(generateAccountNo());
        customerRepository.save(customer);
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public CustomerListResponse listMine(Long ownerPkid, int pageNumber) {
        int page = Math.max(pageNumber, 1);
        Page<Customer> result = customerRepository.findByCreatedByPkid(
                ownerPkid,
                PageRequest.of(page - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new CustomerListResponse(
                true,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getContent().stream().map(CustomerResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public CustomerResponse getOne(Long ownerPkid, String id) {
        return CustomerResponse.from(requireOwned(ownerPkid, id, "Customer not found",
                "You are not authorized to view this customer's information. He/She is not your"
                        + " customer"));
    }

    @Transactional
    public CustomerResponse update(Long ownerPkid, String id, UpdateCustomerRequest request) {
        Customer customer = requireOwned(ownerPkid, id, "That Customer does not exist",
                "You are not authorized to update this customer's information. He/She is not your"
                        + " customer");
        if (request.name() != null) {
            customer.setName(request.name());
        }
        if (request.email() != null) {
            customer.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        }
        if (request.phoneNumber() != null) {
            customer.setPhoneNumber(request.phoneNumber());
        }
        if (request.vatTinNo() != null) {
            customer.setVatTinNo(request.vatTinNo());
        }
        if (request.address() != null) {
            customer.setAddress(request.address());
        }
        if (request.city() != null) {
            customer.setCity(request.city());
        }
        if (request.country() != null) {
            customer.setCountry(request.country());
        }
        customerRepository.save(customer);
        return CustomerResponse.from(customer);
    }

    @Transactional
    public void delete(Long ownerPkid, String id) {
        Customer customer = requireOwned(ownerPkid, id, "That customer does not exist!",
                "You are not authorized to delete this customer's information. He/She is not your"
                        + " customer!");
        customerRepository.delete(customer);
    }

    private Customer requireOwned(
            Long ownerPkid, String id, String notFoundMessage, String forbiddenMessage) {
        Customer customer = externalId(id)
                .flatMap(customerRepository::findByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, notFoundMessage));
        if (!customer.getCreatedBy().getPkid().equals(ownerPkid)) {
            throw new ApiException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
        return customer;
    }

    private String generateAccountNo() {
        byte[] material = new byte[3];
        random.nextBytes(material);
        return "CUS-" + HexFormat.of().formatHex(material).toUpperCase(Locale.ROOT);
    }

    private static Optional<UUID> externalId(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
