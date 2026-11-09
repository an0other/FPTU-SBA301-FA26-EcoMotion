package com.fptufa26.ecomotion.verification.identity.service;

import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityAiAssessmentResponse;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityVideoResult;
import org.springframework.web.multipart.MultipartFile;

public interface IdentityAiAssessmentService {

    IdentityAiAssessmentResponse assess(Long userId, MultipartFile front, MultipartFile back, MultipartFile video);

    IdentityAiAssessmentResponse.DocumentRead readDocuments(Long userId, byte[] front, byte[] back);

    IdentityVideoResult assessVideo(Long userId, byte[] front, byte[] video);
}
