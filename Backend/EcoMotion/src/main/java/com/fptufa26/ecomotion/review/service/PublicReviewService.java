package com.fptufa26.ecomotion.review.service;

import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.review.dto.response.PublicReviewsResponse;
import com.fptufa26.ecomotion.review.entity.Review;
import com.fptufa26.ecomotion.review.repository.ReviewRepository;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;

/** Đọc đánh giá công khai theo xe / tài xế (không lộ thông tin cá nhân người đánh giá). */
@Service
@RequiredArgsConstructor
public class PublicReviewService {

    private static final int MAX_ITEMS = 100;

    private final ReviewRepository reviewRepository;
    private final VehicleService vehicleService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public PublicReviewsResponse forVehicle(Long vehicleId) {
        Vehicle vehicle = vehicleService.findVehicleById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy xe"));
        String name = vehicle.getBrand() + " " + vehicle.getModel()
                + (vehicle.getManufactureYear() != null ? " (" + vehicle.getManufactureYear() + ")" : "");
        return build("VEHICLE", vehicleId, name, null, reviewRepository.findByVehicle_Id(vehicleId));
    }

    @Transactional(readOnly = true)
    public PublicReviewsResponse forDriver(Long driverId) {
        Driver driver = userService.findDriverById(driverId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế"));
        String name = driver.getUser() == null ? "Tài xế" : driver.getUser().getFullName();
        String avatar = driver.getPortraitUrl();
        if (avatar == null || avatar.isBlank()) {
            avatar = driver.getUser() == null ? null : driver.getUser().getAvatarUrl();
        }
        return build("DRIVER", driverId, name, avatar, reviewRepository.findByDriver_Id(driverId));
    }

    private PublicReviewsResponse build(String target, Long id, String name, String image, List<Review> reviews) {
        Long[] counts = {0L, 0L, 0L, 0L, 0L};
        long sum = 0;
        for (Review review : reviews) {
            int rating = Math.max(1, Math.min(5, review.getRating() == null ? 0 : review.getRating()));
            counts[rating - 1]++;
            sum += rating;
        }
        double average = reviews.isEmpty() ? 0 : Math.round(sum * 10.0 / reviews.size()) / 10.0;
        List<PublicReviewsResponse.Item> items = reviews.stream()
                .sorted(Comparator.comparing(Review::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_ITEMS)
                .map(review -> new PublicReviewsResponse.Item(
                        review.getId(),
                        review.getRating(),
                        review.getComment(),
                        review.getMedia() == null || review.getMedia().isBlank() ? List.of()
                                : Arrays.stream(review.getMedia().split("\n")).filter(s -> !s.isBlank()).toList(),
                        maskName(review.getBooking() == null || review.getBooking().getRenter() == null
                                || review.getBooking().getRenter().getUser() == null
                                ? null : review.getBooking().getRenter().getUser().getFullName()),
                        review.getCreatedAt()))
                .toList();
        return new PublicReviewsResponse(target, id, name, image, average, reviews.size(), Arrays.asList(counts), items);
    }

    /** "Nguyễn Văn An" → "Nguyễn V. A." ; tên một chữ giữ chữ cái đầu. */
    static String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Khách thuê";
        String[] parts = fullName.trim().split("\s+");
        if (parts.length == 1) return parts[0].charAt(0) + ".";
        StringBuilder out = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) out.append(' ').append(parts[i].charAt(0)).append('.');
        return out.toString();
    }
}
