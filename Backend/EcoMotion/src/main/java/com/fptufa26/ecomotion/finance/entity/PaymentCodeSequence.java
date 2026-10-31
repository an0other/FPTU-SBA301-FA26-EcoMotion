package com.fptufa26.ecomotion.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_code_sequence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCodeSequence {

    @Id
    @Column(name = "code_prefix", length = 10, nullable = false)
    private String codePrefix;

    @Column(name = "next_val", nullable = false)
    private Long nextVal;
}
