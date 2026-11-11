package com.fptufa26.ecomotion.verification.identity.service;

import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityLinkResponse;
import java.util.*;

public interface IdentityAccountLinkService {
    IdentityLinkResponse request(Long sourceUserId, String password, String targetEmail);
    List<IdentityLinkResponse> list(Long userId, int page);
    IdentityLinkResponse confirm(Long targetUserId, UUID id, long version, String password);
    IdentityLinkResponse cancel(Long userId, UUID id, long version);
}
