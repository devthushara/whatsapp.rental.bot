package com.zoomigo.whatsapp.whatsapprentalbot.controller;

import com.zoomigo.whatsapp.whatsapprentalbot.service.ConversationService;
import com.zoomigo.whatsapp.whatsapprentalbot.service.WhatsappService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WebhookControllerTest {

    @Test
    void verify_success_and_failure() throws Exception {
        ConversationService chat = mock(ConversationService.class);
        WhatsappService ws = mock(WhatsappService.class);
        ThreadPoolTaskExecutor exec = mock(ThreadPoolTaskExecutor.class);
        WebhookController c = new WebhookController(chat, ws, exec);

        // set VERIFY_TOKEN via reflection
        var f = WebhookController.class.getDeclaredField("VERIFY_TOKEN");
        f.setAccessible(true);
        f.set(c, "token123");

        ResponseEntity<String> ok = c.verify("subscribe", "token123", "chal-1");
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(ok.getBody()).isEqualTo("chal-1");

        ResponseEntity<String> bad = c.verify("subscribe", "wrong", "x");
        assertThat(bad.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void receive_text_message_invokes_services() {
        ConversationService chat = mock(ConversationService.class);
        WhatsappService ws = mock(WhatsappService.class);
        ThreadPoolTaskExecutor exec = mock(ThreadPoolTaskExecutor.class);

        // Make executor run submitted Runnable synchronously in test
        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            r.run();
            return null;
        }).when(exec).execute(any(Runnable.class));

        WebhookController c = new WebhookController(chat, ws, exec);

        Map<String, Object> text = Map.of("body", "Hello");
        Map<String, Object> msg = Map.of("from", "123", "type", "text", "text", text);
        Map<String, Object> value = Map.of("messages", List.of(msg));
        Map<String, Object> change = Map.of("value", value);
        Map<String, Object> entry = Map.of("changes", List.of(change));
        Map<String, Object> payload = Map.of("entry", List.of(entry));

        when(chat.handleMessage("123", "Hello")).thenReturn("reply!");

        ResponseEntity<String> res = c.receive(payload);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        verify(chat).handleMessage("123", "Hello");
        verify(ws).sendTextMessage("123", "reply!");
    }

    @Test
    void receive_non_text_message_does_not_invoke_services() {
        ConversationService chat = mock(ConversationService.class);
        WhatsappService ws = mock(WhatsappService.class);
        ThreadPoolTaskExecutor exec = mock(ThreadPoolTaskExecutor.class);

        WebhookController c = new WebhookController(chat, ws, exec);

        // message with type 'image' and no 'text' block
        Map<String, Object> msg = Map.of("from", "999", "type", "image");
        Map<String, Object> value = Map.of("messages", List.of(msg));
        Map<String, Object> change = Map.of("value", value);
        Map<String, Object> entry = Map.of("changes", List.of(change));
        Map<String, Object> payload = Map.of("entry", List.of(entry));

        ResponseEntity<String> res = c.receive(payload);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(chat);
        verifyNoInteractions(ws);
    }

    @Test
    void receive_no_entry_returns_ok() {
        ConversationService chat = mock(ConversationService.class);
        WhatsappService ws = mock(WhatsappService.class);
        ThreadPoolTaskExecutor exec = mock(ThreadPoolTaskExecutor.class);

        WebhookController c = new WebhookController(chat, ws, exec);
        Map<String, Object> payload = Map.of();
        ResponseEntity<String> res = c.receive(payload);
        assertThat(res.getBody()).isEqualTo("No entry");
    }

    @Test
    void receive_no_messages_returns_ok() {
        ConversationService chat = mock(ConversationService.class);
        WhatsappService ws = mock(WhatsappService.class);
        ThreadPoolTaskExecutor exec = mock(ThreadPoolTaskExecutor.class);

        WebhookController c = new WebhookController(chat, ws, exec);

        Map<String, Object> value = Map.of();
        Map<String, Object> change = Map.of("value", value);
        Map<String, Object> entry = Map.of("changes", List.of(change));
        Map<String, Object> payload = Map.of("entry", List.of(entry));

        ResponseEntity<String> res = c.receive(payload);
        assertThat(res.getBody()).isEqualTo("No messages");
    }
}
