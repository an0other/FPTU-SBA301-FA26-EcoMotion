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
    @Column(length = 2000)
    private String status;
}
