package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.DriverAssignment;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.entity.TripEndConfirmation;
import com.fptufa26.ecomotion.booking.entity.TripEvidence;
import com.fptufa26.ecomotion.booking.enums.AssignmentMode;
import com.fptufa26.ecomotion.booking.enums.AssignmentStatus;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.enums.TripEvidenceType;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.DriverAssignmentRepository;
import com.fptufa26.ecomotion.finance.entity.PaymentEvidence;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentEvidenceStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.finance.service.PaymentCodeGenerator;
import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class RenterCompletedLifecycleSeeder {

    public static final String BOOKING_CODE = "EM-2026-DEMO-COMPLETE";
    private static final String DRIVER_EMAIL = "driver.cuong@ecomotion.vn";
    private static final int DAILY_SLOT_MINUTES = 1440;
    private static final int LOOKBACK_START_DAYS = 45;
    private static final int AVAILABILITY_SEARCH_DAYS = 120;
    private static final BigDecimal OUT_OF_RADIUS_FEE = new BigDecimal("75000.00");

    private static final String SEED_IMAGE_BASE = "/seed/demo-complete/";

    private final BookingRepository bookingRepository;
    private final VehicleScheduleRepository vehicleScheduleRepository;
    private final UserRepository userRepository;
    private final RenterRepository renterRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final RentalPlanRepository rentalPlanRepository;
    private final DriverAssignmentRepository driverAssignmentRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final ProviderBankAccountRepository providerBankAccountRepository;
    private final EntityManager entityManager;
    private final SeedDepositPaymentSynchronizer depositPayments;
    private final PaymentCodeGenerator paymentCodes;

    @Transactional
    public void seed() {
        if (bookingRepository.existsByBookingCode(BOOKING_CODE)) {
            bookingRepository.findByBookingCode(BOOKING_CODE).ifPresent(booking -> {
                depositPayments.synchronize(booking);
                synchronizeFinalBalance(booking);
            });
            return;
        }

        User renterUser = userRepository.findByEmailIgnoreCase(DevDataSeeder.RENTER_EMAIL)
                .orElseThrow(() -> new IllegalStateException("Demo renter user is missing"));
        Renter renter = renterRepository.findByUser_Id(renterUser.getId())
                .orElseThrow(() -> new IllegalStateException("Demo renter profile is missing"));

        RentalPlan plan = findDailyPlanForDemoVehicle();
        Vehicle vehicle = plan.getVehicle();
        Provider provider = vehicle.getProvider();
        Driver driver = driverRepository.findByUser_Id(
                        userRepository.findByEmailIgnoreCase(DRIVER_EMAIL).orElseThrow().getId())
                .orElseThrow(() -> new IllegalStateException("Demo driver profile is missing"));
        if (driver.getStatus() != DriverStatus.APPROVED
                || !driver.getProvider().getId().equals(provider.getId())) {
            throw new IllegalStateException("Demo driver is not approved for provider A");
        }

        ProviderBankAccount bank = providerBankAccountRepository
                .findByProvider_IdAndStatus(provider.getId(), BankAccountStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Provider bank account missing for demo complete seed"));

        Instant startAt = Instant.now()
                .minus(LOOKBACK_START_DAYS, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.HOURS);
        int offset = 0;
        while (offset < AVAILABILITY_SEARCH_DAYS && !isAvailable(renter, vehicle, startAt,
                startAt.plus(plan.getSlotLengthMinutes(), ChronoUnit.MINUTES))) {
            startAt = startAt.minus(1, ChronoUnit.DAYS);
            offset++;
        }
        if (offset >= AVAILABILITY_SEARCH_DAYS) {
            log.warn("Skipped {}: no free interval for vehicle {} and renter {}",
                    BOOKING_CODE, vehicle.getLicensePlate(), DevDataSeeder.RENTER_EMAIL);
            return;
        }

        Instant endAt = startAt.plus(plan.getSlotLengthMinutes(), ChronoUnit.MINUTES);
        Instant requestedAt = startAt.minus(3, ChronoUnit.DAYS);
        Instant depositProofAt = requestedAt.plus(2, ChronoUnit.HOURS);
        Instant depositPaidAt = startAt.minus(2, ChronoUnit.DAYS);
        Instant assignedAt = startAt.minus(1, ChronoUnit.DAYS);
        Instant driverConfirmedAt = startAt.minus(20, ChronoUnit.HOURS);
        Instant arrivedAt = startAt.minus(15, ChronoUnit.MINUTES);
        Instant tripStartAt = startAt;
        Instant tripEndAt = endAt;
        Instant driverEndAt = endAt.plus(2, ChronoUnit.MINUTES);
        Instant renterEndAt = endAt.plus(8, ChronoUnit.MINUTES);
        Instant settlementProofAt = endAt.plus(20, ChronoUnit.MINUTES);
        Instant settlementPaidAt = endAt.plus(35, ChronoUnit.MINUTES);
        Instant completedAt = endAt.plus(50, ChronoUnit.MINUTES);

        Booking booking = new Booking();
        booking.setBookingCode(BOOKING_CODE);
        booking.setRenter(renter);
        booking.setVehicle(vehicle);
        booking.setPlan(plan);
        booking.setDriver(driver);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setAssignmentMode(AssignmentMode.MANUAL);
        booking.setPickupAddress(provider.getDepotAddress());
        booking.setPickupTime(startAt);
        booking.setPlannedEndAt(endAt);
        booking.setPassengerCount(2);
        booking.setTotalAmount(plan.getSlotPrice());
        booking.setAssignedAt(assignedAt);
        booking.setAssignmentExpiresAt(driverConfirmedAt.minus(1, ChronoUnit.HOURS));
        booking.setDriverConfirmedAt(driverConfirmedAt);
        booking.setActualArrivedAt(arrivedAt);
        booking.setActualStartAt(tripStartAt);
        booking.setDriverStartConfirmedAt(tripStartAt);
        booking.setRenterStartConfirmedAt(tripStartAt.plus(1, ChronoUnit.MINUTES));
        booking.setActualEndAt(tripEndAt);
        booking.setDriverEndConfirmedAt(driverEndAt);
        booking.setRenterEndConfirmedAt(renterEndAt);
        booking.setRenterConfirmedAt(renterEndAt);
        booking.setProviderConfirmedAt(completedAt);
        booking.setEndConfirmationMethod("RENTER");
        booking.setCreatedAt(requestedAt);
        booking.setUpdatedAt(completedAt);
        entityManager.persist(booking);

        BookingPolicySnapshot snapshot = createSnapshot(booking, plan, provider, requestedAt);
        BigDecimal baseAmount = snapshot.getSlotPrice();
        BigDecimal depositAmount = baseAmount.multiply(snapshot.getDepositRatio())
                .setScale(2, RoundingMode.HALF_UP);
        snapshot.setDepositAmount(depositAmount);
        entityManager.persist(snapshot);

        if (depositAmount.signum() <= 0) {
            throw new IllegalStateException("Demo complete seed requires a positive deposit");
        }

        BookingCharge baseCharge = createCharge(booking, provider.getUser(), ChargeType.BASE_RENTAL,
                1, baseAmount, null, baseAmount, requestedAt, "Thuê 1 ngày — VinFast VF 6");
        entityManager.persist(baseCharge);

        BookingCharge depositCharge = createCharge(booking, provider.getUser(), ChargeType.DEPOSIT,
                null, baseAmount, snapshot.getDepositRatio(), depositAmount, requestedAt,
                "Cọc giữ chỗ (VietQR)");
        entityManager.persist(depositCharge);

        BookingCharge radiusCharge = createCharge(booking, provider.getUser(), ChargeType.OUT_OF_RADIUS,
                1, OUT_OF_RADIUS_FEE, null, OUT_OF_RADIUS_FEE, tripEndAt,
                "Phí vượt bán kính giao nhận (điểm đón ngoài depot)");
        entityManager.persist(radiusCharge);

        BigDecimal settlementAmount = baseAmount.subtract(depositAmount).add(OUT_OF_RADIUS_FEE);

        PaymentTransaction depositTx = createInboundPayment(booking, depositCharge, PaymentType.DEPOSIT_PAY,
                depositAmount, depositPaidAt, "DEMO-COMPLETE-DEP-001", provider.getUser());
        entityManager.persist(depositTx);
        entityManager.flush();

        depositPayments.synchronize(booking);
        PaymentRequest depositRequest = paymentRequestRepository
                .findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.DEPOSIT)
                .orElseThrow();
        depositRequest.setFirstProofAt(depositProofAt);
        depositRequest.setCreatedAt(requestedAt.plus(30, ChronoUnit.MINUTES));
        attachAcceptedEvidence(depositRequest, renterUser, depositAmount, depositProofAt,
                "SEED-DEP-REF", SEED_IMAGE_BASE + "deposit-transfer-receipt.jpg",
                "Chuyển khoản cọc theo mã VietQR trên đơn");
        paymentRequestRepository.save(depositRequest);

        PaymentRequest finalRequest = createFinalPaymentRequest(booking, bank, provider, settlementAmount,
                endAt.plus(10, ChronoUnit.MINUTES));
        finalRequest.setFirstProofAt(settlementProofAt);
        finalRequest.setConfirmedAt(settlementPaidAt);
        finalRequest.setConfirmedBy(provider.getUser());
        finalRequest.setStatus(PaymentRequestStatus.CONFIRMED);
        finalRequest.setPaidAmount(settlementAmount);
        finalRequest = paymentRequestRepository.save(finalRequest);

        PaymentTransaction settlementTx = createInboundPayment(booking, baseCharge, PaymentType.SETTLEMENT,
                settlementAmount, settlementPaidAt, "DEMO-COMPLETE-SET-001", provider.getUser());
        settlementTx.setPaymentRequest(finalRequest);
        entityManager.persist(settlementTx);

        attachAcceptedEvidence(finalRequest, renterUser, settlementAmount, settlementProofAt,
                "SEED-SET-REF", SEED_IMAGE_BASE + "settlement-transfer-receipt.jpg",
                "Quyết toán phần còn lại + phí vượt bán kính");

        VehicleSchedule schedule = new VehicleSchedule();
        schedule.setVehicle(vehicle);
        schedule.setBooking(booking);
        schedule.setType(ScheduleType.BOOKED);
        schedule.setStartAt(startAt);
        schedule.setEndAt(endAt);
        schedule.setStatus(ScheduleStatus.ACTIVE);
        schedule.setExtension(false);
        schedule.setCreatedAt(depositPaidAt);
        schedule.setUpdatedAt(completedAt);
        entityManager.persist(schedule);

        DriverAssignment assignment = new DriverAssignment();
        assignment.setBooking(booking);
        assignment.setDriver(driver);
        assignment.setStatus(AssignmentStatus.ACCEPTED);
        assignment.setRoundNumber(1);
        assignment.setAssignedAt(assignedAt);
        assignment.setRespondedAt(driverConfirmedAt);
        assignment.setExpiresAt(booking.getAssignmentExpiresAt());
        assignment.setCreatedAt(assignedAt);
        assignment.setUpdatedAt(driverConfirmedAt);
        driverAssignmentRepository.save(assignment);

        persistTripEvidence(booking, driver, renterUser, TripEvidenceType.PICKUP_ARRIVAL,
                arrivedAt, SEED_IMAGE_BASE + "pickup-arrival.jpg", "Tài xế đã đến điểm đón");
        persistTripEvidence(booking, driver, driver.getUser(), TripEvidenceType.VEHICLE_HANDOVER,
                tripStartAt, SEED_IMAGE_BASE + "vehicle-handover.jpg", "Bàn giao xe cho khách");
        persistTripEvidence(booking, driver, driver.getUser(), TripEvidenceType.PRE_TRIP_CONDITION,
                tripStartAt.plus(2, ChronoUnit.MINUTES), SEED_IMAGE_BASE + "pre-trip-odometer.jpg",
                "Odo và ngoại thất trước chuyến");
        persistTripEvidence(booking, driver, driver.getUser(), TripEvidenceType.POST_TRIP_CONDITION,
                tripEndAt, SEED_IMAGE_BASE + "post-trip-odometer.jpg", "Odo sau chuyến");
        persistTripEvidence(booking, driver, driver.getUser(), TripEvidenceType.TRIP_COMPLETION,
                driverEndAt, SEED_IMAGE_BASE + "trip-completion.jpg", "Kết thúc chuyến tại điểm trả");

        TripEndConfirmation endHistory = new TripEndConfirmation();
        endHistory.setBooking(booking);
        endHistory.setReportedBy(driver.getUser().getId());
        endHistory.setReportedAt(driverEndAt);
        endHistory.setDeadline(renterEndAt.plus(12, ChronoUnit.HOURS));
        endHistory.setState("CONFIRMED");
        endHistory.setConfirmationMethod("RENTER");
        endHistory.setConfirmedBy(renterUser.getId());
        endHistory.setConfirmedAt(renterEndAt);
        entityManager.persist(endHistory);

        entityManager.flush();
        log.info("Seeded completed lifecycle demo booking {} for {}", BOOKING_CODE, DevDataSeeder.RENTER_EMAIL);
    }

    private void synchronizeFinalBalance(Booking booking) {
        if (paymentRequestRepository
                .findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.FINAL_BALANCE)
                .isPresent()) {
            return;
        }
        var settlementTx = entityManager.createQuery(
                        "select t from PaymentTransaction t where t.booking.id = :bid and t.type = :type and t.status = :status",
                        PaymentTransaction.class)
                .setParameter("bid", booking.getId())
                .setParameter("type", PaymentType.SETTLEMENT)
                .setParameter("status", PaymentStatus.PAID)
                .getResultStream()
                .findFirst();
        if (settlementTx.isEmpty()) {
            return;
        }
        BigDecimal expectedSettlement = settlementTx.get().getAppliedAmount();
        if (expectedSettlement == null || expectedSettlement.signum() <= 0) {
            expectedSettlement = settlementTx.get().getAmount();
        }
        Provider provider = booking.getVehicle().getProvider();
        ProviderBankAccount bank = providerBankAccountRepository
                .findByProvider_IdAndStatus(provider.getId(), BankAccountStatus.ACTIVE)
                .orElseThrow();
        PaymentRequest finalRequest = createFinalPaymentRequest(
                booking, bank, provider, expectedSettlement, settlementTx.get().getPaidAt());
        finalRequest.setStatus(PaymentRequestStatus.CONFIRMED);
        finalRequest.setPaidAmount(expectedSettlement);
        finalRequest.setConfirmedAt(settlementTx.get().getPaidAt());
        finalRequest.setConfirmedBy(provider.getUser());
        finalRequest = paymentRequestRepository.save(finalRequest);
        settlementTx.get().setPaymentRequest(finalRequest);
    }

    private RentalPlan findDailyPlanForDemoVehicle() {
        Vehicle vehicle = vehicleRepository.findByLicensePlate(DevDataSeeder.VEHICLE_PLATE)
                .orElseThrow(() -> new IllegalStateException("Demo vehicle plate missing"));
        return rentalPlanRepository.findByVehicle_IdOrderByIdAsc(vehicle.getId()).stream()
                .filter(plan -> plan.getStatus() == PlanStatus.ACTIVE)
                .filter(plan -> plan.getPlanType() == PlanType.DAILY)
                .filter(plan -> Integer.valueOf(DAILY_SLOT_MINUTES).equals(plan.getSlotLengthMinutes()))
                .filter(plan -> plan.getSlotPrice() != null && plan.getSlotPrice().signum() > 0)
                .min(Comparator.comparing(RentalPlan::getId))
                .orElseThrow(() -> new IllegalStateException("Demo vehicle has no active daily plan"));
    }

    private boolean isAvailable(Renter renter, Vehicle vehicle, Instant startAt, Instant endAt) {
        Instant bufferedStart = startAt.minus(vehicle.getMaintenanceBufferMinutes(), ChronoUnit.MINUTES);
        Instant bufferedEnd = endAt.plus(vehicle.getMaintenanceBufferMinutes(), ChronoUnit.MINUTES);

        boolean vehicleBusy = vehicleScheduleRepository
                .existsByVehicle_IdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
                        vehicle.getId(), ScheduleStatus.ACTIVE, bufferedEnd, bufferedStart);
        boolean renterBusy = vehicleScheduleRepository
                .existsByBooking_Renter_IdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
                        renter.getId(), ScheduleStatus.ACTIVE, endAt, startAt);
        return !vehicleBusy && !renterBusy;
    }

    private BookingPolicySnapshot createSnapshot(
            Booking booking, RentalPlan plan, Provider provider, Instant createdAt) {
        BookingPolicySnapshot snapshot = new BookingPolicySnapshot();
        snapshot.setBooking(booking);
        snapshot.setSlotPrice(plan.getSlotPrice());
        snapshot.setSlotLengthMinutes(plan.getSlotLengthMinutes());
        snapshot.setPlanType(plan.getPlanType());
        snapshot.setDepositRatio(provider.getDepositRatio());
        snapshot.setAllowedOvertimeMinutes(plan.getAllowedOvertimeMinutes());
        snapshot.setOvertimeSurchargeRatio(plan.getOvertimeSurchargeRatio());
        snapshot.setWaitingFreeMinutes(plan.getWaitingFreeMinutes());
        snapshot.setWaitingRate(plan.getWaitingRate());
        snapshot.setWeekendSurchargeRatio(plan.getWeekendSurchargeRatio());
        snapshot.setFreeRadiusKm(plan.getFreeRadiusKm());
        snapshot.setOutOfRadiusRate(plan.getOutOfRadiusRate());
        snapshot.setHourlyToDailyThresholdMinutes(plan.getHourlyToDailyThresholdMinutes());
        snapshot.setCreatedAt(createdAt);
        return snapshot;
    }

    private BookingCharge createCharge(
            Booking booking,
            User createdBy,
            ChargeType type,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal appliedRate,
            BigDecimal amount,
            Instant createdAt,
            String description) {
        BookingCharge charge = new BookingCharge();
        charge.setBooking(booking);
        charge.setChargeType(type);
        charge.setDescription(description);
        charge.setQuantity(quantity);
        charge.setUnitPrice(unitPrice);
        charge.setAppliedRate(appliedRate);
        charge.setAmount(amount);
        charge.setCreatedBy(createdBy);
        charge.setWaived(false);
        charge.setCreatedAt(createdAt);
        return charge;
    }

    private PaymentTransaction createInboundPayment(
            Booking booking,
            BookingCharge charge,
            PaymentType type,
            BigDecimal amount,
            Instant paidAt,
            String gatewayRef,
            User confirmedBy) {
        PaymentTransaction payment = new PaymentTransaction();
        payment.setBooking(booking);
        payment.setCharge(charge);
        payment.setType(type);
        payment.setAmount(amount);
        payment.setAppliedAmount(amount);
        payment.setDirection(PaymentDirection.IN);
        payment.setMethod(PaymentMethod.BANK_TRANSFER);
        payment.setStatus(PaymentStatus.PAID);
        payment.setGatewayRef(gatewayRef);
        payment.setPaidAt(paidAt);
        payment.setCreatedAt(paidAt);
        payment.setUpdatedAt(paidAt);
        payment.setVerificationMethod(PaymentVerificationMethod.PROVIDER_CONFIRMED);
        payment.setConfirmedBy(confirmedBy);
        return payment;
    }

    private PaymentRequest createFinalPaymentRequest(
            Booking booking,
            ProviderBankAccount bank,
            Provider provider,
            BigDecimal amount,
            Instant createdAt) {
        PaymentRequest request = new PaymentRequest();
        request.setBooking(booking);
        request.setPaymentType(PaymentRequestType.FINAL_BALANCE);
        request.setAmount(amount);
        request.setPaidAmount(BigDecimal.ZERO);
        request.setStatus(PaymentRequestStatus.PENDING);
        request.setPaymentCode(paymentCodes.generatePaymentCode("VQ"));
        request.setTransferReference(booking.getBookingCode().replace("-", "") + "FINAL");
        request.setBankCodeSnapshot(bank.getBankCode());
        request.setBankNameSnapshot(bank.getBankName());
        request.setAccountNumberSnapshot(bank.getAccountNumber());
        request.setAccountHolderSnapshot(bank.getAccountHolderName());
        request.setReceivingBankAccount(bank);
        request.setCreatedAt(createdAt);
        request.setUpdatedAt(createdAt);
        request.setExpiresAt(null);
        return paymentRequestRepository.save(request);
    }

    private void attachAcceptedEvidence(
            PaymentRequest request,
            User renterUser,
            BigDecimal amount,
            Instant submittedAt,
            String bankRef,
            String imageUrl,
            String note) {
        if (request.getEvidences().stream().anyMatch(e -> imageUrl.equals(e.getProofImageUrl()))) {
            return;
        }
        PaymentEvidence evidence = new PaymentEvidence();
        evidence.setPaymentRequest(request);
        evidence.setProofImageUrl(imageUrl);
        evidence.setTransferredAmount(amount);
        evidence.setTransactionReference(bankRef);
        evidence.setTransferredAt(submittedAt);
        evidence.setNote(note);
        evidence.setSubmittedBy(renterUser);
        evidence.setStatus(PaymentEvidenceStatus.ACCEPTED);
        evidence.setReviewedAt(submittedAt.plus(15, ChronoUnit.MINUTES));
        evidence.setReviewedBy(request.getBooking().getVehicle().getProvider().getUser());
        evidence.setCreatedAt(submittedAt);
        request.getEvidences().add(evidence);
    }

    private void persistTripEvidence(
            Booking booking,
            Driver driver,
            User uploadedBy,
            TripEvidenceType type,
            Instant recordedAt,
            String imageUrl,
            String note) {
        TripEvidence evidence = new TripEvidence();
        evidence.setBooking(booking);
        evidence.setDriver(driver);
        evidence.setUploadedBy(uploadedBy);
        evidence.setEvidenceType(type);
        evidence.setImageUrl(imageUrl);
        evidence.setOriginalFileName(imageUrl.substring(imageUrl.lastIndexOf('/') + 1));
        evidence.setNote(note);
        evidence.setRecordedAt(recordedAt);
        evidence.setCreatedAt(recordedAt);
        entityManager.persist(evidence);
    }
}
