package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.user.enums.Role;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sinh ảnh/tài liệu minh hoạ offline cho dữ liệu demo: avatar chữ cái đầu, thẻ chân dung và bằng lái tài xế,
 * biên nhận chuyển khoản (SVG) và PDF giấy tờ xe tối giản. Không phụ thuộc dịch vụ ngoài.
 */
final class DemoAssetFactory {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(VN);

    private DemoAssetFactory() {
    }

    static String roleColor(Role role) {
        if (role == null) return "#6B7280";
        return switch (role) {
            case RENTER -> "#0E9F6E";
            case PROVIDER -> "#1C64F2";
            case DRIVER -> "#D97706";
            case ADMIN -> "#7E3AF2";
        };
    }

    static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] words = fullName.trim().split("\\s+");
        String first = words[0].substring(0, 1);
        String last = words[words.length - 1];
        if (words.length == 1 || !Character.isLetter(last.charAt(0))) {
            return first.toUpperCase(Locale.ROOT);
        }
        return (first + last.charAt(0)).toUpperCase(Locale.forLanguageTag("vi"));
    }

    static byte[] avatarSvg(String fullName, Role role) {
        String color = roleColor(role);
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256">
                  <defs>
                    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0" stop-color="%1$s"/>
                      <stop offset="1" stop-color="%1$s" stop-opacity="0.72"/>
                    </linearGradient>
                  </defs>
                  <rect width="256" height="256" rx="128" fill="url(#g)"/>
                  <text x="128" y="128" dy="0.35em" text-anchor="middle" fill="#FFFFFF"
                        font-family="Inter, 'Segoe UI', Roboto, Arial, sans-serif" font-size="104" font-weight="600">%2$s</text>
                </svg>
                """.formatted(color, xml(initials(fullName)));
        return svg.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] portraitSvg(String fullName, String licenseNo) {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="360" height="480" viewBox="0 0 360 480">
                  <defs>
                    <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0" stop-color="#E0F2FE"/>
                      <stop offset="1" stop-color="#BAE6FD"/>
                    </linearGradient>
                  </defs>
                  <rect width="360" height="480" fill="url(#bg)"/>
                  <circle cx="180" cy="168" r="78" fill="#F5D0B5"/>
                  <path d="M102 150 Q110 78 180 78 Q250 78 258 150 Q238 112 180 112 Q122 112 102 150 Z" fill="#1F2937"/>
                  <path d="M40 480 Q48 300 180 292 Q312 300 320 480 Z" fill="#1E3A8A"/>
                  <path d="M150 292 L180 350 L210 292 Z" fill="#FFFFFF"/>
                  <path d="M172 300 L180 380 L188 300 Z" fill="#B91C1C"/>
                  <rect x="0" y="408" width="360" height="72" fill="#0F172A" fill-opacity="0.78"/>
                  <text x="180" y="438" text-anchor="middle" fill="#FFFFFF"
                        font-family="Inter, 'Segoe UI', Roboto, Arial, sans-serif" font-size="22" font-weight="600">%s</text>
                  <text x="180" y="464" text-anchor="middle" fill="#CBD5E1"
                        font-family="Inter, 'Segoe UI', Roboto, Arial, sans-serif" font-size="14">Ảnh chân dung tài xế · GPLX %s</text>
                </svg>
                """.formatted(xml(fullName), xml(licenseNo == null ? "" : licenseNo));
        return svg.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] licenseSvg(String fullName, String licenseNo, String licenseClass, String expiry) {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="640" height="404" viewBox="0 0 640 404">
                  <defs>
                    <linearGradient id="card" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0" stop-color="#FDF2F8"/>
                      <stop offset="1" stop-color="#E0E7FF"/>
                    </linearGradient>
                  </defs>
                  <rect x="2" y="2" width="636" height="400" rx="22" fill="url(#card)" stroke="#94A3B8" stroke-width="2"/>
                  <rect x="2" y="2" width="636" height="64" rx="22" fill="#B91C1C"/>
                  <rect x="2" y="44" width="636" height="22" fill="#B91C1C"/>
                  <text x="320" y="32" text-anchor="middle" fill="#FDE68A" font-family="Inter, 'Segoe UI', Arial, sans-serif"
                        font-size="16" font-weight="600">CỘNG HOÀ XÃ HỘI CHỦ NGHĨA VIỆT NAM</text>
                  <text x="320" y="56" text-anchor="middle" fill="#FFFFFF" font-family="Inter, 'Segoe UI', Arial, sans-serif"
                        font-size="20" font-weight="700">GIẤY PHÉP LÁI XE / DRIVER'S LICENSE</text>
                  <rect x="32" y="96" width="150" height="196" rx="10" fill="#E0F2FE" stroke="#94A3B8"/>
                  <circle cx="107" cy="170" r="40" fill="#F5D0B5"/>
                  <path d="M67 160 Q72 118 107 118 Q142 118 147 160 Q136 140 107 140 Q78 140 67 160 Z" fill="#1F2937"/>
                  <path d="M44 292 Q50 222 107 218 Q164 222 170 292 Z" fill="#1E3A8A"/>
                  <g font-family="Inter, 'Segoe UI', Arial, sans-serif" fill="#0F172A">
                    <text x="214" y="114" font-size="14" fill="#475569">Số / No.</text>
                    <text x="214" y="140" font-size="24" font-weight="700" fill="#B91C1C">%2$s</text>
                    <text x="214" y="176" font-size="14" fill="#475569">Họ tên / Full name</text>
                    <text x="214" y="202" font-size="24" font-weight="700">%1$s</text>
                    <text x="214" y="238" font-size="14" fill="#475569">Hạng / Class</text>
                    <text x="214" y="264" font-size="24" font-weight="700">%3$s</text>
                    <text x="404" y="238" font-size="14" fill="#475569">Có giá trị đến / Expires</text>
                    <text x="404" y="264" font-size="24" font-weight="700">%4$s</text>
                  </g>
                  <text x="320" y="352" text-anchor="middle" fill="#B91C1C" fill-opacity="0.18"
                        font-family="Inter, 'Segoe UI', Arial, sans-serif" font-size="54" font-weight="800"
                        transform="rotate(-8 320 352)">MẪU DEMO · ECOMOTION</text>
                  <text x="32" y="384" fill="#64748B" font-family="Inter, 'Segoe UI', Arial, sans-serif" font-size="13">
                    Bản minh hoạ phục vụ demo, không có giá trị pháp lý.</text>
                </svg>
                """.formatted(xml(fullName.toUpperCase(Locale.forLanguageTag("vi"))), xml(licenseNo), xml(licenseClass), xml(expiry));
        return svg.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] transferReceiptSvg(String bankName, String accountHolder, String accountNumber,
                                     BigDecimal amount, String content, Instant transferredAt, String reference) {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="390" height="720" viewBox="0 0 390 720">
                  <rect width="390" height="720" fill="#F1F5F9"/>
                  <rect x="0" y="0" width="390" height="210" fill="#047857"/>
                  <circle cx="195" cy="78" r="34" fill="#FFFFFF"/>
                  <path d="M178 78 L190 90 L213 66" stroke="#047857" stroke-width="7" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
                  <g font-family="Inter, 'Segoe UI', Roboto, Arial, sans-serif" text-anchor="middle">
                    <text x="195" y="140" fill="#D1FAE5" font-size="16">Chuyển khoản thành công</text>
                    <text x="195" y="182" fill="#FFFFFF" font-size="32" font-weight="700">%4$s đ</text>
                  </g>
                  <rect x="20" y="230" width="350" height="400" rx="16" fill="#FFFFFF"/>
                  <g font-family="Inter, 'Segoe UI', Roboto, Arial, sans-serif" font-size="14">
                    <text x="40" y="270" fill="#64748B">Người nhận</text>
                    <text x="350" y="270" fill="#0F172A" text-anchor="end" font-weight="600">%2$s</text>
                    <text x="40" y="314" fill="#64748B">Ngân hàng</text>
                    <text x="350" y="314" fill="#0F172A" text-anchor="end" font-weight="600">%1$s</text>
                    <text x="40" y="358" fill="#64748B">Số tài khoản</text>
                    <text x="350" y="358" fill="#0F172A" text-anchor="end" font-weight="600">%3$s</text>
                    <text x="40" y="402" fill="#64748B">Nội dung</text>
                    <text x="350" y="402" fill="#0F172A" text-anchor="end" font-weight="600">%5$s</text>
                    <text x="40" y="446" fill="#64748B">Thời gian</text>
                    <text x="350" y="446" fill="#0F172A" text-anchor="end" font-weight="600">%6$s</text>
                    <text x="40" y="490" fill="#64748B">Mã giao dịch</text>
                    <text x="350" y="490" fill="#0F172A" text-anchor="end" font-weight="600">%7$s</text>
                    <line x1="40" y1="528" x2="350" y2="528" stroke="#E2E8F0" stroke-dasharray="6 6"/>
                    <text x="195" y="572" fill="#94A3B8" text-anchor="middle" font-size="12">Ảnh chụp màn hình minh hoạ cho dữ liệu demo</text>
                  </g>
                </svg>
                """.formatted(xml(bankName), xml(accountHolder), xml(maskAccount(accountNumber)), money(amount),
                xml(content), transferredAt == null ? "" : DATE_TIME.format(transferredAt), xml(reference));
        return svg.getBytes(StandardCharsets.UTF_8);
    }

    /** PDF 1 trang (A4) với vài dòng chữ Helvetica; tiếng Việt được bỏ dấu vì font chuẩn không có glyph. */
    static byte[] documentPdf(String title, List<String> lines) {
        StringBuilder content = new StringBuilder();
        content.append("0.75 0.11 0.11 rg 0 792 595 50 re f\n");
        content.append("BT /F2 20 Tf 1 1 1 rg 40 808 Td (").append(pdfText("ECOMOTION - " + title)).append(") Tj ET\n");
        content.append("0 0 0 rg\n");
        int y = 740;
        for (String line : lines) {
            content.append("BT /F1 13 Tf 50 ").append(y).append(" Td (").append(pdfText(line)).append(") Tj ET\n");
            y -= 24;
        }
        content.append("0.85 0.85 0.85 RG 2 w 40 ").append(y - 10).append(" m 555 ").append(y - 10).append(" l S\n");
        content.append("BT /F1 10 Tf 0.4 0.4 0.4 rg 50 ").append(y - 34)
                .append(" Td (").append(pdfText("Ban mau phuc vu demo SBA301 - khong co gia tri phap ly.")).append(") Tj ET\n");
        byte[] stream = content.toString().getBytes(StandardCharsets.ISO_8859_1);

        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R "
                + "/Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>");
        objects.add(null); // stream object written separately
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n");
            if (objects.get(i) == null) {
                write(out, "<< /Length " + stream.length + " >>\nstream\n");
                out.writeBytes(stream);
                write(out, "\nendstream\n");
            } else {
                write(out, objects.get(i) + "\n");
            }
            write(out, "endobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (Integer offset : offsets) {
            write(out, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    static String money(BigDecimal amount) {
        if (amount == null) return "0";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", symbols).format(amount.setScale(0, RoundingMode.HALF_UP));
    }

    static String maskAccount(String account) {
        if (account == null || account.length() < 4) return account == null ? "" : account;
        return "•••• " + account.substring(account.length() - 4);
    }

    static String stripDiacritics(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    private static String pdfText(String text) {
        String ascii = stripDiacritics(text).replaceAll("[^\\x20-\\x7E]", "?");
        return ascii.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String xml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
