package com.invoicespot.customer;

import com.invoicespot.common.ApiException;
import com.invoicespot.customer.dto.CreateCustomerRequest;
import com.invoicespot.customer.dto.CustomerBalance;
import com.invoicespot.customer.dto.CustomerListResponse;
import com.invoicespot.customer.dto.CustomerResponse;
import com.invoicespot.customer.dto.CustomerSummaryResponse;
import com.invoicespot.customer.dto.UpdateCustomerRequest;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final CustomerDirectory customerDirectory;
    private final SettingsService settingsService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CustomerService(
            CustomerRepository customerRepository,
            UserRepository userRepository,
            CustomerDirectory customerDirectory,
            SettingsService settingsService,
            Clock clock) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.customerDirectory = customerDirectory;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional
    public CustomerResponse create(Long ownerPkid, CreateCustomerRequest request) {
        if (isBlank(request.name()) || isBlank(request.email()) || isBlank(request.phoneNumber())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "A Customer must have at least a name, email and phone number");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (customerRepository.existsByCreatedByPkidAndEmailIgnoreCase(ownerPkid, email)) {
            throw duplicateEmail();
        }

        User owner = userRepository
                .findById(ownerPkid)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));

        Customer customer = new Customer();
        customer.setCreatedBy(owner);
        customer.setName(request.name().trim());
        customer.setEmail(email);
        customer.setPhoneNumber(request.phoneNumber().trim());
        customer.setVatTinNo(vatTinNo(request.vatTinNo()));
        customer.setAddress(request.address());
        customer.setCity(request.city());
        customer.setCountry(request.country());
        customer.setAccountNo(generateAccountNo());
        customerRepository.save(customer);
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public CustomerListResponse listMine(Long ownerPkid, CustomerSearch search) {
        String currency = settingsService.forUser(ownerPkid).getDefaultCurrency();
        Page<CustomerDirectory.Row> rows = customerDirectory.search(ownerPkid, search, currency);
        List<Long> pkids = rows.getContent().stream().map(CustomerDirectory.Row::pkid).toList();
        Map<Long, Customer> customers = customerRepository.findAllById(pkids).stream()
                .collect(Collectors.toMap(Customer::getPkid, Function.identity()));
        Map<Long, List<CustomerBalance>> balances = customerDirectory.balances(pkids).stream()
                .collect(Collectors.groupingBy(
                        CustomerDirectory.Balance::customerPkid,
                        Collectors.mapping(
                                balance -> new CustomerBalance(balance.currency(), balance.outstanding()),
                                Collectors.toList())));
        return new CustomerListResponse(
                true,
                rows.getTotalElements(),
                rows.getTotalPages(),
                rows.getContent().stream()
                        .map(row -> CustomerResponse.from(
                                customers.get(row.pkid()),
                                row.documents(),
                                balances.getOrDefault(row.pkid(), List.of())))
                        .toList());
    }

    @Transactional(readOnly = true)
    public CustomerSummaryResponse summary(Long ownerPkid, String id) {
        Customer customer = requireOwned(ownerPkid, id, "Customer not found",
                "You are not authorized to view this customer's information. He/She is not your"
                        + " customer");
        ZoneId zone = ZoneId.of(settingsService.forUser(ownerPkid).getTimeZone());
        Instant startOfToday = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
        return new CustomerSummaryResponse(true, customerDirectory.totals(customer.getPkid(), startOfToday));
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
            if (request.name().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Customer name cannot be blank");
            }
            customer.setName(request.name());
        }
        if (request.email() != null) {
            if (request.email().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Customer email cannot be blank");
            }
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            if (customerRepository.existsByCreatedByPkidAndEmailIgnoreCaseAndPkidNot(
                    ownerPkid, email, customer.getPkid())) {
                throw duplicateEmail();
            }
            customer.setEmail(email);
        }
        if (request.phoneNumber() != null) {
            if (request.phoneNumber().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Customer phone number cannot be blank");
            }
            customer.setPhoneNumber(request.phoneNumber());
        }
        if (request.vatTinNo() != null) {
            customer.setVatTinNo(vatTinNo(request.vatTinNo()));
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

    private static ApiException duplicateEmail() {
        return new ApiException(HttpStatus.CONFLICT, "You already have a customer with that email address");
    }

    private static String vatTinNo(String value) {
        if (value == null || value.isBlank() || value.trim().equals("0")) {
            return null;
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
