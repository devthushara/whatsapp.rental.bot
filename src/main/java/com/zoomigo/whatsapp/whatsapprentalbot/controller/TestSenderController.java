package com.zoomigo.whatsapp.whatsapprentalbot.controller;

import com.zoomigo.whatsapp.whatsapprentalbot.service.WhatsappService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api")
public class TestSenderController {

    private final WhatsappService whatsappService;

    public TestSenderController(WhatsappService whatsappService) {
        this.whatsappService = whatsappService;
    }

    /**
     * Simple test endpoint to send a WhatsApp text message manually.
     * Example: POST /api/send-text?to=6594252874&text=Hello
     */
    @PostMapping("/send-text")
    public ResponseEntity<String> sendText(@RequestParam String to, @RequestParam String text) {
        try {
            log.info("💬 Sending WhatsApp message to {}: {}", to, text);
            whatsappService.sendTextMessage(to, text);
            return ResponseEntity.ok("✅ Message sent to " + to);
        } catch (Exception e) {
            log.error("❌ Failed to send WhatsApp message to {}: {}", to, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error sending message");
        }
    }

    /**
     * (Optional) Placeholder for sending templates, if you want to add it back later.
     * Right now, just returns 501 Not Implemented.
     */
    @PostMapping("/send-template")
    public ResponseEntity<String> sendTemplate(@RequestParam String to, @RequestParam String template) {
        log.warn("⚠️ sendTemplate endpoint called, but not implemented.");
        return ResponseEntity.status(501).body("Template sending not implemented yet.");
    }
}
