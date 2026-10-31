package com.fptufa26.ecomotion.booking.entity;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
@Entity
@Table(name = "booking_pricing_item")
@Getter @Setter @NoArgsConstructor
public class BookingPricingItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false) private Booking booking;
    @Column(name = "plan_id", nullable = false) private Long planId;
    @Enumerated(EnumType.STRING) @Column(name = "plan_type", nullable = false) private PlanType planType;
    @Column(name = "slot_length_minutes", nullable = false) private Integer slotLengthMinutes;
    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false) private Integer quantity;
}
