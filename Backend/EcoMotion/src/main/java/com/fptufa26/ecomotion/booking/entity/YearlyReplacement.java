package com.fptufa26.ecomotion.booking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "yearly_replacement")
@Getter
@Setter
public class YearlyReplacement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private Long version;
    private Long bookingId;
    private Long incidentId;
    private Long oldVehicleId;
    private Long newVehicleId;
    private Long oldFinalKm;
    private Long newInitialKm;
    private Long oldEvidenceId;
    private Long newEvidenceId;
    private LocalDate plannedOn;
    private LocalDate exchangedOn;
    /** Giá năm của xe cũ và xe mới tại thời điểm đề xuất (để quyết toán, không đổi khi giá niêm yết đổi sau đó). */
    @Column(precision = 14, scale = 2)
    // B1: lưu giá năm xe cũ/mới và thông tin chất lượng tại thời điểm đề xuất đổi xe.
    private BigDecimal oldPlanPrice;
    @Column(precision = 14, scale = 2)
    private BigDecimal newPlanPrice;
    /** Boolean (nullable) để hàng cũ chưa có giá trị vẫn đọc được. */
    private Boolean lowerQuality;
    @Column(length = 1000)
    private String qualityNotes;
    private Boolean qualityAcknowledged;
    @Column(length = 2000)
    private String status;
}
