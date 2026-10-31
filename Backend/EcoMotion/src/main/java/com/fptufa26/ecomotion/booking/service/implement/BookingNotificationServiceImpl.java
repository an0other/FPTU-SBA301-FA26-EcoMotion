package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.service.BookingNotificationService;
import com.fptufa26.ecomotion.notification.external.dto.request.BookingNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
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
    private final com.fptufa26.ecomotion.common.service.NotificationStreamService stream;

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
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("bookingId", booking.getId());
        payload.put("bookingCode", booking.getBookingCode());
        payload.put("driverId", booking.getDriver() == null ? null : booking.getDriver().getId());
        payload.put("status", booking.getStatus().name());
        payload.put("version", booking.getVersion());
        payload.put("occurredAt", java.time.Instant.now().toString());
        stream.sendEventToProvider(booking.getVehicle().getProvider().getId(), event, payload);
        if (booking.getRenter() != null && booking.getRenter().getUser() != null) stream.sendEventToUser(booking.getRenter().getUser().getId(), event, payload);
        if (booking.getDriver() != null && booking.getDriver().getUser() != null) stream.sendEventToUser(booking.getDriver().getUser().getId(), event, payload);
    }

    private void schedule(Booking booking, BigDecimal depositAmount, boolean depositPaid) {
        // Copy values before commit so delivery never needs lazy JPA relations.
        BookingNotificationRequest request = new BookingNotificationRequest(
                booking.getRenter().getUser().getEmail(), booking.getBookingCode(),
                booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel(),
                booking.getPickupAddress(), booking.getPickupTime(), depositAmount, NotificationChannel.EMAIL);
        String phone = booking.getRenter().getUser().getPhone();
        BookingNotificationRequest sms = phone == null || phone.isBlank() ? null : new BookingNotificationRequest(
                phone, request.bookingCode(), request.vehicleName(), request.pickupAddress(),
                request.pickupTime(), request.depositAmount(), NotificationChannel.SMS);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliverWithFallback(request, sms, depositPaid);
                }
            });
        } else {
            deliverWithFallback(request, sms, depositPaid);
        }
    }

    private void deliverWithFallback(BookingNotificationRequest email, BookingNotificationRequest sms, boolean depositPaid) {
        if (!deliver(email, depositPaid) && sms != null) {
            deliver(sms, depositPaid);
        }
    }

    private boolean deliver(BookingNotificationRequest request, boolean depositPaid) {
        // shortcut: failures are logged without retry; add an outbox when guaranteed delivery is required.
        try {
            NotificationResult result = depositPaid ? dispatcher.sendDepositPaid(request) : dispatcher.sendBookingPlaced(request);
            if (!result.success()) {
                log.warn("Booking notification failed for {} via {} (depositPaid={}): {}",
                        request.bookingCode(), request.channel(), depositPaid, result.errorMessage());
            }
            return result.success();
        } catch (RuntimeException ex) {
            log.warn("Booking notification failed for {} via {} (depositPaid={})",
                    request.bookingCode(), request.channel(), depositPaid, ex);
            return false;
        }
    }
}
