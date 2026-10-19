package com.fptufa26.ecomotion.finance.enums;

import java.util.Arrays;
import java.util.Optional;

public enum SupportedBank {
    VCB("VCB", "Vietcombank", "Ngân hàng TMCP Ngoại thương Việt Nam", 10, 13),
    TCB("TCB", "Techcombank", "Ngân hàng TMCP Kỹ Thương Việt Nam", 10, 14),
    MB("MB", "MBBank", "Ngân hàng TMCP Quân đội", 9, 14),
    BIDV("BIDV", "BIDV", "Ngân hàng TMCP Đầu tư và Phát triển Việt Nam", 14, 14),
    CTG("CTG", "VietinBank", "Ngân hàng TMCP Công thương Việt Nam", 12, 12),
    VPB("VPB", "VPBank", "Ngân hàng TMCP Việt Nam Thịnh Vượng", 8, 16),
    ACB("ACB", "ACB", "Ngân hàng TMCP Á Châu", 8, 12),
    TPB("TPB", "TPBank", "Ngân hàng TMCP Tiên Phong", 10, 12),
    STB("STB", "Sacombank", "Ngân hàng TMCP Sài Gòn Thương Tín", 10, 12),
    VIB("VIB", "VIB", "Ngân hàng TMCP Quốc tế Việt Nam", 10, 16),
    SHB("SHB", "SHB", "Ngân hàng TMCP Sài Gòn - Hà Nội", 10, 12),
    HDB("HDB", "HDBank", "Ngân hàng TMCP Phát triển TP.HCM", 10, 15),
    MSB("MSB", "MSB", "Ngân hàng TMCP Hàng Hải Việt Nam", 10, 14),
    OCB("OCB", "OCB", "Ngân hàng TMCP Phương Đông", 10, 14),
    AGR("AGR", "Agribank", "Ngân hàng Nông nghiệp và Phát triển Nông thôn Việt Nam", 13, 13);

    private final String code;
    private final String shortName;
    private final String fullName;
    private final int minAccountLength;
    private final int maxAccountLength;

    SupportedBank(String code, String shortName, String fullName, int minAccountLength, int maxAccountLength) {
        this.code = code;
        this.shortName = shortName;
        this.fullName = fullName;
        this.minAccountLength = minAccountLength;
        this.maxAccountLength = maxAccountLength;
    }

    public String getCode() {
        return code;
    }

    public String getShortName() {
        return shortName;
    }

    public String getFullName() {
        return fullName;
    }

    public int getMinAccountLength() {
        return minAccountLength;
    }

    public int getMaxAccountLength() {
        return maxAccountLength;
    }

    public static Optional<SupportedBank> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim();
        return Arrays.stream(values())
                .filter(b -> b.code.equalsIgnoreCase(normalized) || b.shortName.equalsIgnoreCase(normalized))
                .findFirst();
    }

    public static Optional<SupportedBank> fromName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String normalized = name.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(b -> b.shortName.toLowerCase().contains(normalized)
                        || b.fullName.toLowerCase().contains(normalized)
                        || normalized.contains(b.shortName.toLowerCase()))
                .findFirst();
    }

    public boolean isValidAccountNumber(String accountNumber) {
        if (accountNumber == null) {
            return false;
        }
        String trimmed = accountNumber.trim();
        if (!trimmed.matches("^[0-9]+$")) {
            return false;
        }
        return trimmed.length() >= minAccountLength && trimmed.length() <= maxAccountLength;
    }
}
