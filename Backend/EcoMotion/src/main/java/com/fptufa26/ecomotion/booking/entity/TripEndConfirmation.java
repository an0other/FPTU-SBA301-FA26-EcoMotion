package com.fptufa26.ecomotion.booking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name="trip_end_confirmation")
@Getter @Setter
public class TripEndConfirmation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="booking_id",nullable=false) private Booking booking;
    private Long reportedBy;
    private Instant reportedAt;
    private Instant deadline;
    private String state;
    private String confirmationMethod;
    private Instant confirmedAt;
    private Long confirmedBy;
    private Long disputeId;
    private Long adminId;
    private Instant adminActionAt;
    private String adminAction;
    @Column(length=1000) private String adminNote;
}
