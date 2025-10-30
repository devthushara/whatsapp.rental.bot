package com.zoomigo.whatsapp.whatsapprentalbot.controller;

import com.zoomigo.whatsapp.whatsapprentalbot.service.ConversationService;
import com.zoomigo.whatsapp.whatsapprentalbot.service.WhatsappService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WebhookControllerTest {

    @Test
    void verify_success_and_failure() throws Exception {
        ConversationService chat = mock(ConversationService.class);
        WhatsappService ws = mock(WhatsappService.class);
        WebhookController c = new WebhookController(chat, ws);

        // set VERIFY_TOKEN via reflection
        var f = WebhookController.class.getDeclaredField("VERIFY_TOKEN");
        f.setAccessible(true);
        f.set(c, "token123");

        ResponseEntity<String> ok = c.verify("subscribe", "token123", "chal-1");
        assertThat(ok.getStatusCodeValue()).isEqualTo(200);
        assertThat(ok.getBody()).isEqualTo("chal-1");

        ResponseEntity<String> bad = c.verify("subscribe", "wrong", "x");
        assertThat(bad.getStatusCodeValue()).isEqualTo(403);
    }

    @Test
    void receive_text_message_invokes_services() {
        ConversationService chat = mock(ConversationService.class);
        WhatsappService ws = mock(WhatsappService.class);
        WebhookController c = new WebhookController(chat, ws);

        Map<String,Object> text = Map.of("body", "Hello");
        Map<String,Object> msg = Map.of("from", "123", "type", "text", "text", text);
        Map<String,Object> value = Map.of("messages", List.of(msg));
        Map<String,Object> change = Map.of("value", value);
        Map<String,Object> entry = Map.of("changes", List.of(change));
        Map<String,Object> payload = Map.of("entry", List.of(entry));

        when(chat.handleMessage("123", "Hello")).thenReturn("reply!");

        ResponseEntity<String> res = c.receive(payload);
        assertThat(res.getStatusCodeValue()).isEqualTo(200);
        verify(chat).handleMessage("123", "Hello");
        verify(ws).sendTextMessage("123", "reply!");
    }
}

