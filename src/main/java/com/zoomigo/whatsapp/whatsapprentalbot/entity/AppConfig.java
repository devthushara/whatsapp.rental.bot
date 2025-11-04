package com.zoomigo.whatsapp.whatsapprentalbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "app_config")
@Data
public class AppConfig {
    @Id
    @Column(name = "key_text", nullable = false)
    private String keyText;

    @Column(name = "value_text", nullable = false)
    private String valueText;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public AppConfig() {}

    public AppConfig(String keyText, String valueText) {
        this.keyText = keyText;
        this.valueText = valueText;
        this.updatedAt = Instant.now();
    }
}

