package com.zoomigo.whatsapp.whatsapprentalbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.Map;

@Converter
public class JsonbConverter implements AttributeConverter<Map<String, Object>, String> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        try {
            if (attribute == null) return "{}";
            return mapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert map to JSON string.", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isEmpty()) return new HashMap<>();
            return mapper.readValue(dbData, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert JSON string to Map", e);
        }
    }
}
