package com.invoicespot.document;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessService;
import com.invoicespot.common.ApiException;
import com.invoicespot.document.dto.DocumentLink;
import com.invoicespot.document.dto.RecurringListResponse;
import com.invoicespot.document.dto.RecurringRequest;
import com.invoicespot.document.dto.RecurringResponse;
import com.invoicespot.notification.NotificationService;
import com.invoicespot.notification.NotificationType;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.user.DemoAccountGuard;
import com.invoicespot.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RecurringService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecurringService.class);
    private static final int MOST_PER_RUN = 12;

    private record Made(Long businessPkid, String documentId, Long runPkid, boolean email) {}

    private final RecurringScheduleRepository scheduleRepository;
    private final RecurringRunRepository runRepository;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final DocumentMailService documentMailService;
    private final SettingsService settingsService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final BusinessService businessService;
    private final DemoAccountGuard demoAccountGuard;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public RecurringService(
            RecurringScheduleRepository scheduleRepository,
            RecurringRunRepository runRepository,
            DocumentRepository documentRepository,
            DocumentService documentService,
            DocumentMailService documentMailService,
            SettingsService settingsService,
            NotificationService notificationService,
            UserRepository userRepository,
            BusinessService businessService,
            DemoAccountGuard demoAccountGuard,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.runRepository = runRepository;
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.documentMailService = documentMailService;
        this.settingsService = settingsService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.businessService = businessService;
        this.demoAccountGuard = demoAccountGuard;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Transactional
    public RecurringResponse create(BusinessAccess access, RecurringRequest request) {
        Document invoice = requireDocumentInBusiness(access, request.documentId(), true);
        if (invoice.getDocumentType() != DocumentType.INVOICE) {
            throw new ApiException(HttpStatus.CONFLICT, "Only an invoice can repeat");
        }
        DocumentService.requireNotVoid(invoice, "A void invoice can't repeat");
        if (scheduleRepository.findByTemplate(invoice).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "This invoice already repeats");
        }
        RecurringSchedule schedule = new RecurringSchedule();
        schedule.setBusiness(invoice.getBusiness());
        schedule.setCreatedBy(userRepository.getReferenceById(access.userPkid()));
        schedule.setTemplate(invoice);
        apply(schedule, request, today(access.businessPkid()));
        scheduleRepository.save(schedule);
        return respond(schedule);
    }

    @Transactional(readOnly = true)
    public RecurringListResponse list(BusinessAccess access) {
        return new RecurringListResponse(
                true,
                scheduleRepository.findByBusinessPkidOrderByCreatedAtDescPkidDesc(access.businessPkid()).stream()
                        .map(this::respond)
                        .toList());
    }

    @Transactional(readOnly = true)
    public Optional<RecurringResponse> forDocument(BusinessAccess access, String documentId) {
        return scheduleRepository.findByTemplate(requireDocumentInBusiness(access, documentId, false))
                .map(this::respond);
    }

    // A schedule given a new date or frequency counts its dates from the date
    // given; one that keeps both keeps counting from its first. Invoices
    // already made still count toward the most allowed.
    @Transactional
    public RecurringResponse update(BusinessAccess access, String id, RecurringRequest request) {
        RecurringSchedule schedule = requireInBusiness(access, id);
        apply(schedule, request, today(access.businessPkid()));
        return respond(schedule);
    }

    // A schedule resumed after its date has passed makes that invoice at the
    // next daily run.
    @Transactional
    public RecurringResponse setPaused(BusinessAccess access, String id, boolean paused) {
        RecurringSchedule schedule = requireInBusiness(access, id);
        schedule.setPaused(paused);
        return respond(schedule);
    }

    @Transactional
    public void delete(BusinessAccess access, String id) {
        scheduleRepository.delete(requireInBusiness(access, id));
    }

    // Makes every invoice that is due, one schedule at a time, catching up
    // on missed periods. Each run is recorded under its date, so a period is
    // never invoiced twice; one that fails is logged and the rest still run.
    public int makeDue(Instant now) {
        int made = 0;
        LocalDate latest = LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(1);
        for (Long schedulePkid : scheduleRepository.findRunnable(latest)) {
            for (int period = 0; period < MOST_PER_RUN; period++) {
                Optional<Made> invoice;
                try {
                    invoice = transaction.execute(status -> makeOne(schedulePkid, now));
                } catch (RuntimeException exception) {
                    LOGGER.warn("Recurring schedule {} failed to run", schedulePkid, exception);
                    break;
                }
                if (invoice.isEmpty()) {
                    break;
                }
                made++;
                if (invoice.get().email()) {
                    email(invoice.get());
                }
            }
        }
        return made;
    }

    private Optional<Made> makeOne(Long schedulePkid, Instant now) {
        RecurringSchedule schedule = scheduleRepository.findForUpdate(schedulePkid).orElse(null);
        if (schedule == null || schedule.isPaused() || schedule.getNextRun() == null) {
            return Optional.empty();
        }
        Long businessPkid = schedule.getBusiness().getPkid();
        ZoneId zone = ZoneId.of(settingsService.forBusiness(businessPkid).getTimeZone());
        if (schedule.getNextRun().isAfter(LocalDate.ofInstant(now, zone))) {
            return Optional.empty();
        }
        Document invoice = documentService.createRecurring(
                schedule.getTemplate(),
                schedule.getFrequency(),
                schedule.getCreatedBy(),
                schedule.isIssueAutomatically());
        RecurringRun run = new RecurringRun();
        run.setSchedule(schedule);
        run.setRunDate(schedule.getNextRun());
        run.setDocument(invoice);
        run.setCreatedAt(Instant.now(clock));
        runRepository.save(run);
        schedule.setRunsDone(schedule.getRunsDone() + 1);
        schedule.setNextRun(nextRun(schedule));
        CustomerSnapshot client = invoice.getCustomerSnapshot();
        String forClient = client == null || client.getName() == null ? "" : " for " + client.getName();
        if (invoice.isDraft()) {
            notificationService.notify(
                    invoice,
                    NotificationType.RECURRING_DRAFT,
                    "A draft invoice" + forClient + " from its " + schedule.getFrequency().label()
                            + " schedule is ready to review");
        } else {
            notificationService.notify(
                    invoice,
                    NotificationType.RECURRING_CREATED,
                    "Invoice " + invoice.getDocumentNumber() + forClient + " was made by its "
                            + schedule.getFrequency().label() + " schedule");
        }
        boolean email = !invoice.isDraft()
                && schedule.isEmailAutomatically()
                && !demoAccountGuard.isDemo(businessService.owner(businessPkid));
        return Optional.of(new Made(businessPkid, invoice.getId().toString(), run.getPkid(), email));
    }

    private void email(Made invoice) {
        try {
            BusinessAccess access = businessService.ownerAccess(invoice.businessPkid());
            documentMailService.emailAutomatically(access, invoice.documentId());
            transaction.executeWithoutResult(status -> runRepository.markEmailed(invoice.runPkid()));
        } catch (Exception exception) {
            LOGGER.warn("Recurring invoice {} could not be emailed", invoice.documentId(), exception);
        }
    }

    // The run after the ones done, or none once the count or end date is met.
    private static LocalDate nextRun(RecurringSchedule schedule) {
        return schedule.runAfter(schedule.getRunsDone());
    }

    private static void apply(RecurringSchedule schedule, RecurringRequest request, LocalDate today) {
        RecurringFrequency frequency;
        try {
            frequency = RecurringFrequency.valueOf(request.frequency().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Repeat weekly, monthly, quarterly or yearly");
        }
        if (request.startDate().isBefore(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The first invoice can't be in the past");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The end date must come after the first invoice");
        }
        // A change that keeps the frequency and the next date, as its dialog
        // sends it, keeps counting from the first date, so one from Jan 31
        // still runs on Mar 31 after Feb 28.
        boolean sameDates = frequency == schedule.getFrequency()
                && request.startDate().equals(schedule.getNextRun());
        schedule.setFrequency(frequency);
        if (!sameDates) {
            schedule.setStartDate(request.startDate());
            schedule.setStartOffset(schedule.getRunsDone());
        }
        schedule.setEndDate(request.endDate());
        schedule.setMaxRuns(request.maxRuns());
        boolean issue = !Boolean.FALSE.equals(request.issueAutomatically());
        boolean email = Boolean.TRUE.equals(request.emailAutomatically());
        if (email && !issue) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Only an invoice that is issued as it is made can be emailed on its own");
        }
        schedule.setIssueAutomatically(issue);
        schedule.setEmailAutomatically(email);
        schedule.setNextRun(nextRun(schedule));
    }

    private RecurringResponse respond(RecurringSchedule schedule) {
        Document template = schedule.getTemplate();
        CustomerSnapshot client = template.getCustomerSnapshot();
        DocumentLink last = runRepository.findFirstByScheduleOrderByRunDateDesc(schedule)
                .map(RecurringRun::getDocument)
                .map(DocumentLink::from)
                .orElse(null);
        return new RecurringResponse(
                schedule.getId(),
                template.getId(),
                template.getDocumentNumber(),
                client == null ? null : client.getName(),
                template.getTotal(),
                template.getCurrency(),
                schedule.getFrequency().name(),
                schedule.getStartDate(),
                schedule.getNextRun(),
                schedule.getEndDate(),
                schedule.getMaxRuns(),
                schedule.getRunsDone(),
                schedule.isIssueAutomatically(),
                schedule.isEmailAutomatically(),
                schedule.isPaused(),
                schedule.getNextRun() == null,
                last);
    }

    private LocalDate today(Long businessPkid) {
        return LocalDate.now(clock.withZone(ZoneId.of(settingsService.forBusiness(businessPkid).getTimeZone())));
    }

    // The document, locked until the transaction ends when a schedule is made
    // for it, as deleting a draft locks it: a delete that waits for the
    // schedule then finds it, and keeps the draft instead of deleting both.
    private Document requireDocumentInBusiness(BusinessAccess access, String id, boolean lock) {
        Document document = parse(id)
                .flatMap(lock ? documentRepository::findForUpdateByExternalId : documentRepository::findByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That document does not exist"));
        if (!document.getBusiness().getPkid().equals(access.businessPkid())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only repeat your own invoices");
        }
        return document;
    }

    // The schedule, locked until the transaction ends, so a change to it and
    // a run making its invoice take turns.
    private RecurringSchedule requireInBusiness(BusinessAccess access, String id) {
        RecurringSchedule schedule = parse(id)
                .flatMap(scheduleRepository::findForUpdateByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That schedule does not exist"));
        if (!schedule.getBusiness().getPkid().equals(access.businessPkid())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only change your own schedules");
        }
        return schedule;
    }

    private static Optional<UUID> parse(String id) {
        try {
            return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
