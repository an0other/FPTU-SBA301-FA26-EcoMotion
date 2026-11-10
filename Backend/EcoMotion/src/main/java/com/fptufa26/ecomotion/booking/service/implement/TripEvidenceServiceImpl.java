package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.TripEvidenceResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.TripEvidence;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.TripEvidenceType;
import com.fptufa26.ecomotion.booking.exception.InvalidTripTransitionException;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.TripEvidenceRepository;
import com.fptufa26.ecomotion.booking.service.TripEvidenceService;
import com.fptufa26.ecomotion.common.storage.service.FileStorageService;
import com.fptufa26.ecomotion.common.storage.exception.FileStorageException;
import java.io.IOException;
import java.util.UUID;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;

@Service
@Slf4j
public class TripEvidenceServiceImpl implements TripEvidenceService {

    private final BookingRepository bookingRepository;
    private final TripEvidenceRepository tripEvidenceRepository;
    private final FileStorageService fileStorageService;
    private final UserService userService;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public TripEvidenceServiceImpl(
            BookingRepository bookingRepository,
            TripEvidenceRepository tripEvidenceRepository,
            FileStorageService fileStorageService,
            UserService userService,
            @Autowired(required = false) PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.tripEvidenceRepository = tripEvidenceRepository;
        this.fileStorageService = fileStorageService;
        this.userService = userService;
        this.transactionTemplate = transactionManager != null ? new TransactionTemplate(transactionManager) : null;
    }

    public TripEvidenceServiceImpl(
            BookingRepository bookingRepository,
            TripEvidenceRepository tripEvidenceRepository,
            FileStorageService fileStorageService,
            UserService userService) {
        this(bookingRepository, tripEvidenceRepository, fileStorageService, userService, null);
    }

    @Override
    public TripEvidenceResponse uploadEvidence(
            Long currentUserId,
            Role currentUserRole,
            String bookingIdOrCode,
            MultipartFile file,
            TripEvidenceType type,
            String note) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn hình ảnh minh chứng để tải lên");
        }
        if (type == null) {
            throw new IllegalArgumentException("Phân loại minh chứng không được để trống");
        }

        User currentUser = userService.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        Booking booking = resolveBooking(bookingIdOrCode);
        Driver assignedDriver = validateAndResolveAccess(currentUser, currentUserRole, booking);
        if (booking.getPlan().getPlanType() != PlanType.YEARLY) {
            validateBookingStatusForEvidenceType(booking.getStatus(), type);
        } else if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.REJECTED) {
            throw new InvalidTripTransitionException("Không tải minh chứng mới cho đơn đã hủy/từ chối");
        }

        String mediaType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
        String extension = switch (mediaType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> throw new IllegalArgumentException("Ảnh minh chứng chỉ hỗ trợ JPG hoặc PNG.");
        };
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new FileStorageException("Không đọc được ảnh minh chứng", ex);
        }
        boolean validSignature = extension.equals(".jpg")
                ? content.length >= 3 && (content[0] & 255) == 255 && (content[1] & 255) == 216 && (content[2] & 255) == 255
                : content.length >= 8 && content[0] == (byte)137 && content[1] == 80 && content[2] == 78
                  && content[3] == 71 && content[4] == 13 && content[5] == 10 && content[6] == 26 && content[7] == 10;
        if (!validSignature) throw new IllegalArgumentException("Nội dung ảnh không khớp định dạng JPG/PNG.");
        String storedPath = fileStorageService.store("booking", currentUserId,
                "evidence-" + UUID.randomUUID() + extension, content, mediaType);

        Instant now = Instant.now();
        TripEvidence evidence = new TripEvidence();
        evidence.setBooking(booking);
        evidence.setDriver(assignedDriver);
        evidence.setUploadedBy(currentUser);
        evidence.setEvidenceType(type);
        evidence.setImageUrl(storedPath);
        evidence.setOriginalFileName(file.getOriginalFilename());
        evidence.setFileSize((long) content.length);
        evidence.setNote(note != null ? note.trim() : null);
        evidence.setRecordedAt(now);
        evidence.setCreatedAt(now);

        try {
            TripEvidence saved;
            if (transactionTemplate != null) {
                saved = transactionTemplate.execute(status -> tripEvidenceRepository.save(evidence));
            } else {
                saved = tripEvidenceRepository.save(evidence);
            }
            log.info("Saved local trip evidence #{} for booking {}", saved != null ? saved.getId() : null, booking.getBookingCode());
            return TripEvidenceResponse.fromEntity(saved);
        } catch (Exception ex) {
            try {
                fileStorageService.delete(storedPath);
            } catch (RuntimeException cleanupError) {
                ex.addSuppressed(cleanupError);
            }
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripEvidenceResponse> listEvidence(
            Long currentUserId,
            Role currentUserRole,
            String bookingIdOrCode) {

        User currentUser = userService.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        Booking booking = resolveBooking(bookingIdOrCode);
        validateAndResolveAccess(currentUser, currentUserRole, booking);

        return tripEvidenceRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId())
                .stream()
                .map(TripEvidenceResponse::fromEntity)
                .toList();
    }

    @Override
    public void deleteEvidence(
            Long currentUserId,
            Role currentUserRole,
            String bookingIdOrCode,
            Long evidenceId) {

        User currentUser = userService.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        Booking booking = resolveBooking(bookingIdOrCode);
        validateAndResolveAccess(currentUser, currentUserRole, booking);

        if (booking.getPlan().getPlanType() == PlanType.YEARLY) {
            throw new AccessDeniedException("Minh chứng gói năm phải được giữ để đối chiếu, không xóa trực tiếp");
        }

        TripEvidence evidence = tripEvidenceRepository.findByIdAndBooking_Id(evidenceId, booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy minh chứng #" + evidenceId));

        boolean isAdmin = currentUserRole == Role.ADMIN;
        boolean isUploader = evidence.getUploadedBy() != null && evidence.getUploadedBy().getId().equals(currentUser.getId());
        boolean isProvider = false;
        if (currentUserRole == Role.PROVIDER) {
            Provider provider = userService.findProviderProfile(currentUserId).orElse(null);
            if (provider != null && booking.getVehicle() != null && booking.getVehicle().getProvider() != null) {
                isProvider = booking.getVehicle().getProvider().getId().equals(provider.getId());
            }
        }

        if (!isAdmin && !isUploader && !isProvider) {
            throw new AccessDeniedException("Bạn không có quyền xóa ảnh minh chứng này");
        }

        String storedPath = evidence.getImageUrl();

        if (transactionTemplate != null) {
            transactionTemplate.executeWithoutResult(status -> tripEvidenceRepository.delete(evidence));
        } else {
            tripEvidenceRepository.delete(evidence);
        }

        if (storedPath != null && storedPath.startsWith("booking/")) {
            fileStorageService.delete(storedPath);
        }

        log.info("Deleted trip evidence #{} for booking {}", evidenceId, booking.getBookingCode());
    }

    @Override
    @Transactional(readOnly = true)
    public EvidenceFile readEvidence(Long userId, Role role, String code, Long id) {
        Booking booking = resolveBooking(code);
        User user = userService.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        validateAndResolveAccess(user, role, booking);
        TripEvidence evidence = tripEvidenceRepository.findByIdAndBooking_Id(id, booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ảnh minh chứng"));
        String path = evidence.getImageUrl();
        if (path == null || !path.startsWith("booking/")) {
            throw new ResourceNotFoundException("Ảnh này không được lưu local");
        }
        String type = path.endsWith(".png") ? "image/png" : "image/jpeg";
        return new EvidenceFile(fileStorageService.read(path), type);
    }

    private Booking resolveBooking(String bookingIdOrCode) {
        if (bookingIdOrCode == null || bookingIdOrCode.isBlank()) {
            throw new ResourceNotFoundException("Mã đơn đặt xe không được để trống");
        }
        String trimmed = bookingIdOrCode.trim();
        if (trimmed.matches("\\d+")) {
            Long id = Long.parseLong(trimmed);
            return bookingRepository.findById(id)
                    .or(() -> bookingRepository.findByBookingCode(trimmed))
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt xe: " + trimmed));
        }
        return bookingRepository.findByBookingCode(trimmed)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt xe: " + trimmed));
    }

    private Driver validateAndResolveAccess(User currentUser, Role currentUserRole, Booking booking) {
        if (currentUserRole == Role.RENTER) {
            if (booking.getPlan().getPlanType() != PlanType.YEARLY
                    || !booking.getRenter().getUser().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Renter chỉ được truy cập minh chứng đơn năm của mình");
            }
            return null;
        }
        if (currentUserRole == Role.ADMIN) {
            return booking.getDriver();
        }

        if (currentUserRole == Role.DRIVER) {
            Driver driver = userService.findDriverProfile(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Người dùng chưa có hồ sơ tài xế"));

            if (booking.getDriver() == null || !booking.getDriver().getId().equals(driver.getId())) {
                throw new AccessDeniedException("Tài xế không có quyền thao tác trên chuyến đi này");
            }
            return driver;
        }

        if (currentUserRole == Role.PROVIDER) {
            Provider provider = userService.findProviderProfile(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Người dùng chưa có hồ sơ nhà cung cấp"));

            boolean ownsVehicle = booking.getVehicle() != null
                    && booking.getVehicle().getProvider() != null
                    && booking.getVehicle().getProvider().getId().equals(provider.getId());

            if (!ownsVehicle) {
                throw new AccessDeniedException("Nhà cung cấp không sở hữu phương tiện của chuyến đi này");
            }
            return booking.getDriver();
        }

        throw new AccessDeniedException("Vai trò người dùng không có quyền truy cập minh chứng của chuyến đi");
    }

    private void validateBookingStatusForEvidenceType(BookingStatus status, TripEvidenceType type) {
        if (status == BookingStatus.CANCELLED) {
            throw new InvalidTripTransitionException("Không thể tải minh chứng cho chuyến đi đã bị hủy");
        }
        if (status == BookingStatus.PENDING) {
            throw new InvalidTripTransitionException("Chuyến đi đang chờ xử lý, chưa thể tải minh chứng tác nghiệp");
        }

        switch (type) {
            case PICKUP_ARRIVAL -> {
                if (status != BookingStatus.DRIVER_CONFIRMED
                        && status != BookingStatus.DRIVER_ARRIVED
                        && status != BookingStatus.IN_PROGRESS) {
                    throw new InvalidTripTransitionException(
                            "Ảnh có mặt tại điểm đón chỉ hợp lệ khi tài xế đã xác nhận chuyến, đã đến điểm đón hoặc đang thực hiện chuyến"
                    );
                }
            }
            case PRE_TRIP_CONDITION -> {
                if (status != BookingStatus.DRIVER_CONFIRMED
                        && status != BookingStatus.DRIVER_ARRIVED) {
                    throw new InvalidTripTransitionException(
                            "Ảnh tình trạng xe trước chuyến chỉ được tải lên trước khi bắt đầu hành trình (khi tài xế đã xác nhận hoặc đã đến điểm đón)"
                    );
                }
            }
            case POST_TRIP_CONDITION -> {
                if (status != BookingStatus.IN_PROGRESS && status != BookingStatus.COMPLETED) {
                    throw new InvalidTripTransitionException(
                            "Ảnh tình trạng xe sau chuyến chỉ được tải lên khi đang kết thúc chuyến hoặc sau khi chuyến hoàn thành"
                    );
                }
            }
            case INCIDENT -> {
                if (status != BookingStatus.DRIVER_ARRIVED
                        && status != BookingStatus.IN_PROGRESS
                        && status != BookingStatus.COMPLETED) {
                    throw new InvalidTripTransitionException(
                            "Ảnh sự cố chỉ được tải lên khi tài xế đã có mặt, đang thực hiện chuyến hoặc sau chuyến đi"
                    );
                }
            }
            // Ảnh bằng chứng của báo cáo sự cố không phụ thuộc tiến độ chuyến: chỉ cần Driver được phân công
            // (kiểm tra ở validateAndResolveAccess) và chuyến chưa bị hủy hoặc đang chờ xử lý (kiểm tra ở trên).
            case INCIDENT_PROOF -> {
            }
            case VEHICLE_HANDOVER -> {
                if (status != BookingStatus.DRIVER_ARRIVED
                        && status != BookingStatus.IN_PROGRESS
                        && status != BookingStatus.COMPLETED) {
                    throw new InvalidTripTransitionException(
                            "Ảnh bàn giao xe chỉ được tải lên khi đã đến điểm đón hoặc đang trong chuyến"
                    );
                }
            }
            case TRIP_COMPLETION -> {
                if (status != BookingStatus.IN_PROGRESS && status != BookingStatus.COMPLETED) {
                    throw new InvalidTripTransitionException(
                            "Ảnh hoàn tất chỉ được tải lên khi kết thúc chuyến hoặc đã hoàn thành"
                    );
                }
            }
            case OTHER -> {
            }
        }
    }
}
