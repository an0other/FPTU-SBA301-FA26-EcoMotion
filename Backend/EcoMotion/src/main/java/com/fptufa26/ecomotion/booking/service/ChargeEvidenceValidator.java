package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/** Validates charge evidence before any file is sent to external storage. */
@Component
public class ChargeEvidenceValidator {
    private final int maxCount;
    private final long maxBytes;

    public ChargeEvidenceValidator(@Value("${app.surcharge-evidence.max-count:5}") int maxCount,
                                   @Value("${app.surcharge-evidence.max-file-bytes:5242880}") long maxBytes) {
        if (maxCount < 1 || maxBytes < 1) throw new IllegalArgumentException("Invalid surcharge evidence limits");
        this.maxCount = maxCount;
        this.maxBytes = maxBytes;
    }

    public void validate(List<MultipartFile> files, int existingCount) {
        if (files == null || files.isEmpty() || existingCount < 0 || files.size() > maxCount - existingCount)
            throw new BadRequestException("Số ảnh minh chứng không hợp lệ hoặc vượt giới hạn " + maxCount);
        for (MultipartFile file : files) validateFile(file);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > maxBytes)
            throw new BadRequestException("Ảnh rỗng hoặc vượt quá dung lượng cho phép");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        try (InputStream stream = file.getInputStream()) {
            byte[] h = stream.readNBytes(12);
            boolean jpeg = h.length >= 3 && u(h[0]) == 0xff && u(h[1]) == 0xd8 && u(h[2]) == 0xff;
            boolean png = h.length >= 8 && u(h[0]) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                    && h[4] == 13 && h[5] == 10 && h[6] == 26 && h[7] == 10;
            boolean webp = h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            if (!(jpeg && (type.equals("image/jpeg") || type.equals("image/jpg"))
                    || png && type.equals("image/png") || webp && type.equals("image/webp")))
                throw new BadRequestException("Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP hợp lệ");
        } catch (IOException ex) {
            throw new BadRequestException("Không đọc được ảnh minh chứng");
        }
    }

    private static int u(byte value) { return value & 0xff; }
}
