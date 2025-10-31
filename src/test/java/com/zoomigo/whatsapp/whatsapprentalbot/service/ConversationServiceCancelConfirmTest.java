package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Booking;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.ChatSessionEntity;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.User;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ConversationServiceCancelConfirmTest {

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
        // set shopAddress via reflection
        Field f = ConversationService.class.getDeclaredField("shopAddress");
        f.setAccessible(true);
        f.set(svc, "No. 1, Paramulla Road, Matara");
    }

    @Test
    public void keepBookingWithLatestBooking_includesBikeDatesAndPickup() {
        String phone = "u100";
        User u = new User();
        u.setPhoneNumber(phone);
        u.setStage("CANCEL_CONFIRM");
        u.setName("Alice");

        when(userRepo.findByPhoneNumber(phone)).thenReturn(Optional.of(u));

        ChatSessionEntity session = new ChatSessionEntity(phone, "CANCEL_CONFIRM", new HashMap<>());
        when(chatSessionRepo.findByWaId(phone)).thenReturn(Optional.of(session));

        Booking latest = new Booking();
        latest.setBike("Yamaha XSR 155");
        latest.setStartDate(LocalDate.of(2025,11,1));
        latest.setEndDate(LocalDate.of(2025,11,5));
        latest.setPrice(10240);
        latest.setDeposit(20000);
        latest.setPickupType("Pickup at shop");

        when(bookingRepo.findTopByWaIdOrderByCreatedAtDesc(phone)).thenReturn(Optional.of(latest));

        String resp = svc.handleMessage(phone, "2");

        assertThat(resp).contains("Yamaha XSR 155");
        assertThat(resp).contains("01 Nov 2025");
        assertThat(resp).contains("05 Nov 2025");
        assertThat(resp).contains("Pickup location");
        verify(chatSessionRepo, atLeastOnce()).save(any());
    }

    @Test
    public void keepBookingNoLatestFallbackToUserDelivery() {
        String phone = "u200";
        User u = new User();
        u.setPhoneNumber(phone);
        u.setStage("CANCEL_CONFIRM");
        u.setName("Bob");
        u.setPickupType("Home delivery");
        u.setDeliveryAddress("123 Colombo Road");

        when(userRepo.findByPhoneNumber(phone)).thenReturn(Optional.of(u));

        ChatSessionEntity session = new ChatSessionEntity(phone, "CANCEL_CONFIRM", new HashMap<>());
        when(chatSessionRepo.findByWaId(phone)).thenReturn(Optional.of(session));

        when(bookingRepo.findTopByWaIdOrderByCreatedAtDesc(phone)).thenReturn(Optional.empty());

        String resp = svc.handleMessage(phone, "2");

        assertThat(resp).contains("123 Colombo Road");
        assertThat(resp).contains("delivery");
        verify(chatSessionRepo, atLeastOnce()).save(any());
    }
}
