package com.fptufa26.ecomotion.verification.identity.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IdentityEvidenceStorageService {

    String store(UUID attemptId, MultipartFile file, int maxBytes);

    byte[] read(String key, int maxBytes);

    void delete(String key);
}
