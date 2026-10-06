package com.fptufa26.ecomotion.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * JPA attribute converter that transparently encrypts a {@code String} column at rest and decrypts
 * it on read, using the two-way {@link DeterministicCipher}. Apply it to a field with
 * {@code @Convert(converter = EncryptedStringConverter.class)}.
 *
 * <p>Because the cipher is deterministic, equality comparisons and UNIQUE constraints still behave
 * exactly as they would on the plaintext column — including the converter being applied to query
 * parameters by Hibernate.</p>
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final DeterministicCipher cipher;

    public EncryptedStringConverter(DeterministicCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}
