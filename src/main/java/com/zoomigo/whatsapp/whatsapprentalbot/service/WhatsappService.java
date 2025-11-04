package com.zoomigo.whatsapp.whatsapprentalbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class WhatsappService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final WebClient webClient;
    @Value("${whatsapp.api-base-url:}")
    private String apiBaseUrlProp;
    // alias for older tests
    @Value("${whatsapp.api-base-url:}")
    private String apiBaseUrl;

    @Value("${whatsapp.api-version:}")
    private String apiVersionProp;
    // alias for older tests
    @Value("${whatsapp.api-version:}")
    private String apiVersion;

    @Value("${whatsapp.phone-number-id:}")
    private String phoneNumberIdProp;
    // alias for older tests
    @Value("${whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${whatsapp.access-token:}")
    private String accessTokenProp;
    // alias for older tests
    @Value("${whatsapp.access-token:}")
    private String accessToken;

    @Autowired
    public WhatsappService(WebClient whatsappWebClient) {
        this.webClient = whatsappWebClient;
    }

    public WhatsappService() {
        this.webClient = null;
    }

    public void sendTextMessage(String to, String body) {
        try {
            String apiBaseUrl = apiBaseUrlProp == null || apiBaseUrlProp.isBlank() ? this.apiBaseUrl : apiBaseUrlProp;
            String apiVersion = apiVersionProp == null || apiVersionProp.isBlank() ? this.apiVersion : apiVersionProp;
            String phoneNumberId = phoneNumberIdProp == null || phoneNumberIdProp.isBlank() ? this.phoneNumberId : phoneNumberIdProp;
            String accessToken = accessTokenProp == null || accessTokenProp.isBlank() ? this.accessToken : accessTokenProp;

            String urlPath = String.format("/%s/%s/messages", apiVersion, phoneNumberId);

            Map<String, Object> message = new HashMap<>();
            message.put("messaging_product", "whatsapp");
            message.put("to", to);
            message.put("type", "text");
            message.put("text", Map.of("body", body));

            log.info("📤 Sending WhatsApp message to {}: {}", to, body);

            if (webClient != null && apiBaseUrl != null && !apiBaseUrl.isBlank()) {
                // Non-blocking send; fire-and-forget
                WebClient.RequestBodySpec req = webClient.post().uri(urlPath)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON);
                if (accessToken != null && !accessToken.isBlank()) req.headers(h -> h.setBearerAuth(accessToken));

                req.bodyValue(message)
                        .retrieve()
                        .bodyToMono(String.class)
                        .doOnNext(r -> log.info("✅ Message sent successfully to {}", to))
                        .doOnError(err -> log.warn("⚠️ Failed to send message to {}: {}", to, err.getMessage()))
                        .onErrorResume(e -> Mono.empty())
                        .subscribe();
                return;
            }

            // fallback blocking RestTemplate call — only if config present
            if (apiBaseUrl == null || apiBaseUrl.isBlank() || apiVersion == null || apiVersion.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
                log.warn("⚠️ WhatsApp HTTP config missing (apiBaseUrl/apiVersion/phoneNumberId). Skipping send for {}", to);
                return;
            }

            String url = String.format("%s/%s/%s/messages", apiBaseUrl, apiVersion, phoneNumberId);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (accessToken != null && !accessToken.isBlank()) headers.setBearerAuth(accessToken);

            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(message, headers);

            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.POST, entity, String.class);

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
