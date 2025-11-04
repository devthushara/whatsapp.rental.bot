package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.ExchangeRate;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.ExchangeRateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ExchangeRateService {

    private final ExchangeRateRepository repo;
    private final WebClient webClient;

    @Value("${openexchangerates.app_id:}")
    private String openExchangeAppId;

    @Value("${app.base-currency:USD}")
    private String baseCurrency;

    // optional app config service to read runtime settings like default currency
    private final AppConfigService appConfigService;

    @Autowired
    public ExchangeRateService(ExchangeRateRepository repo, @Nullable WebClient.Builder webClientBuilder, @Nullable AppConfigService appConfigService) {
        this.repo = repo;
        this.appConfigService = appConfigService;
        if (webClientBuilder == null) {
            this.webClient = WebClient.create("https://openexchangerates.org/api");
        } else {
            this.webClient = webClientBuilder.baseUrl("https://openexchangerates.org/api").build();
        }
    }

    // 2-arg convenience constructor for tests/backwards compatibility
    public ExchangeRateService(ExchangeRateRepository repo, WebClient.Builder webClientBuilder) {
        this(repo, webClientBuilder, null);
    }

    private String getBaseCurrency() {
        // Prefer runtime config from AppConfigService if present
        try {
            if (this.appConfigService != null) {
                String cfg = this.appConfigService.getDefaultCurrency();
                if (cfg != null && !cfg.isBlank()) return cfg.trim().toUpperCase(Locale.ENGLISH);
            }
        } catch (Exception ignored) {
        }

        if (baseCurrency == null || baseCurrency.isBlank()) return "USD";
        return baseCurrency.trim().toUpperCase(Locale.ENGLISH);
    }

    // Convert amount from one currency to another using DB-stored rates; if useLiveRate flag is set, call API
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String base = getBaseCurrency();
        fromCurrency = normalize(fromCurrency == null ? base : fromCurrency);
        toCurrency = normalize(toCurrency == null ? base : toCurrency);
        if (fromCurrency.equalsIgnoreCase(toCurrency)) return amount.setScale(2, RoundingMode.HALF_UP);

        if (this.repo == null) return amount.setScale(2, RoundingMode.HALF_UP);

        // Try to read rates base->from and base->to
        Optional<ExchangeRate> rateBaseToFrom = repo.findByBaseCurrencyAndTargetCurrency(base, fromCurrency);
        Optional<ExchangeRate> rateBaseToTo = repo.findByBaseCurrencyAndTargetCurrency(base, toCurrency);

        try {
            if (fromCurrency.equalsIgnoreCase(base)) {
                // USD -> target
                if (rateBaseToTo.isPresent() && rateBaseToTo.get().getRate() != null)
                    return amount.multiply(rateBaseToTo.get().getRate()).setScale(2, RoundingMode.HALF_UP);
                return amount.setScale(2, RoundingMode.HALF_UP);
            }

            if (toCurrency.equalsIgnoreCase(base)) {
                // from -> USD
                if (rateBaseToFrom.isPresent() && rateBaseToFrom.get().getRate() != null)
                    return amount.divide(rateBaseToFrom.get().getRate(), 2, RoundingMode.HALF_UP);
                return amount.setScale(2, RoundingMode.HALF_UP);
            }

            // General case: from -> base -> to
            BigDecimal amountInBase = amount;
            if (rateBaseToFrom.isPresent() && rateBaseToFrom.get().getRate() != null) {
                amountInBase = amount.divide(rateBaseToFrom.get().getRate(), 8, RoundingMode.HALF_UP);
            }
            if (rateBaseToTo.isPresent() && rateBaseToTo.get().getRate() != null) {
                return amountInBase.multiply(rateBaseToTo.get().getRate()).setScale(2, RoundingMode.HALF_UP);
            }
            return amountInBase.setScale(2, RoundingMode.HALF_UP);
        } catch (ArithmeticException ae) {
            log.warn("Arithmetic error in convert: {}", ae.getMessage());
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
    }

    // Fetch live rates from OpenExchangeRates for the specific target currency and save into DB.
    // This method is intentionally fire-and-forget for now.
    private void fetchLiveRates(String targetCurrency) {
        if (openExchangeAppId == null || openExchangeAppId.isBlank()) {
            log.warn("OpenExchangeRates app_id not configured. Skipping live fetch.");
            return;
        }
        if (this.repo == null) {
            log.warn("ExchangeRateRepository is null - skipping live fetch in test mode.");
            return;
        }
        try {
            Map<String, Object> resp = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/latest.json")
                            .queryParam("app_id", openExchangeAppId)
                            .queryParam("symbols", targetCurrency)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(5));

            if (resp != null && resp.containsKey("rates")) {
                Map<String, Object> rates = (Map<String, Object>) resp.get("rates");
                Object r = rates.get(targetCurrency);
                if (r instanceof Number) {
                    BigDecimal rate = new BigDecimal(((Number) r).toString());
                    String base = getBaseCurrency();
                    Optional<ExchangeRate> maybe = repo.findByBaseCurrencyAndTargetCurrency(base, targetCurrency);
                    ExchangeRate er = maybe.orElseGet(() -> {
                        ExchangeRate ne = new ExchangeRate();
                        ne.setBaseCurrency(base);
                        ne.setTargetCurrency(targetCurrency);
                        return ne;
                    });
                    er.setRate(rate);
                    er.setLastUpdated(Instant.now());
                    er.setUseLiveRate(true);
                    repo.save(er);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch live rates for {}: {}", targetCurrency, e.getMessage());
        }
    }

    private String normalize(String cur) {
        if (cur == null) return getBaseCurrency();
        return cur.trim().toUpperCase(Locale.ENGLISH);
    }

    // Find the active target currency (admin-marked) or return baseCurrency if none
    public String getActiveCurrency() {
        // First prefer configured app-level default if available
        try {
            if (this.appConfigService != null) {
                String cfg = this.appConfigService.getDefaultCurrency();
                if (cfg != null && !cfg.isBlank()) return cfg.trim().toUpperCase(Locale.ENGLISH);
            }
        } catch (Exception ignored) {
        }

        String base = getBaseCurrency();
        if (this.repo == null) return base;
        Optional<ExchangeRate> maybe = repo.findByActiveTargetTrue();
        if (maybe.isPresent()) return maybe.get().getTargetCurrency();
        // fallback: try any active target
        List<ExchangeRate> all = repo.findByBaseCurrency(base);
        if (all != null && !all.isEmpty()) {
            for (ExchangeRate e : all) if (Boolean.TRUE.equals(e.getActiveTarget())) return e.getTargetCurrency();
            return all.get(0).getTargetCurrency();
        }
        return base;
    }
}
