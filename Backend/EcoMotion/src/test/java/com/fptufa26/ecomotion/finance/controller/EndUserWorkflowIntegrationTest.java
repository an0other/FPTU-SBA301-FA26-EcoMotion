package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.common.storage.service.FileStorageService;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.fptufa26.ecomotion.booking.entity.*;
import com.fptufa26.ecomotion.booking.enums.*;
import com.fptufa26.ecomotion.booking.repository.*;
import com.fptufa26.ecomotion.finance.dto.request.*;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.*;
import com.fptufa26.ecomotion.finance.repository.RefundRequestRepository;
import com.fptufa26.ecomotion.finance.service.*;
import com.fptufa26.ecomotion.user.entity.*;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.vehicle.entity.*;
import com.fptufa26.ecomotion.vehicle.enums.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"app.finance.sandbox-payout-enabled=true", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndUserWorkflowIntegrationTest {
    @MockitoBean
    com.fptufa26.ecomotion.booking.service.MapGeocodingService geocoding;
    @MockitoBean
    com.fptufa26.ecomotion.notification.external.service.ExternalNotificationDispatcherService external;
    @MockitoBean
    FileStorageService storage;
    @DynamicPropertySource
    static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
        if (!Boolean.getBoolean("tv5.workflow.postgres")) return;
        String url = System.getProperty("tv5.postgres.url");
        if (url == null || !url.startsWith("jdbc:postgresql://127.0.0.1:") || !url.endsWith("/tv5_test"))
            throw new IllegalStateException("Dedicated local tv5_test database required");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "tv5_test");
        registry.add("spring.datasource.password", () -> "tv5-local-test-only");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired com.fptufa26.ecomotion.dispute.service.DisputeService disputes;
    @MockitoBean com.fptufa26.ecomotion.location.service.implement.VietmapClient vietmap;

    @Test void depositDisputeIsAvailableDuringCancellationReviewAndDuplicateReviewIsBlocked() {
        tx.executeWithoutResult(s -> em.find(Booking.class, bookingId).setStatus(BookingStatus.CANCELLATION_REVIEW));
        var opened = renterBookings.openDispute(renterId, code(), new com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest(
                com.fptufa26.ecomotion.dispute.enums.DisputeCategory.DEPOSIT_REFUND, (Long) null, "Missing deposit refund"));
        disputes.resolveDispute(opened.disputeId(), adminId, com.fptufa26.ecomotion.dispute.enums.DisputeStatus.IN_REVIEW, null, "Reviewing bank evidence");
        assertThatThrownBy(() -> renterBookings.openDispute(renterId, code(), new com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest(
                com.fptufa26.ecomotion.dispute.enums.DisputeCategory.DEPOSIT_REFUND, (Long) null, "Duplicate review")))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
    }

    @Test void contradictoryChargeDecisionCannotWaiveMoney() {
        ready();
        var opened = tx.execute(s -> {
            var b = em.find(Booking.class, bookingId);
            var charge = new BookingCharge(); charge.setBooking(b); charge.setChargeType(ChargeType.PARKING);
            charge.setDescription("Disputed parking"); charge.setQuantity(1); charge.setUnitPrice(new BigDecimal("20000"));
            charge.setAmount(new BigDecimal("20000")); charge.setCreatedBy(b.getRenter().getUser()); em.persist(charge);
            return disputes.open(b, b.getRenter().getUser(), com.fptufa26.ecomotion.dispute.enums.DisputeCategory.CHARGE, charge);
        });
        assertThatThrownBy(() -> disputes.resolveDispute(opened.disputeId(), adminId,
                com.fptufa26.ecomotion.dispute.enums.DisputeStatus.REJECTED,
                com.fptufa26.ecomotion.dispute.enums.DisputeResolutionDecision.WAIVE_CHARGE, "Contradictory ruling"))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
    }

    @Test void adminDepositAwardCreatesOnlyMissingRefundAndExposesRulingToParticipants() throws Exception {
        ready();
        tx.executeWithoutResult(s -> {
            var b = em.find(Booking.class, bookingId); b.setPickupTime(Instant.now().plusSeconds(3600));
            ledger.findByBooking_Id(bookingId).forEach(t -> { t.setMethod(PaymentMethod.BANK_TRANSFER); t.setAmount(new BigDecimal("540000")); });
        });
        renterBookings.cancel(renterId, code());
        var opened = renterBookings.openDispute(renterId, code(), new com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest(
                com.fptufa26.ecomotion.dispute.enums.DisputeCategory.DEPOSIT_REFUND, (Long) null, "Request full deposit refund"));
        String body = "{\"status\":\"RESOLVED\",\"resolution\":\"Approve full deposit refund\",\"refundAwardTotal\":540000}";
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/operations/disputes/" + opened.disputeId() + "/resolve")
                    .header("Authorization", "Bearer " + tx.execute(s -> jwt.generateAccessToken(em.find(User.class, adminId))))
                    .contentType("application/json").content(body))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        }
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)).hasSize(2);
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId).stream().map(r -> r.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("540000");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/renter/bookings/" + code() + "/workflow")
                .header("Authorization", "Bearer " + tx.execute(s -> jwt.generateAccessToken(em.find(User.class, renterId)))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.disputes[0].resolution").value("Approve full deposit refund"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.disputes[0].financialStatus").value("AWAITING_REFUND"));
    }

    long cancelledDepositDispute() {
        ready();
        tx.executeWithoutResult(s -> {
            var b = em.find(Booking.class, bookingId); b.setPickupTime(Instant.now().plusSeconds(3600));
            ledger.findByBooking_Id(bookingId).forEach(t -> { t.setMethod(PaymentMethod.BANK_TRANSFER); t.setAmount(new BigDecimal("540000")); t.setAppliedAmount(new BigDecimal("540000")); });
        });
        renterBookings.cancel(renterId, code());
        return renterBookings.openDispute(renterId, code(), new com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest(
                com.fptufa26.ecomotion.dispute.enums.DisputeCategory.DEPOSIT_REFUND, (Long) null, "Please review refund")).disputeId();
    }

    @Test void depositAwardBoundsAndMissingEvidenceRollBackRulingAndMoney() {
        long id = cancelledDepositDispute();
        for (String invalid : List.of("0", "540001", "269999", "1.123")) {
            assertThatThrownBy(() -> disputes.resolveDispute(id, adminId,
                    com.fptufa26.ecomotion.dispute.enums.DisputeStatus.RESOLVED, null, "Refund ruling", new BigDecimal(invalid)))
                    .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
            assertThat(disputes.getDisputeById(id).orElseThrow().getStatus()).isEqualTo(com.fptufa26.ecomotion.dispute.enums.DisputeStatus.OPEN);
            assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)).hasSize(1);
        }
        tx.executeWithoutResult(s -> {
            var refund = requests.findByBooking_IdOrderByCreatedAtDesc(bookingId).getFirst(); refund.setSourcePaymentTransaction(null);
        });
        assertThatThrownBy(() -> disputes.resolveDispute(id, adminId,
                com.fptufa26.ecomotion.dispute.enums.DisputeStatus.RESOLVED, null, "Refund ruling", new BigDecimal("540000")))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
        assertThat(disputes.getDisputeById(id).orElseThrow().getResolvedAt()).isNull();
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)).hasSize(1);
    }

    @Test void existingRefundCommitmentIsLinkedWithoutNewMoneyAndParticipantsAreAuthorized() {
        long id = cancelledDepositDispute();
        disputes.resolveDispute(id, adminId, com.fptufa26.ecomotion.dispute.enums.DisputeStatus.RESOLVED, null, "Existing refund is correct", new BigDecimal("270000"));
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)).hasSize(1);
        var renterView = workflow.getWorkflow(renterId, code()).disputes().getFirst();
        assertThat(renterView.refundRequestIds()).hasSize(1);
        assertThat(renterView.resolvedAt()).isNotNull();
        assertThat(renterView.handledByName()).isNotBlank();
        assertThat(workflow.getWorkflow(providerId, code()).disputes().getFirst().resolution()).isEqualTo(renterView.resolution());
        long foreign = tx.execute(s -> user(Role.RENTER).getId());
        assertThatThrownBy(() -> workflow.getWorkflow(foreign, code())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> disputes.resolveDispute(id, adminId, com.fptufa26.ecomotion.dispute.enums.DisputeStatus.RESOLVED, null, "Changed award", new BigDecimal("540000")))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
    }

    @Test void concurrentComplaintCreationCreatesOnlyOneActiveReview() throws Exception {
        tx.executeWithoutResult(s -> em.find(Booking.class, bookingId).setStatus(BookingStatus.CANCELLATION_REVIEW));
        Runnable submit = () -> renterBookings.openDispute(renterId, code(), new com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest(
                com.fptufa26.ecomotion.dispute.enums.DisputeCategory.DEPOSIT_REFUND, (Long) null, "Concurrent complaint"));
        assertThat(race(submit, submit)).containsExactlyInAnyOrder(true, false);
        assertThat(disputes.getDisputesByBookingId(bookingId)).hasSize(1);
    }

    @Test void twoAdminsCannotCreateDuplicateDepositAwards() throws Exception {
        long id = cancelledDepositDispute();
        long secondAdmin = tx.execute(s -> user(Role.ADMIN).getId());
        Runnable first = () -> disputes.resolveDispute(id, adminId, com.fptufa26.ecomotion.dispute.enums.DisputeStatus.RESOLVED, null, "Award full refund", new BigDecimal("540000"));
        Runnable second = () -> disputes.resolveDispute(id, secondAdmin, com.fptufa26.ecomotion.dispute.enums.DisputeStatus.RESOLVED, null, "Award full refund", new BigDecimal("540000"));
        assertThat(race(first, second)).containsOnly(true);
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)).hasSize(2);
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId).stream().map(r -> r.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("540000");
    }

    @Test void reviewAndRejectionDoNotCreateRefundsOrFinancialDecisions() {
        long id = cancelledDepositDispute();
        disputes.resolveDispute(id, adminId, com.fptufa26.ecomotion.dispute.enums.DisputeStatus.IN_REVIEW, null, "Reviewing documents");
        assertThat(disputes.getDisputeById(id).orElseThrow().getResolvedAt()).isNull();
        assertThatThrownBy(() -> disputes.resolveDispute(id, adminId, com.fptufa26.ecomotion.dispute.enums.DisputeStatus.OPEN, null, "Back to open"))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
        disputes.resolveDispute(id, adminId, com.fptufa26.ecomotion.dispute.enums.DisputeStatus.REJECTED, null, "Cancellation policy upheld");
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)).hasSize(1);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLATION_REVIEW);
    }

    @Test void paidFinalRequestIsPreservedAndOnlyUnblockedAfterEveryReviewCloses() {
        ready();
        long[] ids = tx.execute(s -> {
            Booking b = em.find(Booking.class, bookingId); b.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT);
            var charge = new BookingCharge(); charge.setBooking(b); charge.setChargeType(ChargeType.PARKING);
            charge.setDescription("Parking"); charge.setQuantity(1); charge.setUnitPrice(new BigDecimal("20000"));
            charge.setAmount(new BigDecimal("20000")); charge.setCreatedBy(b.getRenter().getUser()); em.persist(charge);
            var pr = new PaymentRequest(); pr.setBooking(b); pr.setPaymentType(PaymentRequestType.FINAL_BALANCE);
            pr.setAmount(new BigDecimal("220000")); pr.setPaidAmount(new BigDecimal("10000"));
            pr.setTransferReference(UUID.randomUUID().toString()); pr.setBankCodeSnapshot("VCB"); pr.setBankNameSnapshot("Bank");
            pr.setAccountNumberSnapshot("123456789"); pr.setAccountHolderSnapshot("Owner"); pr.setStatus(PaymentRequestStatus.REVIEW_REQUIRED);
            pr.setExpiresAt(Instant.now().minusSeconds(86400)); em.persist(pr);
            var paid = incoming(b, PaymentMethod.BANK_TRANSFER, new BigDecimal("10000")); paid.setType(PaymentType.SETTLEMENT); paid.setPaymentRequest(pr);
            em.flush();
            var fee = disputes.open(b, b.getRenter().getUser(), com.fptufa26.ecomotion.dispute.enums.DisputeCategory.CHARGE, charge);
            var quality = disputes.open(b, b.getRenter().getUser(), com.fptufa26.ecomotion.dispute.enums.DisputeCategory.SERVICE_QUALITY, null);
            return new long[]{fee.disputeId(), quality.disputeId(), pr.getId()};
        });
        disputes.resolveDispute(ids[0], adminId, com.fptufa26.ecomotion.dispute.enums.DisputeStatus.REJECTED,
                com.fptufa26.ecomotion.dispute.enums.DisputeResolutionDecision.KEEP_CHARGE, "Parking charge upheld");
        assertThat(tx.<PaymentRequestStatus>execute(s -> em.find(PaymentRequest.class, ids[2]).getStatus())).isEqualTo(PaymentRequestStatus.REVIEW_REQUIRED);
        disputes.resolveDispute(ids[1], adminId, com.fptufa26.ecomotion.dispute.enums.DisputeStatus.RESOLVED, null, "Service explanation accepted");
        tx.executeWithoutResult(s -> {
            var pr = em.find(PaymentRequest.class, ids[2]); assertThat(pr.getPaidAmount()).isEqualByComparingTo("10000");
            assertThat(pr.getStatus()).isEqualTo(PaymentRequestStatus.PARTIALLY_PAID);
            assertThat(pr.getExpiresAt()).isAfter(Instant.now().plusSeconds(47 * 3600));
        });
    }

    @Autowired com.fptufa26.ecomotion.notification.internal.service.NotificationStreamService notificationStream;

    @Test void disputeEventsAreDeliveredOnlyAfterCommit() throws Exception {
        tx.executeWithoutResult(s -> em.find(Booking.class, bookingId).setStatus(BookingStatus.CANCELLATION_REVIEW));
        var emitter = org.mockito.Mockito.mock(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class);
        notificationStream.registerEmitter(renterId, emitter);
        try {
            tx.executeWithoutResult(s -> {
                disputes.open(em.find(Booking.class, bookingId), em.find(User.class, renterId),
                        com.fptufa26.ecomotion.dispute.enums.DisputeCategory.DEPOSIT_REFUND, null, "Rollback complaint", List.of());
                org.mockito.Mockito.verifyNoInteractions(emitter);
                s.setRollbackOnly();
            });
            org.mockito.Mockito.verifyNoInteractions(emitter);
            renterBookings.openDispute(renterId, code(), new com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest(
                    com.fptufa26.ecomotion.dispute.enums.DisputeCategory.DEPOSIT_REFUND, (Long) null, "Committed complaint"));
            org.mockito.Mockito.verify(emitter).send(org.mockito.ArgumentMatchers.any(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder.class));
        } finally { notificationStream.removeEmitter(renterId, emitter); }
    }

    @Test void depositRulingQueuesGatewayRefundInsteadOfSendingMoney() {
        ready();
        tx.executeWithoutResult(s -> em.find(Booking.class, bookingId).setPickupTime(Instant.now().plusSeconds(3600)));
        renterBookings.cancel(renterId, code());
        long id = renterBookings.openDispute(renterId, code(), new com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest(
                com.fptufa26.ecomotion.dispute.enums.DisputeCategory.DEPOSIT_REFUND, (Long) null, "Gateway refund review")).disputeId();
        disputes.resolveDispute(id, adminId, com.fptufa26.ecomotion.dispute.enums.DisputeStatus.RESOLVED, null, "Award full deposit", new BigDecimal("100000"));
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId).stream()
                .filter(r -> r.getSourceEventId().startsWith("DISPUTE_DEPOSIT:")))
                .allMatch(r -> r.getStatus() == RefundStatus.UNDER_REVIEW && r.getVnpRequestId() == null);
    }

    @Test void pickupFeeUsesRoadMetersAndRoundsOnlyBeyondFreeRadius() {
        long[] ids=tx.execute(s -> {
            var b=em.find(Booking.class,bookingId); var p=b.getVehicle().getProvider();
            p.setDepotLatitude(new BigDecimal("10.8")); p.setDepotLongitude(new BigDecimal("106.7"));
            b.getPlan().setFreeRadiusKm(3); b.getPlan().setOutOfRadiusRate(new BigDecimal("10000"));
            return new long[]{b.getVehicle().getId(),b.getPlan().getId()};
        });
        var request=new com.fptufa26.ecomotion.booking.dto.request.PickupQuoteRequest(ids[0],ids[1],new BigDecimal("10.81"),new BigDecimal("106.71"));
        for(double meters:new double[]{3000,3000.1,4000,4000.1}) {
            org.mockito.Mockito.when(vietmap.route(10.8,106.7,10.81,106.71)).thenReturn(
                    new com.fptufa26.ecomotion.location.dto.response.RoadRouteResponse(meters,1200,
                            List.of(List.of(106.7,10.8),List.of(106.71,10.81)),"VIETMAP_CAR",Instant.now()));
            var quote=renterBookings.quotePickup(request);
            assertThat(quote.pickupFee()).isEqualByComparingTo(new BigDecimal(meters==3000?"0":meters<=4000?"10000":"20000"));
            assertThat(quote.durationSeconds()).isEqualTo(1200); assertThat(quote.distanceSource()).isEqualTo("VIETMAP_CAR");
        }
    }

    @Test void missingMapKeyDoesNotInventStraightLineDistance() {
        assertThatThrownBy(() -> new com.fptufa26.ecomotion.location.service.implement.VietmapClient(new tools.jackson.databind.ObjectMapper(),"http://127.0.0.1:1","",java.time.Duration.ofSeconds(1)).route(10.7,106.6,10.8,106.7))
                .isInstanceOf(com.fptufa26.ecomotion.location.exception.MapLocationUnavailableException.class);
    }

    @Autowired com.fptufa26.ecomotion.booking.service.implement.TripEndConfirmationService endWindows;
    @Autowired com.fptufa26.ecomotion.booking.repository.TripEndConfirmationRepository endHistories;

    void reportTripEnd() {
        long[] driver=ready();optimization.manualAssign(bookingId,driver[0],driver[2]);trips.confirmTrip(driver[1],code());
        trips.arriveAtPickup(driver[1],code());trips.startTrip(driver[1],code());trips.finishTrip(driver[1],code());
    }
    @Test void driverEndFinalizesTripImmediatelyWithoutRenterConfirmation() {
        reportTripEnd();
        var b=bookings.findById(bookingId).orElseThrow();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
        assertThat(b.getActualEndAt()).isEqualTo(b.getDriverEndConfirmedAt());
        assertThat(b.getRenterConfirmedAt()).isNull();
        assertThat(b.getEndConfirmationMethod()).isEqualTo("DRIVER");
        var h=endHistories.findFirstByBooking_IdOrderByIdDesc(bookingId).orElseThrow();
        assertThat(h.getState()).isEqualTo("CONFIRMED");
        assertThat(h.getConfirmationMethod()).isEqualTo("DRIVER");
        assertThat(endHistories.findByBooking_IdOrderByIdDesc(bookingId)).hasSize(1);
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)).isEmpty();
    }
    @Test void unassignedDriverCannotStartOrEndTrip() {
        long[] assigned = ready(); long[] other = ready();
        optimization.manualAssign(bookingId, assigned[0], assigned[2]);
        trips.confirmTrip(assigned[1], code()); trips.arriveAtPickup(assigned[1], code());
        assertThatThrownBy(() -> trips.startTrip(other[1], code())).isInstanceOf(RuntimeException.class);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.DRIVER_ARRIVED);
        trips.startTrip(assigned[1], code());
        assertThatThrownBy(() -> trips.finishTrip(other[1], code())).isInstanceOf(RuntimeException.class);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
    }

    @Test void driverCannotStartOrEndTripTwice() {
        long[] driver=ready();optimization.manualAssign(bookingId,driver[0],driver[2]);trips.confirmTrip(driver[1],code());
        trips.arriveAtPickup(driver[1],code());trips.startTrip(driver[1],code());
        var started=bookings.findById(bookingId).orElseThrow().getActualStartAt();
        assertThatThrownBy(() -> trips.startTrip(driver[1],code())).isInstanceOf(RuntimeException.class);
        assertThat(bookings.findById(bookingId).orElseThrow().getActualStartAt()).isEqualTo(started);
        trips.finishTrip(driver[1],code());
        var ended=bookings.findById(bookingId).orElseThrow().getActualEndAt();
        assertThatThrownBy(() -> trips.finishTrip(driver[1],code())).isInstanceOf(RuntimeException.class);
        assertThat(bookings.findById(bookingId).orElseThrow().getActualEndAt()).isEqualTo(ended);
    }

    @Test void driverRejectsThenNextDriverAcceptsAndFeedbackIsPrivateToProvider() {
        long[] first = ready(); long[] second = ready();
        optimization.manualAssign(bookingId, first[0], first[2]);
        trips.rejectTrip(first[1], code(), new com.fptufa26.ecomotion.booking.dto.request.DriverRejectRequest(
                DriverRejectReason.HEALTH_ISSUE, "Cannot drive today"));
        assertThat(workflow.getWorkflow(providerId, code()).driverFeedback()).hasSize(1);
        assertThat(workflow.getWorkflow(renterId, code()).driverFeedback()).isEmpty();
        Booking latest = bookings.findById(bookingId).orElseThrow();
        if (latest.getStatus() == BookingStatus.CONFIRMED) optimization.manualAssign(bookingId, second[0], second[2]);
        assertThat(tx.<Long>execute(s -> em.find(Booking.class, bookingId).getDriver().getId())).isEqualTo(second[0]);
        trips.confirmTrip(second[1], code());
        assertThat(workflow.getWorkflow(renterId, code()).driverStatus()).isEqualTo("ACCEPTED");
    }

    @Test void disputedSnapshotChargeBlocksSettlementThenWaiverAllowsCompletion() {
        long[] driver = ready(); optimization.manualAssign(bookingId, driver[0], driver[2]);
        trips.confirmTrip(driver[1], code()); trips.arriveAtPickup(driver[1], code());
        trips.startTrip(driver[1], code());
        trips.finishTrip(driver[1], code());
        var charge = providerBookings.addCharge(providerId, code(), new com.fptufa26.ecomotion.booking.dto.request.AddChargeRequest(
                ChargeType.PARKING, "Parking at the return point for this trip", 1, new BigDecimal("20000"), BigDecimal.ONE,
                new BigDecimal("20000"), "https://example.test/waiting.jpg", "park-dispute-1"));
        assertThat(charge.amount()).isEqualByComparingTo("20000");
        assertThat(charge.confirmationStatus()).isEqualTo(com.fptufa26.ecomotion.booking.enums.ChargeConfirmationStatus.PENDING);
        var dispute = renterBookings.openDispute(renterId, code(), new com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest(
                com.fptufa26.ecomotion.dispute.enums.DisputeCategory.CHARGE, charge.id(), null,
                "Passenger disputes the recorded waiting time", List.of("https://example.test/passenger.jpg"), null));
        assertThat(workflow.getWorkflow(renterId, code()).settlementStatus()).isEqualTo("DISPUTED");
        assertThatThrownBy(() -> providerBookings.finalizeSettlement(providerId, code())).isInstanceOf(RuntimeException.class);
        disputes.resolveDispute(dispute.disputeId(), adminId, com.fptufa26.ecomotion.dispute.enums.DisputeStatus.RESOLVED,
                com.fptufa26.ecomotion.dispute.enums.DisputeResolutionDecision.WAIVE_CHARGE, "Evidence confirms no waiting charge");
        providerBookings.finalizeSettlement(providerId, code());
        assertThat(workflow.getWorkflow(renterId, code()).settlementStatus()).isEqualTo("SETTLED");
    }
    @Autowired com.fptufa26.ecomotion.booking.service.BookingWorkflowService workflow;
    @Autowired com.fptufa26.ecomotion.booking.service.BookingHoldExpiryService expiry;

    String code() { return bookings.findById(bookingId).orElseThrow().getBookingCode(); }

    @Test void pendingApprovalBranchCreatesHoldAndDepositThenGatewayConfirmsWithoutAutoAssigning() {
        tx.executeWithoutResult(s -> {
            Booking b = em.find(Booking.class, bookingId); b.setStatus(BookingStatus.PENDING);
            ledger.deleteAll(ledger.findByBooking_Id(bookingId));
        });
        providerBookings.acceptBooking(providerId, code());
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.AWAITING_DEPOSIT);
        assertThat(schedule.hasUnexpiredHold(bookingId, Instant.now())).isTrue();
        var deposit = vnpay.createPaymentUrl(renterId, new CreateVnpayPaymentUrlRequest(code(), "DEPOSIT", null, "vn"), "127.0.0.1");
        assertThat(vnpay.processIpn(signed(deposit)).rspCode()).isEqualTo("00");
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(workflow.getWorkflow(renterId, code()).driverStatus()).isEqualTo("NOT_ASSIGNED");
    }

    @Test void yearlyMonthlyInstallmentCanBePaidWithoutCompletingTheBooking() {
        Long[] requestId = new Long[1];
        tx.executeWithoutResult(status -> {
            Booking booking = em.find(Booking.class, bookingId);
            Instant startAt = Instant.now().minusSeconds(30L * 24 * 60 * 60);
            booking.getPlan().setPlanType(PlanType.YEARLY);
            booking.getPlan().setSlotLengthMinutes(525600);
            booking.setStatus(BookingStatus.IN_PROGRESS);
            booking.setPickupTime(startAt);
            booking.setActualStartAt(startAt);
            booking.setPlannedEndAt(startAt.plusSeconds(365L * 24 * 60 * 60));
            booking.setTotalAmount(new BigDecimal("1200000"));

            BookingPolicySnapshot snapshot = snapshots.findByBooking_Id(bookingId).orElseThrow();
            snapshot.setPlanType(PlanType.YEARLY);
            snapshot.setSlotLengthMinutes(525600);
            snapshot.setDepositRatio(null);

            PaymentTransaction previousInstallment = new PaymentTransaction();
            previousInstallment.setBooking(booking);
            previousInstallment.setType(PaymentType.SETTLEMENT);
            previousInstallment.setDirection(PaymentDirection.IN);
            previousInstallment.setMethod(PaymentMethod.BANK_TRANSFER);
            previousInstallment.setAmount(new BigDecimal("100000"));
            previousInstallment.setStatus(PaymentStatus.PAID);
            previousInstallment.setPaidAt(startAt.plusSeconds(1));
            em.persist(previousInstallment);

            PaymentRequest request = new PaymentRequest();
            request.setBooking(booking);
            request.setPaymentType(PaymentRequestType.MONTHLY_INSTALLMENT);
            request.setAmount(new BigDecimal("100000"));
            request.setPaidAmount(BigDecimal.ZERO);
            request.setCurrency("VND");
            request.setTransferReference("YEARLY-" + UUID.randomUUID());
            request.setBankCodeSnapshot("VCB");
            request.setBankNameSnapshot("Test bank");
            request.setAccountNumberSnapshot("0123456789");
            request.setAccountHolderSnapshot("TEST OWNER");
            request.setStatus(PaymentRequestStatus.PENDING);
            request.setExpiresAt(Instant.now().plusSeconds(86400));
            em.persist(request);
            em.flush();
            requestId[0] = request.getId();
        });

        var payment = vnpay.createPaymentUrl(
                renterId,
                new CreateVnpayPaymentUrlRequest(code(), "MONTHLY_INSTALLMENT", null, "vn"),
                "127.0.0.1");
        assertThat(vnpay.processIpn(signed(payment)).rspCode()).isEqualTo("00");

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.IN_PROGRESS);
        PaymentRequestStatus requestStatus = tx.execute(
                status -> em.find(PaymentRequest.class, requestId[0]).getStatus());
        assertThat(requestStatus).isEqualTo(PaymentRequestStatus.CONFIRMED);
        assertThat(ledger.findByBooking_Id(bookingId).stream()
                .filter(row -> row.getPaymentRequest() != null
                        && requestId[0].equals(row.getPaymentRequest().getId()))
                .map(PaymentTransaction::getStatus))
                .containsExactly(PaymentStatus.PAID);
    }

    @Test void acceptedReplacementRemainsVisibleAfterAnotherAcceptedTimeChange() {
        ready();
        long vehicleId = tx.execute(s -> {
            Booking b = em.find(Booking.class, bookingId);
            Vehicle replacement = new Vehicle();
            org.springframework.beans.BeanUtils.copyProperties(b.getVehicle(), replacement, "id", "createdAt", "updatedAt");
            replacement.setLicensePlate(UUID.randomUUID().toString().substring(0, 15));
            replacement.setChassisNo(UUID.randomUUID().toString()); replacement.setEngineNo(UUID.randomUUID().toString());
            replacement.setModel("Accepted replacement"); em.persist(replacement); persistPlan(replacement, "100000");
            snapshots.findByBooking_Id(bookingId).orElseThrow().setVehicleName("Original snapshot vehicle");
            return replacement.getId();
        });
        workflow.proposeAlternative(providerId, code(), new com.fptufa26.ecomotion.booking.dto.request.ResolveOperationalRiskRequest(
                OperationalResolutionType.CHANGE_VEHICLE, vehicleId, null, "Equivalent replacement"));
        workflow.respondToProposal(renterId, code(), workflow.getWorkflow(renterId, code()).proposal().id(), response("ACCEPT"));
        Instant next = bookings.findById(bookingId).orElseThrow().getPickupTime().plusSeconds(86400);
        workflow.proposeAlternative(providerId, code(), proposal(next));
        workflow.respondToProposal(renterId, code(), workflow.getWorkflow(renterId, code()).proposal().id(), response("ACCEPT"));
        assertThat(renterBookings.detail(renterId, code()).vehicleName()).contains("Accepted replacement");
        assertThat(renterBookings.detail(renterId, code()).totalAmount()).isEqualByComparingTo("100000");
    }

    @Test void acceptingVehicleChangeWithAssignedDriverMovesBookingAndScheduleTogether() {
        long[] driver = ready();
        optimization.manualAssign(bookingId, driver[0], driver[2]);
        trips.confirmTrip(driver[1], code());
        long vehicleId = tx.execute(s -> {
            Booking b = em.find(Booking.class, bookingId);
            Vehicle replacement = new Vehicle();
            org.springframework.beans.BeanUtils.copyProperties(b.getVehicle(), replacement, "id", "createdAt", "updatedAt");
            replacement.setLicensePlate(UUID.randomUUID().toString().substring(0, 15));
            replacement.setChassisNo(UUID.randomUUID().toString()); replacement.setEngineNo(UUID.randomUUID().toString());
            replacement.setModel("Driver replacement"); em.persist(replacement); persistPlan(replacement, "100000");
            return replacement.getId();
        });
        workflow.proposeAlternative(providerId, code(), new com.fptufa26.ecomotion.booking.dto.request.ResolveOperationalRiskRequest(
                OperationalResolutionType.CHANGE_VEHICLE, vehicleId, null, "Equivalent replacement"));
        workflow.respondToProposal(renterId, code(), workflow.getWorkflow(renterId, code()).proposal().id(), response("ACCEPT"));
        var after = bookings.findById(bookingId).orElseThrow();
        assertThat(after.getVehicle().getId()).isEqualTo(vehicleId);
        java.util.List<Long> scheduledVehicles = tx.execute(s -> em.createQuery(
                "select s.vehicle.id from com.fptufa26.ecomotion.schedule.entity.VehicleSchedule s where s.booking.id = :id", Long.class)
                .setParameter("id", bookingId).getResultList());
        assertThat(scheduledVehicles).containsOnly(vehicleId);
    }

    // B1: fixture - xe thay thế cần gói giá giờ để tính chênh lệch.
    void persistPlan(Vehicle v, String price) {
        RentalPlan plan = new RentalPlan(); plan.setVehicle(v); plan.setPlanType(PlanType.HOURLY);
        plan.setSlotLengthMinutes(60); plan.setSlotPrice(new BigDecimal(price)); em.persist(plan);
    }

    long replacementVehicle(String model, int seats) { return replacementVehicle(model, seats, "100000", null); }

    long replacementVehicle(String model, int seats, String hourlyPrice, Integer year) {
        return tx.execute(s -> {
            Booking b = em.find(Booking.class, bookingId);
            Vehicle replacement = new Vehicle();
            org.springframework.beans.BeanUtils.copyProperties(b.getVehicle(), replacement, "id", "createdAt", "updatedAt");
            replacement.setLicensePlate(UUID.randomUUID().toString().substring(0, 15));
            replacement.setChassisNo(UUID.randomUUID().toString()); replacement.setEngineNo(UUID.randomUUID().toString());
            replacement.setModel(model); replacement.setSeatCount(seats); if (year != null) replacement.setManufactureYear(year);
            em.persist(replacement); persistPlan(replacement, hourlyPrice);
            return replacement.getId();
        });
    }

    /** A second booking on {@code vehicleId} in the given state, created directly (as seeded/legacy data would be). */
    void otherBooking(long vehicleId, BookingStatus status, Instant pickup, Instant plannedEnd) {
        tx.executeWithoutResult(s -> {
            Booking base = em.find(Booking.class, bookingId);
            Booking o = new Booking(); o.setBookingCode(UUID.randomUUID().toString().substring(0, 30).toUpperCase(java.util.Locale.ROOT));
            o.setRenter(base.getRenter()); o.setVehicle(em.find(Vehicle.class, vehicleId)); o.setPlan(base.getPlan()); o.setStatus(status);
            o.setPickupTime(pickup); o.setPlannedEndAt(plannedEnd); o.setPickupAddress("Other"); o.setPassengerCount(1);
            o.setTotalAmount(new BigDecimal("100000")); em.persist(o);
        });
    }

    com.fptufa26.ecomotion.booking.dto.request.ResolveOperationalRiskRequest changeVehicle(long vehicleId) {
        return new com.fptufa26.ecomotion.booking.dto.request.ResolveOperationalRiskRequest(
                OperationalResolutionType.CHANGE_VEHICLE, vehicleId, null, "Replacement");
    }

    @Test void acceptWorksForBookingThatHasNoScheduleRowLikeSeededData() {
        ready();
        long vehicleId = replacementVehicle("No schedule row", 4);
        tx.executeWithoutResult(s -> em.createQuery("delete from com.fptufa26.ecomotion.schedule.entity.VehicleSchedule s where s.booking.id = :id")
                .setParameter("id", bookingId).executeUpdate());
        workflow.proposeAlternative(providerId, code(), changeVehicle(vehicleId));
        workflow.respondToProposal(renterId, code(), workflow.getWorkflow(renterId, code()).proposal().id(), response("ACCEPT"));
        assertThat(bookings.findById(bookingId).orElseThrow().getVehicle().getId()).isEqualTo(vehicleId);
        java.util.List<Long> scheduled = tx.execute(s -> em.createQuery(
                "select s.vehicle.id from com.fptufa26.ecomotion.schedule.entity.VehicleSchedule s where s.booking.id = :id", Long.class)
                .setParameter("id", bookingId).getResultList());
        assertThat(scheduled).containsExactly(vehicleId);
    }

    private com.fptufa26.ecomotion.booking.dto.request.RespondOperationalProposalRequest accept(boolean acknowledged) {
        return new com.fptufa26.ecomotion.booking.dto.request.RespondOperationalProposalRequest("ACCEPT", "Renter decision", acknowledged);
    }

    private void swapTo(long vehicleId) {
        workflow.proposeAlternative(providerId, code(), changeVehicle(vehicleId));
        workflow.respondToProposal(renterId, code(), workflow.getWorkflow(renterId, code()).proposal().id(), accept(true));
    }

    // B1: các test b1* kiểm tra xe đắt hơn/rẻ hơn/bằng giá, chưa xác nhận chất lượng thấp hơn, xe thiếu gói giá.
    @Test void b1PricierReplacementDoesNotChargeRenterOrChangeContractOrDeposit() {
        ready();
        var depositBefore = snapshots.findByBooking_Id(bookingId).orElseThrow().getDepositAmount();
        swapTo(replacementVehicle("Pricier", 4, "150000", null));
        var after = bookings.findById(bookingId).orElseThrow();
        assertThat(after.getTotalAmount()).isEqualByComparingTo("100000");
        assertThat(after.vehicleChangeCredit()).isEqualByComparingTo("0");
        assertThat(after.getVehicleChangeNewAmount()).isEqualByComparingTo("150000");
        assertThat(after.getVehicleChangeOldAmount()).isEqualByComparingTo("100000");
        assertThat(snapshots.findByBooking_Id(bookingId).orElseThrow().getDepositAmount()).isEqualTo(depositBefore);
        var settlement = vnpay.getSettlementDetails(renterId, code());
        assertThat(settlement.baseRental()).isEqualByComparingTo("100000");
        assertThat(settlement.vehicleChangeDiscount()).isEqualByComparingTo("0");
        assertThat(settlement.finalPayable()).isEqualByComparingTo("0");
        assertThat(settlement.overpayment()).isEqualByComparingTo("0");
    }

    @Test void b1CheaperReplacementIsDeductedFromFinalBillWithoutTouchingPaymentsOrContract() {
        ready();
        long txBefore = ledger.findByBooking_Id(bookingId).size();
        swapTo(replacementVehicle("Cheaper", 4, "70000", null));
        var after = bookings.findById(bookingId).orElseThrow();
        assertThat(after.getTotalAmount()).isEqualByComparingTo("100000");
        assertThat(after.vehicleChangeCredit()).isEqualByComparingTo("30000");
        assertThat(after.getPreviousVehicleId()).isNotNull();
        var settlement = vnpay.getSettlementDetails(renterId, code());
        assertThat(settlement.baseRental()).isEqualByComparingTo("100000");
        assertThat(settlement.vehicleChangeDiscount()).isEqualByComparingTo("30000");
        assertThat(settlement.overpayment()).isEqualByComparingTo("30000");   // đã trả 100000, nghĩa vụ còn 70000
        // không tự động hoàn tiền/ghi giao dịch mới
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize((int) txBefore);
        // tính lại nhiều lần không cộng dồn
        assertThat(vnpay.getSettlementDetails(renterId, code()).vehicleChangeDiscount()).isEqualByComparingTo("30000");
        assertThat(vnpay.getSettlementDetails(renterId, code()).overpayment()).isEqualByComparingTo("30000");
    }

    @Test void b1EqualPriceCreatesNoDifference() {
        ready();
        swapTo(replacementVehicle("Same price", 4, "100000", null));
        assertThat(bookings.findById(bookingId).orElseThrow().vehicleChangeCredit()).isEqualByComparingTo("0");
        assertThat(vnpay.getSettlementDetails(renterId, code()).overpayment()).isEqualByComparingTo("0");
    }

    @Test void b1LowerQualityVehicleIsNotSwappedWithoutRenterAcknowledgement() {
        ready();
        long oldVehicle = bookings.findById(bookingId).orElseThrow().getVehicle().getId();
        long older = replacementVehicle("Older", 4, "90000", 2020);
        workflow.proposeAlternative(providerId, code(), changeVehicle(older));
        var proposal = workflow.getWorkflow(renterId, code()).proposal();
        assertThat(proposal.lowerQuality()).isTrue();
        assertThat(proposal.qualityNotes()).isNotEmpty();
        assertThatThrownBy(() -> workflow.respondToProposal(renterId, code(), proposal.id(), accept(false)))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
        var unchanged = bookings.findById(bookingId).orElseThrow();
        assertThat(unchanged.getVehicle().getId()).isEqualTo(oldVehicle);
        assertThat(unchanged.vehicleChangeCredit()).isEqualByComparingTo("0");
        workflow.respondToProposal(renterId, code(), proposal.id(), accept(true));
        assertThat(bookings.findById(bookingId).orElseThrow().getVehicle().getId()).isEqualTo(older);
    }

    @Test void b1ReplacementWithoutComparablePlanCannotBeProposed() {
        ready();
        long noPlan = tx.execute(s -> {
            Booking b = em.find(Booking.class, bookingId);
            Vehicle v = new Vehicle();
            org.springframework.beans.BeanUtils.copyProperties(b.getVehicle(), v, "id", "createdAt", "updatedAt");
            v.setLicensePlate(UUID.randomUUID().toString().substring(0, 15));
            v.setChassisNo(UUID.randomUUID().toString()); v.setEngineNo(UUID.randomUUID().toString());
            em.persist(v); return v.getId();
        });
        assertThatThrownBy(() -> workflow.proposeAlternative(providerId, code(), changeVehicle(noPlan)))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
    }

    @Test void b7VehicleMustDifferAndHaveSeatsForPassengersPlusDriver() {
        ready();
        long currentVehicle = bookings.findById(bookingId).orElseThrow().getVehicle().getId();
        tx.executeWithoutResult(s -> em.find(Booking.class, bookingId).setPassengerCount(3));
        long tooSmall = replacementVehicle("Four seats", 3);       // 3 passengers + driver = 4 seats needed
        for (long id : new long[]{currentVehicle, tooSmall})
            assertThatThrownBy(() -> workflow.proposeAlternative(providerId, code(), changeVehicle(id)))
                    .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class)
                    .hasMessage("Xe đề xuất phải khác xe hiện tại và đủ chỗ");
        long enough = replacementVehicle("Enough seats", 4);
        workflow.proposeAlternative(providerId, code(), changeVehicle(enough));
        assertThat(workflow.getWorkflow(renterId, code()).proposal().status()).isEqualTo("PENDING");
    }

    @Test void b8BusyReplacementVehicleIsRejectedByBackendWhenProposing() {
        ready();
        Instant pickup = bookings.findById(bookingId).orElseThrow().getPickupTime();
        long busy = replacementVehicle("Busy", 4);
        otherBooking(busy, BookingStatus.CONFIRMED, pickup.minusSeconds(1800), pickup.plusSeconds(1800));
        assertThatThrownBy(() -> workflow.proposeAlternative(providerId, code(), changeVehicle(busy)))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
        assertThat(workflow.getWorkflow(renterId, code()).proposal()).isNull();
    }

    @Test void b6RescheduleRejectedWhenSameVehicleHasUnreturnedEarlierTripOrOverlappingBooking() {
        ready();
        Booking base = bookings.findById(bookingId).orElseThrow();
        long vehicleId = base.getVehicle().getId(); Instant pickup = base.getPickupTime();
        otherBooking(vehicleId, BookingStatus.IN_PROGRESS, pickup.minusSeconds(7200), pickup.minusSeconds(3600)); // overdue, never returned
        assertThatThrownBy(() -> workflow.proposeAlternative(providerId, code(), proposal(pickup.plusSeconds(86400))))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class)
                .hasMessageContaining("chưa được trả về");
        assertThatThrownBy(() -> workflow.proposeAlternative(providerId, code(), proposal(clockNowPlus(1800))))
                .isInstanceOf(RuntimeException.class);   // < 1h from now
        assertThat(workflow.getWorkflow(renterId, code()).proposal()).isNull();
    }

    @Test void b6RescheduleRejectedWhenNewWindowOverlapsAnotherBooking() {
        ready();
        Booking base = bookings.findById(bookingId).orElseThrow();
        Instant next = base.getPickupTime().plusSeconds(86400);
        otherBooking(base.getVehicle().getId(), BookingStatus.CONFIRMED, next.plusSeconds(600), next.plusSeconds(7200));
        assertThatThrownBy(() -> workflow.proposeAlternative(providerId, code(), proposal(next)))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
    }

    Instant clockNowPlus(long seconds) { return Instant.now().plusSeconds(seconds); }

    @Test void b9StaleTabCannotAcceptWithdrawnProposalNorOverwriteTheNewOne() {
        ready();
        Instant pickup = bookings.findById(bookingId).orElseThrow().getPickupTime();
        workflow.proposeAlternative(providerId, code(), proposal(pickup.plusSeconds(86400)));
        String staleId = workflow.getWorkflow(renterId, code()).proposal().id();
        workflow.withdrawProposal(providerId, code(), staleId);
        workflow.proposeAlternative(providerId, code(), proposal(pickup.plusSeconds(2 * 86400)));
        String currentId = workflow.getWorkflow(renterId, code()).proposal().id();
        assertThat(currentId).isNotEqualTo(staleId);
        assertThatThrownBy(() -> workflow.respondToProposal(renterId, code(), staleId, response("ACCEPT")))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class)
                .hasMessage("Đề xuất đã thay đổi, vui lòng tải lại trang.");
        assertThat(bookings.findById(bookingId).orElseThrow().getPickupTime()).isEqualTo(pickup);
        var still = workflow.getWorkflow(renterId, code()).proposal();
        assertThat(still.id()).isEqualTo(currentId); assertThat(still.status()).isEqualTo("PENDING");
    }

    @Test void b10CannotProposeAfterTripStarted() {
        ready();
        tx.executeWithoutResult(s -> em.find(Booking.class, bookingId).setStatus(BookingStatus.IN_PROGRESS));
        long vehicleId = replacementVehicle("Too late", 4);
        assertThatThrownBy(() -> workflow.proposeAlternative(providerId, code(), changeVehicle(vehicleId)))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
        assertThatThrownBy(() -> workflow.proposeAlternative(providerId, code(), proposal(Instant.now().plusSeconds(86400 * 2))))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
    }

    // B1: khoản giảm trừ phải hiện cho khách ngay khi đề xuất còn chờ (trước khi đồng ý), và khớp con số sau khi đồng ý.
    @Test void b1RenterSeesDeductionWhileProposalIsStillPendingAndItMatchesAfterAcceptance() {
        ready();
        tx.executeWithoutResult(s -> { Booking b = em.find(Booking.class, bookingId); b.setPlannedEndAt(b.getPickupTime().plusSeconds(3600)); });
        workflow.proposeAlternative(providerId, code(), changeVehicle(replacementVehicle("Cheaper pending", 4, "80000", null)));
        var pending = workflow.getWorkflow(renterId, code()).proposal();
        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(pending.contractAmount()).isEqualByComparingTo("100000");
        assertThat(pending.proposedAmount()).isEqualByComparingTo("80000");
        assertThat(pending.priceReduction()).isEqualByComparingTo("20000");
        var untouched = bookings.findById(bookingId).orElseThrow();
        assertThat(untouched.getVehicleChangeDiscount()).isNull();       // chỉ là ước tính, chưa ghi gì vào booking
        assertThat(untouched.getTotalAmount()).isEqualByComparingTo("100000");
        workflow.respondToProposal(renterId, code(), pending.id(), accept(true));
        var accepted = workflow.getWorkflow(renterId, code()).proposal();
        assertThat(accepted.priceReduction()).isEqualByComparingTo(pending.priceReduction());
        assertThat(accepted.proposedAmount()).isEqualByComparingTo(pending.proposedAmount());
    }

    @Test void b1PricierPendingProposalShowsNoDeduction() {
        ready();
        tx.executeWithoutResult(s -> { Booking b = em.find(Booking.class, bookingId); b.setPlannedEndAt(b.getPickupTime().plusSeconds(3600)); });
        workflow.proposeAlternative(providerId, code(), changeVehicle(replacementVehicle("Pricier pending", 4, "150000", null)));
        var pending = workflow.getWorkflow(renterId, code()).proposal();
        assertThat(pending.proposedAmount()).isEqualByComparingTo("150000");
        assertThat(pending.priceReduction()).isEqualByComparingTo("0");
    }

    // B4: nhà xe rút đề xuất; khách vẫn nhận API trạng thái WITHDRAWN (FE ẩn) nhưng không thể ACCEPT/REJECT và booking không đổi.
    @Test void b4RenterCannotRespondToWithdrawnProposalAndBookingIsUntouched() {
        ready();
        Booking before = bookings.findById(bookingId).orElseThrow();
        var rowsBefore = scheduleRows();
        workflow.proposeAlternative(providerId, code(), proposal(before.getPickupTime().plusSeconds(86400)));
        String id = workflow.getWorkflow(renterId, code()).proposal().id();
        assertThat(workflow.getWorkflow(renterId, code()).proposal().status()).isEqualTo("PENDING");
        workflow.withdrawProposal(providerId, code(), id);
        assertThat(workflow.getWorkflow(renterId, code()).proposal().status()).isEqualTo("WITHDRAWN");
        for (String action : new String[]{"ACCEPT", "REJECT"})
            assertThatThrownBy(() -> workflow.respondToProposal(renterId, code(), id, response(action)))
                    .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
        Booking after = bookings.findById(bookingId).orElseThrow();
        assertThat(after.getPickupTime()).isEqualTo(before.getPickupTime());
        assertThat(after.getPlannedEndAt()).isEqualTo(before.getPlannedEndAt());
        assertThat(after.getVehicle().getId()).isEqualTo(before.getVehicle().getId());
        assertThat(after.getTotalAmount()).isEqualByComparingTo(before.getTotalAmount());
        assertThat(scheduleRows()).isEqualTo(rowsBefore);
        assertThat(workflow.getWorkflow(renterId, code()).proposal().status()).isEqualTo("WITHDRAWN");
        assertThat(workflow.getWorkflow(providerId, code()).proposal().status()).isEqualTo("WITHDRAWN");
    }

    /** Dòng lịch của booking dưới dạng chuỗi so sánh được: xe, khoảng thời gian, trạng thái. */
    java.util.List<String> scheduleRows() {
        return tx.execute(s -> em.createQuery(
                "select s.vehicle.id, s.startAt, s.endAt, s.status from com.fptufa26.ecomotion.schedule.entity.VehicleSchedule s where s.booking.id = :id order by s.id",
                Object[].class).setParameter("id", bookingId).getResultList().stream()
                .map(r -> r[0] + "|" + r[1] + "|" + r[2] + "|" + r[3]).toList());
    }

    // B2: khách đồng ý dời giờ thành công phải giữ thời lượng, giá và cọc; lịch xe dời đúng một dòng, không nhân đôi.
    @Test void b2RescheduleAcceptKeepsDurationPriceDepositAndMovesTheSingleScheduleRow() {
        ready();
        Instant pickup = tx.execute(s -> {
            Booking b = em.find(Booking.class, bookingId);
            b.setPlannedEndAt(b.getPickupTime().plusSeconds(2 * 3600));
            em.createQuery("update com.fptufa26.ecomotion.schedule.entity.VehicleSchedule s set s.endAt = :end where s.booking.id = :id")
                    .setParameter("end", b.getPickupTime().plusSeconds(2 * 3600)).setParameter("id", bookingId).executeUpdate();
            return b.getPickupTime();
        });
        Booking before = bookings.findById(bookingId).orElseThrow();
        var depositBefore = snapshots.findByBooking_Id(bookingId).orElseThrow().getDepositAmount();
        long rowsBefore = scheduleRows().size();
        Instant next = pickup.plusSeconds(86400);
        workflow.proposeAlternative(providerId, code(), proposal(next));
        workflow.respondToProposal(renterId, code(), workflow.getWorkflow(renterId, code()).proposal().id(), response("ACCEPT"));

        Booking after = bookings.findById(bookingId).orElseThrow();
        assertThat(after.getPickupTime()).isEqualTo(next);
        assertThat(after.getPlannedEndAt()).isEqualTo(next.plusSeconds(2 * 3600));
        assertThat(java.time.Duration.between(after.getPickupTime(), after.getPlannedEndAt()))
                .isEqualTo(java.time.Duration.between(before.getPickupTime(), before.getPlannedEndAt()));
        assertThat(after.getTotalAmount()).isEqualByComparingTo(before.getTotalAmount());
        assertThat(snapshots.findByBooking_Id(bookingId).orElseThrow().getDepositAmount()).isEqualTo(depositBefore);
        assertThat(after.getVehicle().getId()).isEqualTo(before.getVehicle().getId());
        assertThat(after.getStatus()).isEqualTo(before.getStatus());

        var window = tx.execute(s -> com.fptufa26.ecomotion.booking.service.AvailabilityPolicy.bufferedWindow(
                em.find(Vehicle.class, after.getVehicle().getId()), next, next.plusSeconds(2 * 3600)));
        var rows = scheduleRows();
        assertThat(rows).hasSize((int) rowsBefore).hasSize(1);
        assertThat(rows.getFirst()).isEqualTo(after.getVehicle().getId() + "|" + window.start() + "|" + window.end() + "|ACTIVE");
        assertThat(workflow.getWorkflow(renterId, code()).proposal().status()).isEqualTo("ACCEPTED");
    }

    // B8: xe thay thế bị booking khác chiếm sau khi gửi đề xuất, trước khi khách đồng ý.
    @Test void b8ReplacementTakenBeforeAcceptanceIsRejectedAndLeavesEverythingUntouched() throws Exception {
        ready();
        long contested = replacementVehicle("Contested", 4);
        workflow.proposeAlternative(providerId, code(), changeVehicle(contested));
        String id = workflow.getWorkflow(renterId, code()).proposal().id();
        Booking before = bookings.findById(bookingId).orElseThrow();
        Instant pickup = before.getPickupTime();
        var rowsBefore = scheduleRows();
        // Booking B chiếm xe thay thế trong cùng khoảng thuê (đúng khoảng sau khi đổi).
        otherBooking(contested, BookingStatus.CONFIRMED, pickup.minusSeconds(600), pickup.plusSeconds(1800));

        String token = tx.execute(s -> jwt.generateAccessToken(em.find(User.class, renterId)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/renter/bookings/" + code() + "/operational-proposal/" + id)
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"action\":\"ACCEPT\",\"acknowledgeLowerQuality\":true}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());

        Booking after = bookings.findById(bookingId).orElseThrow();
        assertThat(after.getVehicle().getId()).isEqualTo(before.getVehicle().getId());
        assertThat(after.getPreviousVehicleId()).isNull();
        assertThat(after.getVehicleChangeAt()).isNull();
        assertThat(after.getVehicleChangeNewAmount()).isNull();
        assertThat(after.getTotalAmount()).isEqualByComparingTo(before.getTotalAmount());
        assertThat(after.getPickupTime()).isEqualTo(pickup);
        assertThat(after.getStatus()).isEqualTo(before.getStatus());
        assertThat(scheduleRows()).isEqualTo(rowsBefore);
        var proposal = workflow.getWorkflow(renterId, code()).proposal();
        assertThat(proposal.status()).isEqualTo("PENDING");
        assertThat(after.getOperationalProposal().getAccepted()).isNull();
        assertThat(after.getOperationalProposal().getRespondedAt()).isNull();
        // Không có schedule nào của booking A nằm trên xe bị chiếm; booking B là booking duy nhất giữ xe đó.
        assertThat(scheduleRows()).noneMatch(r -> r.startsWith(contested + "|"));
        assertThat(bookings.findAll().stream().filter(b -> b.getVehicle().getId().equals(contested)).count()).isEqualTo(1);
    }

    com.fptufa26.ecomotion.booking.dto.request.BookingQueueFilter queue(String action) {
        return new com.fptufa26.ecomotion.booking.dto.request.BookingQueueFilter(null, code(), null, null, null, null, "CREATED_DESC", action);
    }

    @Test void providerQueueFiltersActionsBeforeDatabasePaginationAndUsesSameFilterForExport() {
        long[] driver = ready();
        assertThat(providerBookings.filterBookings(providerId, queue("ASSIGN_DRIVER"), 0, 1).total()).isEqualTo(1);
        providerBookings.assignDriver(providerId, code(), driver[0]);
        var pending = providerBookings.filterBookings(providerId, queue("DRIVER_RESPONSE"), 0, 1);
        assertThat(pending.items()).hasSize(1);
        assertThat(pending.items().getFirst().actionRequired()).isEqualTo("DRIVER_RESPONSE");
        assertThat(providerBookings.filterBookings(providerId, queue("DRIVER_RESPONSE"), 1, 1).items()).isEmpty();
        assertThat(providerBookings.filterBookings(providerId, queue("ASSIGN_DRIVER"), 0, 1).total()).isZero();
        assertThat(new String(providerBookings.exportFilteredBookings(providerId, queue("DRIVER_RESPONSE")), java.nio.charset.StandardCharsets.UTF_8)).contains(code());
        trips.confirmTrip(driver[1], code());
        tx.executeWithoutResult(s -> em.find(Booking.class, bookingId).setDriverReplacementRequired(true));
        var attention = providerBookings.filterBookings(providerId, queue("NEEDS_ATTENTION"), 0, 10);
        assertThat(attention.total()).isEqualTo(1);
        assertThat(attention.items().getFirst().actionRequired()).isEqualTo("NEEDS_ATTENTION");
        assertThat(providerBookings.filterBookings(providerId, queue("UPCOMING"), 0, 10).total()).isZero();
        assertThatThrownBy(() -> providerBookings.filterBookings(providerId, queue("UNKNOWN"), 0, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void renterCreatesHeldBookingPaysDepositDriverPerformsTripAndFinalPaymentCompletesSettlement() {
        org.mockito.Mockito.when(vietmap.route(10.8,106.7,10.8,106.7)).thenReturn(new com.fptufa26.ecomotion.location.dto.response.RoadRouteResponse(0,0,List.of(List.of(106.7,10.8),List.of(106.7,10.8)),"VIETMAP_CAR",Instant.now()));
        long[] driver = ready();
        var created = tx.execute(s -> {
            Booking fixture = em.find(Booking.class, bookingId);
            Provider provider = fixture.getVehicle().getProvider();
            provider.setDepotLatitude(new BigDecimal("10.8")); provider.setDepotLongitude(new BigDecimal("106.7"));
            fixture.getPlan().setFreeRadiusKm(10); fixture.getPlan().setOutOfRadiusRate(BigDecimal.ZERO); em.flush();
            return renterBookings.create(renterId, new com.fptufa26.ecomotion.booking.dto.request.CreateBookingRequest(
                    fixture.getVehicle().getId(), fixture.getPlan().getId(), 1, Instant.now().plusSeconds(259200),
                    "Pickup at provider depot", new BigDecimal("10.8"), new BigDecimal("106.7"), null, 1));
        });
        bookingId = bookings.findByBookingCode(created.bookingCode()).orElseThrow().getId();
        var deposit = vnpay.createPaymentUrl(renterId, new CreateVnpayPaymentUrlRequest(code(), "DEPOSIT", null, "vn"), "127.0.0.1");
        assertThat(vnpay.processIpn(signed(deposit)).rspCode()).isEqualTo("00");
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(workflow.getWorkflow(renterId, code()).driverStatus()).isEqualTo("NOT_ASSIGNED");
        providerBookings.assignDriver(providerId, code(), driver[0]);
        assertThat(workflow.getWorkflow(renterId, code()).driverStatus()).isEqualTo("PENDING_RESPONSE");
        trips.confirmTrip(driver[1], code()); trips.arriveAtPickup(driver[1], code());
        trips.startTrip(driver[1], code());
        trips.finishTrip(driver[1], code());
        providerBookings.finalizeSettlement(providerId, code());
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.AWAITING_FINAL_PAYMENT);
        var balance = vnpay.createPaymentUrl(renterId, new CreateVnpayPaymentUrlRequest(code(), "FINAL_PAYMENT", null, "vn"), "127.0.0.1");
        assertThat(balance.amount()).isEqualByComparingTo("70000");
        assertThat(vnpay.processIpn(signed(balance)).rspCode()).isEqualTo("00");
        assertThat(workflow.getWorkflow(renterId, code()).settlementStatus()).isEqualTo("SETTLED");
        assertThat(workflow.getWorkflow(renterId, code()).confirmedPaid()).isEqualByComparingTo("100000");
    }

    @Test void pendingGatewayKeepsExpiredHoldOccupiedAndLateCaptureRequiresReconciliation() {
        var session = tx.execute(s -> {
            Booking b = em.find(Booking.class, bookingId); ledger.deleteAll(ledger.findByBooking_Id(bookingId));
            b.setStatus(BookingStatus.AWAITING_DEPOSIT); em.flush();
            schedule.hold(b.getVehicle(), b, b.getPickupTime(), b.getPickupTime().plusSeconds(3600), Instant.now().plusSeconds(60));
            return vnpay.createPaymentUrl(renterId, new CreateVnpayPaymentUrlRequest(code(), "DEPOSIT", null, "vn"), "127.0.0.1");
        });
        tx.executeWithoutResult(s -> em.createQuery("select s from com.fptufa26.ecomotion.schedule.entity.VehicleSchedule s where s.booking.id = :id",
                com.fptufa26.ecomotion.schedule.entity.VehicleSchedule.class).setParameter("id", bookingId)
                .getResultList().forEach(h -> h.setExpiresAt(Instant.now().minusSeconds(1))));
        expiry.expireDueHolds(Instant.now()); expiry.expireDueHolds(Instant.now());
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.AWAITING_DEPOSIT);
        assertThat(workflow.getWorkflow(renterId, code()).paymentStatus()).isEqualTo("VERIFYING");
        assertThat(tx.<Boolean>execute(s -> {
            Booking b = em.find(Booking.class, bookingId);
            return schedule.isRangeFree(b.getVehicle().getId(), b.getPickupTime(), b.getPickupTime().plusSeconds(3600));
        })).isFalse();
        assertThat(vnpay.processIpn(signed(session)).rspCode()).isEqualTo("00");
        vnpay.processIpn(signed(session));
        assertThat(workflow.getWorkflow(renterId, code()).paymentStatus()).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(workflow.getWorkflow(renterId, code()).confirmedPaid()).isZero();
        assertThat(ledger.findByBooking_Id(bookingId).stream().filter(t -> t.getStatus() == PaymentStatus.PAID).findFirst().orElseThrow().getAmount()).isEqualByComparingTo(session.amount());
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLATION_REVIEW);
        assertThatThrownBy(() -> vnpay.createPaymentUrl(renterId, new CreateVnpayPaymentUrlRequest(code(), "DEPOSIT", null, "vn"), "127.0.0.1"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test void consentHttpChecksAuthenticationOwnershipValidationAndRefetchesPersistedDecision() throws Exception {
        ready(); Instant next = bookings.findById(bookingId).orElseThrow().getPickupTime().plusSeconds(86400);
        workflow.proposeAlternative(providerId, code(), proposal(next));
        String id = workflow.getWorkflow(renterId, code()).proposal().id();
        String endpoint = "/api/renter/bookings/" + code() + "/operational-proposal/" + id;
        var put = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(endpoint);
        mvc.perform(put.contentType("application/json").content("{\"action\":\"ACCEPT\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        String token = tx.execute(s -> jwt.generateAccessToken(em.find(User.class, renterId)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(endpoint)
                .header("Authorization", "Bearer " + token).contentType("application/json").content("{\"action\":\"YES\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(endpoint)
                .header("Authorization", "Bearer " + token).contentType("application/json").content("{\"action\":\"REJECT\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/renter/bookings/" + code() + "/workflow")
                .header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.proposal.status").value("REJECTED"));
    }

    @Autowired com.fptufa26.ecomotion.schedule.service.ProviderScheduleService providerSchedules;

    @Test void competingScheduleAtAcceptanceRollsBackConsentAndKeepsOriginalBooking() {
        ready(); Instant original = bookings.findById(bookingId).orElseThrow().getPickupTime();
        Instant next = original.plusSeconds(86400); workflow.proposeAlternative(providerId, code(), proposal(next));
        String id = workflow.getWorkflow(renterId, code()).proposal().id();
        tx.executeWithoutResult(s -> {
            Booking b = em.find(Booking.class, bookingId);
            providerSchedules.createBlockSchedule(b.getVehicle().getProvider().getUser().getId(),
                    new com.fptufa26.ecomotion.schedule.dto.request.CreateBlockScheduleRequest(b.getVehicle().getId(),
                            com.fptufa26.ecomotion.schedule.enums.ScheduleType.BLOCKED, next, next.plusSeconds(3600), "QA conflict"));
        });
        long schedulesBefore = tx.execute(s -> em.createQuery("select count(s) from com.fptufa26.ecomotion.schedule.entity.VehicleSchedule s", Long.class).getSingleResult());
        long bookingsBefore = bookings.count();
        var before = bookings.findById(bookingId).orElseThrow();
        assertThatThrownBy(() -> workflow.respondToProposal(renterId, code(), id, response("ACCEPT")))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
        long schedulesAfter = tx.execute(s -> em.createQuery("select count(s) from com.fptufa26.ecomotion.schedule.entity.VehicleSchedule s", Long.class).getSingleResult());
        assertThat(schedulesAfter).isEqualTo(schedulesBefore);
        assertThat(bookings.count()).isEqualTo(bookingsBefore);
        var after = bookings.findById(bookingId).orElseThrow();
        assertThat(after.getPlannedEndAt()).isEqualTo(before.getPlannedEndAt());
        assertThat(after.getStatus()).isEqualTo(before.getStatus());
        assertThat(after.getTotalAmount()).isEqualByComparingTo(before.getTotalAmount());
        assertThat(workflow.getWorkflow(renterId, code()).proposal().status()).isEqualTo("PENDING");
        assertThat(bookings.findById(bookingId).orElseThrow().getPickupTime()).isEqualTo(original);
    }

    long[] ready() {
        return tx.execute(status -> {
            Booking b = em.find(Booking.class, bookingId); b.setStatus(BookingStatus.CONFIRMED);
            if (em.createQuery("select count(c) from BookingCharge c where c.booking.id = :id", Long.class).setParameter("id", bookingId).getSingleResult() == 0) {
            BookingCharge charge = new BookingCharge(); charge.setBooking(b); charge.setChargeType(ChargeType.BASE_RENTAL); charge.setDescription("Rental snapshot");
            charge.setQuantity(1); charge.setUnitPrice(b.getTotalAmount()); charge.setAmount(b.getTotalAmount());
            charge.setCreatedBy(b.getRenter().getUser()); em.persist(charge);
            }
            Driver d = new Driver(); d.setUser(user(Role.DRIVER)); d.setProvider(b.getVehicle().getProvider());
            d.setStatus(DriverStatus.APPROVED); d.setLicenseNo(UUID.randomUUID().toString().substring(0, 18));
            d.setLicenseClass("B2"); d.setLicenseExpiry(java.time.LocalDate.now().plusYears(1));
            d.setPortraitUrl("test"); d.setLicenseImageUrl("test"); em.persist(d);
            if (em.createQuery("select count(s) from com.fptufa26.ecomotion.schedule.entity.VehicleSchedule s where s.booking.id = :id", Long.class).setParameter("id", bookingId).getSingleResult() == 0)
                schedule.book(b.getVehicle(), b, b.getPickupTime(), b.getPickupTime().plusSeconds(3600));
            return new long[]{d.getId(), d.getUser().getId(), b.getVehicle().getProvider().getId()};
        });
    }

    com.fptufa26.ecomotion.booking.dto.request.ResolveOperationalRiskRequest proposal(Instant pickup) {
        return new com.fptufa26.ecomotion.booking.dto.request.ResolveOperationalRiskRequest(
                OperationalResolutionType.RESCHEDULE, null, pickup, "Driver unavailable; alternative pickup");
    }

    com.fptufa26.ecomotion.booking.dto.request.RespondOperationalProposalRequest response(String action) {
        return new com.fptufa26.ecomotion.booking.dto.request.RespondOperationalProposalRequest(action, "Renter decision", true);
    }

    @Test void proposalLeavesOriginalUntouchedUntilRenterAcceptsAndDuplicateResponseIsIdempotent() {
        ready(); Instant original = bookings.findById(bookingId).orElseThrow().getPickupTime();
        Instant next = original.plusSeconds(86400);
        workflow.proposeAlternative(providerId, code(), proposal(next));
        var pending = workflow.getWorkflow(renterId, code());
        assertThat(pending.proposal().status()).isEqualTo("PENDING");
        assertThat(bookings.findById(bookingId).orElseThrow().getPickupTime()).isEqualTo(original);
        workflow.respondToProposal(renterId, code(), pending.proposal().id(), response("ACCEPT"));
        assertThat(bookings.findById(bookingId).orElseThrow().getPickupTime()).isEqualTo(next);
        assertThat(workflow.respondToProposal(renterId, code(), pending.proposal().id(), response("ACCEPT")).proposal().status()).isEqualTo("ACCEPTED");
        assertThatThrownBy(() -> workflow.respondToProposal(renterId, code(), pending.proposal().id(), response("REJECT")))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
    }

    @Test void halfDepositRefundCompletesIndependentlyOfOpenCancellationReview() throws Exception {
        org.mockito.Mockito.when(storage.store(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(byte[].class), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("refund-proof/test.pdf");
        org.mockito.Mockito.when(storage.read("refund-proof/test.pdf")).thenReturn("%PDF-1.4 evidence".getBytes());
        ready();
        tx.executeWithoutResult(s -> {
            em.find(Booking.class, bookingId).setPickupTime(Instant.now().plusSeconds(3600));
            ledger.findByBooking_Id(bookingId).stream().filter(t -> t.getDirection() == PaymentDirection.IN)
                    .forEach(t -> t.setMethod(PaymentMethod.BANK_TRANSFER));
        });
        renterBookings.cancel(renterId, code());
        renterBookings.updateRefundAccount(renterId, code(),
                new com.fptufa26.ecomotion.finance.dto.request.RefundAccountRequest("Vietcombank", "NGUYEN VAN TEST", "0123456789"));
        var pending = workflow.getWorkflow(renterId, code());
        assertThat(pending.paymentStatus()).isEqualTo("REFUND_PENDING");
        refunds.completeRefund(providerId, pending.refunds().getFirst().paymentTransactionId(),
                new MockMultipartFile("file", "refund.pdf", "application/pdf", "%PDF-1.4 evidence".getBytes()), null, null);
        var completed = workflow.getWorkflow(renterId, code());
        var receipt = completed.refunds().getFirst();
        assertThat(receipt.receiptStatus()).isEqualTo("WAITING");
        assertThat(java.time.Duration.between(receipt.receiptWaitingAt(), receipt.receiptDeadline()).toHours()).isEqualTo(72);
        assertThatThrownBy(() -> receipts.respond(providerId, receipt.id(), true)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        receipts.confirmExpired();
        assertThat(workflow.getWorkflow(renterId, code()).refunds().getFirst().receiptStatus()).isEqualTo("WAITING");
        receipts.respond(renterId, receipt.id(), false);
        tx.executeWithoutResult(s -> em.find(com.fptufa26.ecomotion.finance.entity.RefundRequest.class, receipt.id()).setReceiptDeadline(Instant.now().minusSeconds(1)));
        receipts.confirmExpired();
        assertThat(workflow.getWorkflow(renterId, code()).refunds().getFirst().receiptStatus()).isEqualTo("REPORTED_NOT_RECEIVED");
        tx.executeWithoutResult(s -> em.find(com.fptufa26.ecomotion.finance.entity.RefundRequest.class, receipt.id()).setReceiptStatus("WAITING"));
        receipts.confirmExpired();
        var automatic = workflow.getWorkflow(renterId, code()).refunds().getFirst();
        assertThat(automatic.receiptStatus()).isEqualTo("AUTO_CONFIRMED");
        assertThat(automatic.receiptConfirmedBy()).isNull();
        assertThat(automatic.receiptAutoConfirmedAt()).isNotNull();
        receipts.confirmExpired();
        assertThat(workflow.getWorkflow(renterId, code()).refunds().getFirst().receiptAutoConfirmedAt()).isEqualTo(automatic.receiptAutoConfirmedAt());
        assertThatThrownBy(() -> receipts.respond(renterId, receipt.id(), true)).isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
        assertThatThrownBy(() -> receipts.respond(renterId, receipt.id(), false)).isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
        assertThat(automatic.proofAvailable()).isTrue();
        var storedProof = refunds.getProof(renterId, automatic.paymentTransactionId());
        assertThat(storedProof.contentType()).isEqualTo("application/pdf");
        assertThat(storedProof.content()).containsExactly("%PDF-1.4 evidence".getBytes());
        assertThatThrownBy(() -> refunds.getProof(adminId + 999999, automatic.paymentTransactionId())).isInstanceOf(RuntimeException.class);
        assertThat(completed.refundedAmount()).isEqualByComparingTo(completed.confirmedPaid().multiply(new BigDecimal("0.5")));
        assertThat(completed.paymentStatus()).isEqualTo("REFUNDED");
        tx.executeWithoutResult(s -> disputes.open(em.find(Booking.class, bookingId), em.find(User.class, renterId),
                com.fptufa26.ecomotion.dispute.enums.DisputeCategory.DEPOSIT_REFUND, null,
                "Please review the cancellation refund policy", List.of()));
        var reviewed = workflow.getWorkflow(renterId, code());
        assertThat(reviewed.paymentStatus()).isEqualTo("REFUNDED");
        assertThat(reviewed.settlementStatus()).isEqualTo("DISPUTED");
        assertThat(reviewed.nextAction()).contains("xử lý riêng").doesNotContain("quyết toán");
    }

    @Test void noDriverRenterRejectsAlternativeThenCancellationReservesFullRefundWithoutClaimingPaid() {
        long[] driver = ready();
        tx.executeWithoutResult(s -> em.find(Driver.class, driver[0]).setActivityStatus(DriverActivityStatus.OFFLINE));
        assertThatThrownBy(() -> providerBookings.autoAssignDriver(providerId, code())).isInstanceOf(RuntimeException.class);
        assertThat(workflow.getWorkflow(providerId, code()).needsDriver()).isTrue();
        Instant original = bookings.findById(bookingId).orElseThrow().getPickupTime();
        workflow.proposeAlternative(providerId, code(), proposal(original.plusSeconds(86400)));
        String id = workflow.getWorkflow(renterId, code()).proposal().id();
        workflow.respondToProposal(renterId, code(), id, response("REJECT"));
        assertThat(bookings.findById(bookingId).orElseThrow().getPickupTime()).isEqualTo(original);
        workflow.proposeAlternative(providerId, code(), new com.fptufa26.ecomotion.booking.dto.request.ResolveOperationalRiskRequest(
                OperationalResolutionType.CANCEL_AND_REFUND, null, null, "No driver; renter rejected alternative"));
        var result = workflow.getWorkflow(renterId, code());
        assertThat(result.bookingStatus()).isEqualTo(BookingStatus.CANCELLATION_REVIEW);
        assertThat(result.pendingRefundAmount()).isEqualByComparingTo("100000");
        assertThat(result.refundedAmount()).isZero();
        assertThat(result.refundableAmount()).isZero();
        assertThatThrownBy(() -> refunds.createRefundRequest(renterId, request("100000", "duplicate"))).isInstanceOf(RuntimeException.class);
        long refundId = ledger.findByBooking_Id(bookingId).stream().filter(t -> t.getType() == PaymentType.REFUND).findFirst().orElseThrow().getId();
        var proof = new MockMultipartFile("file", "refund.pdf", "application/pdf", "%PDF-1.4 test evidence".getBytes());
        // ready() collected via VNPay: a bank-transfer image cannot settle a gateway refund.
        assertThatThrownBy(() -> refunds.completeRefund(providerId, refundId, proof, "JOURNEY-" + UUID.randomUUID(), "Bank transfer confirmed"))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
        var completed = workflow.getWorkflow(renterId, code());
        assertThat(completed.bookingStatus()).isEqualTo(BookingStatus.CANCELLATION_REVIEW);
        assertThat(completed.refundedAmount()).isZero();
        assertThat(completed.pendingRefundAmount()).isEqualByComparingTo("100000");
    }

    @Test void changedTermsRequireNewDriverConsentAndRejectOldTabSnapshot() {
        long[] driver = ready(); optimization.manualAssign(bookingId, driver[0], driver[2]);
        Instant oldAssignment = bookings.findById(bookingId).orElseThrow().getAssignedAt();
        trips.confirmTrip(driver[1], code(), oldAssignment);
        Instant next = bookings.findById(bookingId).orElseThrow().getPickupTime().plusSeconds(86400);
        workflow.proposeAlternative(providerId, code(), proposal(next));
        String id = workflow.getWorkflow(renterId, code()).proposal().id();
        workflow.respondToProposal(renterId, code(), id, response("ACCEPT"));
        Booking changed = bookings.findById(bookingId).orElseThrow();
        assertThat(changed.getStatus()).isEqualTo(BookingStatus.DRIVER_ASSIGNED);
        assertThat(changed.getDriverConfirmedAt()).isNull();
        assertThatThrownBy(() -> trips.confirmTrip(driver[1], code(), oldAssignment)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> trips.confirmTrip(driver[1], code())).isInstanceOf(RuntimeException.class);
        trips.confirmTrip(driver[1], code(), changed.getAssignedAt());
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.DRIVER_CONFIRMED);
    }

    @Test void cannotOverwritePendingProposalOrAnswerWithdrawnProposalOrReadAnotherRentersBooking() {
        ready(); Instant next = bookings.findById(bookingId).orElseThrow().getPickupTime().plusSeconds(86400);
        workflow.proposeAlternative(providerId, code(), proposal(next));
        String id = workflow.getWorkflow(renterId, code()).proposal().id();
        workflow.proposeAlternative(providerId, code(), proposal(next));
        assertThat(workflow.getWorkflow(renterId, code()).proposal().id()).isEqualTo(id);
        assertThatThrownBy(() -> workflow.proposeAlternative(providerId, code(), proposal(next.plusSeconds(86400)))).isInstanceOf(RuntimeException.class);
        long foreign = tx.execute(s -> user(Role.RENTER).getId());
        assertThatThrownBy(() -> workflow.getWorkflow(foreign, code())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> workflow.respondToProposal(providerId, code(), id, response("ACCEPT"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        workflow.withdrawProposal(providerId, code(), id);
        assertThat(workflow.getWorkflow(renterId, code()).proposal().status()).isEqualTo("WITHDRAWN");
        assertThatThrownBy(() -> workflow.respondToProposal(renterId, code(), id, response("ACCEPT"))).isInstanceOf(RuntimeException.class);
    }

    @Test void concurrentRenterAcceptAndRejectHaveOneOutcome() throws Exception {
        ready(); Instant next = bookings.findById(bookingId).orElseThrow().getPickupTime().plusSeconds(86400);
        workflow.proposeAlternative(providerId, code(), proposal(next));
        String id = workflow.getWorkflow(renterId, code()).proposal().id();
        assertThat(race(() -> workflow.respondToProposal(renterId, code(), id, response("ACCEPT")),
                () -> workflow.respondToProposal(renterId, code(), id, response("REJECT")))).containsExactlyInAnyOrder(true, false);
        var decision = workflow.getWorkflow(renterId, code()).proposal();
        assertThat(decision.status()).isIn("ACCEPTED", "REJECTED");
        assertThat(bookings.findById(bookingId).orElseThrow().getPickupTime().equals(next)).isEqualTo(decision.status().equals("ACCEPTED"));
    }

    @Test void driverEndOnlyEndsTripThenProviderSettlesWithoutRenterConfirmation() {
        long[] driver = ready(); optimization.manualAssign(bookingId, driver[0], driver[2]);
        trips.confirmTrip(driver[1], code()); trips.arriveAtPickup(driver[1], code());
        trips.startTrip(driver[1], code());
        trips.finishTrip(driver[1], code());
        assertThat(workflow.getWorkflow(renterId, code()).tripStatus()).isEqualTo("ENDED");
        assertThat(workflow.getWorkflow(renterId, code()).settlementStatus()).isEqualTo("REVIEWING_CHARGES");
        providerBookings.finalizeSettlement(providerId, code());
        assertThat(workflow.getWorkflow(renterId, code()).settlementStatus()).isEqualTo("SETTLED");
    }
    @Autowired EntityManager em;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired PlatformTransactionManager manager;
    @Autowired RefundService refunds;
    @Autowired ProviderFinanceService finance;
    @Autowired VnpayService vnpay;
    @Autowired com.fptufa26.ecomotion.booking.service.DriverTripService trips;
    @Autowired com.fptufa26.ecomotion.booking.service.BookingService renterBookings;
    @Autowired com.fptufa26.ecomotion.booking.service.ProviderBookingService providerBookings;
    @Autowired com.fptufa26.ecomotion.booking.service.DriverOptimizationService optimization;
    @Autowired com.fptufa26.ecomotion.schedule.service.ScheduleService schedule;
    @Autowired PaymentTransactionRepository ledger;
    @Autowired BookingRepository bookings;
    @Autowired RefundRequestRepository requests;
    @Autowired com.fptufa26.ecomotion.finance.service.RefundReceiptService receipts;
    @Autowired BookingPolicySnapshotRepository snapshots;
    @Autowired com.fptufa26.ecomotion.finance.config.VnpayConfig config;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.fptufa26.ecomotion.common.security.JwtService jwt;
    TransactionTemplate tx;
    long bookingId;
    long renterId;
    long providerId;
    long adminId;

    User user(Role role) {
        User u = new User(); u.setEmail(UUID.randomUUID() + "@safety.test"); u.setPasswordHash("test-only");
        u.setFullName("TV5 safety"); u.setRole(role); u.setStatus(UserStatus.ACTIVE); em.persist(u); return u;
    }

    @BeforeEach void fixture() {
        if (Boolean.getBoolean("tv5.workflow.postgres")) {
            new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                    new org.springframework.core.io.ClassPathResource("db/migrations/20261029_tv5_refund_reference.sql"),
                    new org.springframework.core.io.ClassPathResource("db/migrations/20261030_tv5_operational_consent.sql"))
                    .execute(dataSource);
        }
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            Provider p = new Provider(); p.setUser(user(Role.PROVIDER)); p.setProviderType(ProviderType.INDIVIDUAL);
            p.setDisplayName("TV5"); p.setHotline("0900000000"); p.setDepotAddress("Test");
            p.setCommissionRate(new BigDecimal("0.1")); p.setDepositRatio(new BigDecimal("0.3"));
            p.setStatus(ProviderStatus.APPROVED); em.persist(p); providerId = p.getUser().getId();
            Renter r = new Renter(); r.setUser(user(Role.RENTER)); em.persist(r); renterId = r.getUser().getId();
            adminId = user(Role.ADMIN).getId();
            Vehicle v = new Vehicle(); v.setProvider(p); v.setLicensePlate(UUID.randomUUID().toString().substring(0, 15));
            v.setChassisNo(UUID.randomUUID().toString()); v.setEngineNo(UUID.randomUUID().toString());
            v.setBrand("Test"); v.setModel("TV5"); v.setManufactureYear(2026); v.setSeatCount(4);
            v.setTransmission(Transmission.AUTOMATIC); v.setColor("White"); v.setFuelType(FuelType.EV);
            v.setMaintenanceBufferMinutes(0); v.setStatus(VehicleStatus.APPROVED); em.persist(v);
            RentalPlan plan = new RentalPlan(); plan.setVehicle(v); plan.setPlanType(PlanType.HOURLY);
            plan.setSlotLengthMinutes(60); plan.setSlotPrice(new BigDecimal("100000")); em.persist(plan);
            Booking b = new Booking(); b.setBookingCode(UUID.randomUUID().toString().substring(0, 30).toUpperCase(java.util.Locale.ROOT));
            b.setRenter(r); b.setVehicle(v); b.setPlan(plan); b.setStatus(BookingStatus.COMPLETED);
            b.setPickupTime(Instant.now().plusSeconds(86400)); b.setPickupAddress("Test pickup");
            b.setPassengerCount(1); b.setTotalAmount(new BigDecimal("100000")); em.persist(b); bookingId = b.getId();
            BookingPolicySnapshot snapshot = new BookingPolicySnapshot(); snapshot.setBooking(b);
            snapshot.setPlanType(PlanType.HOURLY); snapshot.setSlotLengthMinutes(60);
            snapshot.setSlotPrice(new BigDecimal("100000")); snapshot.setDepositRatio(new BigDecimal("0.3")); em.persist(snapshot);
            ProviderBankAccount bank = new ProviderBankAccount(); bank.setProvider(p); bank.setBankCode("VCB");
            bank.setBankName("Test bank"); bank.setAccountNumber("0123456789"); bank.setAccountHolderName("TEST OWNER");
            bank.setStatus(BankAccountStatus.ACTIVE); em.persist(bank);
            incoming(b, PaymentMethod.VNPAY, new BigDecimal("100000"));
        });
    }

    PaymentTransaction incoming(Booking b, PaymentMethod method, BigDecimal amount) {
        PaymentTransaction t = new PaymentTransaction(); t.setBooking(b); t.setType(PaymentType.DEPOSIT_PAY);
        t.setDirection(PaymentDirection.IN); t.setMethod(method); t.setAmount(amount); t.setAppliedAmount(amount); t.setStatus(PaymentStatus.PAID);
        t.setVerificationMethod(method == PaymentMethod.VNPAY ? PaymentVerificationMethod.GATEWAY_VERIFIED : PaymentVerificationMethod.PROVIDER_CONFIRMED);
        em.persist(t); return t;
    }

    CreateRefundRequest request(String amount, String reason) {
        return new CreateRefundRequest(bookings.findById(bookingId).orElseThrow().getBookingCode(), new BigDecimal(amount), reason, null);
    }

    List<Boolean> race(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = List.of(first, second).stream().map(task -> pool.submit(() -> {
                start.await();
                try { task.run(); return true; }
                catch (com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException
                       | com.fptufa26.ecomotion.common.exception.ConflictException ex) { return false; }
            })).toList();
            start.countDown(); return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    java.util.Map<String, String> signed(com.fptufa26.ecomotion.finance.dto.response.VnpayPaymentUrlResponse session) {
        var params = new java.util.HashMap<String, String>();
        params.put("vnp_TmnCode", config.getTmnCode()); params.put("vnp_TxnRef", session.vnpTxnRef());
        params.put("vnp_Amount", session.amount().movePointRight(2).toBigIntegerExact().toString());
        params.put("vnp_ResponseCode", "00"); params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "123456789");
        params.put("vnp_SecureHash", com.fptufa26.ecomotion.finance.config.VnpayUtils.hmacSHA512(config.getHashSecret(),
                com.fptufa26.ecomotion.finance.config.VnpayUtils.buildHashData(params)));
        return params;
    }

}
