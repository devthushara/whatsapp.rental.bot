package com.zoomigo.whatsapp.whatsapprentalbot.repository;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppConfigRepository extends JpaRepository<AppConfig, String> {
}

