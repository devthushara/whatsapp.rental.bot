package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Bike;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.PromoCode;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.PromoCodeBike;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.User;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ConversationServicePromoMappingUnitTest {

    @Test
    void bikeSpecificPromo_onlyAppliesToMappedBike() {
        UserRepository userRepo = Mockito.mock(UserRepository.class);
        BikeRepository bikeRepo = Mockito.mock(BikeRepository.class);
        ChatSessionRepository chatRepo = Mockito.mock(ChatSessionRepository.class);
        SessionResetService reset = Mockito.mock(SessionResetService.class);
        BookingRepository bookingRepo = Mockito.mock(BookingRepository.class);
        PromoCodeRepository promoRepo = Mockito.mock(PromoCodeRepository.class);
        PromoCodeBikeRepository promoBikeRepo = Mockito.mock(PromoCodeBikeRepository.class);

        // Use the backward-compatible 7-arg constructor (no ExchangeRateService / AppConfigService)
        ConversationService svc = new ConversationService(userRepo, bikeRepo, chatRepo, reset, bookingRepo, promoRepo, promoBikeRepo);

        User u = new User();
        u.setPhoneNumber("p3");
        u.setStage("ASK_PROMO");
        u.setSelectedBikeId(1L);
        u.setDays(1);
        u.setStartDate(java.time.LocalDate.now());
        when(userRepo.findByPhoneNumber("p3")).thenReturn(Optional.of(u));

        // let the service create a session placeholder if repository returns null; we provide a real session here
        when(chatRepo.findByWaId("p3")).thenReturn(Optional.of(new com.zoomigo.whatsapp.whatsapprentalbot.entity.ChatSessionEntity("p3","ASK_PROMO", Map.of("bikeMap", Map.of("1",1L)))));

        PromoCode promo = new PromoCode();
        promo.setId(10L);
        promo.setCode("BIKE1_100");
        promo.setActive(true);
        promo.setDiscountAmount(100);
        when(promoRepo.findByCodeIgnoreCase("BIKE1_100")).thenReturn(Optional.of(promo));
        PromoCodeBike mapping = new PromoCodeBike();
        mapping.setPromoCode(promo);
        mapping.setBike(new Bike(){ { setId(1L); } });
        when(promoBikeRepo.findByPromoCode_Id(10L)).thenReturn(List.of(mapping));

        // set up bike repo
        Bike b1 = new Bike(); b1.setId(1L); b1.setName("Honda PCX 150"); b1.setPricePerDay(2500); b1.setDeposit(25000); b1.setAvailable(true);
        when(bikeRepo.findById(1L)).thenReturn(Optional.of(b1));
        when(bikeRepo.findByIsAvailableTrue()).thenReturn(List.of(b1));

        String reply = svc.handleMessage("p3", "BIKE1_100");
        assertThat(reply).contains("Promo 'BIKE1_100' applied");
    }
}
