package com.invoicespot.customer;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessRepository;
import com.invoicespot.common.ApiException;
import com.invoicespot.common.CsvFile;
import com.invoicespot.customer.dto.CreateCustomerRequest;
import com.invoicespot.customer.dto.CustomerBalance;
import com.invoicespot.customer.dto.CustomerListResponse;
import com.invoicespot.customer.dto.CustomerResponse;
import com.invoicespot.customer.dto.CustomerSummaryResponse;
import com.invoicespot.customer.dto.UpdateCustomerRequest;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.tax.GstStates;
import com.invoicespot.tax.Gstin;
import com.invoicespot.user.UserRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
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
    private final BusinessRepository businessRepository;
    private final CustomerDirectory customerDirectory;
    private final SettingsService settingsService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CustomerService(
            CustomerRepository customerRepository,
            UserRepository userRepository,
            BusinessRepository businessRepository,
            CustomerDirectory customerDirectory,
            SettingsService settingsService,
            Clock clock) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.customerDirectory = customerDirectory;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional
    public CustomerResponse create(BusinessAccess access, CreateCustomerRequest request) {
        if (isBlank(request.name()) || isBlank(request.email()) || isBlank(request.phoneNumber())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "A Customer must have at least a name, email and phone number");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (customerRepository.existsByBusinessPkidAndEmailIgnoreCase(access.businessPkid(), email)) {
            throw duplicateEmail();
        }

        Customer customer = new Customer();
        customer.setBusiness(businessRepository.getReferenceById(access.businessPkid()));
        customer.setCreatedBy(userRepository.getReferenceById(access.userPkid()));
        customer.setName(request.name().trim());
        customer.setEmail(email);
        customer.setPhoneNumber(request.phoneNumber().trim());
        customer.setVatTinNo(vatTinNo(request.vatTinNo()));
        gst(customer, request.gstin(), request.stateCode());
        customer.setAddress(request.address());
        customer.setCity(request.city());
        customer.setCountry(request.country());
        customer.setAccountNo(newAccountNo(access.businessPkid()));
        customerRepository.save(customer);
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public CustomerListResponse listMine(BusinessAccess access, CustomerSearch search) {
        String currency = settingsService.forBusiness(access.businessPkid()).getDefaultCurrency();
        Page<CustomerDirectory.Row> rows = customerDirectory.search(access.businessPkid(), search, currency);
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

    // Every client the list's search matches, in its order, with one column
    // for what they owe in each currency.
    @Transactional(readOnly = true)
    public CsvFile csv(BusinessAccess access, CustomerSearch search) {
        Long businessPkid = access.businessPkid();
        BusinessSettings settings = settingsService.forBusiness(businessPkid);
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        List<CustomerDirectory.Contact> contacts =
                customerDirectory.contacts(businessPkid, search, settings.getDefaultCurrency());
        Map<Long, Map<String, BigDecimal>> owed = customerDirectory.balancesOf(businessPkid).stream()
                .collect(Collectors.groupingBy(
                        CustomerDirectory.Balance::customerPkid,
                        Collectors.toMap(
                                CustomerDirectory.Balance::currency,
                                CustomerDirectory.Balance::outstanding,
                                BigDecimal::add,
                                TreeMap::new)));
        List<String> currencies = owed.values().stream()
                .flatMap(balances -> balances.keySet().stream())
                .distinct()
                .sorted()
                .toList();
        List<String> header = new ArrayList<>(List.of(
                "Name", "Email", "Phone", "Account number", "VAT/TIN", "GSTIN", "Address", "City", "Country",
                "Documents"));
        currencies.forEach(currency -> header.add("Owed " + currency));
        header.add("Added");
        return CsvFile.of(
                "invoice-spot-clients-" + LocalDate.now(clock.withZone(zone)) + ".csv",
                header,
                contacts.stream()
                        .map(contact -> {
                            List<Object> row = new ArrayList<>(Arrays.asList(
                                    contact.name(),
                                    contact.email(),
                                    contact.phoneNumber(),
                                    contact.accountNo(),
                                    contact.vatTinNo(),
                                    contact.gstin(),
                                    contact.address(),
                                    contact.city(),
                                    contact.country(),
                                    contact.documents()));
                            Map<String, BigDecimal> balances = owed.getOrDefault(contact.pkid(), Map.of());
                            currencies.forEach(currency ->
                                    row.add(balances.getOrDefault(currency, BigDecimal.ZERO.setScale(2))));
                            row.add(LocalDate.ofInstant(contact.createdAt(), zone));
                            return row;
                        })
                        .toList());
    }

    @Transactional(readOnly = true)
    public CustomerSummaryResponse summary(BusinessAccess access, String id) {
        Customer customer = requireInBusiness(access, id, "Customer not found",
                "You are not authorized to view this customer's information. He/She is not your"
                        + " customer");
        ZoneId zone = ZoneId.of(settingsService.forBusiness(access.businessPkid()).getTimeZone());
        Instant startOfToday = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
        return new CustomerSummaryResponse(true, customerDirectory.totals(customer.getPkid(), startOfToday));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getOne(BusinessAccess access, String id) {
        return CustomerResponse.from(requireInBusiness(access, id, "Customer not found",
                "You are not authorized to view this customer's information. He/She is not your"
                        + " customer"));
    }

    @Transactional
    public CustomerResponse update(BusinessAccess access, String id, UpdateCustomerRequest request) {
        Customer customer = requireInBusiness(access, id, "That Customer does not exist",
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
            if (customerRepository.existsByBusinessPkidAndEmailIgnoreCaseAndPkidNot(
                    access.businessPkid(), email, customer.getPkid())) {
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
        if (request.gstin() != null || request.stateCode() != null) {
            gst(
                    customer,
                    Objects.requireNonNullElse(request.gstin(), Objects.requireNonNullElse(customer.getGstin(), "")),
                    Objects.requireNonNullElse(
                            request.stateCode(), Objects.requireNonNullElse(customer.getStateCode(), "")));
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
    public void delete(BusinessAccess access, String id) {
        Customer customer = requireInBusiness(access, id, "That customer does not exist!",
                "You are not authorized to delete this customer's information. He/She is not your"
                        + " customer!");
        customerRepository.delete(customer);
    }

    private Customer requireInBusiness(
            BusinessAccess access, String id, String notFoundMessage, String forbiddenMessage) {
        Customer customer = externalId(id)
                .flatMap(customerRepository::findByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, notFoundMessage));
        if (!customer.getBusiness().getPkid().equals(access.businessPkid())) {
            throw new ApiException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
        return customer;
    }

    // Account numbers are unique in each business; a clash, likelier when many
    // clients are imported at once, draws a new one.
    private String newAccountNo(Long businessPkid) {
        String accountNo;
        do {
            byte[] material = new byte[3];
            random.nextBytes(material);
            accountNo = "CUS-" + HexFormat.of().formatHex(material).toUpperCase(Locale.ROOT);
        } while (customerRepository.findByBusinessPkidAndAccountNo(businessPkid, accountNo).isPresent());
        return accountNo;
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

    // A GSTIN sets the state it was issued in; without one, the state is any
    // GST state code or outside India, or none. Blank clears either.
    private static void gst(Customer customer, String gstinValue, String stateValue) {
        String gstin = Gstin.parse(gstinValue);
        String state = stateValue == null || stateValue.isBlank() ? null : stateValue.trim();
        if (gstin != null) {
            state = Gstin.stateCode(gstin);
        } else if (state != null && !GstStates.isPlace(state)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose the client's state from the list");
        }
        customer.setGstin(gstin);
        customer.setStateCode(state);
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
