package com.zoomigo.whatsapp.whatsapprentalbot.controller;

import com.zoomigo.whatsapp.whatsapprentalbot.service.ConversationService;
import com.zoomigo.whatsapp.whatsapprentalbot.service.WhatsappService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookControllerUnitTest {

    private ThreadPoolTaskExecutor syncExecutor() {
        return new ThreadPoolTaskExecutor() {
            @Override
            public void execute(Runnable task) {
                task.run();
            }
        };
    }

    @Test
    void verify_success_and_failure() {
        ConversationService cs = Mockito.mock(ConversationService.class);
        WhatsappService ws = Mockito.mock(WhatsappService.class);
        ThreadPoolTaskExecutor exec = syncExecutor();
        WebhookController c = new WebhookController(cs, ws, exec);

        // set private VERIFY_TOKEN via reflection
        try {
            java.lang.reflect.Field f = WebhookController.class.getDeclaredField("VERIFY_TOKEN");
            f.setAccessible(true);
            f.set(c, "token123");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ResponseEntity<String> ok = c.verify("subscribe", "token123", "chall");
        assertThat(ok.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(ok.getBody()).isEqualTo("chall");

        ResponseEntity<String> fail = c.verify("subscribe", "wrong", "x");
        assertThat(fail.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void receive_noEntry_noChanges_noMessages() {
        ConversationService cs = Mockito.mock(ConversationService.class);
        WhatsappService ws = Mockito.mock(WhatsappService.class);
        ThreadPoolTaskExecutor exec = syncExecutor();
        WebhookController c = new WebhookController(cs, ws, exec);

        Map<String, Object> payload = new HashMap<>();
        ResponseEntity<String> r = c.receive(payload);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(r.getBody()).isEqualTo("No entry");

        payload.put("entry", List.of(Map.of()));
        r = c.receive(payload);
        assertThat(r.getBody()).isEqualTo("No changes");

        Map<String, Object> entry = new HashMap<>();
        entry.put("changes", List.of(Map.of("value", Map.of())));
        payload.put("entry", List.of(entry));
        r = c.receive(payload);
        assertThat(r.getBody()).isEqualTo("No messages");
    }

    @Test
    void receive_nonTextAndText_flow() {
        ConversationService cs = Mockito.mock(ConversationService.class);
        WhatsappService ws = Mockito.mock(WhatsappService.class);
        ThreadPoolTaskExecutor exec = syncExecutor();
        WebhookController c = new WebhookController(cs, ws, exec);

        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        message.put("from", "123");
        message.put("type", "image");
        Map<String, Object> value = Map.of("messages", List.of(message));
        Map<String, Object> change = Map.of("value", value);
        Map<String, Object> entry = Map.of("changes", List.of(change));
        payload.put("entry", List.of(entry));

        ResponseEntity<String> r = c.receive(payload);
        assertThat(r.getBody()).isEqualTo("EVENT_RECEIVED");

        // Now text message path
        Map<String, Object> textObj = Map.of("body", "Hello");
        message.put("type", "text");
        message.put("text", textObj);
        payload.put("entry", List.of(entry));

        when(cs.handleMessage(eq("123"), eq("Hello"))).thenReturn("reply!");

        r = c.receive(payload);
        assertThat(r.getBody()).isEqualTo("EVENT_RECEIVED");

        // ensure whatsapp service was called synchronously via executor
        verify(ws).sendTextMessage("123", "reply!");
    }
}

