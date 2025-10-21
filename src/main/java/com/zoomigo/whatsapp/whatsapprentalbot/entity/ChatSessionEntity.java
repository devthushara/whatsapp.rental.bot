package com.zoomigo.whatsapp.whatsapprentalbot.entity;

import com.zoomigo.whatsapp.whatsapprentalbot.config.JsonbConverter;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "chat_session")
@Data
public class ChatSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String waId;
    private String state;

    //@Column(columnDefinition = "jsonb")
    @Convert(converter = JsonbConverter.class)
    private Map<String, Object> dataJson = new HashMap<>();

    private Instant lastUpdated = Instant.now();

    public ChatSessionEntity() {
    }

    public ChatSessionEntity(String waId, String state, Map<String, Object> dataJson) {
        this.waId = waId;
        this.state = state;
        this.dataJson = dataJson;
    }
}
