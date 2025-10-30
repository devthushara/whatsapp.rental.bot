package com.zoomigo.whatsapp.whatsapprentalbot.service;

import org.junit.jupiter.api.Test;

class WhatsappServiceTest {

    @Test
    void sendTextMessage_noException() {
        WhatsappService svc = new WhatsappService();
        // Intentionally call with invalid config; method should catch exceptions and not throw
        svc.sendTextMessage("000", "Hello");
    }
}

