package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.DriverAssignment;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.entity.TripEndConfirmation;
import com.fptufa26.ecomotion.booking.entity.TripEvidence;
import com.fptufa26.ecomotion.booking.enums.AssignmentStatus;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeConfirmationStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.enums.TripEvidenceType;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.DriverAssignmentRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.repository.TripEndConfirmationRepository;
import com.fptufa26.ecomotion.booking.repository.TripEvidenceRepository;
import com.fptufa26.ecomotion.common.storage.service.FileStorageService;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.dispute.enums.DisputeResolutionDecision;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.repository.DisputeRepository;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.finance.entity.PaymentEvidence;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.entity.RefundRequest;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod;
import com.fptufa26.ecomotion.finance.enums.RefundStatus;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.finance.repository.RefundRequestRepository;
import com.fptufa26.ecomotion.finance.service.PaymentCodeGenerator;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.finance.service.RefundMath;
import com.fptufa26.ecomotion.review.entity.Review;
import com.fptufa26.ecomotion.review.repository.ReviewRepository;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleDocument;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import com.fptufa26.ecomotion.vehicle.service.VehicleDepositPolicyService;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Bổ sung tại chỗ dữ liệu demo cho các đơn seed đã có sẵn trong DB (DevDataSeeder bỏ qua đơn đã tồn tại nên
 * dữ liệu cũ không bao giờ được bổ sung): ảnh offline, lưu vết thanh toán theo ledger, lưu vết chuyến, đánh giá
 * và vài khiếu nại đã phân xử.
 *
 * <p>Chạy sau toàn bộ ApplicationRunner (sự kiện {@link ApplicationReadyEvent}). Mỗi bước chạy trong transaction
 * riêng và chỉ tạo dữ liệu còn thiếu, nên khởi động lại nhiều lần không sinh bản ghi trùng; một đơn lỗi chỉ bị bỏ qua
 * và ghi log, không làm hỏng lần khởi động.</p>
 *
 * <p>Phạm vi: đơn có mã {@code BK-}, {@code EM-2026-9}, {@code EM-SEED-}, {@code EM-2026-DEMO-}. Không đụng đơn
 * {@code EM-2026-0000xx} (người dùng tự tạo khi test), đơn thuê năm {@code EM-Y2-} (nằm trên xe 12/13 thuộc nhóm xe
 * 10–15 người dùng tự test và đã có lưu vết riêng theo mô hình thuê năm), cũng như dữ liệu tiền của
 * {@code EM-2026-9003} và {@code BK-1X8C44} (đã được thao tác tay trên giao diện).</p>
 *
 * <p>Chỉ chạy trên CSDL thật (không chạy với H2 của bộ test).</p>
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("!'${spring.datasource.url:}'.startsWith('jdbc:h2')")
@RequiredArgsConstructor
public class DemoDataCompletenessSeeder {

    private static final Pattern TARGET_CODE = Pattern.compile("^(BK-|EM-2026-9|EM-SEED-|EM-2026-DEMO-).*");
    /** Đơn đã được người dùng thao tác tiền/giờ trực tiếp — chỉ bổ sung lưu vết không ảnh hưởng số liệu. */
    private static final Set<String> MONEY_FROZEN = Set.of("EM-2026-9003", "BK-1X8C44");
    private static final Set<Long> PROTECTED_VEHICLES = Set.of(10L, 11L, 12L, 13L, 14L, 15L);

    private static final String REFUND_BOOKING = "BK-5L3M91";
    private static final String DISPUTE_PARTIAL = "BK-AUG-03";
    private static final String DISPUTE_RENTER_TIME = "BK-SEP-03";
    private static final String DISPUTE_MISMATCH = "BK-SEP-06";
    private static final BigDecimal MISMATCH_GAP = new BigDecimal("50000.00");
    /** Phụ phí đã được khách chấp nhận: mã đơn → loại phí. */
    private static final Map<String, ChargeType> ACCEPTED_SURCHARGES = Map.of(
            "BK-6H8U08", ChargeType.OVERTIME,
            "BK-JUL-01", ChargeType.OVERTIME,
            "BK-6J9V09", ChargeType.PARKING,
            "BK-SEP-05", ChargeType.PARKING,
            "BK-JUN-02", ChargeType.PARKING);
    /** Tài xế bổ sung cho đơn đã chạy nhưng thiếu tài xế. */
    private static final Map<String, String> MISSING_DRIVERS = Map.of(
            "BK-6F6S06", "driver.kiet@ecomotion.vn",
            "BK-6K0W10", "driver.nam@ecomotion.vn");

    private static final Set<BookingStatus> DEPOSIT_PAID_STATUSES = Set.of(
            BookingStatus.CONFIRMED, BookingStatus.DRIVER_ASSIGNED, BookingStatus.DRIVER_CONFIRMED,
            BookingStatus.DRIVER_ARRIVED, BookingStatus.AWAITING_START_CONFIRMATION, BookingStatus.IN_PROGRESS,
            BookingStatus.AWAITING_END_CONFIRMATION, BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION,
            BookingStatus.AWAITING_FINAL_PAYMENT, BookingStatus.COMPLETED);
    private static final Set<BookingStatus> ENDED_STATUSES = Set.of(
            BookingStatus.AWAITING_END_CONFIRMATION, BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION,
            BookingStatus.AWAITING_FINAL_PAYMENT, BookingStatus.COMPLETED);
    private static final String[] END_METHODS = {"RENTER", "PAYMENT", "AUTO"};

    private final PlatformTransactionManager transactionManager;
    private final EntityManager em;
    private final BookingRepository bookings;
    private final BookingPolicySnapshotRepository snapshots;
    private final BookingChargeRepository charges;
    private final PaymentTransactionRepository transactions;
    private final PaymentRequestRepository paymentRequests;
    private final ProviderBankAccountRepository banks;
    private final RefundRequestRepository refunds;
    private final DriverAssignmentRepository assignments;
    private final TripEvidenceRepository tripEvidence;
    private final TripEndConfirmationRepository endConfirmations;
    private final DisputeRepository disputes;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final DriverRepository drivers;
    private final RentalPlanRepository plans;
    private final VehicleImageRepository vehicleImages;
    private final SeedDepositPaymentSynchronizer depositPayments;
    private final PaymentCodeGenerator paymentCodes;
    private final PaymentRequestService paymentRequestService;
    private final DisputeService disputeService;
    private final VehicleDepositPolicyService depositPolicy;
    private final VehicleService vehicleService;
    private final UserService userService;
    private final FileStorageService fileStorage;

    @Value("${app.images.root:../../images}")
    private String imagesRootSetting;

    private Path imagesRoot;

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onApplicationReady() {
        imagesRoot = resolveImagesRoot();
        step("avatars", this::completeAvatars);
        step("driver documents", this::completeDriverImages);
        step("vehicle document PDFs", this::completeVehicleDocuments);

        List<String> codes = inTx(() -> em.createQuery(
                        "select b.bookingCode from Booking b order by b.bookingCode", String.class)
                .getResultList()).stream().filter(c -> TARGET_CODE.matcher(c).matches()).toList();
        List<String> completed = inTx(() -> em.createQuery(
                        "select b.bookingCode from Booking b where b.status = :s order by b.bookingCode", String.class)
                .setParameter("s", BookingStatus.COMPLETED).getResultList())
                .stream().filter(codes::contains).toList();
        for (String code : codes) {
            step("booking " + code, () -> completeBooking(code, completed.indexOf(code)));
        }
        step("seed image URLs", this::repairSeedImageUrls);
        step("ratings", this::refreshRatings);
        log.info("Demo data completeness pass finished for {} seed bookings", codes.size());
    }

    // ------------------------------------------------------------------ infrastructure

    private void step(String name, Runnable work) {
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(status -> work.run());
        } catch (RuntimeException ex) {
            log.warn("Demo completeness step '{}' skipped: {}", name, ex.toString());
        }
    }

    private <T> T inTx(java.util.function.Supplier<T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx.execute(status -> work.get());
    }

    private Path resolveImagesRoot() {
        Path resolved = Paths.get(imagesRootSetting).toAbsolutePath().normalize();
        if (!Files.exists(resolved)) {
            Path fromRoot = Paths.get("images").toAbsolutePath().normalize();
            Path fromParent = Paths.get("../images").toAbsolutePath().normalize();
            if (Files.exists(fromRoot)) resolved = fromRoot;
            else if (Files.exists(fromParent)) resolved = fromParent;
        }
        return resolved;
    }

    /** Ghi file vào thư mục images (phục vụ ở /images/**) nếu chưa có; trả về URL công khai. */
    private String writeImage(String relative, java.util.function.Supplier<byte[]> content) {
        Path target = imagesRoot.resolve(relative).normalize();
        if (!target.startsWith(imagesRoot)) throw new IllegalArgumentException("Invalid image path " + relative);
        if (!Files.exists(target)) {
            try {
                Files.createDirectories(target.getParent());
                Files.write(target, content.get());
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot write demo image " + target, ex);
            }
        }
        return "/images/" + relative.replace('\\', '/');
    }

    // ------------------------------------------------------------------ 1. offline images & documents

    private void completeAvatars() {
        List<User> seedUsers = em.createQuery(
                "select u from User u where lower(u.email) like '%@ecomotion.vn' order by u.id", User.class).getResultList();
        int created = 0;
        for (User user : seedUsers) {
            String url = user.getAvatarUrl();
            if (url == null || url.isBlank() || url.startsWith("/images/demo/avatars/")) {
                String generated = writeImage("demo/avatars/user-" + user.getId() + ".svg",
                        () -> DemoAssetFactory.avatarSvg(user.getFullName(), user.getRole()));
                if (!generated.equals(url)) {
                    user.setAvatarUrl(generated);
                    created++;
                }
            }
        }
        if (created > 0) log.info("Generated {} demo avatars", created);
    }

    private void completeDriverImages() {
        for (Driver driver : drivers.findAll()) {
            User user = driver.getUser();
            if (user == null || user.getEmail() == null || !user.getEmail().toLowerCase().endsWith("@ecomotion.vn")) continue;
            if (brokenOrGenerated(driver.getPortraitUrl())) {
                driver.setPortraitUrl(writeImage("demo/drivers/portrait-" + user.getId() + ".svg",
                        () -> DemoAssetFactory.portraitSvg(user.getFullName(), driver.getLicenseNo())));
            }
            if (brokenOrGenerated(driver.getLicenseImageUrl())) {
                String expiry = driver.getLicenseExpiry() == null ? "31/12/2030"
                        : String.format("%02d/%02d/%d", driver.getLicenseExpiry().getDayOfMonth(),
                        driver.getLicenseExpiry().getMonthValue(), driver.getLicenseExpiry().getYear());
                driver.setLicenseImageUrl(writeImage("demo/drivers/license-" + user.getId() + ".svg",
                        () -> DemoAssetFactory.licenseSvg(user.getFullName(), Objects.toString(driver.getLicenseNo(), ""),
                                Objects.toString(driver.getLicenseClass(), "B2"), expiry)));
            }
        }
    }

    private boolean brokenOrGenerated(String url) {
        return url == null || url.isBlank() || url.startsWith("/seed/") || url.startsWith("/images/demo/")
                || url.matches("^/images/(portrait|license)-\\d+\\.webp$");
    }

    private void completeVehicleDocuments() {
        List<VehicleDocument> docs = em.createQuery(
                "select d from VehicleDocument d join fetch d.vehicle v order by v.id, d.id", VehicleDocument.class).getResultList();
        for (VehicleDocument doc : docs) {
            Vehicle vehicle = doc.getVehicle();
            if (PROTECTED_VEHICLES.contains(vehicle.getId())) continue;
            String url = doc.getFileUrl();
            String kind = switch (doc.getDocType()) {
                case REGISTRATION -> "registration";
                case INSPECTION -> "inspection";
                case INSURANCE -> "insurance";
                case BUSINESS_BADGE -> "badge";
            };
            String expectedKey = "vehicle/" + vehicle.getId() + "/sample-" + kind + ".pdf";
            boolean legacySample = url != null && url.startsWith("/api/files/sample/");
            boolean ours = ("/api/files/" + expectedKey).equals(url);
            if (!legacySample && !ours) continue;
            if (!ours || !storedFileExists(expectedKey)) {
                String key = fileStorage.store("vehicle", vehicle.getId(), "sample-" + kind + ".pdf",
                        vehicleDocumentPdf(doc, vehicle), "application/pdf");
                doc.setFileUrl("/api/files/" + key);
            }
        }
    }

    private boolean storedFileExists(String key) {
        try {
            return fileStorage.read(key).length > 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private byte[] vehicleDocumentPdf(VehicleDocument doc, Vehicle vehicle) {
        String title = switch (doc.getDocType()) {
            case REGISTRATION -> "GIAY CHUNG NHAN DANG KY XE";
            case INSPECTION -> "GIAY CHUNG NHAN KIEM DINH";
            case INSURANCE -> "BAO HIEM TRACH NHIEM DAN SU";
            case BUSINESS_BADGE -> "PHU HIEU XE KINH DOANH VAN TAI";
        };
        List<String> lines = new ArrayList<>();
        lines.add("So giay to: " + doc.getDocNumber());
        lines.add("Bien so: " + vehicle.getLicensePlate());
        lines.add("Nhan hieu / so loai: " + vehicle.getBrand() + " " + vehicle.getModel());
        lines.add("Nam san xuat: " + vehicle.getManufactureYear() + "   So cho: " + vehicle.getSeatCount());
        lines.add("Chu xe / don vi kinh doanh: " + vehicle.getProvider().getDisplayName());
        lines.add("Co gia tri den: " + (doc.getExpiryDate() == null ? "khong thoi han" : doc.getExpiryDate()));
        return DemoAssetFactory.documentPdf(title, lines);
    }

    /** Các URL /seed/... cũ không tồn tại ở đâu cả: thay bằng ảnh thật của xe hoặc biên nhận sinh offline. */
    private void repairSeedImageUrls() {
        List<TripEvidence> evidence = em.createQuery(
                "select e from TripEvidence e join fetch e.booking b where e.imageUrl like '/seed/%'", TripEvidence.class)
                .getResultList();
        for (TripEvidence item : evidence) {
            if (!TARGET_CODE.matcher(item.getBooking().getBookingCode()).matches()) continue;
            item.setImageUrl(vehiclePhoto(item.getBooking().getVehicle(), item.getEvidenceType().ordinal()));
        }
        List<PaymentEvidence> proofs = em.createQuery(
                "select e from PaymentEvidence e join fetch e.paymentRequest r join fetch r.booking b "
                        + "where e.proofImageUrl like '/seed/%'", PaymentEvidence.class).getResultList();
        for (PaymentEvidence proof : proofs) {
            PaymentRequest request = proof.getPaymentRequest();
            Booking booking = request.getBooking();
            if (!TARGET_CODE.matcher(booking.getBookingCode()).matches()) continue;
            proof.setProofImageUrl(receiptImage(booking, request, proof.getTransferredAmount(), proof.getTransferredAt()));
        }
    }

    private String receiptImage(Booking booking, PaymentRequest request, BigDecimal amount, Instant at) {
        String name = "demo/receipts/" + booking.getBookingCode() + "-" + request.getPaymentType().name().toLowerCase() + ".svg";
        return writeImage(name, () -> DemoAssetFactory.transferReceiptSvg(request.getBankNameSnapshot(),
                request.getAccountHolderSnapshot(), request.getAccountNumberSnapshot(), amount,
                request.getTransferReference(), at, "FT" + Math.abs(Objects.hash(booking.getBookingCode(), request.getPaymentType())) % 1_000_000_000L));
    }

    private String vehiclePhoto(Vehicle vehicle, int index) {
        List<String> photos = vehicleImages.findAll().stream()
                .filter(img -> img.getVehicle() != null && img.getVehicle().getId().equals(vehicle.getId()))
                .sorted(Comparator.comparing(img -> img.getId()))
                .map(img -> img.getImageUrl())
                .filter(url -> url != null && url.startsWith("/images/"))
                .toList();
        if (photos.isEmpty()) return null;
        return photos.get(Math.floorMod(index, photos.size()));
    }

    // ------------------------------------------------------------------ 2–5. per booking

    private void completeBooking(String code, int completedIndex) {
        Booking booking = bookings.findByBookingCode(code).orElseThrow();
        if (PROTECTED_VEHICLES.contains(booking.getVehicle().getId())) return;
        BookingStatus status = booking.getStatus();
        boolean moneyFrozen = MONEY_FROZEN.contains(code);
        BookingPolicySnapshot snapshot = snapshots.findByBooking_Id(booking.getId()).orElse(null);

        if (!moneyFrozen && snapshot != null) normalizePlannedTimes(booking, snapshot);
        Timeline t = new Timeline(booking, Math.max(completedIndex, 0));

        if (status == BookingStatus.CANCELLED) {
            if (REFUND_BOOKING.equals(code) && snapshot != null) completeCancelledRefund(booking, snapshot, t);
            return;
        }
        if (!DEPOSIT_PAID_STATUSES.contains(status)) return;

        if (!moneyFrozen && snapshot != null) ensureDeposit(booking, snapshot, t);
        assignMissingDriver(booking);
        ensureAssignment(booking, t);
        if (booking.getActualStartAt() == null) return;

        if (status == BookingStatus.COMPLETED && !moneyFrozen) {
            ensureAcceptedSurcharge(booking, snapshot);
            if (DISPUTE_PARTIAL.equals(code)) ensurePartialWaiveDispute(booking, snapshot);
            booking = bookings.findById(booking.getId()).orElseThrow();
            snapshot = snapshots.findByBooking_Id(booking.getId()).orElse(null);
            if (DISPUTE_RENTER_TIME.equals(code)) ensureRenterTimeDispute(booking, snapshot);
            booking = bookings.findById(booking.getId()).orElseThrow();
            t = new Timeline(booking, Math.max(completedIndex, 0));
        }
        ensureTripTimestamps(booking, t);
        ensureTripEvidence(booking, t);
        if (status == BookingStatus.COMPLETED) {
            ensureEndConfirmation(booking, t);
            if (!moneyFrozen) {
                ensureSettlement(booking, t);
                if (DISPUTE_MISMATCH.equals(code)) ensureMismatchDispute(booking, t);
                booking = bookings.findById(booking.getId()).orElseThrow();
            }
            ensureReviews(booking, t, completedIndex);
        }
    }

    /** Mốc thời gian dẫn xuất từ giờ đón/kết thúc của đơn, cố định để các lần chạy cho cùng kết quả. */
    private static final class Timeline {
        final Instant created;
        final Instant depositPaid;
        final Instant assigned;
        final Instant driverConfirmed;
        final Instant arrived;
        final Instant start;
        final Instant end;
        final Instant reported;
        final Instant settlementRequested;
        final Instant settlementPaid;
        final String endMethod;
        final Instant endConfirmed;
        final Instant completed;

        Timeline(Booking b, int index) {
            Instant pickup = b.getPickupTime();
            Instant createdAt = b.getCreatedAt() != null && b.getCreatedAt().isBefore(pickup)
                    ? b.getCreatedAt() : pickup.minus(1, ChronoUnit.DAYS);
            created = createdAt;
            Instant paid = createdAt.plus(90 + (index % 4) * 25L, ChronoUnit.MINUTES);
            Instant latest = pickup.minus(3, ChronoUnit.HOURS);
            depositPaid = paid.isAfter(latest) ? createdAt.plus(Duration.between(createdAt, pickup).dividedBy(3)) : paid;
            assigned = depositPaid.plus(35, ChronoUnit.MINUTES);
            Instant confirmed = b.getDriverConfirmedAt() != null && b.getDriverConfirmedAt().isAfter(assigned)
                    ? b.getDriverConfirmedAt() : assigned.plus(Duration.between(assigned, pickup).dividedBy(4));
            driverConfirmed = confirmed;
            start = b.getActualStartAt() != null ? b.getActualStartAt() : pickup;
            arrived = start.minus(9 + (index % 3) * 4L, ChronoUnit.MINUTES);
            end = b.getActualEndAt();
            reported = end == null ? null : end.plus(3, ChronoUnit.MINUTES);
            settlementRequested = end == null ? null : end.plus(6, ChronoUnit.MINUTES);
            settlementPaid = end == null ? null : end.plus(38 + (index % 5) * 9L, ChronoUnit.MINUTES);
            endMethod = END_METHODS[index % END_METHODS.length];
            if (end == null) {
                endConfirmed = null;
            } else {
                endConfirmed = switch (endMethod) {
                    case "RENTER" -> end.plus(14, ChronoUnit.MINUTES);
                    case "PAYMENT" -> settlementPaid;
                    default -> reported.plus(TripEndConfirmationWindow.HOURS, ChronoUnit.HOURS);
                };
            }
            completed = end == null ? null
                    : (endConfirmed.isAfter(settlementPaid) ? endConfirmed : settlementPaid).plus(12, ChronoUnit.MINUTES);
        }
    }

    private static final class TripEndConfirmationWindow {
        static final long HOURS = 12;
    }

    /** Đơn nhiều ngày/giờ: giờ kết thúc dự kiến phải khớp số slot đã tính tiền. */
    private void normalizePlannedTimes(Booking b, BookingPolicySnapshot s) {
        if (s.getSlotPrice() == null || s.getSlotPrice().signum() <= 0 || s.getSlotLengthMinutes() == null
                || b.getTotalAmount() == null || b.getPickupTime() == null) return;
        BigDecimal[] division = b.getTotalAmount().divideAndRemainder(s.getSlotPrice());
        if (division[1].signum() != 0) return;
        int slots = division[0].intValue();
        if (slots < 2 || slots > 60) return;
        Instant planned = b.getPickupTime().plus((long) slots * s.getSlotLengthMinutes(), ChronoUnit.MINUTES);
        Instant oneSlot = b.getPickupTime().plus(s.getSlotLengthMinutes(), ChronoUnit.MINUTES);
        if (b.getPlannedEndAt() != null && b.getPlannedEndAt().equals(oneSlot)) b.setPlannedEndAt(planned);
        if (b.getStatus() == BookingStatus.COMPLETED && b.getActualEndAt() != null && b.getPlannedEndAt() != null
                && b.getActualEndAt().isBefore(b.getPlannedEndAt())) {
            b.setActualEndAt(b.getPlannedEndAt());
        }
    }

    // ------------------------------------------------------------------ payments

    private ProviderBankAccount bank(Booking b) {
        return banks.findByProvider_IdAndStatus(b.getVehicle().getProvider().getId(), BankAccountStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Provider bank account missing"));
    }

    private List<PaymentTransaction> paidIn(Booking b, PaymentType type) {
        return transactions.findByBooking_Id(b.getId()).stream()
                .filter(tx -> tx.getType() == type && tx.getDirection() == PaymentDirection.IN
                        && tx.getStatus() == PaymentStatus.PAID)
                .toList();
    }

    private void ensureDeposit(Booking b, BookingPolicySnapshot s, Timeline t) {
        List<PaymentTransaction> deposits = paidIn(b, PaymentType.DEPOSIT_PAY);
        if (deposits.isEmpty() && b.getStatus() == BookingStatus.COMPLETED && !paidIn(b, PaymentType.SETTLEMENT).isEmpty()
                && s.getDepositAmount() == null) {
            return; // đơn đã thu một lần trọn gói từ trước — không thêm cọc để tránh thu trùng
        }
        if (s.getDepositAmount() == null) {
            BigDecimal amount = deposits.isEmpty()
                    ? depositPolicy.resolve(b.getVehicle(), b.getTotalAmount()).amount()
                    : deposits.stream().map(PaymentTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            s.setDepositAmount(amount.setScale(2, RoundingMode.HALF_UP));
            if (s.getDepositRatio() == null) s.setDepositRatio(depositPolicy.resolve(b.getVehicle(), b.getTotalAmount()).ratio());
        }
        if (deposits.isEmpty()) {
            if (s.getDepositAmount().signum() <= 0) return;
            BookingCharge depositCharge = charges.findByBooking_Id(b.getId()).stream()
                    .filter(c -> c.getChargeType() == ChargeType.DEPOSIT).findFirst()
                    .orElseGet(() -> charges.save(charge(b, ChargeType.DEPOSIT, null, b.getTotalAmount(), s.getDepositRatio(),
                            s.getDepositAmount(), t.created, "Cọc giữ chỗ (VietQR)", ChargeConfirmationStatus.ACCEPTED)));
            PaymentTransaction tx = inbound(b, depositCharge, PaymentType.DEPOSIT_PAY, s.getDepositAmount(), t.depositPaid,
                    "SEED-" + b.getBookingCode() + "-DEP");
            transactions.save(tx);
            em.flush();
        }
        depositPayments.synchronize(b);
        paymentRequests.findByBooking_IdAndPaymentType(b.getId(), PaymentRequestType.DEPOSIT).ifPresent(request -> {
            if (request.getStatus() == PaymentRequestStatus.CONFIRMED && request.getRenterAcknowledgedAt() == null
                    && request.getConfirmedAt() != null) {
                request.setRenterAcknowledgedAt(request.getConfirmedAt().plus(7, ChronoUnit.MINUTES));
                if (request.getFirstProofAt() == null) request.setFirstProofAt(request.getConfirmedAt().minus(12, ChronoUnit.MINUTES));
            }
        });
    }

    private void ensureAcceptedSurcharge(Booking b, BookingPolicySnapshot s) {
        ChargeType type = ACCEPTED_SURCHARGES.get(b.getBookingCode());
        if (type == null || !paidIn(b, PaymentType.SETTLEMENT).isEmpty()) return;
        boolean exists = charges.findByBooking_Id(b.getId()).stream().anyMatch(c -> c.getChargeType() == type);
        if (exists) return;
        if (type == ChargeType.OVERTIME) {
            int grace = s == null || s.getAllowedOvertimeMinutes() == null ? 30 : s.getAllowedOvertimeMinutes();
            int lateMinutes = grace + 35 + (b.getBookingCode().hashCode() & 1) * 50;
            b.setActualEndAt(b.getPlannedEndAt().plus(lateMinutes, ChronoUnit.MINUTES));
            int hours = (int) Math.ceil((lateMinutes - grace) / 60.0);
            BigDecimal unit = hourlyPrice(b.getVehicle());
            charges.save(charge(b, ChargeType.OVERTIME, hours, unit, null, unit.multiply(BigDecimal.valueOf(hours)),
                    b.getActualEndAt().plus(4, ChronoUnit.MINUTES),
                    "Quá giờ " + lateMinutes + " phút (đã trừ " + grace + " phút ân hạn) — khách đã xác nhận",
                    ChargeConfirmationStatus.ACCEPTED));
        } else {
            BigDecimal fee = new BigDecimal("40000.00");
            charges.save(charge(b, ChargeType.PARKING, 1, fee, null, fee, b.getActualEndAt().minus(2, ChronoUnit.HOURS),
                    "Phí gửi xe tại bãi điểm đến (có vé gửi xe) — khách đã xác nhận", ChargeConfirmationStatus.ACCEPTED));
        }
    }

    private BigDecimal hourlyPrice(Vehicle vehicle) {
        return plans.findByVehicle_IdOrderByIdAsc(vehicle.getId()).stream()
                .filter(p -> p.getPlanType() == PlanType.HOURLY && p.getSlotPrice() != null)
                .map(RentalPlan::getSlotPrice).findFirst().orElse(new BigDecimal("150000.00"));
    }

    private void ensureSettlement(Booking b, Timeline t) {
        Optional<PaymentRequest> existing = paymentRequests.findByBooking_IdAndPaymentType(b.getId(), PaymentRequestType.FINAL_BALANCE);
        List<PaymentTransaction> settlements = paidIn(b, PaymentType.SETTLEMENT);
        if (existing.isPresent()) {
            settlements.stream().filter(tx -> tx.getPaymentRequest() == null).forEach(tx -> tx.setPaymentRequest(existing.get()));
            return;
        }
        ProviderBankAccount bank = bank(b);
        if (!settlements.isEmpty()) {
            BigDecimal amount = settlements.stream().map(PaymentTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            Instant paidAt = settlements.stream().map(PaymentTransaction::getPaidAt).filter(Objects::nonNull)
                    .max(Instant::compareTo).orElse(t.settlementPaid);
            PaymentRequest request = finalRequest(b, bank, amount, amount, PaymentRequestStatus.CONFIRMED,
                    t.settlementRequested != null ? t.settlementRequested : paidAt.minus(30, ChronoUnit.MINUTES), paidAt);
            for (PaymentTransaction tx : settlements) {
                tx.setPaymentRequest(request);
                if (tx.getAppliedAmount() == null || tx.getAppliedAmount().signum() == 0) tx.setAppliedAmount(tx.getAmount());
            }
            return;
        }
        em.flush();
        BigDecimal remaining = paymentRequestService.calculateRemainingAmount(b.getId());
        if (remaining.signum() <= 0) return;
        boolean mismatch = DISPUTE_MISMATCH.equals(b.getBookingCode());
        BigDecimal recorded = mismatch ? remaining.subtract(MISMATCH_GAP) : remaining;
        PaymentRequest request = finalRequest(b, bank, remaining, recorded,
                mismatch ? PaymentRequestStatus.PARTIALLY_PAID : PaymentRequestStatus.CONFIRMED,
                t.settlementRequested, t.settlementPaid);
        BookingCharge base = charges.findByBooking_Id(b.getId()).stream()
                .filter(c -> c.getChargeType() == ChargeType.BASE_RENTAL).findFirst().orElse(null);
        PaymentTransaction tx = inbound(b, base, PaymentType.SETTLEMENT, recorded, t.settlementPaid,
                "SEED-" + b.getBookingCode() + "-SET");
        tx.setPaymentRequest(request);
        transactions.save(tx);
    }

    private PaymentRequest finalRequest(Booking b, ProviderBankAccount bank, BigDecimal amount, BigDecimal paid,
                                        PaymentRequestStatus status, Instant createdAt, Instant paidAt) {
        PaymentRequest request = new PaymentRequest();
        request.setBooking(b);
        request.setPaymentType(PaymentRequestType.FINAL_BALANCE);
        request.setAmount(amount);
        request.setPaidAmount(paid);
        request.setStatus(status);
        request.setPaymentCode(paymentCodes.generatePaymentCode("VQ"));
        request.setTransferReference(b.getBookingCode().replace("-", "") + "FINAL");
        request.setBankCodeSnapshot(bank.getBankCode());
        request.setBankNameSnapshot(bank.getBankName());
        request.setAccountNumberSnapshot(bank.getAccountNumber());
        request.setAccountHolderSnapshot(bank.getAccountHolderName());
        request.setReceivingBankAccount(bank);
        request.setFirstProofAt(paidAt.minus(9, ChronoUnit.MINUTES));
        request.setConfirmedAt(paidAt);
        request.setConfirmedBy(b.getVehicle().getProvider().getUser());
        if (status == PaymentRequestStatus.CONFIRMED) request.setRenterAcknowledgedAt(paidAt.plus(6, ChronoUnit.MINUTES));
        request.setCreatedAt(createdAt);
        request.setUpdatedAt(paidAt);
        return paymentRequests.save(request);
    }

    private PaymentTransaction inbound(Booking b, BookingCharge charge, PaymentType type, BigDecimal amount,
                                       Instant paidAt, String gatewayRef) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setBooking(b);
        tx.setCharge(charge);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setAppliedAmount(amount);
        tx.setDirection(PaymentDirection.IN);
        tx.setMethod(PaymentMethod.BANK_TRANSFER);
        tx.setStatus(PaymentStatus.PAID);
        tx.setGatewayRef(gatewayRef);
        tx.setTransactionCode(paymentCodes.generatePaymentCode("VQ"));
        tx.setPaidAt(paidAt);
        tx.setCreatedAt(paidAt);
        tx.setUpdatedAt(paidAt);
        tx.setVerificationMethod(PaymentVerificationMethod.PROVIDER_CONFIRMED);
        tx.setConfirmedBy(b.getVehicle().getProvider().getUser());
        tx.setReceivingBankAccount(bank(b));
        return tx;
    }

    private BookingCharge charge(Booking b, ChargeType type, Integer quantity, BigDecimal unitPrice, BigDecimal rate,
                                 BigDecimal amount, Instant createdAt, String description, ChargeConfirmationStatus status) {
        BookingCharge charge = new BookingCharge();
        charge.setBooking(b);
        charge.setChargeType(type);
        charge.setQuantity(quantity);
        charge.setUnitPrice(unitPrice);
        charge.setAppliedRate(rate);
        charge.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        charge.setDescription(description);
        charge.setCreatedBy(b.getVehicle().getProvider().getUser());
        charge.setWaived(false);
        charge.setConfirmationStatus(status);
        charge.setCreatedAt(createdAt);
        return charge;
    }

    /** Đơn huỷ sau khi đã cọc: hoàn 100% cọc qua chuyển khoản, khách đã xác nhận đã nhận tiền. */
    private void completeCancelledRefund(Booking b, BookingPolicySnapshot s, Timeline t) {
        if (!refunds.findByBooking_IdOrderByCreatedAtDesc(b.getId()).isEmpty()) return;
        Instant cancelledAt = b.getCancelledAt() != null ? b.getCancelledAt() : b.getPickupTime().minus(6, ChronoUnit.HOURS);
        Instant paidAt = t.created.plus(70, ChronoUnit.MINUTES);
        if (!paidAt.isBefore(cancelledAt)) paidAt = cancelledAt.minus(3, ChronoUnit.HOURS);
        if (s.getDepositAmount() == null) {
            s.setDepositAmount(depositPolicy.resolve(b.getVehicle(), b.getTotalAmount()).amount());
        }
        List<PaymentTransaction> deposits = paidIn(b, PaymentType.DEPOSIT_PAY);
        PaymentTransaction source;
        if (deposits.isEmpty()) {
            BookingCharge depositCharge = charges.save(charge(b, ChargeType.DEPOSIT, null, b.getTotalAmount(), s.getDepositRatio(),
                    s.getDepositAmount(), t.created, "Cọc giữ chỗ (VietQR)", ChargeConfirmationStatus.ACCEPTED));
            source = transactions.save(inbound(b, depositCharge, PaymentType.DEPOSIT_PAY, s.getDepositAmount(), paidAt,
                    "SEED-" + b.getBookingCode() + "-DEP"));
            em.flush();
        } else {
            source = deposits.get(0);
        }
        depositPayments.synchronize(b);

        User renterUser = b.getRenter().getUser();
        User providerUser = b.getVehicle().getProvider().getUser();
        Instant refundedAt = cancelledAt.plus(3, ChronoUnit.HOURS);
        Instant receivedAt = refundedAt.plus(2, ChronoUnit.HOURS);
        String reason = "Hoàn 100% tiền cọc: khách huỷ trước giờ đón theo chính sách huỷ của nhà xe";

        PaymentTransaction out = new PaymentTransaction();
        out.setBooking(b);
        out.setType(PaymentType.REFUND);
        out.setDirection(PaymentDirection.OUT);
        out.setMethod(PaymentMethod.BANK_TRANSFER);
        out.setAmount(source.getAmount());
        out.setAppliedAmount(BigDecimal.ZERO);
        out.setStatus(PaymentStatus.PAID);
        out.setPaidAt(refundedAt);
        out.setGatewayRef("SEED-" + b.getBookingCode() + "-REFUND");
        out.setProcessingNote(reason);
        out.setVerificationMethod(PaymentVerificationMethod.PROVIDER_CONFIRMED);
        out.setConfirmedBy(providerUser);
        out.setCreatedAt(cancelledAt);
        out.setUpdatedAt(refundedAt);
        out = transactions.save(out);

        RefundRequest refund = new RefundRequest();
        refund.setBooking(b);
        refund.setPaymentTransaction(out);
        refund.setSourcePaymentTransaction(source);
        refund.setAmount(RefundMath.money(source.getAmount()));
        refund.setReason(reason);
        refund.setSourceEventId(RefundMath.partId("CANCEL:" + b.getId(), source.getId()));
        refund.setRecipientBankName("Techcombank");
        refund.setRecipientAccountHolder(DemoAssetFactory.stripDiacritics(renterUser.getFullName()).toUpperCase());
        refund.setRecipientAccountNumber("19036" + String.format("%07d", renterUser.getId() * 7919 % 10_000_000));
        refund.setStatus(RefundStatus.REFUNDED);
        refund.setRequestedBy(renterUser);
        refund.setApprovedBy(providerUser);
        refund.setRefundTransactionNo("FT" + (26_000_000_000L + b.getId() * 7_777L));
        refund.setReceiptStatus("CONFIRMED");
        refund.setReceiptWaitingAt(refundedAt);
        refund.setReceiptDeadline(refundedAt.plus(72, ChronoUnit.HOURS));
        refund.setReceiptConfirmedAt(receivedAt);
        refund.setReceiptConfirmedBy(renterUser.getId());
        refund.setCreatedAt(cancelledAt);
        refund.setUpdatedAt(receivedAt);
        refunds.save(refund);
    }

    // ------------------------------------------------------------------ trip trail

    private void assignMissingDriver(Booking b) {
        if (b.getDriver() != null) return;
        String email = MISSING_DRIVERS.get(b.getBookingCode());
        if (email == null) return;
        users.findByEmailIgnoreCase(email).flatMap(u -> drivers.findByUser_Id(u.getId()))
                .filter(d -> d.getStatus() == DriverStatus.APPROVED
                        && d.getProvider().getId().equals(b.getVehicle().getProvider().getId()))
                .ifPresent(b::setDriver);
    }

    private void ensureAssignment(Booking b, Timeline t) {
        if (b.getDriver() == null) return;
        if (b.getDriverConfirmedAt() == null) b.setDriverConfirmedAt(t.driverConfirmed);
        if (b.getAssignedAt() == null) b.setAssignedAt(t.assigned);
        if (!assignments.findByBooking_IdOrderByCreatedAtDesc(b.getId()).isEmpty()) return;
        DriverAssignment assignment = new DriverAssignment();
        assignment.setBooking(b);
        assignment.setDriver(b.getDriver());
        assignment.setStatus(AssignmentStatus.ACCEPTED);
        assignment.setRoundNumber(1);
        assignment.setAssignedAt(b.getAssignedAt());
        assignment.setRespondedAt(b.getDriverConfirmedAt());
        assignment.setExpiresAt(b.getAssignedAt().plus(2, ChronoUnit.HOURS));
        assignment.setCreatedAt(b.getAssignedAt());
        assignment.setUpdatedAt(b.getDriverConfirmedAt());
        assignments.save(assignment);
    }

    private void ensureTripTimestamps(Booking b, Timeline t) {
        setIfNull(b.getActualArrivedAt(), t.arrived, b::setActualArrivedAt);
        setIfNull(b.getDriverStartConfirmedAt(), t.start, b::setDriverStartConfirmedAt);
        setIfNull(b.getRenterStartConfirmedAt(), t.start.plus(2, ChronoUnit.MINUTES), b::setRenterStartConfirmedAt);
        if (b.getStatus() != BookingStatus.COMPLETED || t.end == null) return;
        setIfNull(b.getDriverEndConfirmedAt(), t.reported, b::setDriverEndConfirmedAt);
        setIfNull(b.getSettlementRequestedAt(), t.settlementRequested, b::setSettlementRequestedAt);
        if (b.getEndConfirmationMethod() == null) {
            String method = endConfirmations.findFirstByBooking_IdOrderByIdDesc(b.getId())
                    .map(TripEndConfirmation::getConfirmationMethod).orElse(t.endMethod);
            b.setEndConfirmationMethod(method);
        }
        if (!"AUTO".equals(b.getEndConfirmationMethod()) && !"ADMIN".equals(b.getEndConfirmationMethod())) {
            setIfNull(b.getRenterEndConfirmedAt(), t.endConfirmed, b::setRenterEndConfirmedAt);
            setIfNull(b.getRenterConfirmedAt(), t.endConfirmed, b::setRenterConfirmedAt);
        }
        setIfNull(b.getProviderConfirmedAt(), t.completed, b::setProviderConfirmedAt);
    }

    private static void setIfNull(Instant current, Instant value, Consumer<Instant> setter) {
        if (current == null && value != null) setter.accept(value);
    }

    private void ensureTripEvidence(Booking b, Timeline t) {
        if (b.getDriver() == null) return;
        Set<TripEvidenceType> present = new LinkedHashSet<>();
        tripEvidence.findByBooking_IdOrderByRecordedAtAsc(b.getId()).forEach(e -> present.add(e.getEvidenceType()));
        boolean ended = ENDED_STATUSES.contains(b.getStatus()) && b.getActualEndAt() != null;
        Instant end = b.getActualEndAt();
        addEvidence(b, present, TripEvidenceType.PICKUP_ARRIVAL, t.arrived, 0,
                "Tài xế đã đến điểm đón: " + Objects.toString(b.getPickupAddress(), ""));
        addEvidence(b, present, TripEvidenceType.PRE_TRIP_CONDITION, t.start.plus(1, ChronoUnit.MINUTES), 1,
                "Ngoại thất và odo trước chuyến, xe sạch, không trầy xước mới");
        if (!ended) return;
        addEvidence(b, present, TripEvidenceType.POST_TRIP_CONDITION, end, 2,
                "Tình trạng xe sau chuyến: bình thường, đã kiểm tra nội thất");
        addEvidence(b, present, TripEvidenceType.TRIP_COMPLETION, end.plus(2, ChronoUnit.MINUTES), 3,
                "Kết thúc chuyến, đã trả khách tại điểm hẹn");
    }

    private void addEvidence(Booking b, Set<TripEvidenceType> present, TripEvidenceType type, Instant at, int photoIndex, String note) {
        if (present.contains(type)) return;
        String url = vehiclePhoto(b.getVehicle(), photoIndex);
        if (url == null) return;
        TripEvidence evidence = new TripEvidence();
        evidence.setBooking(b);
        evidence.setDriver(b.getDriver());
        evidence.setUploadedBy(b.getDriver().getUser());
        evidence.setEvidenceType(type);
        evidence.setImageUrl(url);
        evidence.setOriginalFileName(java.net.URLDecoder.decode(url.substring(url.lastIndexOf('/') + 1),
                java.nio.charset.StandardCharsets.UTF_8));
        evidence.setNote(note);
        evidence.setRecordedAt(at);
        evidence.setCreatedAt(at);
        tripEvidence.save(evidence);
        present.add(type);
    }

    private void ensureEndConfirmation(Booking b, Timeline t) {
        if (endConfirmations.findFirstByBooking_IdOrderByIdDesc(b.getId()).isPresent() || t.end == null) return;
        TripEndConfirmation history = new TripEndConfirmation();
        history.setBooking(b);
        history.setReportedBy(b.getDriver() != null ? b.getDriver().getUser().getId() : b.getVehicle().getProvider().getUser().getId());
        history.setReportedAt(t.reported);
        history.setDeadline(t.reported.plus(TripEndConfirmationWindow.HOURS, ChronoUnit.HOURS));
        history.setState("CONFIRMED");
        String method = b.getEndConfirmationMethod() != null ? b.getEndConfirmationMethod() : t.endMethod;
        history.setConfirmationMethod(method);
        history.setConfirmedAt(t.endConfirmed);
        history.setConfirmedBy("AUTO".equals(method) ? null : b.getRenter().getUser().getId());
        endConfirmations.save(history);
    }

    // ------------------------------------------------------------------ disputes

    private User admin() {
        return users.findByEmailIgnoreCase("admin01@ecomotion.vn").orElseThrow();
    }

    private boolean hasDispute(Booking b, DisputeCategory category) {
        return disputes.findByBooking_Id(b.getId()).stream().anyMatch(d -> d.getCategory() == category);
    }

    /** Khách khiếu nại phí quá giờ; admin đối chiếu GPS và giảm một phần (PARTIAL_WAIVE). */
    private void ensurePartialWaiveDispute(Booking b, BookingPolicySnapshot s) {
        if (hasDispute(b, DisputeCategory.CHARGE) || !paidIn(b, PaymentType.SETTLEMENT).isEmpty()) return;
        int grace = s == null || s.getAllowedOvertimeMinutes() == null ? 30 : s.getAllowedOvertimeMinutes();
        int lateMinutes = grace + 120;
        Instant reportedEnd = b.getPlannedEndAt().plus(lateMinutes, ChronoUnit.MINUTES);
        b.setActualEndAt(reportedEnd);
        BigDecimal unit = hourlyPrice(b.getVehicle());
        BookingCharge overtime = charges.save(charge(b, ChargeType.OVERTIME, 2, unit, null, unit.multiply(BigDecimal.valueOf(2)),
                reportedEnd.plus(5, ChronoUnit.MINUTES),
                "Quá giờ " + lateMinutes + " phút (đã trừ " + grace + " phút ân hạn)", ChargeConfirmationStatus.DISPUTED));
        em.flush();
        Long id = disputeService.open(b, b.getRenter().getUser(), DisputeCategory.CHARGE, overtime,
                "Tôi chỉ trả xe trễ khoảng 1 tiếng do kẹt xe trên cao tốc Phan Thiết – Dầu Giây, không phải 2 tiếng rưỡi như tài xế ghi.",
                List.of()).disputeId();
        em.flush();
        em.clear(); // resolveDispute refresh() cần dispute được nạp lại từ DB
        disputeService.resolveDispute(id, admin().getId(), DisputeStatus.RESOLVED, DisputeResolutionDecision.PARTIAL_WAIVE,
                "Đối chiếu GPS: xe về bãi trễ khoảng 1 giờ 20 phút. Giảm 1 giờ quá giờ, giữ lại 1 giờ.", unit);
        backdateDispute(id, reportedEnd.plus(40, ChronoUnit.MINUTES), reportedEnd.plus(26, ChronoUnit.HOURS));
    }

    /** Khách phản đối giờ kết thúc tài xế báo; admin chốt theo giờ khách (ACCEPT_RENTER_TIME + correctedEndAt). */
    private void ensureRenterTimeDispute(Booking b, BookingPolicySnapshot s) {
        if (hasDispute(b, DisputeCategory.TRIP_NOT_CONFIRMED) || !paidIn(b, PaymentType.SETTLEMENT).isEmpty()) return;
        if (endConfirmations.findFirstByBooking_IdOrderByIdDesc(b.getId()).isPresent()) return;
        Instant planned = b.getPlannedEndAt();
        Instant driverReported = planned.plus(100, ChronoUnit.MINUTES);
        Instant renterSays = planned.plus(10, ChronoUnit.MINUTES);
        b.setActualEndAt(driverReported);
        b.setDriverEndConfirmedAt(driverReported.plus(2, ChronoUnit.MINUTES));
        b.setEndConfirmationMethod("ADMIN");
        BigDecimal unit = hourlyPrice(b.getVehicle());
        charges.save(charge(b, ChargeType.OVERTIME, 2, unit, null, unit.multiply(BigDecimal.valueOf(2)),
                driverReported.plus(4, ChronoUnit.MINUTES), "Quá giờ 100 phút theo giờ tài xế báo kết thúc",
                ChargeConfirmationStatus.DISPUTED));

        TripEndConfirmation history = new TripEndConfirmation();
        history.setBooking(b);
        history.setReportedBy(b.getDriver().getUser().getId());
        Instant now = Instant.now();
        history.setReportedAt(now.minus(1, ChronoUnit.HOURS));
        history.setDeadline(now.plus(11, ChronoUnit.HOURS));
        history.setState("WAITING");
        history = endConfirmations.save(history);
        em.flush();

        Long id = disputeService.open(b, b.getRenter().getUser(), DisputeCategory.TRIP_NOT_CONFIRMED, null,
                "Tôi đã trả xe tại bãi lúc khoảng 10 phút sau giờ hẹn, tài xế bấm kết thúc chuyến muộn hơn gần 1 tiếng rưỡi.",
                List.of()).disputeId();
        em.flush();
        em.clear(); // resolveDispute refresh() cần dispute được nạp lại từ DB
        disputeService.resolveDispute(id, admin().getId(), DisputeStatus.RESOLVED, null,
                "Camera bãi xe và GPS xác nhận xe về lúc 10 phút sau giờ hẹn; chốt giờ kết thúc theo khách, miễn phí quá giờ.",
                null, "ACCEPT_RENTER_TIME", renterSays);
        em.flush();
        history = endConfirmations.findFirstByBooking_IdOrderByIdDesc(b.getId()).orElseThrow();
        Instant reportedAt = driverReported.plus(2, ChronoUnit.MINUTES);
        Instant settledAt = reportedAt.plus(20, ChronoUnit.HOURS);
        history.setReportedAt(reportedAt);
        history.setDeadline(reportedAt.plus(TripEndConfirmationWindow.HOURS, ChronoUnit.HOURS));
        history.setConfirmedAt(settledAt);
        history.setAdminActionAt(settledAt);
        backdateDispute(id, reportedAt.plus(35, ChronoUnit.MINUTES), settledAt);
    }

    /** Nhà xe ghi thiếu 50.000 đ tiền quyết toán; admin đối chiếu sao kê và ghi bù (PAYMENT_MISMATCH). */
    private void ensureMismatchDispute(Booking b, Timeline t) {
        if (hasDispute(b, DisputeCategory.PAYMENT_MISMATCH)) return;
        PaymentRequest request = paymentRequests.findByBooking_IdAndPaymentType(b.getId(), PaymentRequestType.FINAL_BALANCE)
                .orElse(null);
        if (request == null || request.getStatus() != PaymentRequestStatus.PARTIALLY_PAID) return;
        BigDecimal transferred = request.getAmount();
        String receipt = receiptImage(b, request, transferred, t.settlementPaid.minus(9, ChronoUnit.MINUTES));
        em.flush();
        Long id = disputeService.open(b, b.getRenter().getUser(), DisputeCategory.PAYMENT_MISMATCH, null,
                "Tôi đã chuyển đủ " + DemoAssetFactory.money(transferred) + " đ nhưng nhà xe chỉ ghi nhận "
                        + DemoAssetFactory.money(request.getPaidAmount()) + " đ.", List.of(receipt)).disputeId();
        em.flush();
        em.clear();
        disputeService.attachPaymentMismatch(id, request.getId(), transferred, "UNDER_RECORDED");
        disputeService.resolveDispute(id, admin().getId(), DisputeStatus.RESOLVED, null,
                "Sao kê ngân hàng xác nhận khách chuyển đủ " + DemoAssetFactory.money(transferred)
                        + " đ; nhà xe ghi thiếu 50.000 đ. Đã ghi bù giao dịch.", transferred);
        em.flush();
        Instant resolvedAt = t.settlementPaid.plus(1, ChronoUnit.DAYS);
        for (PaymentTransaction tx : paidIn(b, PaymentType.SETTLEMENT)) {
            if (tx.getVerificationMethod() == PaymentVerificationMethod.ADMIN_RECONCILED) {
                tx.setPaidAt(resolvedAt);
                tx.setUpdatedAt(resolvedAt);
            }
        }
        request = paymentRequests.findById(request.getId()).orElseThrow();
        request.setConfirmedAt(resolvedAt);
        request.setRenterAcknowledgedAt(resolvedAt.plus(2, ChronoUnit.HOURS));
        bookings.findById(b.getId()).ifPresent(fresh -> {
            fresh.setStatus(BookingStatus.COMPLETED);
            if (fresh.getSettlementRequestedAt() != null && fresh.getSettlementRequestedAt().isAfter(resolvedAt)) {
                fresh.setSettlementRequestedAt(t.settlementRequested);
            }
        });
        backdateDispute(id, t.settlementPaid.plus(3, ChronoUnit.HOURS), resolvedAt);
    }

    private void backdateDispute(Long disputeId, Instant createdAt, Instant resolvedAt) {
        em.flush();
        em.createNativeQuery("update dispute set created_at = ?1, resolved_at = ?2, updated_at = ?2 where id = ?3")
                .setParameter(1, createdAt).setParameter(2, resolvedAt).setParameter(3, disputeId).executeUpdate();
        em.createNativeQuery("update booking set settlement_requested_at = ?1 where id = "
                        + "(select booking_id from dispute where id = ?2) and settlement_requested_at > ?3")
                .setParameter(1, resolvedAt).setParameter(2, disputeId).setParameter(3, resolvedAt).executeUpdate();
    }

    // ------------------------------------------------------------------ reviews

    private static final String[] VEHICLE_5 = {
            "Xe sạch sẽ, thơm tho, điều hoà mát lạnh. Cả nhà đi rất thoải mái.",
            "Xe mới, chạy êm, cách âm tốt. Sẽ thuê lại lần sau.",
            "Nội thất rộng rãi, ghế ngồi êm, đi đường dài không mỏi.",
            "Xe đúng như hình, giao đúng giờ, pin/xăng đầy đủ khi nhận.",
            "Rất hài lòng, xe đẹp và được chuẩn bị chu đáo, có cả nước uống."};
    private static final String[] VEHICLE_4 = {
            "Xe chạy ổn, sạch sẽ. Cốp hơi nhỏ so với hành lý của nhóm mình.",
            "Xe tốt, điều hoà mát. Màn hình giải trí thỉnh thoảng hơi chậm.",
            "Nhìn chung hài lòng, ghế sau hơi chật cho 3 người lớn.",
            "Xe ổn, giá hợp lý. Mong nhà xe trang bị thêm sạc điện thoại."};
    private static final String[] VEHICLE_3 = {
            "Xe tạm được, có mùi hơi ẩm lúc mới lên, mở cửa một lúc thì hết.",
            "Xe chạy được nhưng thắng hơi kêu, nhà xe nên bảo dưỡng thêm."};
    private static final String[] DRIVER_5 = {
            "Tài xế lịch sự, lái xe an toàn, đến đón sớm 10 phút.",
            "Anh tài nhiệt tình, hỗ trợ khuân hành lý, rành đường.",
            "Lái êm, không phanh gấp, bé nhà mình ngủ suốt chuyến.",
            "Tài xế đúng giờ, vui vẻ, chủ động hỏi nhiệt độ điều hoà."};
    private static final String[] DRIVER_4 = {
            "Tài xế lái cẩn thận, chỉ hơi ít nói. Nhìn chung ổn.",
            "Đến đúng giờ, lái an toàn, có lúc chọn đường hơi kẹt xe."};
    private static final String[] DRIVER_3 = {
            "Tài xế đến trễ khoảng 15 phút, có báo trước nhưng mong cải thiện."};
    private static final int[] VEHICLE_RATINGS = {5, 4, 5, 5, 3, 4, 5, 4, 5, 5, 4, 5};
    private static final int[] DRIVER_RATINGS = {5, 5, 4, 5, 5, 3, 5, 4, 5, 4, 5, 5};

    private void ensureReviews(Booking b, Timeline t, int index) {
        if (index < 0 || index % 5 == 1 || index % 5 == 3) return; // ~60% đơn hoàn tất có đánh giá
        Instant base = (b.getProviderConfirmedAt() != null ? b.getProviderConfirmedAt()
                : t.completed != null ? t.completed : b.getActualEndAt()).plus(3 + index % 7, ChronoUnit.HOURS);
        int vehicleRating = VEHICLE_RATINGS[index % VEHICLE_RATINGS.length];
        if (DISPUTE_PARTIAL.equals(b.getBookingCode())) vehicleRating = 4;
        if (!reviews.existsByBooking_IdAndVehicle_Id(b.getId(), b.getVehicle().getId())) {
            Review review = new Review();
            review.setBooking(b);
            review.setVehicle(b.getVehicle());
            review.setRating(vehicleRating);
            review.setComment(pick(vehicleRating == 5 ? VEHICLE_5 : vehicleRating == 4 ? VEHICLE_4 : VEHICLE_3, index));
            if (index % 4 == 0) {
                List<String> media = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    String url = vehiclePhoto(b.getVehicle(), index + i);
                    if (url != null && !media.contains(url)) media.add(url);
                }
                if (!media.isEmpty()) review.setMedia(String.join("\n", media));
            }
            review.setCreatedAt(base);
            reviews.save(review);
        }
        if (b.getDriver() != null && !reviews.existsByBooking_IdAndDriver_Id(b.getId(), b.getDriver().getId())) {
            int driverRating = DRIVER_RATINGS[index % DRIVER_RATINGS.length];
            Review review = new Review();
            review.setBooking(b);
            review.setDriver(b.getDriver());
            review.setRating(driverRating);
            review.setComment(pick(driverRating == 5 ? DRIVER_5 : driverRating == 4 ? DRIVER_4 : DRIVER_3, index));
            review.setCreatedAt(base.plus(2, ChronoUnit.MINUTES));
            reviews.save(review);
        }
    }

    private static String pick(String[] pool, int index) {
        return pool[Math.floorMod(index * 7 + 3, pool.length)];
    }

    private void refreshRatings() {
        List<Review> all = reviews.findAll();
        Set<Long> vehicleIds = new LinkedHashSet<>();
        Set<Long> driverIds = new LinkedHashSet<>();
        Set<Long> providerIds = new LinkedHashSet<>();
        for (Review review : all) {
            if (review.getVehicle() != null) {
                vehicleIds.add(review.getVehicle().getId());
                providerIds.add(review.getVehicle().getProvider().getId());
            }
            if (review.getDriver() != null) {
                driverIds.add(review.getDriver().getId());
                if (review.getDriver().getProvider() != null) providerIds.add(review.getDriver().getProvider().getId());
            }
        }
        for (Long vehicleId : vehicleIds) {
            if (PROTECTED_VEHICLES.contains(vehicleId)) continue;
            List<Review> list = reviews.findByVehicle_Id(vehicleId);
            vehicleService.updateRating(vehicleId, average(list), list.size());
        }
        for (Long driverId : driverIds) {
            List<Review> list = reviews.findByDriver_Id(driverId);
            userService.updateDriverRating(driverId, average(list), list.size());
        }
        for (Long providerId : providerIds) {
            List<Review> list = reviews.findByVehicle_Provider_IdOrDriver_Provider_Id(providerId, providerId);
            userService.updateProviderRating(providerId, average(list), list.size());
        }
    }

    private static BigDecimal average(List<Review> list) {
        if (list.isEmpty()) return BigDecimal.ZERO.setScale(2);
        return list.stream().map(r -> BigDecimal.valueOf(r.getRating())).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP);
    }

}
