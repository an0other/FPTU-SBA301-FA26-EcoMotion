package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.BookingContractResponse;
import org.springframework.web.multipart.MultipartFile;

public interface BookingContractService {

    BookingContractResponse uploadContract(
            Long userId,
            String bookingCode,
            MultipartFile file);

    byte[] downloadContract(
            Long userId,
            String bookingCode);
}