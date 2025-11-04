package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.ExchangeRate;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExchangeRateServiceTest {

    ExchangeRateRepository repo = mock(ExchangeRateRepository.class);
    ExchangeRateService svc;

    @BeforeEach
    void setUp() {
        svc = new ExchangeRateService(repo, null);
    }

    @Test
    void convert_usd_to_lkr_using_db_rate() {
        ExchangeRate er = new ExchangeRate();
        er.setBaseCurrency("USD");
        er.setTargetCurrency("LKR");
        er.setRate(new BigDecimal("320.5"));
        er.setLastUpdated(Instant.now());
        when(repo.findByBaseCurrencyAndTargetCurrency("USD", "LKR")).thenReturn(Optional.of(er));

        BigDecimal out = svc.convert(new BigDecimal("10"), "USD", "LKR");
        assertThat(out).isEqualByComparingTo(new BigDecimal("3205.00"));
    }

    @Test
    void convert_lkr_to_usd_using_db_rate_reverse() {
        ExchangeRate er = new ExchangeRate();
        er.setBaseCurrency("USD");
        er.setTargetCurrency("LKR");
        er.setRate(new BigDecimal("300"));
        when(repo.findByBaseCurrencyAndTargetCurrency("USD", "LKR")).thenReturn(Optional.of(er));

        BigDecimal out = svc.convert(new BigDecimal("3000"), "LKR", "USD");
        // to base: amount / rate = 10.00 -> USD
        assertThat(out).isEqualByComparingTo(new BigDecimal("10.00"));
    }
}

