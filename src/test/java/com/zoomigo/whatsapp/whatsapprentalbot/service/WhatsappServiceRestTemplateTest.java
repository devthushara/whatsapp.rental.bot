package com.zoomigo.whatsapp.whatsapprentalbot.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class WhatsappServiceRestTemplateTest {

    @Test
    void sendTextMessage_usesRestTemplate_whenConfigPresent() throws Exception {
        // Create service using default constructor (webClient==null)
        WhatsappService service = new WhatsappService();

        // set fields via reflection
        setField(service, "apiBaseUrl", "http://example.com");
        setField(service, "apiVersion", "v1");
        setField(service, "phoneNumberId", "pnid");
        setField(service, "accessToken", "tok");

        // replace internal restTemplate with mock
        RestTemplate mockRt = Mockito.mock(RestTemplate.class);
        ResponseEntity<String> ok = new ResponseEntity<>("ok", HttpStatus.OK);
        when(mockRt.exchange(any(String.class), any(), any(), eq(String.class))).thenReturn(ok);
        setField(service, "restTemplate", mockRt);

        // call
        service.sendTextMessage("000", "Hello");

        // verify exchange was called
        Mockito.verify(mockRt).exchange(any(String.class), any(), any(), eq(String.class));
    }

    // helper to set private final fields via reflection
    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        // in case of final fields, remove final modifier (not necessary in tests but do it defensively)
        try {
            java.lang.reflect.Field modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(f, f.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
        } catch (NoSuchFieldException ignored) {
        }
        f.set(target, value);
    }
}

