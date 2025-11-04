package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.AppConfig;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.AppConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class AppConfigService {
    private final AppConfigRepository repo;

    public AppConfigService(AppConfigRepository repo) {
        this.repo = repo;
    }

    public String getDefaultCurrency() {
        return getString("default_currency", "USD");
    }

    public void setDefaultCurrency(String currency) {
        setString("default_currency", currency);
    }

    public String getString(String key, String defaultValue) {
        try {
            Optional<AppConfig> maybe = repo.findById(key);
            if (maybe.isPresent()) return maybe.get().getValueText();
        } catch (Exception ignored) {}
        return defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String v = getString(key, String.valueOf(defaultValue));
        try { return Boolean.parseBoolean(v); } catch (Exception ignored) { return defaultValue; }
    }

    public int getInt(String key, int defaultValue) {
        String v = getString(key, String.valueOf(defaultValue));
        try { return Integer.parseInt(v); } catch (Exception ignored) { return defaultValue; }
    }

    public void setString(String key, String value) {
        AppConfig cfg = new AppConfig();
        cfg.setKeyText(key);
        cfg.setValueText(value == null ? "" : value);
        cfg.setUpdatedAt(Instant.now());
        repo.save(cfg);
    }
}
