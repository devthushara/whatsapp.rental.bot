package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.ExchangeRate;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ExchangeRateServiceAppConfigTest {

    @Test
    void getActiveCurrency_prefersAppConfig_whenPresent() {
        ExchangeRateRepository repo = Mockito.mock(ExchangeRateRepository.class);
        AppConfigService cfg = Mockito.mock(AppConfigService.class);
        when(cfg.getDefaultCurrency()).thenReturn("EUR");

        ExchangeRateService s = new ExchangeRateService(repo, null, cfg);
        String active = s.getActiveCurrency();
        assertThat(active).isEqualTo("EUR");
    }

    @Test
    void convert_usesRepoRates_forUsdToTarget() {
        ExchangeRateRepository repo = Mockito.mock(ExchangeRateRepository.class);
        ExchangeRate toLkr = new ExchangeRate();
        toLkr.setRate(BigDecimal.valueOf(320.5));
        when(repo.findByBaseCurrencyAndTargetCurrency("USD", "LKR")).thenReturn(Optional.of(toLkr));

        ExchangeRateService s = new ExchangeRateService(repo, null, null);
        BigDecimal out = s.convert(BigDecimal.valueOf(10), "USD", "LKR");
        assertThat(out).isEqualByComparingTo("3205.00");
    }
}

