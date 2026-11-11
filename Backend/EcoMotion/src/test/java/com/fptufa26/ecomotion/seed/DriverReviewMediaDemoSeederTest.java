package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.review.entity.Review;
import com.fptufa26.ecomotion.review.repository.ReviewRepository;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleImage;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DriverReviewMediaDemoSeederTest {
    final BookingRepository bookings = mock(BookingRepository.class);
    final ReviewRepository reviews = mock(ReviewRepository.class);
    final VehicleImageRepository images = mock(VehicleImageRepository.class);
    final DriverReviewMediaDemoSeeder seeder = new DriverReviewMediaDemoSeeder(bookings, reviews, images);

    Booking fixture() {
        Booking b = new Booking(); b.setId(50L); b.setStatus(BookingStatus.COMPLETED);
        Driver d = new Driver(); d.setId(7L); b.setDriver(d);
        Vehicle v = new Vehicle(); v.setId(9L); b.setVehicle(v);
        when(bookings.findByBookingCode("BK-6A1M01")).thenReturn(Optional.of(b));
        return b;
    }
    @Test void existingUserReviewIsNeverEditedEvenWhenItHasNoPhotos() {
        fixture(); Review existing = new Review(); existing.setComment("My original review");
        when(reviews.findByBooking_IdAndDriver_Id(50L, 7L)).thenReturn(Optional.of(existing));
        seeder.onReady();
        assertThat(existing.getComment()).isEqualTo("My original review");
        assertThat(existing.getMedia()).isNull();
        verifyNoInteractions(images); verify(reviews, never()).saveAndFlush(any());
    }
    @Test void insertsExplicitDemoOnceAndDoesNotTouchBookingState() {
        Booking b = fixture(); VehicleImage photo = new VehicleImage(); photo.setImageUrl("/images/demo/car.webp");
        when(images.findByVehicle_IdOrderByDisplayOrderAsc(9L)).thenReturn(List.of(photo, photo));
        when(reviews.saveAndFlush(any())).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            assertThat(review.getComment()).startsWith("[DEMO]");
            assertThat(review.getMedia()).isEqualTo("/images/demo/car.webp");
            assertThat(review.getVehicle()).isNull();
            when(reviews.findByBooking_IdAndDriver_Id(50L, 7L)).thenReturn(Optional.of(review));
            return review;
        });
        seeder.onReady(); seeder.onReady();
        verify(reviews, times(1)).saveAndFlush(any());
        assertThat(b.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }
    @Test void activeTripsCannotReceiveSeededReviews() {
        Booking b = fixture(); b.setStatus(BookingStatus.IN_PROGRESS);
        seeder.onReady(); verifyNoInteractions(images, reviews);
    }
}
