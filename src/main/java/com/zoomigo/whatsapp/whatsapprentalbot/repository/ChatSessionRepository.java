package com.zoomigo.whatsapp.whatsapprentalbot.repository;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, Long> {
    Optional<ChatSessionEntity> findByWaId(String waId);
}
