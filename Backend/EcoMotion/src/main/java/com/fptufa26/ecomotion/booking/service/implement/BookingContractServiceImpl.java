package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.BookingContractResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.exception.BookingContractNotAllowedException;
import com.fptufa26.ecomotion.booking.exception.BookingContractNotFoundException;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.service.BookingContractService;
import com.fptufa26.ecomotion.common.storage.FileStorageException;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.fptufa26.ecomotion.common.storage.DocumentStorageService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

@Service
public class BookingContractServiceImpl implements BookingContractService {

    private static final String STORAGE_ENTITY = "booking";
    private static final byte[] PDF_SIGNATURE =
            "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final BookingRepository bookingRepository;
    private final BookingPolicySnapshotRepository snapshotRepository;
    private final DocumentStorageService documentStorageService;
    private final long maxBytes;

    public BookingContractServiceImpl(
            BookingRepository bookingRepository,
            BookingPolicySnapshotRepository snapshotRepository,
            DocumentStorageService documentStorageService,
            @Value("${app.upload.max-file-size-bytes:8388608}") long maxBytes) {
        this.bookingRepository = bookingRepository;
        this.snapshotRepository = snapshotRepository;
        this.documentStorageService = documentStorageService;
        this.maxBytes = maxBytes;
    }

    @Override
    @Transactional
    public BookingContractResponse uploadContract(
            Long userId,
            String bookingCode,
            MultipartFile file) {
        Booking booking = bookingRepository
                .findBookingByBookingCodeAndRenter_User_Id(
                        bookingCode,
                        userId)
                .orElseThrow(BookingNotFoundException::new);

        validateYearlyBooking(booking);

        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new BookingContractNotAllowedException(
                    "Contract upload requires an accepted booking");
        }

        byte[] content = readPdf(file);
        String filename = "contract-" + UUID.randomUUID() + ".pdf";

        String storedPath = documentStorageService.store(
                STORAGE_ENTITY,
                userId,
                filename,
                content,
                MediaType.APPLICATION_PDF_VALUE);

        booking.setContractUrl(storedPath);
        bookingRepository.flush();

        return new BookingContractResponse(
                booking.getBookingCode(),
                true);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadContract(
            Long userId,
            String bookingCode) {
        Booking booking = bookingRepository
                .findByBookingCodeAndRenter_User_Id(
                        bookingCode,
                        userId)
                .orElseThrow(BookingNotFoundException::new);

        validateYearlyBooking(booking);

        String storedPath = booking.getContractUrl();

        if (storedPath == null || storedPath.isBlank()) {
            throw new BookingContractNotFoundException();
        }

        if (!storedPath.startsWith(STORAGE_ENTITY + "/" + userId + "/")
                || !storedPath.endsWith(".pdf")) {
            throw new BookingContractNotAllowedException(
                    "Contract is not stored in the supported private PDF format");
        }

        byte[] content = documentStorageService.read(storedPath);
        validatePdfSignature(content);
        return content;
    }

    private void validateYearlyBooking(Booking booking) {
        BookingPolicySnapshot snapshot = snapshotRepository
                .findByBooking_Id(booking.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Booking policy snapshot is missing"));

        if (snapshot.getPlanType() != PlanType.YEARLY) {
            throw new BookingContractNotAllowedException(
                    "This endpoint only supports yearly bookings");
        }
    }

    private byte[] readPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Contract file is required");
        }

        if (file.getSize() > maxBytes) {
            throw new FileStorageException(
                    "Contract exceeds the maximum allowed size");
        }

        if (!MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(
                file.getContentType())) {
            throw new FileStorageException(
                    "Contract must be a PDF document");
        }

        try {
            byte[] content = file.getBytes();
            validatePdfSignature(content);
            return content;
        } catch (IOException ex) {
            throw new FileStorageException(
                    "Could not read the uploaded contract",
                    ex);
        }
    }

    private void validatePdfSignature(byte[] content) {
        if (content == null
                || content.length < PDF_SIGNATURE.length
                || !Arrays.equals(
                PDF_SIGNATURE,
                Arrays.copyOf(content, PDF_SIGNATURE.length))) {
            throw new FileStorageException(
                    "Contract does not have a valid PDF header");
        }
    }
}