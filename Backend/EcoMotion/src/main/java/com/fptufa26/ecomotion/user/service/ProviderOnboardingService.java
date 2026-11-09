package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.user.dto.request.ProviderApplicationRequest;
import com.fptufa26.ecomotion.user.dto.response.ProviderApplicationResponse;
import java.util.List;

public interface ProviderOnboardingService {
    ProviderApplicationResponse get(Long userId);
    ProviderApplicationResponse save(Long userId, ProviderApplicationRequest request);
    ProviderApplicationResponse submit(Long userId, long version);
    ProviderApplicationResponse adminGet(Long adminId, Long userId);
    List<ProviderApplicationResponse> pending(Long adminId, int page, com.fptufa26.ecomotion.user.enums.ProviderApplicationStatus status);
    ProviderApplicationResponse review(Long adminId, Long userId, long version, String decision, String reasonCode);
    List<com.fptufa26.ecomotion.user.entity.ProviderApplicationReview> history(Long adminId, Long userId);
}
