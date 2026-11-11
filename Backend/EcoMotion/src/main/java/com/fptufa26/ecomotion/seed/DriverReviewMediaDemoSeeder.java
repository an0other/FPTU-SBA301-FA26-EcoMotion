package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.review.entity.Review;
import com.fptufa26.ecomotion.review.repository.ReviewRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Optional dev-only media examples. Existing reviews, including seed reviews, are never edited. */
@Component
@Profile("dev & !prod")
@ConditionalOnProperty(prefix = "app.demo-seed", name = {"enabled", "review-media.enabled"}, havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DriverReviewMediaDemoSeeder {
    private final BookingRepository bookings;
    private final ReviewRepository reviews;
    private final VehicleImageRepository images;

    private static final List<String> CODES = List.of(
            "BK-6A1M01", "BK-6E5R05", "BK-6J9V09", "BK-AUG-01", "BK-AUG-05", "BK-SEP-01", "BK-SEP-05");

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    @Transactional
    public void onReady() {
        int created = 0;
        for (String code : CODES) {
            if (created == 3) break;
            var booking = bookings.findByBookingCode(code).orElse(null);
            if (booking == null || booking.getStatus() != BookingStatus.COMPLETED
                    || booking.getDriver() == null || booking.getVehicle() == null) continue;
            if (reviews.findByBooking_IdAndDriver_Id(booking.getId(), booking.getDriver().getId()).isPresent()) continue;
            var urls = images.findByVehicle_IdOrderByDisplayOrderAsc(booking.getVehicle().getId()).stream()
                    .map(image -> image.getImageUrl()).filter(DriverReviewMediaDemoSeeder::safePhoto)
                    .distinct().limit(2).toList();
            if (urls.isEmpty()) continue;
            Review review = new Review();
            review.setBooking(booking);
            review.setDriver(booking.getDriver());
            review.setRating(4);
            review.setComment("[DEMO] Mẫu đánh giá có hình ảnh để kiểm tra giao diện. "
                    + "Ảnh minh họa từ bộ ảnh xe demo, không phải ảnh khách chụp hoặc bằng chứng chuyến đi thực tế.");
            review.setMedia(String.join("\n", urls));
            review.setCreatedAt(booking.getActualEndAt() == null ? Instant.now() : booking.getActualEndAt());
            reviews.saveAndFlush(review);
            var all = reviews.findByDriver_Id(booking.getDriver().getId());
            if (!all.isEmpty()) {
                int sum = all.stream().mapToInt(item -> Math.max(1, Math.min(5, item.getRating() == null ? 0 : item.getRating()))).sum();
                booking.getDriver().setRatingCount(all.size());
                booking.getDriver().setRatingAvg(java.math.BigDecimal.valueOf(sum)
                        .divide(java.math.BigDecimal.valueOf(all.size()), 1, java.math.RoundingMode.HALF_UP));
            }
            created++;
        }
        log.info("Review media demo: added {} new examples; existing reviews were preserved. "
                + "Set app.demo-seed.review-media.enabled=false to disable.", created);
    }

    private static boolean safePhoto(String url) {
        return url != null && url.length() <= 190 && !url.chars().anyMatch(Character::isWhitespace)
                && (url.startsWith("/images/") || url.startsWith("https://") || url.startsWith("http://"));
    }
}
