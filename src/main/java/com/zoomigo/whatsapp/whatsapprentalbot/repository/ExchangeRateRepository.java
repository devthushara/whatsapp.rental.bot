package com.zoomigo.whatsapp.whatsapprentalbot.repository;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    Optional<ExchangeRate> findByBaseCurrencyAndTargetCurrency(String base, String target);

    List<ExchangeRate> findByBaseCurrency(String base);

    Optional<ExchangeRate> findByTargetCurrencyAndActiveTargetTrue(String target);

    Optional<ExchangeRate> findByActiveTargetTrue();
}
