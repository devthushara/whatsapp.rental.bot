package com.zoomigo.whatsapp.whatsapprentalbot.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

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
    private String status;
    private Instant createdAt = Instant.now();
}
