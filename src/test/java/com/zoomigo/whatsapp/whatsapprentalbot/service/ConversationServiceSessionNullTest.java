package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.ChatSessionEntity;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.User;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationServiceSessionNullTest {

    @Mock
    UserRepository userRepo;
    @Mock
    ChatSessionRepository chatSessionRepo;
    @Mock
    BikeRepository bikeRepo;
    @Mock
    BookingRepository bookingRepo;
    @Mock
    SessionResetService sessionResetService;
    @Mock
    PromoCodeRepository promoRepo;
    @Mock
    com.zoomigo.whatsapp.whatsapprentalbot.repository.PromoCodeBikeRepository promoBikeRepo;

    ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(userRepo, bikeRepo, chatSessionRepo, sessionResetService, bookingRepo, promoRepo, promoBikeRepo);
    }

    @Test
    void sessionSaveReturnsNullDoesNotThrow() {
        when(userRepo.findByPhoneNumber("uNull")).thenReturn(Optional.empty());
        // simulate save returning null (e.g., mocked repo)
        when(userRepo.save(any())).thenReturn(null);
        when(chatSessionRepo.findByWaId("uNull")).thenReturn(Optional.empty());
        when(chatSessionRepo.save(any())).thenReturn(null);

        String r = service.handleMessage("uNull", "Hi");
        assertThat(r).contains("Welcome");
        // verify we attempted to save at least once
        verify(userRepo, atLeastOnce()).save(any());
        verify(chatSessionRepo, atLeastOnce()).save(any());
    }

    @Test
    void pickupMappingVarieties() {
        User u = new User();
        u.setPhoneNumber("uP");
        u.setStage("ASK_PICKUP");
        when(userRepo.findByPhoneNumber("uP")).thenReturn(Optional.of(u));
        when(chatSessionRepo.findByWaId("uP")).thenReturn(Optional.of(new ChatSessionEntity("uP", "ASK_PICKUP", new HashMap<>())));

        // reset stage before each call to simulate fresh prompt
        u.setStage("ASK_PICKUP");
        String r1 = service.handleMessage("uP", "pick up at shop");
        assertThat(r1).contains("pickup at shop");

        u.setStage("ASK_PICKUP");
        String r2 = service.handleMessage("uP", "pickup at store");
        assertThat(r2).contains("pickup at shop");

        u.setStage("ASK_PICKUP");
        String r3 = service.handleMessage("uP", "Home delivery please");
        assertThat(r3).contains("delivery address");

        u.setStage("ASK_PICKUP");
        String r4 = service.handleMessage("uP", "yes");
        assertThat(r4).contains("pickup at shop");

        u.setStage("ASK_PICKUP");
        String r5 = service.handleMessage("uP", "no");
        assertThat(r5).contains("delivery address");
    }
}
