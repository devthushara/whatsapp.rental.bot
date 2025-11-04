package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.ExchangeRate;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ExchangeRateServiceConvertTest {

    @Test
    void usdToTarget_conversionUsesRate() {
        ExchangeRateRepository repo = Mockito.mock(ExchangeRateRepository.class);
        ExchangeRate toLkr = new ExchangeRate();
        toLkr.setRate(BigDecimal.valueOf(320.5));
        when(repo.findByBaseCurrencyAndTargetCurrency("USD", "LKR")).thenReturn(Optional.of(toLkr));

        ExchangeRateService s = new ExchangeRateService(repo, null);
        BigDecimal out = s.convert(BigDecimal.valueOf(1), "USD", "LKR");
        assertThat(out).isEqualByComparingTo("320.50");
    }

    @Test
    void targetToUsd_conversionUsesRate() {
        ExchangeRateRepository repo = Mockito.mock(ExchangeRateRepository.class);
        ExchangeRate baseToLkr = new ExchangeRate();
        baseToLkr.setRate(BigDecimal.valueOf(320.5));
        when(repo.findByBaseCurrencyAndTargetCurrency("USD", "LKR")).thenReturn(Optional.of(baseToLkr));

        ExchangeRateService s = new ExchangeRateService(repo, null);
        BigDecimal out = s.convert(BigDecimal.valueOf(3205), "LKR", "USD");
        assertThat(out).isEqualByComparingTo("10.00");
    }

    @Test
    void fromToViaBase_generalCase() {
        ExchangeRateRepository repo = Mockito.mock(ExchangeRateRepository.class);
        ExchangeRate usdToEur = new ExchangeRate();
        usdToEur.setRate(BigDecimal.valueOf(0.92));
        ExchangeRate usdToJpy = new ExchangeRate();
        usdToJpy.setRate(BigDecimal.valueOf(150.25));
        when(repo.findByBaseCurrencyAndTargetCurrency("USD", "EUR")).thenReturn(Optional.of(usdToEur));
        when(repo.findByBaseCurrencyAndTargetCurrency("USD", "JPY")).thenReturn(Optional.of(usdToJpy));

        ExchangeRateService s = new ExchangeRateService(repo, null);
        // convert 92 EUR to JPY => EUR->USD = 92 / 0.92 = 100, USD->JPY = 100 * 150.25 = 15025
        BigDecimal out = s.convert(BigDecimal.valueOf(92), "EUR", "JPY");
        assertThat(out).isEqualByComparingTo("15025.00");
    }
}

