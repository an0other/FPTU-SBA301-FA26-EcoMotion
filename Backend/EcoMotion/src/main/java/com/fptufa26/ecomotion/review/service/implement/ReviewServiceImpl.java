package com.fptufa26.ecomotion.review.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.BookingReviewContext;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.BookingReviewAccessService;
import com.fptufa26.ecomotion.review.dto.request.CreateReviewRequest;
import com.fptufa26.ecomotion.review.dto.response.ReviewResponse;
import com.fptufa26.ecomotion.review.entity.Review;
import com.fptufa26.ecomotion.review.enums.ReviewTarget;
import com.fptufa26.ecomotion.review.exception.ReviewAlreadyExistsException;
import com.fptufa26.ecomotion.review.exception.ReviewNotAllowedException;
import com.fptufa26.ecomotion.review.repository.ReviewRepository;
import com.fptufa26.ecomotion.review.service.ReviewService;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fptufa26.ecomotion.review.dto.response.BookingReviewsResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import com.fptufa26.ecomotion.review.dto.request.UpdateReviewRequest;
import com.fptufa26.ecomotion.review.exception.ReviewNotFoundException;
import com.fptufa26.ecomotion.booking.service.BookingQueryService;
import com.fptufa26.ecomotion.common.storage.service.EvidenceImageStorage;
import com.fptufa26.ecomotion.review.service.ReviewContentPolicy;
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final int RATING_SCALE = 2;

    private final ReviewRepository reviewRepository;
    private final BookingReviewAccessService bookingReviewAccessService;
    private final VehicleService vehicleService;
    private final BookingQueryService bookingQueryService;
    private final UserService userService;
    private final EntityManager entityManager;
    private final EvidenceImageStorage evidenceImageStorage;

    private static final int MAX_MEDIA = 5;
    private static final int MAX_MEDIA_COLUMN = 500;

    @Override
    @Transactional(readOnly = true)
    public String uploadReviewImage(Long userId, String bookingCode, org.springframework.web.multipart.MultipartFile file) {
        BookingReviewContext booking = bookingReviewAccessService.getOwnedBooking(userId, bookingCode);
        validateBooking(booking, ReviewTarget.VEHICLE);
        return evidenceImageStorage.store(file, userId, "review").url();
    }

    private void validateComment(String comment) {
        if (!ReviewContentPolicy.isClean(comment)) {
            throw new IllegalArgumentException(
                    "Nhận xét có từ ngữ không phù hợp. Vui lòng chỉnh lại nội dung lịch sự trước khi gửi.");
        }
    }

    private String normalizeMedia(Long userId, java.util.List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        String prefix = "/api/files/booking/" + userId + "/review-";
        java.util.List<String> clean = urls.stream()
                .filter(java.util.Objects::nonNull).map(String::trim)
                .filter(url -> !url.isEmpty()).distinct().toList();
        if (clean.size() > MAX_MEDIA) throw new IllegalArgumentException("Tối đa " + MAX_MEDIA + " ảnh cho mỗi đánh giá");
        if (clean.stream().anyMatch(url -> !url.startsWith(prefix) || !url.matches("[A-Za-z0-9/._-]+")))
            throw new IllegalArgumentException("Ảnh đánh giá không hợp lệ, vui lòng tải lại ảnh");
        String joined = String.join("\n", clean);
        if (joined.length() > MAX_MEDIA_COLUMN) throw new IllegalArgumentException("Quá nhiều ảnh cho mỗi đánh giá");
        return clean.isEmpty() ? null : joined;
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(Long userId, String bookingCode, ReviewTarget target, UpdateReviewRequest request) {
        validateUpdateRequest(target, request);

        BookingReviewContext booking =
                bookingReviewAccessService.getOwnedBooking(userId, bookingCode);

        validateBooking(booking, target);

        userService.lockProviderForReview(booking.vehicleProviderId());

        Review review = findExistingReview(booking, target);
        validateComment(request.comment());
        review.setRating(request.rating());
        review.setComment(normalizeComment(request.comment()));
        if (request.mediaUrls() != null) review.setMedia(normalizeMedia(userId, request.mediaUrls()));

        reviewRepository.flush();

        refreshTargetRating(booking, target);
        refreshProviderRating(booking.vehicleProviderId());

        return toResponse(review);
    }

    @Override
    @Transactional
    public void deleteReview(Long userId, String bookingCode, ReviewTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("Review target is required");
        }

        BookingReviewContext booking =
                bookingReviewAccessService.getOwnedBooking(userId, bookingCode);

        validateBooking(booking, target);

        userService.lockProviderForReview(booking.vehicleProviderId());

        Review review = findExistingReview(booking, target);
        reviewRepository.delete(review);
        reviewRepository.flush();

        refreshTargetRating(booking, target);
        refreshProviderRating(booking.vehicleProviderId());
    }

    private Review findExistingReview(BookingReviewContext booking, ReviewTarget target) {
        if (target == ReviewTarget.VEHICLE) {
            return reviewRepository
                    .findByBooking_IdAndVehicle_Id(
                            booking.bookingId(),
                            booking.vehicleId())
                    .orElseThrow(ReviewNotFoundException::new);
        }

        return reviewRepository
                .findByBooking_IdAndDriver_Id(
                        booking.bookingId(),
                        booking.driverId())
                .orElseThrow(ReviewNotFoundException::new);
    }

    private void validateUpdateRequest(ReviewTarget target, UpdateReviewRequest request) {
        if (target == null
                || request == null
                || request.rating() == null
                || request.rating() < 1
                || request.rating() > 5) {
            throw new IllegalArgumentException("Invalid review update request");
        }

        if (request.comment() != null && request.comment().length() > 1000) {
            throw new IllegalArgumentException(
                    "Review comment must not exceed 1000 characters");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BookingReviewsResponse getBookingReviews(
            Long userId,
            String bookingCode) {
        BookingReviewContext booking =
                bookingReviewAccessService.getOwnedBooking(userId, bookingCode);

        List<ReviewResponse> reviews = reviewRepository
                .findByBooking_BookingCodeOrderByCreatedAtAscIdAsc(
                        booking.bookingCode())
                .stream()
                .map(this::toResponse)
                .toList();

        boolean canReview = booking.status() == BookingStatus.COMPLETED;

        return new BookingReviewsResponse(
                booking.bookingCode(),
                booking.status(),
                booking.vehicleName(),
                booking.driverId() != null,
                booking.driverName(),
                canReview,
                canReview
                        ? null
                        : "Bạn chỉ có thể đánh giá sau khi đơn thuê hoàn tất.",
                reviews);
    }

    @Override
    @Transactional
    public ReviewResponse createReview(
            Long userId,
            String bookingCode,
            CreateReviewRequest request) {
        validateRequest(request);

        BookingReviewContext booking =
                bookingReviewAccessService.getOwnedBooking(userId, bookingCode);

        validateBooking(booking, request.target());

        userService.lockProviderForReview(booking.vehicleProviderId());

        validateDuplicate(booking, request.target());

        Review review = new Review();
        review.setBooking(bookingQueryService.findBookingById(booking.bookingId())
                .orElseThrow(() -> new ReviewNotAllowedException("Không tìm thấy đơn thuê")));
        validateComment(request.comment());
        review.setRating(request.rating());
        review.setComment(normalizeComment(request.comment()));
        review.setMedia(normalizeMedia(userId, request.mediaUrls()));

        if (request.target() == ReviewTarget.VEHICLE) {
            review.setVehicle(vehicleService.findVehicleById(booking.vehicleId())
                    .orElseThrow(() -> new ReviewNotAllowedException("Không tìm thấy xe của đơn thuê")));
        } else {
            review.setDriver(userService.findDriverById(booking.driverId())
                    .orElseThrow(() -> new ReviewNotAllowedException("Không tìm thấy tài xế của đơn thuê")));
        }

        Review saved = reviewRepository.saveAndFlush(review);

        refreshTargetRating(booking, request.target());
        refreshProviderRating(booking.vehicleProviderId());

        return toResponse(saved);
    }

    private void validateRequest(CreateReviewRequest request) {
        if (request == null
                || request.target() == null
                || request.rating() == null
                || request.rating() < 1
                || request.rating() > 5) {
            throw new IllegalArgumentException("Invalid review request");
        }

        if (request.comment() != null && request.comment().length() > 1000) {
            throw new IllegalArgumentException(
                    "Review comment must not exceed 1000 characters");
        }
    }

    private void validateBooking(
            BookingReviewContext booking,
            ReviewTarget target) {
        if (booking.status() != BookingStatus.COMPLETED) {
            throw new ReviewNotAllowedException(
                    "Booking must be completed before reviewing");
        }

        if (target == ReviewTarget.DRIVER) {
            if (booking.driverId() == null) {
                throw new ReviewNotAllowedException(
                        "This booking has no driver to review");
            }

            if (!Objects.equals(
                    booking.vehicleProviderId(),
                    booking.driverProviderId())) {
                throw new ReviewNotAllowedException(
                        "Booking vehicle and driver must belong to the same provider");
            }
        }
    }

    private void validateDuplicate(
            BookingReviewContext booking,
            ReviewTarget target) {
        boolean exists;

        if (target == ReviewTarget.VEHICLE) {
            exists = reviewRepository.existsByBooking_IdAndVehicle_Id(
                    booking.bookingId(),
                    booking.vehicleId());
        } else {
            exists = reviewRepository.existsByBooking_IdAndDriver_Id(
                    booking.bookingId(),
                    booking.driverId());
        }

        if (exists) {
            throw new ReviewAlreadyExistsException();
        }
    }

    private void refreshTargetRating(
            BookingReviewContext booking,
            ReviewTarget target) {
        if (target == ReviewTarget.VEHICLE) {
            List<Review> reviews =
                    reviewRepository.findByVehicle_Id(booking.vehicleId());

            vehicleService.updateRating(
                    booking.vehicleId(),
                    calculateAverage(reviews),
                    reviews.size());
        } else {
            List<Review> reviews =
                    reviewRepository.findByDriver_Id(booking.driverId());

            userService.updateDriverRating(
                    booking.driverId(),
                    calculateAverage(reviews),
                    reviews.size());
        }
    }

    private void refreshProviderRating(Long providerId) {
        List<Review> reviews = reviewRepository
                .findByVehicle_Provider_IdOrDriver_Provider_Id(
                        providerId,
                        providerId);

        userService.updateProviderRating(
                providerId,
                calculateAverage(reviews),
                reviews.size());
    }

    private BigDecimal calculateAverage(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return BigDecimal.ZERO.setScale(RATING_SCALE);
        }

        BigDecimal sum = reviews.stream()
                .map(review -> BigDecimal.valueOf(review.getRating()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(
                BigDecimal.valueOf(reviews.size()),
                RATING_SCALE,
                RoundingMode.HALF_UP);
    }

    private String normalizeComment(String comment) {
        return comment == null || comment.isBlank()
                ? null
                : comment.trim();
    }

    private ReviewResponse toResponse(Review review) {
        ReviewTarget target = review.getVehicle() != null
                ? ReviewTarget.VEHICLE
                : ReviewTarget.DRIVER;

        return new ReviewResponse(
                review.getBooking().getBookingCode(),
                target,
                review.getRating(),
                review.getComment(),
                review.getMedia(),
                review.getCreatedAt(),
                review.getMedia() == null || review.getMedia().isBlank()
                        ? java.util.List.of()
                        : java.util.Arrays.stream(review.getMedia().split("\n")).filter(s -> !s.isBlank()).toList());
    }
}