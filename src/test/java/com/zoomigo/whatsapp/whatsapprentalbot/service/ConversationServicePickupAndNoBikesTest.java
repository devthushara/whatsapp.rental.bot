package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Bike;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.ChatSessionEntity;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.User;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ConversationServicePickupAndNoBikesTest {

    @Test
    void askPickup_variations_mapToChoices_and_noBikes_fromPickup() {
        UserRepository userRepo = Mockito.mock(UserRepository.class);
        BikeRepository bikeRepo = Mockito.mock(BikeRepository.class);
        ChatSessionRepository chatRepo = Mockito.mock(ChatSessionRepository.class);
        SessionResetService reset = Mockito.mock(SessionResetService.class);
        BookingRepository bookingRepo = Mockito.mock(BookingRepository.class);
        PromoCodeRepository promoRepo = Mockito.mock(PromoCodeRepository.class);
        PromoCodeBikeRepository promoBikeRepo = Mockito.mock(PromoCodeBikeRepository.class);
        ExchangeRateService ex = Mockito.mock(ExchangeRateService.class);

        ConversationService svc = new ConversationService(userRepo, bikeRepo, chatRepo, reset, bookingRepo, promoRepo, promoBikeRepo, ex);

        User u = new User();
        u.setPhoneNumber("p1");
        u.setStage("ASK_PICKUP");
        when(userRepo.findByPhoneNumber("p1")).thenReturn(Optional.of(u));

        ChatSessionEntity sess = new ChatSessionEntity("p1", "ASK_PICKUP", new HashMap<>());
        when(chatRepo.findByWaId("p1")).thenReturn(Optional.of(sess));

        // No bikes available
        when(bikeRepo.findByIsAvailableTrue()).thenReturn(Collections.emptyList());

        String reply = svc.handleMessage("p1", "pick up at shop");
        assertThat(reply).contains("Proceeding to bike selection");

        // Also try a home delivery phrasing
        u.setStage("ASK_PICKUP");
        when(userRepo.findByPhoneNumber("p1")).thenReturn(Optional.of(u));
        reply = svc.handleMessage("p1", "I want home delivery please");
        assertThat(reply.toLowerCase()).contains("delivery");
    }

    @Test
    void askBike_noAvailableShowsMessage_whenKickoff() {
        UserRepository userRepo = Mockito.mock(UserRepository.class);
        BikeRepository bikeRepo = Mockito.mock(BikeRepository.class);
        ChatSessionRepository chatRepo = Mockito.mock(ChatSessionRepository.class);
        SessionResetService reset = Mockito.mock(SessionResetService.class);
        BookingRepository bookingRepo = Mockito.mock(BookingRepository.class);
        PromoCodeRepository promoRepo = Mockito.mock(PromoCodeRepository.class);
        PromoCodeBikeRepository promoBikeRepo = Mockito.mock(PromoCodeBikeRepository.class);
        ExchangeRateService ex = Mockito.mock(ExchangeRateService.class);

        ConversationService svc = new ConversationService(userRepo, bikeRepo, chatRepo, reset, bookingRepo, promoRepo, promoBikeRepo, ex);

        User u = new User();
        u.setPhoneNumber("p2");
        u.setStage("ASK_BIKE");
        when(userRepo.findByPhoneNumber("p2")).thenReturn(Optional.of(u));

        ChatSessionEntity sess = new ChatSessionEntity("p2", "ASK_BIKE", new HashMap<>());
        when(chatRepo.findByWaId("p2")).thenReturn(Optional.of(sess));

        when(bikeRepo.findByIsAvailableTrue()).thenReturn(Collections.emptyList());

        String r = svc.handleMessage("p2", "1");
        assertThat(r).contains("no bikes are available");
    }
}

