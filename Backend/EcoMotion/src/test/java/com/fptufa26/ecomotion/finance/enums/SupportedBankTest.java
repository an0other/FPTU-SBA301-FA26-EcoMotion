package com.fptufa26.ecomotion.finance.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedBankTest {

    @ParameterizedTest
    @ValueSource(strings = {"VCB", "vcb", " Vietcombank ", "TCB", "MB", "bidv", "CTG", "ACB"})
    @DisplayName("fromCode resolves supported bank case-insensitively with trimming")
    void fromCodeResolvesValidBanks(String input) {
        Optional<SupportedBank> bank = SupportedBank.fromCode(input);
        assertThat(bank).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID", "", "   ", "XYZ", "BANK123"})
    @DisplayName("fromCode returns empty for unsupported or blank bank codes")
    void fromCodeReturnsEmptyForInvalid(String input) {
        assertThat(SupportedBank.fromCode(input)).isEmpty();
    }

    @Test
    @DisplayName("fromCode handles null safely")
    void fromCodeHandlesNull() {
        assertThat(SupportedBank.fromCode(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "VCB, 0123456789, true",
            "VCB, 0123456789012, true",
            "VCB, 12345, false",
            "VCB, 0123456789012345, false",
            "VCB, 012345678A, false",
            "VCB, '******6789', false",
            "MB, 0901234567, true",
            "MB, 123, false",
            "BIDV, 12345678901234, true",
            "BIDV, 1234567890123, false"
    })
    @DisplayName("isValidAccountNumber validates digits and bank specific length rules")
    void validatesAccountNumberCorrectly(String bankCode, String accountNumber, boolean expectedValid) {
        SupportedBank bank = SupportedBank.fromCode(bankCode).orElseThrow();
        assertThat(bank.isValidAccountNumber(accountNumber)).isEqualTo(expectedValid);
    }

    @Test
    @DisplayName("fromName finds bank by partial or full Vietnamese name")
    void fromNameFindsBank() {
        assertThat(SupportedBank.fromName("Vietcombank")).contains(SupportedBank.VCB);
        assertThat(SupportedBank.fromName("Ngoại thương")).contains(SupportedBank.VCB);
        assertThat(SupportedBank.fromName("Kỹ Thương")).contains(SupportedBank.TCB);
        assertThat(SupportedBank.fromName(null)).isEmpty();
    }
}
