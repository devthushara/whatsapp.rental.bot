package com.zoomigo.whatsapp.whatsapprentalbot.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Data;

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

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant cancelledAt;
}
