package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.User;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.ChatSessionRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionResetServiceTest {

    @Mock
    UserRepository userRepo;
    @Mock
    ChatSessionRepository chatSessionRepo;

    SessionResetService service;

    @BeforeEach
    void setUp() {
        service = new SessionResetService(userRepo, chatSessionRepo);
    }

    @Test
    void resetUserAndSession() {
        User u = new User(); u.setPhoneNumber("p1"); u.setStage("ASK_NAME");
        when(userRepo.findByPhoneNumber("p1")).thenReturn(Optional.of(u));

        service.resetUserAndSession("p1");

        verify(userRepo).save(any(User.class));
        verify(chatSessionRepo).deleteByWaId("p1");
    }
}

