package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.BookingContractResponse;
import org.springframework.web.multipart.MultipartFile;import com.fptufa26.ecomotion.booking.dto.response.BookingContractStatusResponse;

public interface BookingContractService {

    BookingContractResponse uploadContract(
            Long userId,
            String bookingCode,
            MultipartFile file);

    byte[] downloadContract(
            Long userId,
            String bookingCode);

    BookingContractStatusResponse getContractStatus(
            Long userId,
            String bookingCode);
}