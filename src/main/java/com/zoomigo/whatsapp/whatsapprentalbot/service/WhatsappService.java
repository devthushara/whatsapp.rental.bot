package com.zoomigo.whatsapp.whatsapprentalbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class WhatsappService {

    private final RestTemplate restTemplate = new RestTemplate();
    @Value("${whatsapp.api-base-url}")
    private String apiBaseUrl;
    @Value("${whatsapp.api-version}")
    private String apiVersion;
    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;
    @Value("${whatsapp.access-token}")
    private String accessToken;

    public void sendTextMessage(String to, String body) {
        try {
            String url = String.format("%s/%s/%s/messages", apiBaseUrl, apiVersion, phoneNumberId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            Map<String, Object> message = new HashMap<>();
            message.put("messaging_product", "whatsapp");
            message.put("to", to);
            message.put("type", "text");
            message.put("text", Map.of("body", body));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(message, headers);
            log.info("📤 Sending WhatsApp message to {}: {}", to, body);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Message sent successfully to {}", to);
            } else {
                log.warn("⚠️ Failed to send message to {}. Response: {}", to, response.getBody());
            }
        } catch (Exception e) {
            log.error("❌ Error sending WhatsApp message to {}: {}", to, e.getMessage(), e);
        }
    }
}
