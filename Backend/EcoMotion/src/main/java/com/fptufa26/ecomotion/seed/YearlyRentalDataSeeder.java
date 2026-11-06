package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.common.storage.service.FileStorageService;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class YearlyRentalDataSeeder {

    private static final long INELIGIBLE_RENTER_ID = 108L;
    private static final long ACTIVE_RENTER_ID = 109L;
    private static final long FLOW_RENTER_ID = 110L;
    private static final String ACTIVE_BOOKING_CODE = "EM-SEED-YEARLY-ACTIVE";
    private static final String HISTORY_BOOKING_CODE = "EM-SEED-YEARLY-HISTORY";
    private static final int YEARLY_SLOT_MINUTES = 525600;
    private static final int INSTALLMENT_COUNT = 12;
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserRepository userRepository;
    private final RenterRepository renterRepository;
    private final VehicleRepository vehicleRepository;
    private final RentalPlanRepository rentalPlanRepository;
    private final ProviderBankAccountRepository providerBankAccountRepository;
    private final FileStorageService fileStorageService;
    private final EntityManager entityManager;

    @Transactional
    public void seed() {
        Renter ineligibleRenter = findRenter(INELIGIBLE_RENTER_ID);
        Renter activeRenter = findRenter(ACTIVE_RENTER_ID);
        Renter flowRenter = findRenter(FLOW_RENTER_ID);

        seedLicense(ineligibleRenter, "A1");
        seedLicense(activeRenter, "B");
        seedLicense(flowRenter, "B");

        if (!exists(ACTIVE_BOOKING_CODE)) {
            seedActiveRental(activeRenter);
        } else {
            synchronizeActiveRental();
        }
        if (!exists(HISTORY_BOOKING_CODE)) {
            seedCompletedRental(activeRenter);
        }
    }

    private Renter findRenter(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Yearly demo renter user is missing: " + userId));
        return renterRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Yearly demo renter profile is missing: " + userId));
    }

    private void seedLicense(Renter renter, String licenseClass) {
        if (renter.getLicenseNo() != null) {
            return;
        }

        String normalizedClass = licenseClass.toUpperCase();
        String filePrefix = "yearly-demo-license-" + renter.getUser().getId();
        String frontPath = fileStorageService.store(
                "renter",
                renter.getUser().getId(),
                filePrefix + "-front.png",
                createDemoLicenseImage("FRONT", renter.getUser().getFullName(), normalizedClass),
                "image/png");
        String backPath = fileStorageService.store(
                "renter",
                renter.getUser().getId(),
                filePrefix + "-back.png",
                createDemoLicenseImage("BACK", renter.getUser().getFullName(), normalizedClass),
                "image/png");

        renter.setLicenseNo("SEED-YEARLY-" + renter.getUser().getId());
        renter.setLicenseFullName(renter.getUser().getFullName());
        renter.setLicenseDateOfBirth(LocalDate.of(1992, 4, 12));
        renter.setLicenseClass(normalizedClass);
        renter.setLicenseIssueDate(LocalDate.of(2025, 2, 1));
        renter.setLicenseExpiry(LocalDate.now(VIETNAM_ZONE).plusYears(5));
        renter.setLicenseExpiryUnlimited(false);
        renter.setLicenseVerified(true);
        renter.setLicenseFrontImagePath(frontPath);
        renter.setLicenseBackImagePath(backPath);
        renterRepository.saveAndFlush(renter);
    }

    private byte[] createDemoLicenseImage(String side, String fullName, String licenseClass) {
        BufferedImage image = new BufferedImage(900, 560, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(28, 67, 93));
            graphics.fillRect(0, 0, image.getWidth(), 90);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
            graphics.drawString("DEMO ONLY - NOT AN OFFICIAL DOCUMENT", 28, 57);
            graphics.setColor(new Color(28, 67, 93));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            graphics.drawString("SAMPLE DRIVING LICENSE - " + side, 32, 165);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
            graphics.drawString("Name: " + fullName, 32, 235);
            graphics.drawString("Class: " + licenseClass, 32, 285);
            graphics.drawString("Seed data for local development only", 32, 390);
            graphics.setColor(new Color(180, 35, 35));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            graphics.drawString("NOT VALID FOR IDENTIFICATION", 32, 485);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("Could not encode demo driving-license image");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create demo driving-license image", exception);
        }
    }

    private boolean exists(String bookingCode) {
        return entityManager.createQuery(
                        "SELECT COUNT(b) FROM Booking b WHERE b.bookingCode = :bookingCode",
                        Long.class)
                .setParameter("bookingCode", bookingCode)
                .getSingleResult() > 0;
    }

    private void seedActiveRental(Renter renter) {
        RentalPlan plan = findYearlyPlan(6L);
        Vehicle vehicle = plan.getVehicle();
        Provider provider = vehicle.getProvider();
        LocalDate startDate = activeStartDate();
        LocalDate dueDate = startDate.plusMonths(3);
        Instant startAt = startDate.atStartOfDay(VIETNAM_ZONE).toInstant();
        Instant createdAt = startDate.minusDays(30).atStartOfDay(VIETNAM_ZONE).toInstant();
        Instant endAt = startAt.plus(plan.getSlotLengthMinutes(), ChronoUnit.MINUTES);

        Booking booking = createBooking(
                ACTIVE_BOOKING_CODE,
                renter,
                plan,
                BookingStatus.IN_PROGRESS,
                startAt,
                endAt,
                createdAt);
        booking.setActualStartAt(startAt);
        entityManager.persist(booking);

        BookingPolicySnapshot snapshot = createSnapshot(booking, plan);
        entityManager.persist(snapshot);

        BookingCharge baseCharge = createBaseCharge(booking, provider.getUser(), createdAt);
        entityManager.persist(baseCharge);

        for (int installment = 1; installment <= 3; installment++) {
            Instant paidAt = startDate.plusMonths(installment - 1L)
                    .minusDays(1)
                    .atStartOfDay(VIETNAM_ZONE)
                    .toInstant();
            persistPayment(
                    booking,
                    baseCharge,
                    PaymentType.SETTLEMENT,
                    installmentAmount(plan.getSlotPrice(), installment),
                    paidAt,
                    "SEED-YR-ACT-" + installment);
        }

        ProviderBankAccount bankAccount = providerBankAccountRepository
                .findByProvider_IdAndStatus(provider.getId(), BankAccountStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException(
                        "Yearly demo requires an active provider bank account"));
        persistUpcomingInstallmentRequest(
                booking,
                bankAccount,
                installmentAmount(plan.getSlotPrice(), 4),
                dueDate.atStartOfDay(VIETNAM_ZONE).toInstant());

        VehicleSchedule schedule = new VehicleSchedule();
        schedule.setVehicle(vehicle);
        schedule.setBooking(booking);
        schedule.setType(ScheduleType.BOOKED);
        schedule.setStartAt(startAt);
        schedule.setEndAt(endAt);
        schedule.setStatus(ScheduleStatus.ACTIVE);
        schedule.setExtension(false);
        schedule.setCreatedAt(createdAt);
        entityManager.persist(schedule);
    }

    private void synchronizeActiveRental() {
        Booking booking = entityManager.createQuery(
                        "SELECT b FROM Booking b WHERE b.bookingCode = :bookingCode",
                        Booking.class)
                .setParameter("bookingCode", ACTIVE_BOOKING_CODE)
                .getSingleResult();
        if (booking.getPickupTime() != null
                && booking.getCreatedAt() != null
                && booking.getCreatedAt().isBefore(booking.getPickupTime())) {
            return;
        }
        LocalDate startDate = activeStartDate();
        LocalDate dueDate = startDate.plusMonths(3);
        Instant startAt = startDate.atStartOfDay(VIETNAM_ZONE).toInstant();
        Instant endAt = startAt.plus(booking.getPlan().getSlotLengthMinutes(), ChronoUnit.MINUTES);
        Instant createdAt = startDate.minusDays(30).atStartOfDay(VIETNAM_ZONE).toInstant();

        booking.setPickupTime(startAt);
        booking.setPlannedEndAt(endAt);
        booking.setActualStartAt(startAt);
        booking.setCreatedAt(createdAt);
        booking.setUpdatedAt(createdAt);
        entityManager.createNativeQuery(
                        "UPDATE booking SET created_at = :createdAt WHERE id = :bookingId")
                .setParameter("createdAt", createdAt)
                .setParameter("bookingId", booking.getId())
                .executeUpdate();

        VehicleSchedule schedule = entityManager.createQuery(
                        "SELECT s FROM VehicleSchedule s WHERE s.booking.id = :bookingId",
                        VehicleSchedule.class)
                .setParameter("bookingId", booking.getId())
                .getResultStream()
                .findFirst()
                .orElseGet(() -> {
                    VehicleSchedule created = new VehicleSchedule();
                    created.setVehicle(booking.getVehicle());
                    created.setBooking(booking);
                    created.setType(ScheduleType.BOOKED);
                    created.setStatus(ScheduleStatus.ACTIVE);
                    created.setExtension(false);
                    entityManager.persist(created);
                    return created;
                });
        schedule.setStartAt(startAt);
        schedule.setEndAt(endAt);

        PaymentRequest request = entityManager.createQuery(
                        "SELECT r FROM PaymentRequest r WHERE r.booking.id = :bookingId AND r.paymentType = :paymentType",
                        PaymentRequest.class)
                .setParameter("bookingId", booking.getId())
                .setParameter("paymentType", PaymentRequestType.MONTHLY_INSTALLMENT)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (request != null) {
            Instant dueAt = dueDate.atStartOfDay(VIETNAM_ZONE).toInstant();
            request.setExpiresAt(dueAt.plus(3, ChronoUnit.DAYS));
            request.setIssueNote(
                    "Demo seed: monthly rent installment 4/12 due "
                            + dueDate);
            request.setCreatedAt(dueAt.minus(7, ChronoUnit.DAYS));
            request.setUpdatedAt(request.getCreatedAt());
            entityManager.createNativeQuery(
                            "UPDATE payment_request SET created_at = :createdAt WHERE id = :requestId")
                    .setParameter("createdAt", request.getCreatedAt())
                    .setParameter("requestId", request.getId())
                    .executeUpdate();
        }
    }

    private LocalDate activeStartDate() {
        return LocalDate.now(VIETNAM_ZONE).minusMonths(3).plusDays(3);
    }

    private void seedCompletedRental(Renter renter) {
        RentalPlan plan = findYearlyPlan(8L);
        Vehicle vehicle = plan.getVehicle();
        Provider provider = vehicle.getProvider();
        LocalDate startDate = LocalDate.now(VIETNAM_ZONE).minusMonths(20);
        Instant startAt = startDate.atStartOfDay(VIETNAM_ZONE).toInstant();
        Instant endAt = startAt.plus(plan.getSlotLengthMinutes(), ChronoUnit.MINUTES);
        Instant createdAt = startAt.minus(30, ChronoUnit.DAYS);

        Booking booking = createBooking(
                HISTORY_BOOKING_CODE,
                renter,
                plan,
                BookingStatus.COMPLETED,
                startAt,
                endAt,
                createdAt);
        booking.setActualStartAt(startAt);
        booking.setActualEndAt(endAt);
        booking.setProviderConfirmedAt(createdAt.plus(1, ChronoUnit.DAYS));
        booking.setRenterConfirmedAt(endAt);
        entityManager.persist(booking);

        BookingPolicySnapshot snapshot = createSnapshot(booking, plan);
        entityManager.persist(snapshot);

        BookingCharge baseCharge = createBaseCharge(booking, provider.getUser(), createdAt);
        entityManager.persist(baseCharge);

        for (int installment = 1; installment <= INSTALLMENT_COUNT; installment++) {
            Instant paidAt = startDate.plusMonths(installment - 1L)
                    .minusDays(1)
                    .atStartOfDay(VIETNAM_ZONE)
                    .toInstant();
            persistPayment(
                    booking,
                    baseCharge,
                    PaymentType.SETTLEMENT,
                    installmentAmount(plan.getSlotPrice(), installment),
                    paidAt,
                    "SEED-YR-HISTORY-" + installment);
        }
    }

    private RentalPlan findYearlyPlan(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new IllegalStateException(
                        "Yearly demo vehicle is missing: " + vehicleId));
        return rentalPlanRepository.findByVehicle_IdOrderByIdAsc(vehicle.getId()).stream()
                .filter(plan -> plan.getStatus() == PlanStatus.ACTIVE)
                .filter(plan -> plan.getPlanType() == PlanType.YEARLY)
                .filter(plan -> Integer.valueOf(YEARLY_SLOT_MINUTES)
                        .equals(plan.getSlotLengthMinutes()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Yearly demo vehicle requires an active 365-day plan: " + vehicleId));
    }

    private Booking createBooking(
            String bookingCode,
            Renter renter,
            RentalPlan plan,
            BookingStatus status,
            Instant startAt,
            Instant endAt,
            Instant createdAt) {
        Booking booking = new Booking();
        booking.setBookingCode(bookingCode);
        booking.setRenter(renter);
        booking.setVehicle(plan.getVehicle());
        booking.setPlan(plan);
        booking.setStatus(status);
        booking.setPickupAddress(plan.getVehicle().getProvider().getDepotAddress());
        booking.setPickupTime(startAt);
        booking.setPlannedEndAt(endAt);
        booking.setPassengerCount(1);
        booking.setTotalAmount(plan.getSlotPrice());
        booking.setCreatedAt(createdAt);
        booking.setUpdatedAt(createdAt);
        return booking;
    }

    private BookingPolicySnapshot createSnapshot(Booking booking, RentalPlan plan) {
        BookingPolicySnapshot snapshot = new BookingPolicySnapshot();
        snapshot.setBooking(booking);
        snapshot.setSlotPrice(plan.getSlotPrice());
        snapshot.setSlotLengthMinutes(plan.getSlotLengthMinutes());
        snapshot.setPlanType(plan.getPlanType());
        snapshot.setDepositRatio(null);
        snapshot.setUpfrontAmount(null);
        snapshot.setVehicleName(
                plan.getVehicle().getBrand() + " " + plan.getVehicle().getModel());
        snapshot.setVehicleManufactureYear(plan.getVehicle().getManufactureYear());
        snapshot.setVehicleLicensePlate(plan.getVehicle().getLicensePlate());
        return snapshot;
    }

    private BookingCharge createBaseCharge(
            Booking booking,
            User createdBy,
            Instant createdAt) {
        BookingCharge charge = new BookingCharge();
        charge.setBooking(booking);
        charge.setChargeType(ChargeType.BASE_RENTAL);
        charge.setDescription("Tiền thuê gói năm, thanh toán theo 12 kỳ hằng tháng.");
        charge.setQuantity(INSTALLMENT_COUNT);
        charge.setUnitPrice(monthlyAmount(booking.getTotalAmount()));
        charge.setAmount(booking.getTotalAmount());
        charge.setCreatedBy(createdBy);
        charge.setWaived(false);
        charge.setCreatedAt(createdAt);
        return charge;
    }

    private void persistPayment(
            Booking booking,
            BookingCharge charge,
            PaymentType type,
            BigDecimal amount,
            Instant paidAt,
            String transactionCode) {
        PaymentTransaction payment = new PaymentTransaction();
        payment.setBooking(booking);
        payment.setCharge(charge);
        payment.setType(type);
        payment.setAmount(amount);
        payment.setAppliedAmount(amount);
        payment.setDirection(PaymentDirection.IN);
        payment.setMethod(PaymentMethod.BANK_TRANSFER);
        payment.setTransactionCode(transactionCode);
        payment.setGatewayRef(transactionCode);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(paidAt);
        payment.setProcessingNote("Seeded demo rent installment");
        payment.setCreatedAt(paidAt);
        payment.setUpdatedAt(paidAt);
        entityManager.persist(payment);
    }

    private void persistUpcomingInstallmentRequest(
            Booking booking,
            ProviderBankAccount bankAccount,
            BigDecimal amount,
            Instant dueAt) {
        PaymentRequest request = new PaymentRequest();
        request.setBooking(booking);
        request.setPaymentType(PaymentRequestType.MONTHLY_INSTALLMENT);
        request.setAmount(amount);
        request.setPaidAmount(BigDecimal.ZERO);
        request.setCurrency("VND");
        request.setPaymentCode("YR-ACT-MONTH-04");
        request.setTransferReference("YR-ACT-MONTH-04-TRANSFER");
        request.setBankCodeSnapshot(bankAccount.getBankCode());
        request.setBankNameSnapshot(bankAccount.getBankName());
        request.setAccountNumberSnapshot(bankAccount.getAccountNumber());
        request.setAccountHolderSnapshot(bankAccount.getAccountHolderName());
        request.setReceivingBankAccount(bankAccount);
        request.setStatus(PaymentRequestStatus.PENDING);
        request.setExpiresAt(dueAt.plus(3, ChronoUnit.DAYS));
        request.setIssueNote(
                "Demo seed: monthly rent installment 4/12 due "
                        + dueAt.atZone(VIETNAM_ZONE).toLocalDate());
        request.setCreatedAt(dueAt.minus(7, ChronoUnit.DAYS));
        request.setUpdatedAt(request.getCreatedAt());
        entityManager.persist(request);
    }

    private BigDecimal monthlyAmount(BigDecimal total) {
        return total.divide(BigDecimal.valueOf(INSTALLMENT_COUNT), 2, RoundingMode.DOWN);
    }

    private BigDecimal installmentAmount(BigDecimal total, int installment) {
        BigDecimal monthly = monthlyAmount(total);
        if (installment < INSTALLMENT_COUNT) {
            return monthly;
        }
        return total.subtract(monthly.multiply(BigDecimal.valueOf(INSTALLMENT_COUNT - 1L)));
    }
}
