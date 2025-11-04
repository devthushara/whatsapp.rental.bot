package com.zoomigo.whatsapp.whatsapprentalbot.controller;

import com.zoomigo.whatsapp.whatsapprentalbot.service.WhatsappService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

class TestSenderControllerTest {

    @Test
    void sendText_returnsOk_whenWhatsappServiceSucceeds() {
        WhatsappService ws = Mockito.mock(WhatsappService.class);
        TestSenderController c = new TestSenderController(ws);

        ResponseEntity<String> r = c.sendText("000", "Hello");
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(r.getBody()).contains("Message sent to 000");
    }

    @Test
    void sendText_handlesException_andReturns500() {
        WhatsappService ws = Mockito.mock(WhatsappService.class);
        doThrow(new RuntimeException("boom")).when(ws).sendTextMessage(Mockito.anyString(), Mockito.anyString());
        TestSenderController c = new TestSenderController(ws);

        ResponseEntity<String> r = c.sendText("000", "Hello");
        assertThat(r.getStatusCode().is5xxServerError()).isTrue();
    }

    @Test
    void sendTemplate_returnsNotImplemented() {
        WhatsappService ws = Mockito.mock(WhatsappService.class);
        TestSenderController c = new TestSenderController(ws);
        ResponseEntity<String> r = c.sendTemplate("000", "tpl");
        assertThat(r.getStatusCodeValue()).isEqualTo(501);
    }
}

