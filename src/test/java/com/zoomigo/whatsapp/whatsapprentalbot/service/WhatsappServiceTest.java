package com.zoomigo.whatsapp.whatsapprentalbot.service;

import org.junit.jupiter.api.Test;

class WhatsappServiceTest {

    @Test
    void sendTextMessage_noException() {
        WhatsappService svc = new WhatsappService();
        // Should not throw even when WebClient is null and config may be missing
        svc.sendTextMessage("000", "Hello");
    }
}

