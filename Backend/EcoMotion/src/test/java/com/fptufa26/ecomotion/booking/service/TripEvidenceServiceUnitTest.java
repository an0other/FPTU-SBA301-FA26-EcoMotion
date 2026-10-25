package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.TripEvidenceResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.TripEvidence;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.TripEvidenceType;
import com.fptufa26.ecomotion.booking.exception.InvalidTripTransitionException;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.TripEvidenceRepository;
import com.fptufa26.ecomotion.booking.service.implement.TripEvidenceServiceImpl;
import com.fptufa26.ecomotion.common.cloudinary.CloudinaryService;
import com.fptufa26.ecomotion.common.cloudinary.CloudinaryUploadResult;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripEvidenceServiceUnitTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private TripEvidenceRepository tripEvidenceRepository;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private UserService userService;

    private TripEvidenceServiceImpl evidenceService;

    private User driverUser;
    private Driver driver;
    private Booking booking;

    @BeforeEach
    void setUp() {
        evidenceService = new TripEvidenceServiceImpl(
                bookingRepository,
                tripEvidenceRepository,
                cloudinaryService,
                userService
        );

        driverUser = new User();
        driverUser.setId(10L);
        driverUser.setEmail("driver@ecomotion.vn");
        driverUser.setRole(Role.DRIVER);

        driver = new Driver();
        driver.setId(20L);
        driver.setUser(driverUser);

        booking = new Booking();
        booking.setId(100L);
        booking.setBookingCode("BK-TEST01");
        booking.setStatus(BookingStatus.DRIVER_CONFIRMED);
        booking.setDriver(driver);
    }

    @Test
    @DisplayName("Throws exception when upload file is null or empty")
    void uploadEvidence_emptyFile_throwsException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", new byte[0]);

        assertThatThrownBy(() -> evidenceService.uploadEvidence(
                10L, Role.DRIVER, "BK-TEST01", emptyFile, TripEvidenceType.PICKUP_ARRIVAL, "Note"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chọn hình ảnh");
    }

    @Test
    @DisplayName("Throws 404 when booking is not found")
    void uploadEvidence_bookingNotFound_throws404() {
        MockMultipartFile file = new MockMultipartFile("file", "pickup.jpg", "image/jpeg", "image-content".getBytes());

        when(userService.findById(10L)).thenReturn(Optional.of(driverUser));
        when(bookingRepository.findByBookingCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> evidenceService.uploadEvidence(
                10L, Role.DRIVER, "UNKNOWN", file, TripEvidenceType.PICKUP_ARRIVAL, "Note"
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Throws 403 when driver is not the assigned driver of the trip")
    void uploadEvidence_unauthorizedDriver_throws403() {
        MockMultipartFile file = new MockMultipartFile("file", "pickup.jpg", "image/jpeg", "image-content".getBytes());

        Driver otherDriver = new Driver();
        otherDriver.setId(99L);
        otherDriver.setUser(driverUser);

        when(userService.findById(10L)).thenReturn(Optional.of(driverUser));
        when(bookingRepository.findByBookingCode("BK-TEST01")).thenReturn(Optional.of(booking));
        when(userService.findDriverProfile(10L)).thenReturn(Optional.of(otherDriver));

        assertThatThrownBy(() -> evidenceService.uploadEvidence(
                10L, Role.DRIVER, "BK-TEST01", file, TripEvidenceType.PICKUP_ARRIVAL, "Note"
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Throws InvalidTripTransitionException when booking is cancelled")
    void uploadEvidence_cancelledBooking_throwsInvalidTransition() {
        MockMultipartFile file = new MockMultipartFile("file", "pickup.jpg", "image/jpeg", "image-content".getBytes());
        booking.setStatus(BookingStatus.CANCELLED);

        when(userService.findById(10L)).thenReturn(Optional.of(driverUser));
        when(bookingRepository.findByBookingCode("BK-TEST01")).thenReturn(Optional.of(booking));
        when(userService.findDriverProfile(10L)).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> evidenceService.uploadEvidence(
                10L, Role.DRIVER, "BK-TEST01", file, TripEvidenceType.PICKUP_ARRIVAL, "Note"
        )).isInstanceOf(InvalidTripTransitionException.class)
                .hasMessageContaining("đã bị hủy");
    }

    @Test
    @DisplayName("Successfully uploads evidence to Cloudinary and saves to database")
    void uploadEvidence_success() {
        MockMultipartFile file = new MockMultipartFile("file", "pickup.jpg", "image/jpeg", "image-data".getBytes());

        when(userService.findById(10L)).thenReturn(Optional.of(driverUser));
        when(bookingRepository.findByBookingCode("BK-TEST01")).thenReturn(Optional.of(booking));
        when(userService.findDriverProfile(10L)).thenReturn(Optional.of(driver));

        CloudinaryUploadResult uploadResult = new CloudinaryUploadResult(
                "https://res.cloudinary.com/ecomotion/image/upload/v1/ecomotion/trip-evidence/100/xyz.jpg",
                "ecomotion/trip-evidence/100/xyz",
                "jpg",
                1024L,
                800,
                600
        );
        when(cloudinaryService.uploadTripEvidence(eq(file), eq(100L))).thenReturn(uploadResult);

        when(tripEvidenceRepository.save(any(TripEvidence.class))).thenAnswer(invocation -> {
            TripEvidence ev = invocation.getArgument(0);
            ev.setId(55L);
            return ev;
        });

        TripEvidenceResponse response = evidenceService.uploadEvidence(
                10L, Role.DRIVER, "BK-TEST01", file, TripEvidenceType.PICKUP_ARRIVAL, "Đã tới điểm đón"
        );

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(55L);
        assertThat(response.bookingId()).isEqualTo(100L);
        assertThat(response.type()).isEqualTo(TripEvidenceType.PICKUP_ARRIVAL);
        assertThat(response.imageUrl()).contains("res.cloudinary.com");
        assertThat(response.publicId()).isEqualTo("ecomotion/trip-evidence/100/xyz");
        assertThat(response.originalFileName()).isEqualTo("pickup.jpg");

        verify(tripEvidenceRepository).save(any(TripEvidence.class));
    }

    @Test
    @DisplayName("Rolls back and deletes image from Cloudinary if database save fails")
    void uploadEvidence_dbSaveFails_cleansUpCloudinaryImage() {
        MockMultipartFile file = new MockMultipartFile("file", "pickup.jpg", "image/jpeg", "image-data".getBytes());

        when(userService.findById(10L)).thenReturn(Optional.of(driverUser));
        when(bookingRepository.findByBookingCode("BK-TEST01")).thenReturn(Optional.of(booking));
        when(userService.findDriverProfile(10L)).thenReturn(Optional.of(driver));

        CloudinaryUploadResult uploadResult = new CloudinaryUploadResult(
                "https://res.cloudinary.com/ecomotion/image/upload/v1/ecomotion/trip-evidence/100/orphan.jpg",
                "ecomotion/trip-evidence/100/orphan",
                "jpg",
                1024L,
                800,
                600
        );
        when(cloudinaryService.uploadTripEvidence(eq(file), eq(100L))).thenReturn(uploadResult);
        when(tripEvidenceRepository.save(any(TripEvidence.class))).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> evidenceService.uploadEvidence(
                10L, Role.DRIVER, "BK-TEST01", file, TripEvidenceType.PICKUP_ARRIVAL, "Note"
        )).isInstanceOf(RuntimeException.class).hasMessage("DB error");

        // Verify rollback cleanup
        verify(cloudinaryService).deleteImage("ecomotion/trip-evidence/100/orphan");
    }

    @Test
    @DisplayName("Deletes evidence from Cloudinary and DB when user is uploader")
    void deleteEvidence_uploader_success() {
        TripEvidence evidence = new TripEvidence();
        evidence.setId(77L);
        evidence.setBooking(booking);
        evidence.setUploadedBy(driverUser);
        evidence.setCloudinaryPublicId("ecomotion/trip-evidence/100/to-delete");

        when(userService.findById(10L)).thenReturn(Optional.of(driverUser));
        when(bookingRepository.findByBookingCode("BK-TEST01")).thenReturn(Optional.of(booking));
        when(userService.findDriverProfile(10L)).thenReturn(Optional.of(driver));
        when(tripEvidenceRepository.findByIdAndBooking_Id(77L, 100L)).thenReturn(Optional.of(evidence));

        evidenceService.deleteEvidence(10L, Role.DRIVER, "BK-TEST01", 77L);

        verify(cloudinaryService).deleteImage("ecomotion/trip-evidence/100/to-delete");
        verify(tripEvidenceRepository).delete(evidence);
    }
}
