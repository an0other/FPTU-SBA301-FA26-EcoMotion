package com.fptufa26.ecomotion.booking.job;
import com.fptufa26.ecomotion.booking.service.implement.TripEndConfirmationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component @RequiredArgsConstructor
public class TripEndConfirmationJob {
    private final TripEndConfirmationService confirmations;
    @Scheduled(fixedDelayString="${app.trip-end.check-ms:10000}",initialDelayString="${app.trip-end.check-ms:10000}")
    public void check(){ confirmations.confirmExpired(); }
}
