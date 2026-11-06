package com.fptufa26.ecomotion.common.cloudinary;

/**
 * Nhận diện định dạng ảnh theo chữ ký (magic bytes) ở đầu file. Không tin Content-Type hay đuôi file do client gửi.
 * Chỉ hỗ trợ JPEG, PNG và WebP, đúng với danh sách định dạng upload của hệ thống.
 */
public final class ImageSignature {

    /** Số byte đầu cần đọc để nhận diện: WebP cần 12 byte ("RIFF" + size + "WEBP"). */
    public static final int HEAD_BYTES = 12;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private ImageSignature() {
    }

    /**
     * @return "image/jpeg", "image/png", "image/webp" nếu chữ ký khớp; null nếu không phải định dạng được hỗ trợ.
     */
    public static String detect(byte[] head) {
        if (head == null || head.length < 3) return null;
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (head.length >= PNG_SIGNATURE.length) {
            boolean png = true;
            for (int i = 0; i < PNG_SIGNATURE.length; i++) {
                if (head[i] != PNG_SIGNATURE[i]) {
                    png = false;
                    break;
                }
            }
            if (png) return "image/png";
        }
        if (head.length >= 12 && ascii(head, 0, 4).equals("RIFF") && ascii(head, 8, 12).equals("WEBP")) {
            return "image/webp";
        }
        return null;
    }

    private static String ascii(byte[] bytes, int from, int to) {
        return new String(bytes, from, to - from, java.nio.charset.StandardCharsets.US_ASCII);
    }
}
