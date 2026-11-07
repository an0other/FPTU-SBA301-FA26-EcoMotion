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


import com.fptufa26.ecomotion.booking.service.YearlyWorkflowService;
import com.fptufa26.ecomotion.booking.dto.request.YearlyReviewRequests;
import com.fptufa26.ecomotion.finance.service.RefundEventService;
import com.fptufa26.ecomotion.finance.service.CancellationClosureService;
import com.fptufa26.ecomotion.finance.service.RefundReconciliationService;
import com.fptufa26.ecomotion.finance.service.FinancialAuditService;
import com.fptufa26.ecomotion.finance.service.gateway.implement.VnpayPaymentGatewayStrategy;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class P1ClosureIntegrationTest {
    @MockitoBean com.fptufa26.ecomotion.booking.service.MapGeocodingService geocoding;
    @MockitoBean com.fptufa26.ecomotion.notification.external.service.ExternalNotificationDispatcherService external;
    @MockitoBean FileStorageService storage;
    @MockitoBean com.fptufa26.ecomotion.location.service.implement.VietmapClient vietmap;
    @MockitoSpyBean VnpayPaymentGatewayStrategy gateway;
    @Autowired com.fptufa26.ecomotion.dispute.service.DisputeService disputes;
    @Autowired RefundEventService events;
    @Autowired CancellationClosureService closure;
    @Autowired RefundReconciliationService reviews;
    @Autowired FinancialAuditService audit;
    @Autowired PaymentRequestService payments;
    @Autowired YearlyWorkflowService yearly;
    @Autowired com.fptufa26.ecomotion.booking.service.BookingHoldExpiryService expiry;

    @org.junit.jupiter.api.AfterEach void resetConfig() { config.setRefundEnabled(false); }

    @Test void p1AdminRefundRoutesEnforceRoleValidationAndPersistRuling() throws Exception {
        long id = gatewayRefund("100000");
        String adminToken = jwt.generateAccessToken(em.find(User.class, adminId));
        String providerToken = jwt.generateAccessToken(em.find(User.class, providerId));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/refunds"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/refunds")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/refunds/{id}/reconcile", id)
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json").content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        var request = review(id, "http-admin-ruling", "TRANSFER_VERIFIED", "HTTP-VERIFIED-TRACE");
        String body = "{\"requestId\":\"http-admin-ruling\",\"version\":" + request.version()
                + ",\"outcome\":\"TRANSFER_VERIFIED\",\"note\":\"Verified with bank evidence\","
                + "\"evidenceReference\":\"bank-case-123\",\"transactionReference\":\"HTTP-VERIFIED-TRACE\"}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/refunds/{id}/reconcile", id)
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json").content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/refunds/{id}/history", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].outcome").value("TRANSFER_VERIFIED"));
        assertThat(status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test void p1OrdinaryHoldExpiresAtFifteenMinutesAndReleasesAvailability() {
        Instant now = Instant.now();
        tx.executeWithoutResult(s -> {
            ledger.findByBooking_Id(bookingId).forEach(em::remove);
            var b = em.find(Booking.class, bookingId); b.setStatus(BookingStatus.AWAITING_DEPOSIT);
            payment(b, PaymentRequestType.DEPOSIT, false, 0, PaymentRequestStatus.PENDING);
            schedule.hold(b.getVehicle(), b, b.getPickupTime(), b.getPickupTime().plusSeconds(3600), now.plusSeconds(900));
        });
        expiry.expireDueHolds(now.plusSeconds(899));
        assertThat(status()).isEqualTo(BookingStatus.AWAITING_DEPOSIT);
        expiry.expireDueHolds(now.plusSeconds(901));
        assertThat(status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(payments.findPaymentRequest(bookingId,PaymentRequestType.DEPOSIT).orElseThrow().getStatus()).isEqualTo(PaymentRequestStatus.EXPIRED);
        tx.executeWithoutResult(s -> { var b=em.find(Booking.class,bookingId);
            assertThat(schedule.isRangeFree(b.getVehicle().getId(),b.getPickupTime(),b.getPickupTime().plusSeconds(3600))).isTrue(); });
    }

    @Test void p1ExpiredHoldWithPendingGatewayMustWaitForReconciliation() {
        Instant now=Instant.now();
        tx.executeWithoutResult(s -> {
            var b=em.find(Booking.class,bookingId);b.setStatus(BookingStatus.AWAITING_DEPOSIT);
            ledger.findByBooking_Id(bookingId).forEach(t -> t.setStatus(PaymentStatus.PENDING));
            payment(b,PaymentRequestType.DEPOSIT,false,0,PaymentRequestStatus.PENDING);
            schedule.hold(b.getVehicle(),b,b.getPickupTime(),b.getPickupTime().plusSeconds(3600),now.plusSeconds(900));
        });
        expiry.expireDueHolds(now.plusSeconds(901));
        assertThat(status()).isEqualTo(BookingStatus.AWAITING_DEPOSIT);
        tx.executeWithoutResult(s -> ledger.findByBooking_Id(bookingId).forEach(t -> t.setStatus(PaymentStatus.FAILED)));
        expiry.expireDueHolds(now.plusSeconds(902));assertThat(status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test void p1ConcurrentGatewaySubmissionsSendOnlyOneCommand() throws Exception {
        long id=gatewayRefund("100000");config.setRefundEnabled(true);
        doReturn(success()).when(gateway).refund(anyString(),anyString(),nullable(String.class),anyString(),anyString(),anyLong(),anyString(),anyString());
        assertThat(race(() -> events.submitGateway(id), () -> events.submitGateway(id))).containsExactly(true,true);
        verify(gateway,times(1)).refund(anyString(),anyString(),nullable(String.class),anyString(),anyString(),anyLong(),anyString(),anyString());
        assertThat(status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test void p1OutOfOrderGatewayResultCannotOverwriteAdminRuling() {
        long id=gatewayRefund("100000");
        config.setRefundEnabled(true);var attempt=events.prepareGateway(id);
        reviews.reconcile(adminId,id,review(id,"verified-ruling","TRANSFER_VERIFIED","TRACE-VERIFIED"));
        events.applyGateway(id,attempt.requestId(),new RefundEventService.GatewayResult(false,true,"04","09",null,null,null));
        assertThat(requests.findById(id).orElseThrow().getStatus()).isEqualTo(RefundStatus.REFUNDED);
        assertThat(status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test void p1GatewaySuccessClosesCancellationAndDuplicateNeverSendsTwice() {
        long id = gatewayRefund("100000");
        config.setRefundEnabled(true);
        doReturn(success()).when(gateway).refund(anyString(),anyString(),nullable(String.class),anyString(),anyString(),anyLong(),anyString(),anyString());
        events.submitGateway(id); events.submitGateway(id);
        assertThat(requests.findById(id).orElseThrow().getStatus()).isEqualTo(RefundStatus.REFUNDED);
        assertThat(status()).isEqualTo(BookingStatus.CANCELLED);
        verify(gateway,times(1)).refund(anyString(),anyString(),nullable(String.class),anyString(),anyString(),anyLong(),anyString(),anyString());
    }

    @Test void p1GatewayPendingMustBeVerifiedBeforeRetryAndThenCanFinish() {
        long id = gatewayRefund("100000"); config.setRefundEnabled(true);
        doReturn(new VnpayPaymentGatewayStrategy.VnpayRefundCallResult(true,false,"98",null,null,null,null,null))
                .when(gateway).refund(anyString(),anyString(),nullable(String.class),anyString(),anyString(),anyLong(),anyString(),anyString());
        events.submitGateway(id); events.submitGateway(id);
        assertThat(requests.findById(id).orElseThrow().getStatus()).isEqualTo(RefundStatus.PENDING_RECONCILIATION);
        var actor=tx.execute(s->em.find(User.class,providerId));
        assertThatThrownBy(()->events.releaseForFollowUp(id,actor,true)).isInstanceOf(RuntimeException.class);
        var hold=review(id,"hold","HOLD","TRACE"); reviews.reconcile(adminId,id,hold); reviews.reconcile(adminId,id,hold);
        assertThat(audit.history("REFUND",id)).hasSize(1);
        reviews.reconcile(adminId,id,review(id,"failed","FAILED_VERIFIED","TRACE"));
        events.releaseForFollowUp(id,actor,true);
        completeBank(id,"RETRY-"+UUID.randomUUID());
        receipts.respond(renterId,id,true);
        assertThat(requests.findById(id).orElseThrow().getStatus()).isEqualTo(RefundStatus.REFUNDED);
        assertThat(status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)).hasSize(1);
        assertThat(ledger.findByBooking_Id(bookingId).stream().filter(t->t.getDirection()==PaymentDirection.OUT)).hasSize(1);
        verify(gateway,times(1)).refund(anyString(),anyString(),nullable(String.class),anyString(),anyString(),anyLong(),anyString(),anyString());
    }

    @Test void p1SuccessfulOneOfTwoRefundsDoesNotCloseCancellation() {
        long first=gatewayRefund("40000");
        var actor=tx.execute(s->em.find(User.class,renterId));
        long second=events.reserve(bookingId,"CANCEL:second-"+bookingId,new BigDecimal("60000"),"Remaining refund",actor).getFirst().getId();
        config.setRefundEnabled(true);
        doReturn(success()).when(gateway).refund(anyString(),anyString(),nullable(String.class),anyString(),anyString(),anyLong(),anyString(),anyString());
        events.submitGateway(first);
        assertThat(status()).isEqualTo(BookingStatus.CANCELLATION_REVIEW);
        assertThat(requests.findById(second).orElseThrow().getStatus()).isEqualTo(RefundStatus.PROCESSING);
    }

    @Test void p1RejectBankProofAfterCancelClosesWithoutRefundingUnreceivedMoney() {
        long pr=tx.execute(s->{
            ledger.findByBooking_Id(bookingId).forEach(em::remove);
            var b=em.find(Booking.class,bookingId);b.setStatus(BookingStatus.AWAITING_DEPOSIT);
            var row=payment(b,PaymentRequestType.DEPOSIT,false,0,PaymentRequestStatus.PROOF_SUBMITTED);
            var e=new com.fptufa26.ecomotion.finance.entity.PaymentEvidence();e.setPaymentRequest(row);e.setProofImageUrl("qa/proof.png");
            e.setSubmittedBy(em.find(User.class,renterId));row.getEvidences().add(e);em.persist(e);return row.getId();
        });
        renterBookings.cancel(renterId,code());
        assertThat(status()).isEqualTo(BookingStatus.CANCELLATION_REVIEW);
        payments.rejectPayment(pr,providerId,new RejectPaymentRequest("No funds received"));
        assertThat(status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)).isEmpty();
    }

    @Test void p1YearlyUnpaidCanCancelBeforeReceivingCar() {
        tx.executeWithoutResult(s->{
            ledger.findByBooking_Id(bookingId).forEach(em::remove);
            var b=em.find(Booking.class,bookingId);b.getPlan().setPlanType(PlanType.YEARLY);b.setStatus(BookingStatus.AWAITING_DEPOSIT);
            payment(b,PaymentRequestType.DEPOSIT,true,0,PaymentRequestStatus.PENDING);
        });
        renterBookings.cancel(renterId,code());renterBookings.cancel(renterId,code());
        assertThat(status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookings.findById(bookingId).orElseThrow().getActualStartAt()).isNull();
        assertThat(payments.findPaymentRequest(bookingId,PaymentRequestType.DEPOSIT).orElseThrow().getStatus()).isEqualTo(PaymentRequestStatus.VOIDED);
    }

    @Test void p1YearlyPaidCancelNeedsRulingThenRefundCanClose() {
        tx.executeWithoutResult(s->{
            var b=em.find(Booking.class,bookingId);b.getPlan().setPlanType(PlanType.YEARLY);b.setStatus(BookingStatus.CONFIRMED);
            payment(b,PaymentRequestType.DEPOSIT,true,0,PaymentRequestStatus.CONFIRMED);
            ledger.findByBooking_Id(bookingId).forEach(t->t.setMethod(PaymentMethod.BANK_TRANSFER));
        });
        renterBookings.cancel(renterId,code());
        assertThat(status()).isEqualTo(BookingStatus.CANCELLATION_REVIEW);
        long dispute=disputes.getDisputesByBookingId(bookingId).getFirst().getId();
        var ruling=new YearlyReviewRequests.CancellationRuling("yearly-cancel",dispute,new BigDecimal("100000"),"Verified cancellation agreement full refund","QA signed agreement");
        yearly.adjudicateCancellation(adminId,code(),ruling);yearly.adjudicateCancellation(adminId,code(),ruling);
        var row=requests.findByBooking_IdOrderByCreatedAtDesc(bookingId).getFirst();
        completeBank(row.getId(),"YEARLY-"+UUID.randomUUID());receipts.respond(renterId,row.getId(),true);
        assertThat(status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)).hasSize(1);
        assertThat(audit.history("YEARLY",bookingId)).hasSize(1);
    }

    @Test void p1YearlyCancelKeepsUnknownIncomingPaymentOpen() {
        tx.executeWithoutResult(s->{var b=em.find(Booking.class,bookingId);b.getPlan().setPlanType(PlanType.YEARLY);b.setStatus(BookingStatus.AWAITING_DEPOSIT);
            payment(b,PaymentRequestType.DEPOSIT,true,0,PaymentRequestStatus.PENDING);
            ledger.findByBooking_Id(bookingId).forEach(t->t.setStatus(PaymentStatus.PENDING));});
        renterBookings.cancel(renterId,code());assertThat(status()).isEqualTo(BookingStatus.CANCELLATION_REVIEW);
        tx.executeWithoutResult(s->ledger.findByBooking_Id(bookingId).forEach(t->t.setStatus(PaymentStatus.FAILED)));
        closure.reconcile(bookingId);assertThat(status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test void p1YearlyOpenQualityComplaintBlocksCompletionUntilResolved() {
        yearlyStage("SETTLEMENT_ACCEPTED");
        tx.executeWithoutResult(s->em.find(Booking.class,bookingId).setStatus(BookingStatus.IN_PROGRESS));
        var complaint=renterBookings.openDispute(renterId,code(),new com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest(
                com.fptufa26.ecomotion.dispute.enums.DisputeCategory.SERVICE_QUALITY,(Long)null,"Quality issue"));
        tx.executeWithoutResult(s->{var b=em.find(Booking.class,bookingId);b.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT);b.setActualEndAt(Instant.now());});
        assertThatThrownBy(()->yearly.complete(providerId,code(),new YearlyWorkflowService.Version(0L))).isInstanceOf(RuntimeException.class);
        disputes.resolveDispute(complaint.disputeId(),adminId,com.fptufa26.ecomotion.dispute.enums.DisputeStatus.RESOLVED,null,"Verified issue settled without financial award");
        yearly.complete(providerId,code(),new YearlyWorkflowService.Version(0L));assertThat(status()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test void p1NotReceivedCanBeHeldAndAdjudicatedWithAuditAndNoNewTransfer() {
        long id=bankRefund();String reference="BANK-"+UUID.randomUUID();completeBank(id,reference);
        receipts.respond(renterId,id,false);assertThat(status()).isEqualTo(BookingStatus.CANCELLATION_REVIEW);
        reviews.reconcile(adminId,id,review(id,"hold","HOLD",reference));
        assertThat(requests.findById(id).orElseThrow().getReceiptStatus()).isEqualTo("REPORTED_NOT_RECEIVED");
        var decision=review(id,"verified","TRANSFER_VERIFIED",reference);reviews.reconcile(adminId,id,decision);reviews.reconcile(adminId,id,decision);
        assertThat(status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(requests.findById(id).orElseThrow().getReceiptStatus()).isEqualTo("ADMIN_VERIFIED");
        assertThat(audit.history("REFUND",id)).hasSize(2);
        assertThat(ledger.findByBooking_Id(bookingId).stream().filter(t->t.getDirection()==PaymentDirection.OUT)).hasSize(1);
    }

    @Test void p1WrongRecipientMustNotAllowAnotherTransferUntilFundsVerifiedReturned() {
        long id=bankRefund();completeBank(id,"BANK-"+UUID.randomUUID());receipts.respond(renterId,id,false);
        reviews.reconcile(adminId,id,review(id,"wrong","WRONG_RECIPIENT","WRONG-TRACE"));
        var actor=tx.execute(s->em.find(User.class,providerId));
        assertThatThrownBy(()->events.releaseForFollowUp(id,actor,true)).isInstanceOf(RuntimeException.class);
        reviews.reconcile(adminId,id,review(id,"returned","FAILED_VERIFIED","RETURNED-TRACE"));
        events.releaseForFollowUp(id,actor,true);completeBank(id,"CORRECT-"+UUID.randomUUID());receipts.respond(renterId,id,true);
        assertThat(status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)).hasSize(1);
    }

    @Test void p1ReviewRejectsWrongRoleStaleVersionAndChangedReplay() {
        long id=gatewayRefund("100000");
        var r=review(id,"request","HOLD","TRACE");
        assertThatThrownBy(()->reviews.reconcile(providerId,id,r)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        reviews.reconcile(adminId,id,r);
        assertThatThrownBy(()->reviews.reconcile(adminId,id,new RefundReconcileRequest("request",r.version(),"TRANSFER_VERIFIED","Different","Proof","Trace"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->reviews.reconcile(adminId,id,new RefundReconcileRequest("stale",-1L,"HOLD","Note","Proof","Trace"))).isInstanceOf(RuntimeException.class);
    }

    @Test void p1YearlyDisputeAdminRulingUpdatesMoneyAndCanFinishWithoutRenterAcceptingDisputedAmount() {
        yearlyStage("RETURN_INSPECTED");
        long evidence=tx.execute(s->{
            var b=em.find(Booking.class,bookingId);b.setStatus(BookingStatus.IN_PROGRESS);b.setActualEndAt(null);b.setTotalAmount(new BigDecimal("1200000"));
            var w=em.find(YearlyWorkflow.class,bookingId);w.setRoundingUnit(new BigDecimal("100000"));w.setAnnualKm(12000L);w.setCurrentInitialKm(0L);
            payment(b,PaymentRequestType.DEPOSIT,true,0,PaymentRequestStatus.CONFIRMED);
            payment(b,PaymentRequestType.MONTHLY_INSTALLMENT,false,1,PaymentRequestStatus.CONFIRMED);
            var extra=incoming(b,PaymentMethod.BANK_TRANSFER,new BigDecimal("100000"));extra.setType(PaymentType.SETTLEMENT);
            var photo=new TripEvidence();photo.setBooking(b);photo.setUploadedBy(em.find(User.class,renterId));photo.setEvidenceType(TripEvidenceType.OTHER);photo.setImageUrl("qa/yearly-proof.png");em.persist(photo);return photo.getId();
        });
        var complaint=new YearlyReviewRequests.Complaint("complaint",yearly.get(renterId,code()).version(),"Disagree with return inspection",evidence);
        var view=yearly.disputeSettlement(renterId,code(),complaint);yearly.disputeSettlement(renterId,code(),complaint);
        long d=disputes.getDisputesByBookingId(bookingId).getFirst().getId();
        long disputedVersion = view.version();
        assertThatThrownBy(()->yearly.confirmReturn(renterId,code(),new YearlyWorkflowService.Version(disputedVersion))).isInstanceOf(RuntimeException.class);
        yearly.replyReview(providerId,code(),new YearlyReviewRequests.Reply("reply",d,"Provider evidence",evidence));
        view=yearly.get(renterId,code());
        var ruling=new YearlyReviewRequests.SettlementRuling("ruling",view.version(),d,java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")),100L,
                new BigDecimal("100000"),new BigDecimal("100000"),"Verified inspection and final charges",evidence);
        var result=yearly.adjudicateSettlement(adminId,code(),ruling);yearly.adjudicateSettlement(adminId,code(),ruling);
        assertThat(result.stage()).isEqualTo("SETTLEMENT_ACCEPTED");assertThat(result.money().balanceDue()).isZero();assertThat(result.money().depositRefund()).isZero();
        yearly.complete(providerId,code(),new YearlyWorkflowService.Version(result.version()));assertThat(status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(audit.history("YEARLY",bookingId)).hasSize(3);
    }

    @Test void p1YearlyReviewCannotBeClosedThroughGenericAdminResolve() {
        yearlyStage("SETTLEMENT_PROPOSED");
        var d=tx.execute(s->disputes.open(em.find(Booking.class,bookingId),em.find(User.class,renterId),com.fptufa26.ecomotion.dispute.enums.DisputeCategory.YEARLY_SETTLEMENT,null));
        assertThatThrownBy(()->disputes.resolveDispute(d.disputeId(),adminId,com.fptufa26.ecomotion.dispute.enums.DisputeStatus.RESOLVED,null,"Bypass"))
                .isInstanceOf(RuntimeException.class);
    }

    String code(){return bookings.findById(bookingId).orElseThrow().getBookingCode();}
    BookingStatus status(){return bookings.findById(bookingId).orElseThrow().getStatus();}
    long gatewayRefund(String amount){
        tx.executeWithoutResult(s->{em.find(Booking.class,bookingId).setStatus(BookingStatus.CANCELLATION_REVIEW);ledger.findByBooking_Id(bookingId).forEach(t->t.setVnpTxnRef("QA-"+UUID.randomUUID()));});
        return events.reserve(bookingId,"CANCEL:"+UUID.randomUUID(),new BigDecimal(amount),"Cancellation refund",tx.execute(s->em.find(User.class,renterId))).getFirst().getId();
    }
    long bankRefund(){tx.executeWithoutResult(s->{em.find(Booking.class,bookingId).setStatus(BookingStatus.CANCELLATION_REVIEW);ledger.findByBooking_Id(bookingId).forEach(t->t.setMethod(PaymentMethod.BANK_TRANSFER));});
        return events.reserve(bookingId,"CANCEL:"+UUID.randomUUID(),new BigDecimal("100000"),"Bank refund",tx.execute(s->em.find(User.class,renterId))).getFirst().getId();}
    void completeBank(long id,String reference){long out=tx.execute(s->em.find(com.fptufa26.ecomotion.finance.entity.RefundRequest.class,id).getPaymentTransaction().getId());
        refunds.completeRefund(providerId,out,new MockMultipartFile("file","proof.pdf","application/pdf","%PDF-1.4 qa".getBytes()),reference,"Verified bank transfer");}
    RefundReconcileRequest review(long id,String request,String outcome,String reference){return new RefundReconcileRequest(request,requests.findById(id).orElseThrow().getVersion(),outcome,"Verified bank/gateway evidence; failure means no transfer or recovered funds","QA trace evidence",reference);}
    VnpayPaymentGatewayStrategy.VnpayRefundCallResult success(){return new VnpayPaymentGatewayStrategy.VnpayRefundCallResult(false,true,"00","00",UUID.randomUUID().toString().replace("-",""),"02",null,null);}
    void yearlyStage(String stage){tx.executeWithoutResult(s->{var b=em.find(Booking.class,bookingId);b.getPlan().setPlanType(PlanType.YEARLY);b.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT);
        b.setActualStartAt(Instant.now().minusSeconds(86400));b.setPlannedEndAt(Instant.now().plusSeconds(3600*24*364L));b.setActualEndAt(Instant.now());
        var w=new YearlyWorkflow();w.setBookingId(bookingId);w.setStage(stage);em.persist(w);});}
    com.fptufa26.ecomotion.finance.entity.PaymentRequest payment(Booking b,PaymentRequestType type,boolean security,int n,PaymentRequestStatus status){
        var r=new com.fptufa26.ecomotion.finance.entity.PaymentRequest();r.setBooking(b);r.setPaymentType(type);r.setSecurityDeposit(security);r.setInstallmentNumber(n);
        r.setAmount(new BigDecimal("100000"));r.setStatus(status);if(status==PaymentRequestStatus.CONFIRMED)r.setPaidAmount(r.getAmount());
        r.setTransferReference("QA-"+UUID.randomUUID());r.setBankCodeSnapshot("VCB");r.setBankNameSnapshot("QA");r.setAccountNumberSnapshot("0123456789");r.setAccountHolderSnapshot("QA");em.persist(r);return r;
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
