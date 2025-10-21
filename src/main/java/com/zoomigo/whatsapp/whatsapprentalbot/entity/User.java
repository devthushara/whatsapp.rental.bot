package com.zoomigo.whatsapp.whatsapprentalbot.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String phoneNumber;

    private String name;
    private Integer days;
    private String pickupType;
    private String deliveryAddress;
    private Long selectedBikeId;
    private String stage; // ASK_NAME, ASK_DAYS, etc.
}
