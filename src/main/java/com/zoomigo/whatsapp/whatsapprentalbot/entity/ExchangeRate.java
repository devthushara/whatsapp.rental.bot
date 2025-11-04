package com.zoomigo.whatsapp.whatsapprentalbot.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "exchange_rate", uniqueConstraints = {@UniqueConstraint(columnNames = {"base_currency", "target_currency"})})
@Data
public class ExchangeRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_currency", nullable = false)
    private String baseCurrency; // e.g., USD

    @Column(name = "target_currency", nullable = false)
    private String targetCurrency; // e.g., LKR

    @Column(precision = 19, scale = 6)
    private BigDecimal rate; // multiply base * rate = target amount

    private Instant lastUpdated;

    private Boolean useLiveRate = false;

    // convenience column to mark an active target currency for display (optional)
    private Boolean activeTarget = false;

    public ExchangeRate() {
    }

    public ExchangeRate(String baseCurrency, String targetCurrency, BigDecimal rate, Instant lastUpdated, Boolean useLiveRate, Boolean activeTarget) {
        this.baseCurrency = baseCurrency;
        this.targetCurrency = targetCurrency;
        this.rate = rate;
        this.lastUpdated = lastUpdated;
        this.useLiveRate = useLiveRate;
        this.activeTarget = activeTarget;
    }

    // Getters and Setters
}
