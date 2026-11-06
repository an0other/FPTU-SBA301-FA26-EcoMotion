package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.common.exception.BadRequestException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Quy tắc danh sách URL ảnh bằng chứng của báo cáo sự cố.
 * URL phải do cơ chế upload của hệ thống trả về (https). Giới hạn số lượng và độ dài để bảo đảm
 * JSON của danh sách luôn vừa cột evidence_urls VARCHAR(2000) hiện có, không cần migration.
 */
public final class IncidentEvidenceUrls {

    public static final int MAX_COUNT = 10;
    public static final int MAX_URL_LENGTH = 190;

    private IncidentEvidenceUrls() {
    }

    /** Chuẩn hóa: bỏ rỗng, bỏ trùng (giữ thứ tự); kiểm tra https và độ dài; tối đa MAX_COUNT. */
    public static List<String> normalize(List<String> urls) {
        if (urls == null || urls.isEmpty()) return List.of();
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : urls) {
            if (raw == null || raw.isBlank()) continue;
            String url = raw.trim();
            if (!url.startsWith("https://") || url.length() > MAX_URL_LENGTH || url.chars().anyMatch(Character::isWhitespace)) {
                throw new BadRequestException("Đường dẫn ảnh minh chứng không hợp lệ");
            }
            unique.add(url);
        }
        if (unique.size() > MAX_COUNT) {
            throw new BadRequestException("Chỉ được đính kèm tối đa " + MAX_COUNT + " ảnh minh chứng");
        }
        return new ArrayList<>(unique);
    }
}
