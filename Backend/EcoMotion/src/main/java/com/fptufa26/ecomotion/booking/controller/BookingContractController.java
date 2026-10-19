package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.response.BookingContractResponse;
import com.fptufa26.ecomotion.booking.service.BookingContractService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.fptufa26.ecomotion.booking.dto.response.BookingContractStatusResponse;

@RestController
@RequestMapping("/api/renter/bookings/{bookingCode}/contract")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RENTER')")
@Tag(name = "Renter Booking Contract")
public class BookingContractController {

    private final BookingContractService bookingContractService;

    @GetMapping("/status")
    public ApiResponse<BookingContractStatusResponse> getContractStatus(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingCode) {
        return ApiResponse.success(
                bookingContractService.getContractStatus(
                        principal.getUserId(),
                        bookingCode));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BookingContractResponse> uploadContract(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingCode,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(
                bookingContractService.uploadContract(
                        principal.getUserId(),
                        bookingCode,
                        file));
    }

//    @GetMapping(produces = MediaType.APPLICATION_PDF_VALUE)
    @GetMapping
    public ResponseEntity<byte[]> downloadContract(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingCode) {
        byte[] content = bookingContractService.downloadContract(
                principal.getUserId(),
                bookingCode);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("contract.pdf")
                                .build()
                                .toString())
                .body(content);
    }
}