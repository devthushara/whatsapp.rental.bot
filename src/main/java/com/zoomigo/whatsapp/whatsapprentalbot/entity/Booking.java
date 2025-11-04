package com.zoomigo.whatsapp.whatsapprentalbot.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "booking")
@Data
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String waId;
    private String name;
    private String bike;
    private Integer duration;
    private Integer price;
    private Integer deposit;
    private String status; // e.g., CONFIRMED, CANCELLED, COMPLETED
    private LocalDate startDate;
    private LocalDate endDate;

    private String pickupType;
    private String deliveryAddress;

    // Map the promo code as a foreign key that references PromoCode.code (not the numeric id)
    @ManyToOne
    @JoinColumn(name = "promo_code", referencedColumnName = "code")
    private PromoCode promoCode;

    // Amount actually discounted (in currency units) when the booking was confirmed
    private Integer promoDiscountAmount;

    // Whether a promo was applied to this booking
    private Boolean promoApplied = false;

    // Currency unit and exchange rate captured at booking time (for historical accuracy)
    private String currencyUnit; // e.g., USD, LKR
    private BigDecimal appliedExchangeRate; // rate used to convert from base (USD) to booking currency

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant cancelledAt;
}
