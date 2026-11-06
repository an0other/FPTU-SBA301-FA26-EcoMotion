package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.*;
import com.fptufa26.ecomotion.booking.repository.TripEndConfirmationRepository;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.booking.service.BookingNotificationService;
import com.fptufa26.ecomotion.dispute.repository.DisputeRepository;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import com.fptufa26.ecomotion.booking.dto.response.TripEndHistoryResponse;
import java.util.List;

@Service @RequiredArgsConstructor
public class TripEndConfirmationService {
    private final TripEndConfirmationRepository histories;
    private final BookingPaymentLedgerService ledger;
    private final BookingNotificationService notifications;
    private final DisputeRepository disputes;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entityManager;

    @Transactional(readOnly=true)
    public List<TripEndHistoryResponse> history(Long bookingId) {
        return histories.findByBooking_IdOrderByIdDesc(bookingId).stream().map(h -> new TripEndHistoryResponse(h.getId(),h.getReportedBy(),h.getReportedAt(),h.getDeadline(),h.getState(),h.getConfirmationMethod(),h.getConfirmedAt(),h.getConfirmedBy(),h.getDisputeId(),h.getAdminId(),h.getAdminNote(),h.getAdminActionAt(),h.getAdminAction())).toList();
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

    /** Tài xế kết thúc chuyến là kết thúc chốt: ghi lịch sử đã xác nhận, không mở cửa sổ phản đối của Renter. */
    @Transactional
    public void driverEnded(Booking booking, Long driverUserId) {
        var h = new TripEndConfirmation();
        h.setBooking(booking);
        h.setReportedBy(driverUserId);
        h.setReportedAt(booking.getDriverEndConfirmedAt());
        h.setDeadline(booking.getDriverEndConfirmedAt());
        h.setState("CONFIRMED");
        h.setConfirmationMethod("DRIVER");
        h.setConfirmedAt(booking.getDriverEndConfirmedAt());
        h.setConfirmedBy(driverUserId);
        histories.save(h);
    }

    @Transactional
    public Instant manual(Booking booking, Long userId) {
        if (disputes.findByBooking_Id(booking.getId()).stream().anyMatch(d -> d.getCategory()==DisputeCategory.TRIP_NOT_CONFIRMED
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
        if(booking.getStatus()!=BookingStatus.AWAITING_END_CONFIRMATION) throw new ConflictException("Đơn không còn chờ xác nhận kết thúc");
        if (disputes.findByBooking_Id(booking.getId()).stream().anyMatch(d -> !d.getId().equals(disputeId)
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
            h.setDeadline(clock.instant().plus(Duration.ofHours(12))); h.setState("WAITING"); h.setAdminId(adminId); h.setAdminActionAt(clock.instant()); h.setAdminAction(action); h.setAdminNote(note); h.setDisputeId(disputeId); histories.save(h);
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
                    || booking.getStatus()!=BookingStatus.AWAITING_END_CONFIRMATION || booking.isEndAccepted()) continue;
            boolean blocked=disputes.findByBooking_Id(booking.getId()).stream().anyMatch(d -> d.getCategory()==DisputeCategory.TRIP_NOT_CONFIRMED && (d.getStatus()==DisputeStatus.OPEN || d.getStatus()==DisputeStatus.IN_REVIEW));
            if(blocked) {h.setState("PAUSED"); continue;}
            h.setState("CONFIRMED"); h.setConfirmationMethod("AUTO"); h.setConfirmedAt(clock.instant());
            booking.setEndConfirmationMethod("AUTO"); booking.setActualEndAt(h.getReportedAt()); booking.setStatus(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
            ledger.saveBooking(booking); notifications.changed(booking,"TRIP_END_CONFIRMATION_CHANGED");
        }
    }
}
