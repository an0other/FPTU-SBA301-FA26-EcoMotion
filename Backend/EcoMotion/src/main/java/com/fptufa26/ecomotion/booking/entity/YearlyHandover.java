package com.fptufa26.ecomotion.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "yearly_handover")
@Getter
@Setter
public class YearlyHandover {
    @Id
    private Long bookingId;
    @Version
    private Long version;
    @Column(nullable = false, length = 30)
    private String stage;
    private Instant dispatchedAt;
    private Instant arrivedAt;
    private LocalDate receivedOn;
    private Long initialKm;
    private Long evidenceId;
    @Column(length = 2000)
    private String evidenceUrl;
}
