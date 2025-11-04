package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Bike;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.PromoCode;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ConversationServiceCacheTest {

    @Test
    void getAvailableBikes_usesCache_whenCacheManagerProvided() {
        UserRepository userRepo = mock(UserRepository.class);
        BikeRepository bikeRepo = mock(BikeRepository.class);
        ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
        SessionResetService sessionResetService = mock(SessionResetService.class);
        BookingRepository bookingRepo = mock(BookingRepository.class);
        PromoCodeRepository promoRepo = mock(PromoCodeRepository.class);
        PromoCodeBikeRepository promoBikeRepo = mock(PromoCodeBikeRepository.class);

        ConversationService svc = new ConversationService(userRepo, bikeRepo, chatSessionRepo, sessionResetService, bookingRepo, promoRepo, promoBikeRepo);
        svc.setCacheManager(new ConcurrentMapCacheManager("bikes", "promos"));

        Bike b = new Bike();
        b.setId(900L);
        b.setName("CacheBike");
        b.setPricePerDay(100);
        when(bikeRepo.findByIsAvailableTrue()).thenReturn(List.of(b));

        // first call should populate cache
        List<Bike> first = svc.getAvailableBikes();
        List<Bike> second = svc.getAvailableBikes();

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        // verify repo called only once because cache should be used on second call
        verify(bikeRepo, times(1)).findByIsAvailableTrue();
    }

    @Test
    void getPromoByCode_usesCache_whenCacheManagerProvided() {
        UserRepository userRepo = mock(UserRepository.class);
        BikeRepository bikeRepo = mock(BikeRepository.class);
        ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
        SessionResetService sessionResetService = mock(SessionResetService.class);
        BookingRepository bookingRepo = mock(BookingRepository.class);
        PromoCodeRepository promoRepo = mock(PromoCodeRepository.class);
        PromoCodeBikeRepository promoBikeRepo = mock(PromoCodeBikeRepository.class);

        ConversationService svc = new ConversationService(userRepo, bikeRepo, chatSessionRepo, sessionResetService, bookingRepo, promoRepo, promoBikeRepo);
        svc.setCacheManager(new ConcurrentMapCacheManager("bikes", "promos"));

        PromoCode p = new PromoCode();
        p.setId(55L);
        p.setCode("CACHEME");
        p.setActive(true);
        when(promoRepo.findByCodeIgnoreCase("CACHEME")).thenReturn(java.util.Optional.of(p));

        PromoCode a = svc.getPromoByCode("CACHEME");
        PromoCode b2 = svc.getPromoByCode("CACHEME");

        assertThat(a).isNotNull();
        assertThat(b2).isNotNull();
        verify(promoRepo, times(1)).findByCodeIgnoreCase("CACHEME");
    }
}

