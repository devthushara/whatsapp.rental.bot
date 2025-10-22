package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.repository.ChatSessionRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionResetService {

    private final UserRepository userRepo;
    private final ChatSessionRepository chatSessionRepo;

    @Transactional
    public void resetUserAndSession(String waId) {
        log.info("🧨 Resetting session for user {}", waId);
        userRepo.findByPhoneNumber(waId).ifPresent(u -> {
            u.setStage("START");
            userRepo.save(u);
        });
        chatSessionRepo.deleteByWaId(waId);
        log.info("✅ Session reset completed for {}", waId);
    }
}
