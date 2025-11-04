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
    private Integer pricePerDay; // stored in smallest currency unit (cents) or base unit; existing code uses integer rupees
    private Integer deposit;
    private boolean isAvailable;

    // Currency unit for this bike's pricing. If null, system default (USD) applies.
    private String currencyUnit;
}
