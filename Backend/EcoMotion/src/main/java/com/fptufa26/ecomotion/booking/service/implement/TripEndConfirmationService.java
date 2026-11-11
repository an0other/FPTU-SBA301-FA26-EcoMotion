package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.*;
import com.fptufa26.ecomotion.booking.repository.TripEndConfirmationRepository;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.booking.service.BookingNotificationService;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import com.fptufa26.ecomotion.booking.dto.response.TripEndHistoryResponse;
import java.util.List;
import java.util.Set;
import com.fptufa26.ecomotion.dispute.service.DisputeQueryService;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.finance.service.RefundEventService;

@Service @RequiredArgsConstructor
public class TripEndConfirmationService {
    public static final Duration OBJECTION_WINDOW = Duration.ofHours(12);
    private static final Set<String> OPEN_STATES = Set.of("WAITING", "PAUSED");
    private static final Set<BookingStatus> SETTLEMENT_STATUSES = Set.of(
            BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION, BookingStatus.AWAITING_FINAL_PAYMENT);

    private final TripEndConfirmationRepository histories;
    private final BookingPaymentLedgerService ledger;
    private final BookingNotificationService notifications;
    private final org.springframework.beans.factory.ObjectProvider<DisputeQueryService> disputeQueries;
    private final Clock clock;
    private final org.springframework.beans.factory.ObjectProvider<PaymentRequestService> paymentRequests;
    private final jakarta.persistence.EntityManager entityManager;
    private final org.springframework.beans.factory.ObjectProvider<RefundEventService> refundEvents;

    @Transactional(readOnly=true)
    public List<TripEndHistoryResponse> history(Long bookingId) {
        return histories.findByBooking_IdOrderByIdDesc(bookingId).stream().map(h -> new TripEndHistoryResponse(h.getId(),h.getReportedBy(),h.getReportedAt(),h.getDeadline(),h.getState(),h.getConfirmationMethod(),h.getConfirmedAt(),h.getConfirmedBy(),h.getDisputeId(),h.getAdminId(),h.getAdminNote(),h.getAdminActionAt(),h.getAdminAction(),h.getCorrectedEndAt())).toList();
    }

    @Transactional
    public void reported(Booking booking, Long userId) {
        var previous=histories.findFirstByBooking_IdOrderByIdDesc(booking.getId());
        if(previous.isPresent() && !"CANCELLED".equals(previous.get().getState())) return;
        var h=new TripEndConfirmation(); h.setBooking(booking); h.setReportedBy(userId);
        h.setReportedAt(booking.getDriverEndConfirmedAt()); h.setDeadline(h.getReportedAt().plus(Duration.ofHours(12)));
        h.setState(booking.isEndAccepted()?"CONFIRMED":"WAITING");
        if(booking.isEndAccepted()) { h.setConfirmationMethod("RENTER"); h.setConfirmedBy(booking.getRenter().getUser().getId()); h.setConfirmedAt(booking.getRenterEndConfirmedAt()!=null?booking.getRenterEndConfirmedAt():booking.getRenterConfirmedAt()); }
        histories.save(h);
    }

    @Transactional
    public void driverEnded(Booking booking, Long driverUserId) {
        var h = new TripEndConfirmation();
        h.setBooking(booking);
        h.setReportedBy(driverUserId);
        h.setReportedAt(booking.getDriverEndConfirmedAt());
        h.setDeadline(booking.getDriverEndConfirmedAt().plus(OBJECTION_WINDOW));
        h.setState("WAITING");
        histories.save(h);
    }

    @Transactional(readOnly = true)
    public boolean isEndSettled(Long bookingId) {
        return histories.findFirstByBooking_IdOrderByIdDesc(bookingId)
                .map(h -> !OPEN_STATES.contains(h.getState()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean canObject(Booking booking) {
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.CANCELLED) return false;
        return histories.findFirstByBooking_IdOrderByIdDesc(booking.getId())
                .filter(h -> "WAITING".equals(h.getState()) && h.getDeadline().isAfter(clock.instant()))
                .isPresent();
    }

    @Transactional
    public void renterConfirm(Booking booking, Long renterUserId) {
        var h = histories.findFirstByBooking_IdOrderByIdDesc(booking.getId())
                .orElseThrow(() -> new ConflictException("Chuyến chưa được tài xế kết thúc"));
        if ("CONFIRMED".equals(h.getState())) return;
        if (!"WAITING".equals(h.getState()))
            throw new ConflictException("Giờ kết thúc đang được admin xác minh theo khiếu nại của bạn");
        Instant now = clock.instant();
        h.setState("CONFIRMED"); h.setConfirmationMethod("RENTER"); h.setConfirmedAt(now); h.setConfirmedBy(renterUserId);
        histories.save(h);
        booking.setRenterEndConfirmedAt(now);
        ledger.saveBooking(booking);
        notifications.changed(booking, "TRIP_END_CONFIRMATION_CHANGED");
        completeIfReady(booking);
    }

    @Transactional
    public void confirmedByPayment(Booking booking, Long renterUserId) {
        histories.findFirstByBooking_IdOrderByIdDesc(booking.getId())
                .filter(h -> "WAITING".equals(h.getState()))
                .ifPresent(h -> {
                    h.setState("CONFIRMED"); h.setConfirmationMethod("PAYMENT");
                    h.setConfirmedAt(clock.instant()); h.setConfirmedBy(renterUserId);
                    histories.save(h);
                });
    }

    @Transactional
    public void adminSettled(Booking booking, Long disputeId, Long adminId, String note) {
        adminSettled(booking, disputeId, adminId, note, null);
    }

    @Transactional
    public void adminSettled(Booking booking, Long disputeId, Long adminId, String note, Instant correctedEndAt) {
        histories.findFirstByBooking_IdOrderByIdDesc(booking.getId())
                .filter(h -> OPEN_STATES.contains(h.getState()))
                .ifPresent(h -> {
                    h.setState("CONFIRMED"); h.setConfirmationMethod("ADMIN"); h.setConfirmedAt(clock.instant());
                    h.setConfirmedBy(adminId); h.setAdminId(adminId); h.setAdminActionAt(clock.instant());
                    h.setAdminNote(note); h.setDisputeId(disputeId); h.setCorrectedEndAt(correctedEndAt);
                    histories.save(h);
                });
        notifications.changed(booking, "TRIP_END_CONFIRMATION_CHANGED");
        completeIfReady(booking);
    }

    @Transactional
    public void completeIfReady(Booking booking) {
        if (booking.getSettlementRequestedAt() == null || !SETTLEMENT_STATUSES.contains(booking.getStatus())) return;
        if (!isEndSettled(booking.getId())) return;
        boolean activeDispute = disputeQueries.getObject().findByBooking(booking.getId()).stream()
                .anyMatch(d -> d.getStatus() == DisputeStatus.OPEN || d.getStatus() == DisputeStatus.IN_REVIEW);
        if (activeDispute) return;
        if (paymentRequests.getObject().calculateRemainingAmount(booking.getId()).signum() > 0) return;
        if (refundEvents.getObject().hasOpenExcessRefunds(booking.getId())) return;
        booking.setStatus(BookingStatus.COMPLETED);
        ledger.saveBooking(booking);
        notifications.changed(booking, "BOOKING_CHANGED");
    }

    @Transactional
    public Instant manual(Booking booking, Long userId) {
        if (disputeQueries.getObject().findByBooking(booking.getId()).stream().anyMatch(d -> d.getCategory()==DisputeCategory.TRIP_NOT_CONFIRMED
                && (d.getStatus()==DisputeStatus.OPEN || d.getStatus()==DisputeStatus.IN_REVIEW)))
            throw new ConflictException("Khiếu nại kết thúc đang chờ admin xác minh");
        var h=histories.findFirstByBooking_IdOrderByIdDesc(booking.getId()).orElse(null);
        if(h==null) return booking.getDriverEndConfirmedAt()!=null?booking.getDriverEndConfirmedAt():booking.getProviderConfirmedAt();
        if("PAUSED".equals(h.getState())) throw new ConflictException("Khiếu nại kết thúc đang chờ admin xác minh");
        if("WAITING".equals(h.getState())) { h.setState("CONFIRMED"); h.setConfirmationMethod("RENTER"); h.setConfirmedAt(clock.instant()); h.setConfirmedBy(userId); }
        return h.getReportedAt();
    }

    @Transactional(readOnly=true)
    public void ensureCanObject(Booking booking) {
        var h=histories.findFirstByBooking_IdOrderByIdDesc(booking.getId()).orElse(null);
        if(h!=null && "WAITING".equals(h.getState()) && !h.getDeadline().isAfter(clock.instant()))
            throw new ConflictException("Đã hết thời hạn phản đối. Vui lòng tải lại trạng thái chuyến");
    }

    @Transactional
    public void pause(Booking booking, Long disputeId) {
        histories.findFirstByBooking_IdOrderByIdDesc(booking.getId()).ifPresent(h -> {
            if("WAITING".equals(h.getState())) { h.setState("PAUSED"); h.setDisputeId(disputeId); }
        });
    }

    @Transactional
    public void adminAction(Booking booking, Long disputeId, Long adminId, String action, String note) {
        if(!List.of("RESUME_TRIP","REOPEN_CONFIRMATION").contains(action)) throw new ConflictException("Hành động kết thúc không hợp lệ");
        if(booking.getStatus()!=BookingStatus.AWAITING_END_CONFIRMATION && booking.getStatus()!=BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION) throw new ConflictException("Đơn không còn ở bước xác nhận kết thúc");
        if (disputeQueries.getObject().findByBooking(booking.getId()).stream().anyMatch(d -> !d.getId().equals(disputeId)
                && d.getCategory()==DisputeCategory.TRIP_NOT_CONFIRMED
                && (d.getStatus()==DisputeStatus.OPEN || d.getStatus()==DisputeStatus.IN_REVIEW)))
            throw new ConflictException("Còn khiếu nại kết thúc khác cần được xử lý trước");
        var old=histories.findFirstByBooking_IdOrderByIdDesc(booking.getId()).orElse(null);
        if(old!=null && !List.of("WAITING","PAUSED").contains(old.getState())) throw new ConflictException("Lần báo kết thúc đã được chốt");
        Instant reported=old!=null?old.getReportedAt():booking.getDriverEndConfirmedAt()!=null?booking.getDriverEndConfirmedAt():booking.getProviderConfirmedAt();
        if(reported==null) throw new ConflictException("Không có bằng chứng tài xế báo kết thúc");
        if(old!=null) { old.setState("CANCELLED"); old.setAdminId(adminId); old.setAdminActionAt(clock.instant()); old.setAdminAction(action); old.setAdminNote(note); old.setDisputeId(disputeId); }
        if("RESUME_TRIP".equals(action)) {
            booking.setStatus(BookingStatus.IN_PROGRESS); booking.setDriverEndConfirmedAt(null); booking.setProviderConfirmedAt(null);
            booking.setRenterEndConfirmedAt(null); booking.setRenterConfirmedAt(null); booking.setActualEndAt(null); booking.setEndConfirmationMethod(null);
        } else {
            var h=new TripEndConfirmation(); h.setBooking(booking); h.setReportedAt(reported); h.setReportedBy(old==null?null:old.getReportedBy());
            h.setDeadline(clock.instant().plus(OBJECTION_WINDOW)); h.setState("WAITING"); h.setAdminId(adminId); h.setAdminActionAt(clock.instant()); h.setAdminAction(action); h.setAdminNote(note); h.setDisputeId(disputeId); histories.save(h);
        }
        ledger.saveBooking(booking); notifications.changed(booking,"TRIP_END_CONFIRMATION_CHANGED");
    }

    @Transactional
    public void confirmExpired() {
        for(var candidate:histories.findByStateAndDeadlineLessThanEqual("WAITING",clock.instant())) {
            Booking booking=ledger.requireBookingForUpdate(candidate.getBooking().getId());
            var h=histories.findFirstByBooking_IdOrderByIdDesc(booking.getId()).orElse(null);
            if(h!=null) entityManager.refresh(h);
            if(h==null || !h.getId().equals(candidate.getId()) || !"WAITING".equals(h.getState()) || h.getDeadline().isAfter(clock.instant())
                    || booking.getStatus()==BookingStatus.CANCELLED) continue;
            boolean blocked=disputeQueries.getObject().findByBooking(booking.getId()).stream().anyMatch(d -> d.getCategory()==DisputeCategory.TRIP_NOT_CONFIRMED && (d.getStatus()==DisputeStatus.OPEN || d.getStatus()==DisputeStatus.IN_REVIEW));
            if(blocked) {h.setState("PAUSED"); continue;}
            h.setState("CONFIRMED"); h.setConfirmationMethod("AUTO"); h.setConfirmedAt(clock.instant());
            if(booking.getStatus()==BookingStatus.AWAITING_END_CONFIRMATION) {
                booking.setEndConfirmationMethod("AUTO"); booking.setActualEndAt(h.getReportedAt()); booking.setStatus(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
            }
            ledger.saveBooking(booking); notifications.changed(booking,"TRIP_END_CONFIRMATION_CHANGED");
            completeIfReady(booking);
        }
    }
}
