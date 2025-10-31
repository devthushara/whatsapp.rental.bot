package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Bike;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.ChatSessionEntity;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.PromoCode;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.PromoCodeBike;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.User;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class ConversationServicePromoMappingTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final BikeRepository bikeRepo = mock(BikeRepository.class);
    private final ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
    private final SessionResetService sessionResetService = mock(SessionResetService.class);
    private final BookingRepository bookingRepo = mock(BookingRepository.class);
    private final PromoCodeRepository promoRepo = mock(PromoCodeRepository.class);
    private final PromoCodeBikeRepository promoBikeRepo = mock(PromoCodeBikeRepository.class);

    private ConversationService svc;

    @BeforeEach
    public void setUp() throws Exception {
        svc = new ConversationService(userRepo, bikeRepo, chatSessionRepo, sessionResetService, bookingRepo, promoRepo, promoBikeRepo);
        Field f = ConversationService.class.getDeclaredField("shopAddress");
        f.setAccessible(true);
        f.set(svc, "No. 1, Paramulla Road, Matara");
    }

    @Test
    public void bikeSpecificPromo_applies_only_to_mapped_bike() {
        String phone = "p1";
        User u = new User();
        u.setPhoneNumber(phone);
        u.setStage("ASK_PROMO");
        u.setSelectedBikeId(1L);
        u.setDays(1);
        u.setStartDate(LocalDate.of(2025,11,2));

        when(userRepo.findByPhoneNumber(phone)).thenReturn(Optional.of(u));
        ChatSessionEntity session = new ChatSessionEntity(phone, "ASK_PROMO", new HashMap<>());
        when(chatSessionRepo.findByWaId(phone)).thenReturn(Optional.of(session));

        Bike bike1 = new Bike(); bike1.setId(1L); bike1.setName("Honda PCX 150"); bike1.setPricePerDay(2500); bike1.setDeposit(2500);
        when(bikeRepo.findById(1L)).thenReturn(Optional.of(bike1));

        PromoCode p = new PromoCode(); p.setId(10L); p.setCode("BIKE1_100"); p.setDiscountAmount(100); p.setActive(true); p.setTotalAllocation(50); p.setUsedCount(0);
        when(promoRepo.findByCodeIgnoreCase("BIKE1_100")).thenReturn(Optional.of(p));
        when(promoRepo.findById(10L)).thenReturn(Optional.of(p));

        PromoCodeBike mapping = new PromoCodeBike(); mapping.setId(100L);
        mapping.setPromoCode(p);
        Bike mappedBike = new Bike(); mappedBike.setId(1L); mapping.setBike(mappedBike);
        when(promoBikeRepo.findByPromoCode_Id(10L)).thenReturn(List.of(mapping));

        String resp = svc.handleMessage(phone, "BIKE1_100");
        assertThat(resp).contains("Discount: Rs100");
        assertThat(resp).contains("New total: Rs2400");
    }

    @Test
    public void bikeSpecificPromo_ignored_for_other_bike() {
        String phone = "p2";
        User u = new User();
        u.setPhoneNumber(phone);
        u.setStage("ASK_PROMO");
        u.setSelectedBikeId(2L);
        u.setDays(1);
        u.setStartDate(LocalDate.of(2025,11,2));

        when(userRepo.findByPhoneNumber(phone)).thenReturn(Optional.of(u));
        ChatSessionEntity session = new ChatSessionEntity(phone, "ASK_PROMO", new HashMap<>());
        when(chatSessionRepo.findByWaId(phone)).thenReturn(Optional.of(session));

        Bike bike2 = new Bike(); bike2.setId(2L); bike2.setName("Suzuki Gixxer 250"); bike2.setPricePerDay(4000); bike2.setDeposit(4000);
        when(bikeRepo.findById(2L)).thenReturn(Optional.of(bike2));

        PromoCode p = new PromoCode(); p.setId(10L); p.setCode("BIKE1_100"); p.setDiscountAmount(100); p.setActive(true); p.setTotalAllocation(50); p.setUsedCount(0);
        when(promoRepo.findByCodeIgnoreCase("BIKE1_100")).thenReturn(Optional.of(p));
        when(promoRepo.findById(10L)).thenReturn(Optional.of(p));

        PromoCodeBike mapping = new PromoCodeBike(); mapping.setId(100L);
        mapping.setPromoCode(p);
        Bike mappedBike = new Bike(); mappedBike.setId(1L); mapping.setBike(mappedBike);
        when(promoBikeRepo.findByPromoCode_Id(10L)).thenReturn(List.of(mapping));

        String resp = svc.handleMessage(phone, "BIKE1_100");
        assertThat(resp).contains("does not apply");
    }
}

