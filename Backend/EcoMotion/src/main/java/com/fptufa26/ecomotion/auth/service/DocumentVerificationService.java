package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.response.FaceMatchResponse;
import com.fptufa26.ecomotion.auth.dto.response.ScanCccdResponse;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentVerificationService {

    ScanCccdResponse scanCccd(String activationToken, MultipartFile cccdFront);

    FaceMatchResponse verifyFace(String activationToken, MultipartFile portrait);
}
