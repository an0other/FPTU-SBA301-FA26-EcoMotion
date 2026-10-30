package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.booking.entity.*;
import com.fptufa26.ecomotion.booking.enums.*;
import com.fptufa26.ecomotion.booking.repository.*;
import com.fptufa26.ecomotion.finance.dto.request.*;
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
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@ActiveProfiles("test")
class EndUserWorkflowIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.fptufa26.ecomotion.booking.service.MapGeocodingService geocoding;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.fptufa26.ecomotion.notification.external.service.ExternalNotificationDispatcherService external;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.fptufa26.ecomotion.common.storage.FileStorageService storage;
    @org.springframework.test.context.DynamicPropertySource
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
        trips.startTrip(driver[1], code()); renterBookings.confirmStart(renterId, code());
        trips.finishTrip(driver[1], code()); renterBookings.confirmEnd(renterId, code());
        tx.executeWithoutResult(s -> snapshots.findByBooking_Id(bookingId).orElseThrow().setWaitingRate(new BigDecimal("10000")));
        var charge = providerBookings.addCharge(providerId, code(), new com.fptufa26.ecomotion.booking.dto.request.AddChargeRequest(
                ChargeType.WAITING, "Waiting at pickup", 2, new BigDecimal("99999"), BigDecimal.ONE,
                new BigDecimal("99999"), "https://example.test/waiting.jpg"));
        assertThat(charge.amount()).isEqualByComparingTo("20000");
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

    @Test void acceptedReplacementRemainsVisibleAfterAnotherAcceptedTimeChange() {
        ready();
        long vehicleId = tx.execute(s -> {
            Booking b = em.find(Booking.class, bookingId);
            Vehicle replacement = new Vehicle();
            org.springframework.beans.BeanUtils.copyProperties(b.getVehicle(), replacement, "id", "createdAt", "updatedAt");
            replacement.setLicensePlate(UUID.randomUUID().toString().substring(0, 15));
            replacement.setChassisNo(UUID.randomUUID().toString()); replacement.setEngineNo(UUID.randomUUID().toString());
            replacement.setModel("Accepted replacement"); em.persist(replacement);
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
        trips.startTrip(driver[1], code()); renterBookings.confirmStart(renterId, code());
        trips.finishTrip(driver[1], code()); renterBookings.confirmEnd(renterId, code());
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
        tx.executeWithoutResult(s -> em.createQuery("select s from VehicleSchedule s where s.booking.id = :id",
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
        assertThat(workflow.getWorkflow(renterId, code()).confirmedPaid()).isEqualByComparingTo(session.amount());
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

    @Test void competingScheduleAtAcceptanceRollsBackConsentAndKeepsOriginalBooking() {
        ready(); Instant original = bookings.findById(bookingId).orElseThrow().getPickupTime();
        Instant next = original.plusSeconds(86400); workflow.proposeAlternative(providerId, code(), proposal(next));
        String id = workflow.getWorkflow(renterId, code()).proposal().id();
        tx.executeWithoutResult(s -> {
            Booking b = em.find(Booking.class, bookingId);
            schedule.book(b.getVehicle(), null, next, next.plusSeconds(3600));
        });
        assertThatThrownBy(() -> workflow.respondToProposal(renterId, code(), id, response("ACCEPT"))).isInstanceOf(RuntimeException.class);
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
            if (em.createQuery("select count(s) from VehicleSchedule s where s.booking.id = :id", Long.class).setParameter("id", bookingId).getSingleResult() == 0)
                schedule.book(b.getVehicle(), b, b.getPickupTime(), b.getPickupTime().plusSeconds(3600));
            return new long[]{d.getId(), d.getUser().getId(), b.getVehicle().getProvider().getId()};
        });
    }

    com.fptufa26.ecomotion.booking.dto.request.ResolveOperationalRiskRequest proposal(Instant pickup) {
        return new com.fptufa26.ecomotion.booking.dto.request.ResolveOperationalRiskRequest(
                OperationalResolutionType.RESCHEDULE, null, pickup, "Driver unavailable; alternative pickup");
    }

    com.fptufa26.ecomotion.booking.dto.request.RespondOperationalProposalRequest response(String action) {
        return new com.fptufa26.ecomotion.booking.dto.request.RespondOperationalProposalRequest(action, "Renter decision");
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
        org.mockito.Mockito.when(storage.store(org.mockito.ArgumentMatchers.eq("refund-proof"), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(byte[].class), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("/test-only/refund-proof.pdf");
        long refundId = ledger.findByBooking_Id(bookingId).stream().filter(t -> t.getType() == PaymentType.REFUND).findFirst().orElseThrow().getId();
        var proof = new MockMultipartFile("file", "refund.pdf", "application/pdf", "%PDF-1.4 test evidence".getBytes());
        refunds.completeRefund(providerId, refundId, proof, "JOURNEY-" + UUID.randomUUID(), "Bank transfer confirmed");
        var completed = workflow.getWorkflow(renterId, code());
        assertThat(completed.bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(completed.refundedAmount()).isEqualByComparingTo("100000");
        assertThat(completed.pendingRefundAmount()).isZero();
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

    @Test void bothPartiesEndTripThenProviderSettlesWithoutMarkingDriverFinishAsCompleted() {
        long[] driver = ready(); optimization.manualAssign(bookingId, driver[0], driver[2]);
        trips.confirmTrip(driver[1], code()); trips.arriveAtPickup(driver[1], code());
        trips.startTrip(driver[1], code()); renterBookings.confirmStart(renterId, code());
        trips.finishTrip(driver[1], code());
        assertThat(workflow.getWorkflow(renterId, code()).tripStatus()).isEqualTo("AWAITING_END_CONFIRMATION");
        assertThatThrownBy(() -> providerBookings.finalizeSettlement(providerId, code())).isInstanceOf(RuntimeException.class);
        renterBookings.confirmEnd(renterId, code());
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
        t.setDirection(PaymentDirection.IN); t.setMethod(method); t.setAmount(amount); t.setStatus(PaymentStatus.PAID);
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
        params.put("vnp_SecureHash", com.fptufa26.ecomotion.finance.config.VnpayUtils.hmacSHA512(config.getHashSecret(),
                com.fptufa26.ecomotion.finance.config.VnpayUtils.buildHashData(params)));
        return params;
    }

}
