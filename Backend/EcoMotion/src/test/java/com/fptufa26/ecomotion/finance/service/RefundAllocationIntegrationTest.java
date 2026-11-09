package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.booking.enums.ChargeConfirmationStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.RejectPaymentRequest;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.RefundRequest;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.enums.RefundStatus;
import com.fptufa26.ecomotion.finance.repository.RefundRequestRepository;
import com.fptufa26.ecomotion.finance.service.RefundEventService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "logging.level.org.hibernate.SQL=WARN")
@ActiveProfiles("test")
class RefundAllocationIntegrationTest {

    @org.springframework.test.context.DynamicPropertySource
    static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
        if (!Boolean.getBoolean("tv5.postgres")) {
            return;
        }
        String url = System.getProperty("tv5.postgres.url");
        if (url == null || !url.startsWith("jdbc:postgresql://127.0.0.1:") || !url.endsWith("/tv5_test")) {
            throw new IllegalStateException("Dedicated local tv5_test database required");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "tv5_test");
        registry.add("spring.datasource.password", () -> "tv5-local-test-only");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired RefundEventService refunds;
    @Autowired RefundService refundService;
    @Autowired PaymentRequestService payments;
    @Autowired BookingService bookingService;
    @Autowired com.fptufa26.ecomotion.booking.service.ProviderBookingService providerBookings;
    @Autowired RefundRequestRepository requests;
    @Autowired BookingRepository bookings;
    TransactionTemplate tx;
    long bookingId;
    long renterUserId;
    long providerUserId;
    String bookingCode;

    @Test void refundProofDoesNotRequireBankReferenceOrNote() {
        tx.executeWithoutResult(s -> em.createQuery("select t from PaymentTransaction t where t.booking.id=:id", PaymentTransaction.class)
                .setParameter("id",bookingId).getResultList().forEach(t -> t.setMethod(PaymentMethod.BANK_TRANSFER)));
        User actor=tx.execute(s -> em.find(User.class,providerUserId));
        var refund=refunds.reserve(bookingId,"CANCEL:optional",new BigDecimal("300000"),"Test refund",actor).getFirst();
        Long transactionId=tx.execute(s -> em.find(RefundRequest.class,refund.getId()).getPaymentTransaction().getId());
        var proof=new org.springframework.mock.web.MockMultipartFile("file","refund.pdf","application/pdf","%PDF-1.4 test".getBytes());
        var result=refundService.completeRefund(providerUserId,transactionId,proof,null,null);
        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.bankReference()).isNull();
        assertThat(result.completedAt()).isNotNull();
    }

    @BeforeEach
    void fixture() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            User providerUser = user(Role.PROVIDER);
            Provider provider = new Provider();
            provider.setUser(providerUser);
            provider.setProviderType(ProviderType.INDIVIDUAL);
            provider.setDisplayName("Refund");
            provider.setHotline("0900000001");
            provider.setDepotAddress("Depot");
            provider.setCommissionRate(new BigDecimal("0.1"));
            provider.setDepositRatio(new BigDecimal("0.3"));
            provider.setStatus(ProviderStatus.APPROVED);
            em.persist(provider);
            providerUserId = providerUser.getId();
            User renterUser = user(Role.RENTER);
            Renter renter = new Renter();
            renter.setUser(renterUser);
            em.persist(renter);
            renterUserId = renterUser.getId();
            Vehicle vehicle = new Vehicle();
            vehicle.setProvider(provider);
            vehicle.setLicensePlate(UUID.randomUUID().toString().substring(0, 12));
            vehicle.setChassisNo(UUID.randomUUID().toString());
            vehicle.setEngineNo(UUID.randomUUID().toString());
            vehicle.setBrand("Test");
            vehicle.setModel("Refund");
            vehicle.setManufactureYear(2026);
            vehicle.setSeatCount(4);
            vehicle.setTransmission(Transmission.AUTOMATIC);
            vehicle.setColor("White");
            vehicle.setFuelType(FuelType.EV);
            vehicle.setMaintenanceBufferMinutes(0);
            vehicle.setStatus(VehicleStatus.APPROVED);
            em.persist(vehicle);
            RentalPlan plan = new RentalPlan();
            plan.setVehicle(vehicle);
            plan.setPlanType(PlanType.HOURLY);
            plan.setSlotLengthMinutes(60);
            plan.setSlotPrice(new BigDecimal("100000"));
            em.persist(plan);
            Booking booking = new Booking();
            booking.setBookingCode(UUID.randomUUID().toString().substring(0, 20).toUpperCase(Locale.ROOT));
            booking.setRenter(renter);
            booking.setVehicle(vehicle);
            booking.setPlan(plan);
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setPickupTime(Instant.now().plusSeconds(86400));
            booking.setPickupAddress("Pickup");
            booking.setPassengerCount(1);
            booking.setTotalAmount(new BigDecimal("1000000.00"));
            em.persist(booking);
            BookingPolicySnapshot snapshot = new BookingPolicySnapshot();
            snapshot.setBooking(booking);
            snapshot.setPlanType(PlanType.HOURLY);
            snapshot.setSlotLengthMinutes(60);
            snapshot.setSlotPrice(new BigDecimal("100000"));
            snapshot.setDepositRatio(new BigDecimal("0.30"));
            em.persist(snapshot);
            bookingId = booking.getId();
            bookingCode = booking.getBookingCode();
            paidIn(booking, new BigDecimal("300000.00"), new BigDecimal("300000.00"));
        });
    }

    @Test
    void waiveWhileCustomerStillOwesDoesNotRefundTheCharge() {
        long chargeId = tx.execute(status -> charge(ChargeType.PARKING, "100000.00", ChargeConfirmationStatus.ACCEPTED, false).getId());
        assertThat(refunds.openAllocated(bookingId, new BigDecimal("1100000.00"))).isEqualByComparingTo("0");
        tx.executeWithoutResult(status -> em.find(BookingCharge.class, chargeId).setWaived(true));
        User actor = tx.execute(status -> em.find(User.class, providerUserId));
        assertThat(refunds.reserve(bookingId, "WAIVE:" + chargeId, refunds.openAllocated(bookingId, new BigDecimal("1000000.00")),
                "Hoàn phần đã thu sau khi miễn phụ phí", actor)).isEmpty();
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)).isEmpty();
    }

    @Test
    void processingWaiveIsSubtractedFromLaterSettlement() {
        tx.executeWithoutResult(status -> em.find(Booking.class, bookingId).setTotalAmount(new BigDecimal("100000.00")));
        long chargeId = tx.execute(status -> charge(ChargeType.PARKING, "100000.00", ChargeConfirmationStatus.ACCEPTED, false).getId());
        tx.executeWithoutResult(status -> em.find(BookingCharge.class, chargeId).setWaived(true));
        User actor = tx.execute(status -> em.find(User.class, providerUserId));
        var waived = refunds.reserve(bookingId, "WAIVE:" + chargeId, new BigDecimal("100000.00"), "Hoàn sau miễn", actor);
        assertThat(waived).hasSize(1);
        assertThat(waived.get(0).getStatus()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(refunds.openAllocated(bookingId, new BigDecimal("100000.00"))).isEqualByComparingTo("100000");
        var again = refunds.reserve(bookingId, "WAIVE:" + chargeId, new BigDecimal("100000.00"), "Hoàn sau miễn", actor);
        assertThat(again).extracting(RefundRequest::getId).containsExactly(waived.get(0).getId());
        assertThat(refunds.reserve(bookingId, "SETTLE:" + bookingId, refunds.openAllocated(bookingId, new BigDecimal("100000.00")),
                "Quyết toán", actor)).extracting(RefundRequest::getAmount).containsExactly(new BigDecimal("100000.00") /* remaining after waive */);
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)).extracting(RefundRequest::getAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactlyInAnyOrder(new BigDecimal("100000.00"), new BigDecimal("100000.00"));
    }

    @Test
    void excessThenProviderCancelDoesNotRefundTheExcessTwice() {
        long sourceId = tx.execute(status -> {
            PaymentTransaction extra = paidIn(em.find(Booking.class, bookingId), new BigDecimal("350000.00"), new BigDecimal("300000.00"));
            return extra.getId();
        });
        User actor = tx.execute(status -> em.find(User.class, renterUserId));
        var excess = refunds.reserveOnTransaction(bookingId, "EXCESS:" + sourceId, new BigDecimal("50000.00"),
                "Hoàn tiền chuyển thừa", actor, sourceId);
        assertThat(excess).singleElement().extracting(RefundRequest::getAmount).isEqualTo(new BigDecimal("50000.00"));
        var cancel = refunds.reserve(bookingId, "PROVIDER_CANCEL:" + bookingId, refunds.remainingReceived(bookingId),
                "Hoàn 100% do không thể cung cấp xe", actor);
        assertThat(cancel.stream().map(RefundRequest::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("600000.00");
        assertThatThrownBy(() -> refunds.reserve(bookingId, "PROVIDER_CANCEL:" + bookingId, new BigDecimal("1.00"),
                "Khác số tiền", actor)).isInstanceOf(ConflictException.class);
    }

    @Test
    void sameChargeRequestWithADifferentPayloadIsRejected() {
        tx.executeWithoutResult(status -> {
            Booking booking = em.find(Booking.class, bookingId);
            booking.setStatus(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
            booking.setActualEndAt(Instant.now());
        });
        var first = new com.fptufa26.ecomotion.booking.dto.request.AddChargeRequest(
                ChargeType.PARKING, "Parking at return", 1, new BigDecimal("20000"), BigDecimal.ONE,
                new BigDecimal("20000"), "https://example.test/park.jpg", "park-1");
        var created = providerBookings.addCharge(providerUserId, bookingCode, first);
        assertThat(providerBookings.addCharge(providerUserId, bookingCode, first).id()).isEqualTo(created.id());
        var changed = new com.fptufa26.ecomotion.booking.dto.request.AddChargeRequest(
                ChargeType.TOLL, "Parking at return", 1, new BigDecimal("20000"), BigDecimal.ONE,
                new BigDecimal("20000"), "https://example.test/park.jpg", "park-1");
        assertThatThrownBy(() -> providerBookings.addCharge(providerUserId, bookingCode, changed)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> providerBookings.addCharge(providerUserId, bookingCode,
                new com.fptufa26.ecomotion.booking.dto.request.AddChargeRequest(
                        ChargeType.WEEKEND, "Weekend after trip should stay in the plan price", 1,
                        new BigDecimal("20000"), BigDecimal.ONE, new BigDecimal("20000"),
                        "https://example.test/park.jpg", "weekend-1"))).isInstanceOf(ConflictException.class);
    }

    @Test
    void pendingAndWeekendChargesDoNotIncreaseTheBalance() {
        tx.executeWithoutResult(status -> {
            charge(ChargeType.PARKING, "50000.00", ChargeConfirmationStatus.PENDING, false);
            charge(ChargeType.WEEKEND, "80000.00", ChargeConfirmationStatus.ACCEPTED, false);
        });
        assertThat(payments.calculateRemainingAmount(bookingId)).isEqualByComparingTo("700000.00");
    }

    @Test
    void cancelAfterConfirmedDepositKeepsThePaymentAndBlocksAnotherConfirmation() {
        long requestId = tx.execute(status -> depositRequest(PaymentRequestStatus.CONFIRMED, new BigDecimal("300000.00")).getId());
        bookingService.cancel(renterUserId, bookingCode);
        PaymentRequestStatus paymentStatus = tx.execute(status -> em.find(PaymentRequest.class, requestId).getStatus());
        BookingStatus bookingStatus = tx.execute(status -> em.find(Booking.class, bookingId).getStatus());
        assertThat(paymentStatus).isEqualTo(PaymentRequestStatus.CONFIRMED);
        assertThat(bookingStatus).isEqualTo(BookingStatus.CANCELLATION_REVIEW);
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId))
                .extracting(RefundRequest::getSourceEventId)
                .anyMatch(id -> id.startsWith("CANCEL:" + bookingId));
        assertThatThrownBy(() -> payments.confirmPayment(requestId, providerUserId,
                new ConfirmPaymentRequest(new BigDecimal("300000"), "AGAIN", null)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> payments.rejectPayment(requestId, providerUserId, new RejectPaymentRequest("Không từ chối tiền đã thu")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelAfterUnconfirmedProofMovesTheRequestToReview() {
        long requestId = tx.execute(status -> {
            em.createQuery("select t from PaymentTransaction t where t.booking.id = :id", PaymentTransaction.class)
                    .setParameter("id", bookingId)
                    .getResultList()
                    .forEach(row -> row.setStatus(PaymentStatus.FAILED));
            return depositRequest(PaymentRequestStatus.PROOF_SUBMITTED, BigDecimal.ZERO).getId();
        });
        bookingService.cancel(renterUserId, bookingCode);
        PaymentRequestStatus paymentStatus = tx.execute(status -> em.find(PaymentRequest.class, requestId).getStatus());
        assertThat(paymentStatus).isEqualTo(PaymentRequestStatus.REVIEW_REQUIRED);
    }

    @Test
    void payoutDoesNotZeroRefundableAndFailedNeedsAVerifiedResponse() {
        tx.executeWithoutResult(status -> {
            Booking booking = em.find(Booking.class, bookingId);
            booking.setTotalAmount(new BigDecimal("100000.00"));
            PaymentTransaction payout = new PaymentTransaction();
            payout.setBooking(booking);
            payout.setType(PaymentType.PAYOUT);
            payout.setDirection(PaymentDirection.OUT);
            payout.setMethod(PaymentMethod.BANK_TRANSFER);
            payout.setAmount(new BigDecimal("90000.00"));
            payout.setStatus(PaymentStatus.PENDING);
            em.persist(payout);
        });
        assertThat(refundService.refundableAmount(renterUserId, bookingCode)).isEqualByComparingTo("300000.00");
        User actor = tx.execute(status -> em.find(User.class, providerUserId));
        RefundRequest held = refunds.reserve(bookingId, "SETTLE:" + bookingId, new BigDecimal("200000.00"), "Sau chi", actor).get(0);
        assertThat(held.getStatus()).isEqualTo(RefundStatus.UNDER_REVIEW);
        tx.executeWithoutResult(status -> {
            RefundRequest row = em.find(RefundRequest.class, held.getId());
            row.setStatus(RefundStatus.FAILED);
            row.setVnpRequestId("abc123");
        });
        assertThatThrownBy(() -> refunds.releaseForFollowUp(held.getId(), actor, false))
                .isInstanceOf(ConflictException.class);
        tx.executeWithoutResult(status -> {
            RefundRequest row = em.find(RefundRequest.class, held.getId());
            row.setVnpResponseCode("97");
        });
        assertThat(refunds.releaseForFollowUp(held.getId(), actor, true).getStatus()).isEqualTo(RefundStatus.PROCESSING);
    }

    private User user(Role role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@refund.test");
        user.setPasswordHash("test-only");
        user.setFullName("Refund");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        em.persist(user);
        return user;
    }

    private PaymentRequest depositRequest(PaymentRequestStatus status, BigDecimal paidAmount) {
        PaymentRequest request = new PaymentRequest();
        request.setBooking(em.find(Booking.class, bookingId));
        request.setPaymentType(PaymentRequestType.DEPOSIT);
        request.setAmount(new BigDecimal("300000.00"));
        request.setPaidAmount(paidAmount);
        request.setTransferReference("DEP-" + UUID.randomUUID());
        request.setBankCodeSnapshot("VCB");
        request.setBankNameSnapshot("Vietcombank");
        request.setAccountNumberSnapshot("0123456789");
        request.setAccountHolderSnapshot("REFUND TEST");
        request.setStatus(status);
        em.persist(request);
        return request;
    }

    private PaymentTransaction paidIn(Booking booking, BigDecimal amount, BigDecimal applied) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setBooking(booking);
        tx.setType(PaymentType.DEPOSIT_PAY);
        tx.setDirection(PaymentDirection.IN);
        tx.setMethod(PaymentMethod.BANK_TRANSFER);
        tx.setAmount(amount);
        tx.setAppliedAmount(applied);
        tx.setStatus(PaymentStatus.PAID);
        tx.setPaidAt(Instant.now());
        em.persist(tx);
        return tx;
    }

    private BookingCharge charge(ChargeType type, String amount, ChargeConfirmationStatus confirmation, boolean waived) {
        BookingCharge charge = new BookingCharge();
        charge.setBooking(em.find(Booking.class, bookingId));
        charge.setChargeType(type);
        charge.setDescription(type == ChargeType.WEEKEND ? "Weekend already inside the rental price" : "Recorded extra charge");
        charge.setQuantity(type == ChargeType.WEEKEND ? null : 1);
        charge.setUnitPrice(new BigDecimal(amount));
        charge.setAmount(new BigDecimal(amount));
        charge.setCreatedBy(em.find(User.class, providerUserId));
        charge.setWaived(waived);
        charge.setConfirmationStatus(confirmation);
        charge.setEvidenceUrl("https://example.test/evidence.jpg");
        em.persist(charge);
        return charge;
    }
}
