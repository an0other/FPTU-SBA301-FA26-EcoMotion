package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.service.BookingNotificationService;
import com.fptufa26.ecomotion.notification.external.dto.request.BookingNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;
import com.fptufa26.ecomotion.notification.external.service.ExternalNotificationDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingNotificationServiceImpl implements BookingNotificationService {
    private final ExternalNotificationDispatcherService dispatcher;
    private final com.fptufa26.ecomotion.notification.internal.service.NotificationStreamService stream;

    @Override
    public void bookingPlaced(Booking booking, BigDecimal depositAmount) {
        schedule(booking, depositAmount, false);
    }

    @Override
    public void depositPaid(Booking booking, BigDecimal depositAmount) {
        schedule(booking, depositAmount, true);
    }

    @Override
    public void changed(Booking booking, String event) {
        java.util.Map<String, Object> payload = payload(booking);
        // The stream already defers delivery until commit. Scheduling it again from
        // afterCommit registers a callback too late and silently loses the event.
        stream.sendEventToProvider(booking.getVehicle().getProvider().getId(), event, payload);
        if (booking.getRenter() != null && booking.getRenter().getUser() != null)
            stream.sendEventToUser(booking.getRenter().getUser().getId(), event, payload);
        if (booking.getDriver() != null && booking.getDriver().getUser() != null)
            stream.sendEventToUser(booking.getDriver().getUser().getId(), event, payload);
    }

    @Override
    public void tripStarted(Booking booking) {
        tripEvent(booking, "TRIP_STARTED");
    }

    @Override
    public void tripEnded(Booking booking) {
        tripEvent(booking, "TRIP_COMPLETED");
    }

    private void tripEvent(Booking booking, String renterEvent) {
        java.util.Map<String, Object> payload = payload(booking);
        payload.put("actualStartAt", booking.getActualStartAt() == null ? null : booking.getActualStartAt().toString());
        payload.put("actualEndAt", booking.getActualEndAt() == null ? null : booking.getActualEndAt().toString());
        // Provider và Tài xế chỉ cần đồng bộ lại booking; Renter nhận đúng một sự kiện nghiệp vụ.
        stream.sendEventToProvider(booking.getVehicle().getProvider().getId(), "BOOKING_CHANGED", payload);
        if (booking.getDriver() != null && booking.getDriver().getUser() != null)
            stream.sendEventToUser(booking.getDriver().getUser().getId(), "BOOKING_CHANGED", payload);
        if (booking.getRenter() != null && booking.getRenter().getUser() != null)
            stream.sendEventToUser(booking.getRenter().getUser().getId(), renterEvent, payload);
    }

    @Override
    public void driverAssigned(Booking booking) {
        java.util.Map<String, Object> payload = payload(booking);
        // Mỗi bên chỉ nhận một event: Provider và Renter cập nhật booking, Tài xế mới nhận phân công.
        stream.sendEventToProvider(booking.getVehicle().getProvider().getId(), "BOOKING_CHANGED", payload);
        if (booking.getRenter() != null && booking.getRenter().getUser() != null)
            stream.sendEventToUser(booking.getRenter().getUser().getId(), "BOOKING_CHANGED", payload);
        if (booking.getDriver() != null && booking.getDriver().getUser() != null) {
            java.util.Map<String, Object> driverPayload = new java.util.HashMap<>(payload);
            driverPayload.put("pickupTime", booking.getPickupTime() == null ? null : booking.getPickupTime().toString());
            driverPayload.put("assignedAt", booking.getAssignedAt() == null ? null : booking.getAssignedAt().toString());
            stream.sendEventToUser(booking.getDriver().getUser().getId(), "DRIVER_ASSIGNED", driverPayload);
        }
    }

    private java.util.Map<String, Object> payload(Booking booking) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("bookingId", booking.getId());
        payload.put("bookingCode", booking.getBookingCode());
        payload.put("driverId", booking.getDriver() == null ? null : booking.getDriver().getId());
        payload.put("status", booking.getStatus().name());
        payload.put("version", booking.getVersion());
        payload.put("occurredAt", java.time.Instant.now().toString());
        return payload;
    }

    private void schedule(Booking booking, BigDecimal depositAmount, boolean depositPaid) {
        // Copy values before commit so delivery never needs lazy JPA relations.
        BookingNotificationRequest request = new BookingNotificationRequest(
                booking.getRenter().getUser().getEmail(), booking.getRenter().getUser().getPhone(), booking.getBookingCode(),
                booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel(),
                booking.getPickupAddress(), booking.getPickupTime(), depositAmount);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver(request, depositPaid);
                }
            });
        } else {
            deliver(request, depositPaid);
        }
    }

    private void deliver(BookingNotificationRequest request, boolean depositPaid) {
        // shortcut: failures are logged without retry; add an outbox when guaranteed delivery is required.
        try {
            NotificationResult result = depositPaid ? dispatcher.sendDepositPaid(request) : dispatcher.sendBookingPlaced(request);
            if (!result.success()) {
                log.warn("Booking notification failed for {} (depositPaid={}): {}",
                        request.bookingCode(), depositPaid, result.errorMessage());
            }
        } catch (RuntimeException ex) {
            log.warn("Booking notification failed for {} (depositPaid={})",
                    request.bookingCode(), depositPaid, ex);
        }
    }
}
