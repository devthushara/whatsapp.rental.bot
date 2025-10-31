package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Bike;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.PromoCode;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.User;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationServiceExtraTest {

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
    @Mock
    CacheManager cacheManager;
    @Mock
    Cache promosCache;
    @Mock
    Cache bikesCache;

    ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(userRepo, bikeRepo, chatSessionRepo, sessionResetService, bookingRepo, promoRepo, promoBikeRepo);
        service.setCacheManager(cacheManager);
    }

    @Test
    void parseDate_acceptsVariousFormats() {
        User u = new User();
        u.setPhoneNumber("d1");
        u.setStage("ASK_START_DATE");
        when(userRepo.findByPhoneNumber("d1")).thenReturn(Optional.of(u));
        when(chatSessionRepo.findByWaId("d1")).thenReturn(Optional.of(new com.zoomigo.whatsapp.whatsapprentalbot.entity.ChatSessionEntity("d1","ASK_START_DATE", new java.util.HashMap<>())));

        String r = service.handleMessage("d1", "9 Nov");
        assertThat(r).contains("pick up");

        // also explicit yyyy-mm-dd
        u.setStage("ASK_START_DATE"); when(userRepo.findByPhoneNumber("d1")).thenReturn(Optional.of(u));
        r = service.handleMessage("d1", LocalDate.now().plusDays(1).toString());
        assertThat(r).contains("pick up");
    }

    @Test
    void confirmationYesMapsToConfirm() {
        User u = new User();
        u.setPhoneNumber("c1");
        u.setStage("CONFIRM_BIKE");
        u.setSelectedBikeId(123L);
        u.setStartDate(LocalDate.now());
        u.setDays(1);
        u.setName("Bob");
        when(userRepo.findByPhoneNumber("c1")).thenReturn(Optional.of(u));
        when(chatSessionRepo.findByWaId("c1")).thenReturn(Optional.of(new com.zoomigo.whatsapp.whatsapprentalbot.entity.ChatSessionEntity("c1","CONFIRM_BIKE", new java.util.HashMap<>())));

        Bike b = new Bike(); b.setId(123L); b.setName("ConfBike"); b.setPricePerDay(500); b.setDeposit(100);
        when(bikeRepo.findById(123L)).thenReturn(Optional.of(b));

        String r = service.handleMessage("c1", "yes");
        assertThat(r).contains("Booking confirmed");
    }

    @Test
    void getPromoByCode_cachesResult() {
        when(cacheManager.getCache("promos")).thenReturn(promosCache);
        when(promosCache.get("test", PromoCode.class)).thenReturn(null);

        PromoCode p = new PromoCode(); p.setId(2L); p.setCode("TEST"); p.setActive(true);
        when(promoRepo.findByCodeIgnoreCase("TEST")).thenReturn(Optional.of(p));

        PromoCode fetched = service.getPromoByCode("TEST");
        assertThat(fetched).isNotNull();
        verify(promosCache).put("test", p);
    }

    @Test
    void getAvailableBikes_usesCache() {
        when(cacheManager.getCache("bikes")).thenReturn(bikesCache);
        when(bikesCache.get("all", List.class)).thenReturn(null);
        Bike b = new Bike(); b.setId(10L); b.setName("CacheBike"); b.setPricePerDay(200);
        when(bikeRepo.findByIsAvailableTrue()).thenReturn(List.of(b));

        List<Bike> first = service.getAvailableBikes();
        assertThat(first).hasSize(1);
        verify(bikesCache).put("all", first);
    }
}
