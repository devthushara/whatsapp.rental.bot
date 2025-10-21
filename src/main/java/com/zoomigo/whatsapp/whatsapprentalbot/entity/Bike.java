package com.zoomigo.whatsapp.whatsapprentalbot.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "bikes")
@Data
public class Bike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Integer pricePerDay;
    private Integer deposit;
    private boolean isAvailable;
}
