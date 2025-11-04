package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.AppConfig;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.AppConfigRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AppConfigServiceTest {

    @Test
    void getDefaultCurrency_returnsValueFromRepo_whenPresent() {
        AppConfigRepository repo = Mockito.mock(AppConfigRepository.class);
        AppConfig cfg = new AppConfig("default_currency", "EUR");
        cfg.setUpdatedAt(Instant.now());
        when(repo.findById("default_currency")).thenReturn(Optional.of(cfg));

        AppConfigService s = new AppConfigService(repo);
        assertThat(s.getDefaultCurrency()).isEqualTo("EUR");
    }

    @Test
    void setDefaultCurrency_savesToRepo() {
        AppConfigRepository repo = Mockito.mock(AppConfigRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppConfigService s = new AppConfigService(repo);
        s.setDefaultCurrency("USD");

        Mockito.verify(repo).save(any(AppConfig.class));
        assertThat(s.getDefaultCurrency()).isEqualTo("USD");
    }
}

