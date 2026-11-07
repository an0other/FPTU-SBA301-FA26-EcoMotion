package com.fptufa26.ecomotion.booking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "yearly_incident", uniqueConstraints = @UniqueConstraint(name = "uk_yearly_incident_request", columnNames = {"booking_id", "request_id"}))
@Getter
@Setter
public class YearlyIncident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private Long version;
    private Long bookingId;
    private Long evidenceId;
    private Long transferEvidenceId;
    @Column(length = 2000)
    private String requestId;
    @Column(length = 2000)
    private String category;
    @Column(length = 2000)
    private String description;
    @Column(length = 2000)
    private String status;
    @Column(length = 2000)
    private String outcome;
    @Column(length = 2000)
    private String resolutionNote;
    @Column(length = 2000)
    private String bankReference;
    private LocalDate occurredOn;
    @Column(precision = 14, scale = 2)
    private BigDecimal invoiceAmount = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal approvedAmount = BigDecimal.ZERO;
}
