package com.zoomigo.whatsapp.whatsapprentalbot.config;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class JsonbConverterTest {

    @Test
    void convertToDatabaseColumn_and_back() {
        JsonbConverter c = new JsonbConverter();
        Map<String, Object> map = Map.of("a", 1, "b", "x");
        String js = c.convertToDatabaseColumn(map);
        Map<String, Object> out = c.convertToEntityAttribute(js);
        assertThat(out).containsEntry("a", 1).containsEntry("b", "x");
    }

    @Test
    void nullsHandled() {
        JsonbConverter c = new JsonbConverter();
        String js = c.convertToDatabaseColumn(null);
        assertThat(js).isNotNull();
        Map<String, Object> out = c.convertToEntityAttribute(null);
        assertThat(out).isEmpty();
    }
}

