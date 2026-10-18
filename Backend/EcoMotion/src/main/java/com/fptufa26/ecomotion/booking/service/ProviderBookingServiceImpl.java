package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.AddBookingChargeRequest;
import com.fptufa26.ecomotion.booking.dto.request.AssignDriverRequest;
import com.fptufa26.ecomotion.booking.dto.request.RejectBookingRequest;
import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverAvailabilityResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.exception.BookingAccessDeniedException;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.exception.DriverUnavailableException;
import com.fptufa26.ecomotion.booking.exception.InvalidBookingStateException;
import com.fptufa26.ecomotion.booking.exception.SettledChargeException;
import com.fptufa26.ecomotion.booking.exception.VehicleUnavailableException;
import com.fptufa26.ecomotion.booking.mapper.ProviderBookingMapper;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.vehicle.entity.VehicleImage;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderBookingServiceImpl implements ProviderBookingService {

    private final BookingRepository bookingRepository;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final BookingChargeRepository bookingChargeRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BookingPolicySnapshotRepository bookingPolicySnapshotRepository;
    private final DriverRepository driverRepository;
    private final VehicleScheduleRepository vehicleScheduleRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final ProviderBookingMapper bookingMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ProviderBookingResponse> getProviderBookings(Long userId, BookingStatus status) {
        Provider provider = resolveProvider(userId);

        List<Booking> bookings;
        if (status == null) {
            bookings = bookingRepository.findByVehicle_Provider_IdOrderByCreatedAtDesc(provider.getId());
        } else {
            bookings = bookingRepository.findByVehicle_Provider_IdAndStatusOrderByCreatedAtDesc(provider.getId(), status);
        }

        return bookings.stream()
                .map(this::mapToQueueResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderBookingDetailResponse getBookingDetail(Long userId, String bookingIdentifier) {
        Provider provider = resolveProvider(userId);
        Booking booking = findBookingAndVerifyOwnership(provider, bookingIdentifier, true);

        List<BookingCharge> charges = bookingChargeRepository.findByBookingIdWithUser(booking.getId());
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByBooking_IdOrderByCreatedAtAsc(booking.getId());
        BookingPolicySnapshot snapshot = bookingPolicySnapshotRepository.findByBooking_Id(booking.getId()).orElse(null);
        List<String> imageUrls = getVehicleImageUrls(booking.getVehicle().getId());

        return bookingMapper.toDetailResponse(booking, charges, transactions, snapshot, imageUrls);
    }

    @Override
    @Transactional
    public ProviderBookingDetailResponse acceptBooking(Long userId, String bookingIdentifier) {
        Provider provider = resolveProvider(userId);
        Booking booking = findBookingAndVerifyOwnership(provider, bookingIdentifier, false);

        // Lock for concurrent safety
        Booking lockedBooking = bookingRepository.findByIdForUpdate(booking.getId())
                .orElse(booking);

        if (lockedBooking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException(
                    "Chỉ có thể chấp nhận đơn ở trạng thái PENDING. Trạng thái hiện tại: " + lockedBooking.getStatus());
        }

        // Validate vehicle availability
        Instant startTime = lockedBooking.getPickupTime();
        int slotMinutes = lockedBooking.getPlan() != null && lockedBooking.getPlan().getSlotLengthMinutes() != null
                ? lockedBooking.getPlan().getSlotLengthMinutes() : 1440;
        Instant endTime = startTime.plus(slotMinutes, ChronoUnit.MINUTES);

        boolean scheduleOverlap = vehicleScheduleRepository.existsOverlappingSchedule(
                lockedBooking.getVehicle().getId(),
                ScheduleStatus.ACTIVE,
                startTime,
                endTime,
                lockedBooking.getId()
        );
        if (scheduleOverlap) {
            throw new VehicleUnavailableException("Xe đang bận lịch bảo trì hoặc đã bị chặn trong khung giờ thuê yêu cầu");
        }

        List<Booking> vehicleConflicts = bookingRepository.findPotentialVehicleConflicts(
                lockedBooking.getVehicle().getId(),
                List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS),
                lockedBooking.getId(),
                endTime
        );
        for (Booking vb : vehicleConflicts) {
            int vbMinutes = vb.getPlan() != null ? vb.getPlan().getSlotLengthMinutes() : 1440;
            Instant vbEnd = vb.getPickupTime().plus(vbMinutes, ChronoUnit.MINUTES);
            if (vb.getPickupTime().isBefore(endTime) && vbEnd.isAfter(startTime)) {
                throw new VehicleUnavailableException("Xe đã có đơn thuê trùng lặp thời gian di chuyển (" + vb.getBookingCode() + ")");
            }
        }

        lockedBooking.setStatus(BookingStatus.ACCEPTED);
        bookingRepository.save(lockedBooking);

        return getBookingDetail(userId, String.valueOf(lockedBooking.getId()));
    }

    @Override
    @Transactional
    public ProviderBookingDetailResponse rejectBooking(Long userId, String bookingIdentifier, RejectBookingRequest request) {
        Provider provider = resolveProvider(userId);
        Booking booking = findBookingAndVerifyOwnership(provider, bookingIdentifier, false);

        Booking lockedBooking = bookingRepository.findByIdForUpdate(booking.getId())
                .orElse(booking);

        if (lockedBooking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException(
                    "Chỉ có thể từ chối đơn ở trạng thái PENDING. Trạng thái hiện tại: " + lockedBooking.getStatus());
        }

        // Apply REJECTED status. Keep cancel_reason / cancelled_at null to satisfy DB constraint
        lockedBooking.setStatus(BookingStatus.REJECTED);
        bookingRepository.save(lockedBooking);

        return getBookingDetail(userId, String.valueOf(lockedBooking.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DriverAvailabilityResponse> getAvailableDrivers(Long userId, String bookingIdentifier) {
        Provider provider = resolveProvider(userId);
        Booking booking = findBookingAndVerifyOwnership(provider, bookingIdentifier, false);

        if (booking.getPlan() != null && booking.getPlan().getPlanType() == PlanType.YEARLY) {
            return List.of();
        }

        Instant startTime = booking.getPickupTime();
        int slotMinutes = booking.getPlan() != null && booking.getPlan().getSlotLengthMinutes() != null
                ? booking.getPlan().getSlotLengthMinutes() : 1440;
        Instant endTime = startTime.plus(slotMinutes, ChronoUnit.MINUTES);

        List<Driver> allDrivers = driverRepository.findEligibleDrivers(
                provider.getId(), DriverStatus.APPROVED, LocalDate.now());

        return allDrivers.stream()
                .map(driver -> checkDriverAvailability(driver, booking.getId(), startTime, endTime))
                .sorted(Comparator.comparing(DriverAvailabilityResponse::available).reversed())
                .toList();
    }

    @Override
    @Transactional
    public ProviderBookingDetailResponse assignDriver(Long userId, String bookingIdentifier, AssignDriverRequest request) {
        Provider provider = resolveProvider(userId);
        Booking booking = findBookingAndVerifyOwnership(provider, bookingIdentifier, false);

        if (booking.getPlan() != null && booking.getPlan().getPlanType() == PlanType.YEARLY) {
            throw new InvalidBookingStateException("Gói thuê theo năm là hình thức tự lái, không hỗ trợ gán tài xế");
        }

        if (booking.getStatus() == BookingStatus.COMPLETED
                || booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.REJECTED) {
            throw new InvalidBookingStateException("Không thể gán tài xế cho đơn ở trạng thái " + booking.getStatus());
        }

        Driver driver = driverRepository.findByIdAndProvider_Id(request.driverId(), provider.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài xế hoặc tài xế không thuộc quản lý của bạn"));

        if (driver.getStatus() != DriverStatus.APPROVED) {
            throw new DriverUnavailableException("Tài xế đang ở trạng thái " + driver.getStatus() + ", không thể phân công");
        }
        if (driver.getLicenseExpiry().isBefore(LocalDate.now())) {
            throw new DriverUnavailableException("Giấy phép lái xe của tài xế đã hết hạn ngày " + driver.getLicenseExpiry());
        }

        Instant startTime = booking.getPickupTime();
        int slotMinutes = booking.getPlan() != null && booking.getPlan().getSlotLengthMinutes() != null
                ? booking.getPlan().getSlotLengthMinutes() : 1440;
        Instant endTime = startTime.plus(slotMinutes, ChronoUnit.MINUTES);

        List<Booking> conflicts = bookingRepository.findPotentialDriverConflicts(
                driver.getId(),
                List.of(BookingStatus.ACCEPTED, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS),
                booking.getId(),
                endTime
        );

        for (Booking cb : conflicts) {
            int cbMinutes = cb.getPlan() != null ? cb.getPlan().getSlotLengthMinutes() : 1440;
            Instant cbEnd = cb.getPickupTime().plus(cbMinutes, ChronoUnit.MINUTES);
            if (cb.getPickupTime().isBefore(endTime) && cbEnd.isAfter(startTime)) {
                throw new DriverUnavailableException(
                        "Tài xế " + driver.getUser().getFullName() + " đang bận chuyến cho đơn " + cb.getBookingCode());
            }
        }

        booking.setDriver(driver);
        bookingRepository.save(booking);

        return getBookingDetail(userId, String.valueOf(booking.getId()));
    }

    @Override
    @Transactional
    public BookingChargeResponse addManualCharge(Long userId, String bookingIdentifier, AddBookingChargeRequest request) {
        Provider provider = resolveProvider(userId);
        Booking booking = findBookingAndVerifyOwnership(provider, bookingIdentifier, false);

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.REJECTED) {
            throw new InvalidBookingStateException("Không thể thêm phụ phí cho đơn ở trạng thái " + booking.getStatus());
        }

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền phụ phí phải lớn hơn 0");
        }

        Integer quantity = request.quantity();
        if (request.chargeType() == ChargeType.DEPOSIT || request.chargeType() == ChargeType.WEEKEND) {
            quantity = null;
        } else {
            if (quantity == null || quantity <= 0) {
                quantity = 1;
            }
        }

        BigDecimal unitPrice = request.unitPrice();
        if (unitPrice == null) {
            unitPrice = request.amount();
        }

        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        BookingCharge charge = new BookingCharge();
        charge.setBooking(booking);
        charge.setChargeType(request.chargeType());
        charge.setDescription(request.description());
        charge.setQuantity(quantity);
        charge.setUnitPrice(unitPrice);
        charge.setAppliedRate(request.appliedRate());
        charge.setAmount(request.amount());
        charge.setCreatedBy(currentUser);
        charge.setWaived(false);
        charge.setEvidenceUrl(request.evidenceUrl());

        bookingChargeRepository.save(charge);

        BigDecimal currentTotal = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;
        booking.setTotalAmount(currentTotal.add(request.amount()));
        bookingRepository.save(booking);

        return bookingMapper.toChargeResponse(charge);
    }

    @Override
    @Transactional
    public void removeManualCharge(Long userId, String bookingIdentifier, Long chargeId) {
        Provider provider = resolveProvider(userId);
        Booking booking = findBookingAndVerifyOwnership(provider, bookingIdentifier, false);

        BookingCharge charge = bookingChargeRepository.findByIdAndBooking_Id(chargeId, booking.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khoản phụ phí này trong đơn đặt xe"));

        boolean isPaid = paymentTransactionRepository.existsByCharge_IdAndStatus(chargeId, PaymentStatus.PAID);
        if (isPaid) {
            throw new SettledChargeException("Không thể xóa khoản phụ phí đã được khách thanh toán / tất toán");
        }

        BigDecimal currentTotal = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal newTotal = currentTotal.subtract(charge.getAmount());
        if (newTotal.compareTo(BigDecimal.ZERO) < 0) {
            newTotal = BigDecimal.ZERO;
        }
        booking.setTotalAmount(newTotal);
        bookingRepository.save(booking);

        bookingChargeRepository.delete(charge);
    }

    private DriverAvailabilityResponse checkDriverAvailability(
            Driver driver, Long currentBookingId, Instant startTime, Instant endTime) {
        List<Booking> conflicts = bookingRepository.findPotentialDriverConflicts(
                driver.getId(),
                List.of(BookingStatus.ACCEPTED, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS),
                currentBookingId,
                endTime
        );

        boolean available = true;
        String conflictReason = null;
        for (Booking cb : conflicts) {
            int cbMinutes = cb.getPlan() != null ? cb.getPlan().getSlotLengthMinutes() : 1440;
            Instant cbEnd = cb.getPickupTime().plus(cbMinutes, ChronoUnit.MINUTES);
            if (cb.getPickupTime().isBefore(endTime) && cbEnd.isAfter(startTime)) {
                available = false;
                conflictReason = "Đang được phân công cho đơn " + cb.getBookingCode();
                break;
            }
        }

        return bookingMapper.toDriverAvailabilityResponse(driver, available, conflictReason);
    }

    private Provider resolveProvider(Long userId) {
        return providerRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ nhà cung cấp cho tài khoản"));
    }

    private Booking findBookingAndVerifyOwnership(Provider provider, String identifier, boolean useFetch) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Mã hoặc ID đơn đặt xe không được để trống");
        }

        String cleanIdentifier = identifier.trim();
        Booking booking = null;

        Long numericId = parseLongOrNull(cleanIdentifier);
        if (numericId != null) {
            if (useFetch) {
                booking = bookingRepository.findDetailByIdAndProviderId(numericId, provider.getId()).orElse(null);
            } else {
                booking = bookingRepository.findByIdAndVehicle_Provider_Id(numericId, provider.getId()).orElse(null);
            }
        }

        if (booking == null) {
            if (useFetch) {
                booking = bookingRepository.findDetailByBookingCodeAndProviderId(cleanIdentifier, provider.getId()).orElse(null);
            } else {
                booking = bookingRepository.findByBookingCodeAndVehicle_Provider_Id(cleanIdentifier, provider.getId()).orElse(null);
            }
        }

        if (booking != null) {
            return booking;
        }

        // Determine if booking exists under another provider or not at all
        Booking globalBooking = null;
        if (numericId != null) {
            globalBooking = bookingRepository.findById(numericId).orElse(null);
        }
        if (globalBooking == null) {
            globalBooking = bookingRepository.findByBookingCode(cleanIdentifier).orElse(null);
        }

        if (globalBooking != null) {
            throw new BookingAccessDeniedException("Bạn không có quyền truy cập đơn đặt xe của nhà cung cấp khác");
        }

        throw new BookingNotFoundException("Không tìm thấy đơn đặt xe với thông tin: " + identifier);
    }

    private List<String> getVehicleImageUrls(Long vehicleId) {
        return vehicleImageRepository.findByVehicle_IdOrderByDisplayOrderAsc(vehicleId)
                .stream()
                .map(VehicleImage::getImageUrl)
                .toList();
    }

    private Long parseLongOrNull(String str) {
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ProviderBookingResponse mapToQueueResponse(Booking booking) {
        String vehicleName = "";
        String licensePlate = "";
        if (booking.getVehicle() != null) {
            vehicleName = (booking.getVehicle().getBrand() != null ? booking.getVehicle().getBrand() : "")
                    + " "
                    + (booking.getVehicle().getModel() != null ? booking.getVehicle().getModel() : "");
            vehicleName = vehicleName.trim();
            licensePlate = booking.getVehicle().getLicensePlate();
        }

        String renterName = "";
        if (booking.getRenter() != null && booking.getRenter().getUser() != null) {
            renterName = booking.getRenter().getUser().getFullName();
        }

        return new ProviderBookingResponse(
                booking.getBookingCode(),
                vehicleName,
                licensePlate,
                renterName,
                booking.getPickupTime(),
                booking.getPickupAddress(),
                booking.getStatus(),
                booking.getTotalAmount()
        );
    }
}
