package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Nhà xe báo sai lệch khi đối soát chuyển khoản. issueType: UNDERPAID (chuyển thiếu), OVERPAID (chuyển dư),
 * WRONG_CONTENT (sai nội dung), NOT_RECEIVED (không thấy tiền về), OTHER (khác — bắt buộc mô tả).
 */
public record ReportIssueRequest(
        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String issueNote,

        @Pattern(regexp = "UNDERPAID|OVERPAID|WRONG_CONTENT|NOT_RECEIVED|OTHER", message = "Loại sai lệch không hợp lệ")
        String issueType,

        @DecimalMin(value = "0", message = "Số tiền thực nhận không được âm")
        BigDecimal receivedAmount,

        @Size(max = 100, message = "Mã giao dịch ngân hàng tối đa 100 ký tự")
        String bankTransactionRef
) {
    public ReportIssueRequest(String issueNote) {
        this(issueNote, null, null, null);
    }

    public static String label(String issueType) {
        if (issueType == null) return null;
        return switch (issueType) {
            case "UNDERPAID" -> "Khách chuyển thiếu";
            case "OVERPAID" -> "Khách chuyển dư";
            case "WRONG_CONTENT" -> "Sai nội dung chuyển khoản";
            case "NOT_RECEIVED" -> "Chưa thấy tiền về tài khoản";
            default -> "Khác";
        };
    }

    /** Ghép thành ghi chú hiển thị cho khách và admin. */
    public String composedNote() {
        StringBuilder out = new StringBuilder();
        String label = label(issueType);
        if (label != null) out.append('[').append(label).append(']');
        if (receivedAmount != null) {
            out.append(out.isEmpty() ? "" : " ").append("Thực nhận ")
                    .append(String.format(java.util.Locale.forLanguageTag("vi-VN"), "%,.0f", receivedAmount)).append(" đ");
        }
        if (bankTransactionRef != null && !bankTransactionRef.isBlank()) {
            out.append(out.isEmpty() ? "" : " · ").append("Mã GD ").append(bankTransactionRef.trim());
        }
        if (issueNote != null && !issueNote.isBlank()) {
            out.append(out.isEmpty() ? "" : " — ").append(issueNote.trim());
        }
        String note = out.toString();
        return note.length() > 500 ? note.substring(0, 500) : note;
    }
}
