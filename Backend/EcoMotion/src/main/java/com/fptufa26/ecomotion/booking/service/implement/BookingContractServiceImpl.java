package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.BookingContractResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.exception.BookingContractNotAllowedException;
import com.fptufa26.ecomotion.booking.exception.BookingContractNotFoundException;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.service.BookingContractService;
import com.fptufa26.ecomotion.booking.service.BookingNotificationService;
import com.fptufa26.ecomotion.common.storage.exception.FileStorageException;
import com.fptufa26.ecomotion.common.storage.service.DocumentStorageService;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.verification.documentai.DrivingLicenseEligibilityPolicy;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.fptufa26.ecomotion.booking.dto.response.BookingContractStatusResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class BookingContractServiceImpl implements BookingContractService {

    private static final String STORAGE_ENTITY = "booking";
    private static final byte[] PDF_SIGNATURE =
            "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final BookingRepository bookingRepository;
    private final BookingPolicySnapshotRepository snapshotRepository;
    private final DocumentStorageService documentStorageService;
    private final PaymentRequestService paymentRequestService;
    private final ScheduleService scheduleService;
    private final BookingNotificationService bookingNotificationService;
    private final Clock clock;
    private final long maxBytes;
    private final com.fptufa26.ecomotion.user.service.UserService userService;
    private final com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService yearlyLedger;

    private boolean snapshotTermsAccepted(Booking booking) {
        return yearlyLedger.yearlyRoundingUnit(booking.getId()).isPresent();
    }

    public BookingContractServiceImpl(
            BookingRepository bookingRepository,
            BookingPolicySnapshotRepository snapshotRepository,
            DocumentStorageService documentStorageService,
            PaymentRequestService paymentRequestService,
            ScheduleService scheduleService,
            BookingNotificationService bookingNotificationService,
            Clock clock,
            @Value("${app.upload.max-file-size-bytes:8388608}") long maxBytes,
            com.fptufa26.ecomotion.user.service.UserService userService,
            com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService yearlyLedger) {
        this.bookingRepository = bookingRepository;
        this.snapshotRepository = snapshotRepository;
        this.documentStorageService = documentStorageService;
        this.paymentRequestService = paymentRequestService;
        this.scheduleService = scheduleService;
        this.bookingNotificationService = bookingNotificationService;
        this.clock = clock;
        this.maxBytes = maxBytes;
        this.userService = userService;
        this.yearlyLedger = yearlyLedger;
    }

    @Override
    @Transactional(readOnly = true)
    public BookingContractStatusResponse getContractStatus(
            Long userId,
            String bookingCode) {
        Booking booking = bookingRepository
                .findByBookingCodeAndRenter_User_Id(
                        bookingCode,
                        userId)
                .orElseThrow(() -> new BookingNotFoundException(
                        "Không tìm thấy đơn thuê: " + bookingCode));

        validateYearlyBooking(booking);

        String storedPath = booking.getContractUrl();
        boolean contractUploaded =
                storedPath != null && !storedPath.isBlank();

        boolean supportedDocument = contractUploaded
                && storedPath.startsWith(STORAGE_ENTITY + "/" + userId + "/")
                && storedPath.endsWith(".pdf");

        boolean termsAccepted = snapshotTermsAccepted(booking);
        boolean canUpload = booking.getStatus() == BookingStatus.ACCEPTED && termsAccepted
                && scheduleService.hasUnexpiredHold(booking.getId(), clock.instant());

        String restrictionReason = canUpload
                ? null
                : !termsAccepted ? "Cần đồng ý điều khoản trước khi nộp hợp đồng đã ký."
                : "Chỉ nộp hợp đồng sau khi nhà cung cấp chấp nhận và trong thời gian giữ lịch.";

        return new BookingContractStatusResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getVehicle().getBrand()
                        + " "
                        + booking.getVehicle().getModel(),
                PlanType.YEARLY,
                contractUploaded,
                canUpload,
                supportedDocument,
                restrictionReason);
    }

    @Override
    @Transactional
    public BookingContractResponse uploadContract(
            Long userId,
            String bookingCode,
            MultipartFile file) {
        Booking booking = bookingRepository
                .findByBookingCodeAndRenterUserIdForUpdate(
                        bookingCode,
                        userId)
                .orElseThrow(() -> new BookingNotFoundException(
                        "Không tìm thấy đơn thuê: " + bookingCode));

        validateYearlyBooking(booking);
        userService.assertRentalAllowed(userId, booking.getVehicle().getProvider().getUser().getId());

        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new BookingContractNotAllowedException(
                    "Contract upload requires an accepted booking");
        }
        if (!scheduleService.hasUnexpiredHold(booking.getId(), clock.instant())) {
            throw new BookingContractNotAllowedException(
                    "Thời hạn giữ xe đã hết. Vui lòng gửi yêu cầu thuê năm mới.");
        }
        DrivingLicenseEligibilityPolicy.requireEligibleForBooking(
                booking.getRenter(), booking.getVehicle(), booking.getPickupTime(), booking.getPlannedEndAt());

        if (!snapshotTermsAccepted(booking)) {
            throw new BookingContractNotAllowedException("Cần đồng ý điều khoản trước khi nộp hợp đồng đã ký.");
        }
        byte[] content = readPdf(file);
        String filename = "contract-" + UUID.randomUUID() + ".pdf";

        String storedPath = documentStorageService.store(
                STORAGE_ENTITY,
                userId,
                filename,
                content,
                MediaType.APPLICATION_PDF_VALUE);

        registerRollbackCleanup(storedPath);
        booking.setContractUrl(storedPath);
        booking.setStatus(BookingStatus.AWAITING_DEPOSIT);
        bookingRepository.flush();
        PaymentRequest paymentRequest = paymentRequestService.createDepositPaymentRequest(booking);
        scheduleService.renewHold(booking.getId(), paymentRequest.getExpiresAt());
        bookingNotificationService.changed(booking, "BOOKING_CHANGED");

        return new BookingContractResponse(
                booking.getBookingCode(),
                true);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadContract(
            Long userId,
            String bookingCode) {
        Booking booking = bookingRepository
                .findByBookingCode(bookingCode)
                .filter(row -> row.getRenter().getUser().getId().equals(userId)
                        || row.getVehicle().getProvider().getUser().getId().equals(userId))
                .orElseThrow(() -> new BookingNotFoundException(
                        "Không tìm thấy đơn thuê: " + bookingCode));

        validateYearlyBooking(booking);

        String storedPath = booking.getContractUrl();

        if (storedPath == null || storedPath.isBlank()) {
            throw new BookingContractNotFoundException();
        }

        if (!storedPath.startsWith(STORAGE_ENTITY + "/" + booking.getRenter().getUser().getId() + "/")
                || !storedPath.endsWith(".pdf")) {
            throw new BookingContractNotAllowedException(
                    "Contract is not stored in the supported private PDF format");
        }

        byte[] content = documentStorageService.read(storedPath);
        validatePdfSignature(content);
        return content;
    }

    private void validateYearlyBooking(Booking booking) {
        BookingPolicySnapshot snapshot = snapshotRepository
                .findByBooking_Id(booking.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Booking policy snapshot is missing"));

        if (snapshot.getPlanType() != PlanType.YEARLY) {
            throw new BookingContractNotAllowedException(
                    "This endpoint only supports yearly bookings");
        }
    }

    private void registerRollbackCleanup(String storedPath) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    try {
                        documentStorageService.delete(storedPath);
                    } catch (RuntimeException ex) {
                        log.error("Could not clean up a contract after transaction rollback", ex);
                    }
                }
            }
        });
    }

    private byte[] readPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Contract file is required");
        }

        if (file.getSize() > maxBytes) {
            throw new FileStorageException(
                    "Contract exceeds the maximum allowed size");
        }

        if (!MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(
                file.getContentType())) {
            throw new FileStorageException(
                    "Contract must be a PDF document");
        }

        try {
            byte[] content = file.getBytes();
            validatePdfSignature(content);
            return content;
        } catch (IOException ex) {
            throw new FileStorageException(
                    "Could not read the uploaded contract",
                    ex);
        }
    }

    private void validatePdfSignature(byte[] content) {
        if (content == null
                || content.length < PDF_SIGNATURE.length
                || !Arrays.equals(
                PDF_SIGNATURE,
                Arrays.copyOf(content, PDF_SIGNATURE.length))) {
            throw new FileStorageException(
                    "Contract does not have a valid PDF header");
        }
    }
}
