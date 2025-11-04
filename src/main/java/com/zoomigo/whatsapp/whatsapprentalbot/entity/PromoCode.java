package com.zoomigo.whatsapp.whatsapprentalbot.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "promo_code")
@Data
public class PromoCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code; // e.g., ZOOMI10

    private String title;
    private String description;

    private Integer totalAllocation = 0; // total allowed usages
    private Integer usedCount = 0; // how many used so far

    // Flat discount in currency units (e.g., 50)
    private Integer discountAmount = 0;

    // Percentage discount (0-100). When set (>0), percentage discount is applied instead
    // of the flat discountAmount. Admin will set either percentage or flat or both (percent takes precedence).
    private Integer discountPercent = 0;

    private Boolean active = true;

    // Currency unit for this promo (e.g., LKR, USD). If null, fallback to system default.
    private String currencyUnit;
}
