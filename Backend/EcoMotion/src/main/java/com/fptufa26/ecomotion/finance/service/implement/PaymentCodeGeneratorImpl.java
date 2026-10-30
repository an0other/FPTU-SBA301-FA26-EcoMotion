package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.finance.entity.PaymentCodeSequence;
import com.fptufa26.ecomotion.finance.repository.PaymentCodeSequenceRepository;
import com.fptufa26.ecomotion.finance.service.PaymentCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCodeGeneratorImpl implements PaymentCodeGenerator {

    private final PaymentCodeSequenceRepository sequenceRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized String generatePaymentCode(String prefix) {
        String cleanPrefix = (prefix != null ? prefix.trim().toUpperCase(Locale.ROOT) : "TX");
        PaymentCodeSequence sequence = sequenceRepository.findByPrefixForUpdate(cleanPrefix)
                .orElseGet(() -> sequenceRepository.saveAndFlush(new PaymentCodeSequence(cleanPrefix, 1L)));

        long currentVal = sequence.getNextVal() != null ? sequence.getNextVal() : 1L;
        sequence.setNextVal(currentVal + 1);
        sequenceRepository.saveAndFlush(sequence);

        String code;
        if (currentVal < 1_000_000L) {
            code = String.format("%s%06d", cleanPrefix, currentVal);
        } else {
            code = String.format("%s%d", cleanPrefix, currentVal);
        }

        log.debug("Allocated internal transaction code {} for prefix {}", code, cleanPrefix);
        return code;
    }
}
