package com.fptufa26.ecomotion.finance.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "vietqr_callback")
@Getter
@Setter
public class VietQrCallback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String transactionId;
    @Column(nullable = false, length = 64)
    private String payloadHash;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(nullable = false, length = 19)
    private String orderId;
    private Long paymentRequestId;
    @Column(nullable = false, length = 500)
    private String outcome;
    @Column(nullable = false)
    private Instant receivedAt;
}
