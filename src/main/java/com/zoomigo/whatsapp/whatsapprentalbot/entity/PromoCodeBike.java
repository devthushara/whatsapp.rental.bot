package com.zoomigo.whatsapp.whatsapprentalbot.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "promo_code_bike")
@Data
public class PromoCodeBike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "promo_id")
    private PromoCode promoCode;

    @ManyToOne(optional = false)
    @JoinColumn(name = "bike_id")
    private Bike bike;
}

