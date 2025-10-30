package com.zoomigo.whatsapp.whatsapprentalbot.controller;

import com.zoomigo.whatsapp.whatsapprentalbot.service.ConversationService;
import com.zoomigo.whatsapp.whatsapprentalbot.service.WhatsappService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private final ConversationService chatService;
    private final WhatsappService whatsappService;
    private final ThreadPoolTaskExecutor conversationExecutor;
    @Value("${security.verify-token}")
    private String VERIFY_TOKEN;

    public WebhookController(ConversationService chatService, WhatsappService whatsappService, ThreadPoolTaskExecutor conversationExecutor) {
        this.chatService = chatService;
        this.whatsappService = whatsappService;
        this.conversationExecutor = conversationExecutor;
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        log.info("🔗 Verification request received (mode={}, token={})", mode, token);
        if ("subscribe".equals(mode) && VERIFY_TOKEN.equals(token)) {
            log.info("✅ Webhook verified successfully");
            return ResponseEntity.ok(challenge);
        }
        log.warn("❌ Webhook verification failed: invalid token");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    @PostMapping
    public ResponseEntity<String> receive(@RequestBody Map<String, Object> payload) {
        try {
            log.info("📩 Incoming WhatsApp payload: {}", payload);

            var entryList = (java.util.List<?>) payload.get("entry");
            if (entryList == null || entryList.isEmpty()) return ResponseEntity.ok("No entry");
            var entry = (Map<String, Object>) entryList.get(0);
            var changes = (java.util.List<?>) entry.get("changes");
            if (changes == null || changes.isEmpty()) return ResponseEntity.ok("No changes");

            var change = (Map<String, Object>) changes.get(0);
            var value = (Map<String, Object>) change.get("value");
            var messages = (java.util.List<?>) value.get("messages");
            if (messages == null || messages.isEmpty()) return ResponseEntity.ok("No messages");

            var message = (Map<String, Object>) messages.get(0);
            String from = (String) message.get("from");
            String type = (String) message.get("type");

            if ("text".equals(type)) {
                var textObj = (Map<String, Object>) message.get("text");
                String text = (String) textObj.get("body");
                log.info("💬 [{}] {}", from, text);

                // offload to conversation executor so controller returns fast
                conversationExecutor.execute(() -> {
                    try {
                        String reply = chatService.handleMessage(from, text);
                        whatsappService.sendTextMessage(from, reply);
                        log.info("✅ Reply sent to {}: {}", from, reply);
                    } catch (Exception e) {
                        log.error("❌ Error processing message for {}: {}", from, e.getMessage(), e);
                    }
                });

            } else {
                log.info("ℹ️ Unsupported message type: {}", type);
            }

            return ResponseEntity.ok("EVENT_RECEIVED");
        } catch (Exception e) {
            log.error("❌ Error handling webhook", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }
}
