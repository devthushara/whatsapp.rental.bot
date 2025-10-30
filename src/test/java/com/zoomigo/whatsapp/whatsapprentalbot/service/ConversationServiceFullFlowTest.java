package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Bike;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.ChatSessionEntity;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.User;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.BikeRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.BookingRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.ChatSessionRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.PromoCodeRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.PromoCodeBikeRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceFullFlowTest {

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
    PromoCodeBikeRepository promoBikeRepo;

    ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(userRepo, bikeRepo, chatSessionRepo, sessionResetService, bookingRepo, promoRepo, promoBikeRepo);
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatSessionRepo.save(any(ChatSessionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void fullConversation_NoPromo_confirmsBooking() {
        String wa = "end1";

        // simulate first call: no user/session -> then return user/session
        User u = new User();
        u.setPhoneNumber(wa);
        // findByPhoneNumber: first empty, then return user
        when(userRepo.findByPhoneNumber(wa)).thenReturn(Optional.empty(), Optional.of(u), Optional.of(u), Optional.of(u), Optional.of(u), Optional.of(u), Optional.of(u));

        ChatSessionEntity s = new ChatSessionEntity(wa, "START", new HashMap<>());
        when(chatSessionRepo.findByWaId(wa)).thenReturn(Optional.empty(), Optional.of(s), Optional.of(s), Optional.of(s), Optional.of(s), Optional.of(s), Optional.of(s));

        // available bike
        Bike bike = new Bike(); bike.setId(700L); bike.setName("Enduro"); bike.setPricePerDay(300); bike.setDeposit(50);
        when(bikeRepo.findByIsAvailableTrue()).thenReturn(List.of(bike));
        when(bikeRepo.findById(700L)).thenReturn(Optional.of(bike));

        // 1) Start -> ASK_NAME
        String r1 = service.handleMessage(wa, "Hi");
        assertThat(r1).contains("Welcome");

        // 2) Ask name -> ASK_DAYS
        // make findByPhoneNumber return user with stage ASK_NAME
        u.setStage("ASK_NAME");
        when(userRepo.findByPhoneNumber(wa)).thenReturn(Optional.of(u));
        String r2 = service.handleMessage(wa, "Alice");
        assertThat(r2).contains("How many *days*");

        // 3) days -> ASK_START_DATE
        u.setStage("ASK_DAYS"); when(userRepo.findByPhoneNumber(wa)).thenReturn(Optional.of(u));
        String r3 = service.handleMessage(wa, "2");
        assertThat(r3).contains("rental start date");

        // 4) start date -> ASK_PICKUP
        u.setStage("ASK_START_DATE"); when(userRepo.findByPhoneNumber(wa)).thenReturn(Optional.of(u));
        String r4 = service.handleMessage(wa, "today");
        assertThat(r4).contains("pick up");

        // 5) pickup choose numeric 1 -> ASK_BIKE
        u.setStage("ASK_PICKUP"); when(userRepo.findByPhoneNumber(wa)).thenReturn(Optional.of(u));
        String r5 = service.handleMessage(wa, "1");
        assertThat(r5).contains("Available bikes");

        // 6) user selects bike number (service will create bikeMap first, then selection). Provide session with bikeMap
        Map<String,Object> data = new HashMap<>(); data.put("bikeMap", Map.of("1", 700L)); s.setDataJson(data);
        when(chatSessionRepo.findByWaId(wa)).thenReturn(Optional.of(s));
        // ensure user has startDate and days for price
        u.setStartDate(LocalDate.now()); u.setDays(2); u.setPickupType("Pickup at shop"); when(userRepo.findByPhoneNumber(wa)).thenReturn(Optional.of(u));
        String r6 = service.handleMessage(wa, "1");
        assertThat(r6).contains("Do you have a promo code");

        // 7) reply no to promo -> CONFIRM_BIKE
        u.setStage("ASK_PROMO"); when(userRepo.findByPhoneNumber(wa)).thenReturn(Optional.of(u));
        String r7 = service.handleMessage(wa, "no");
        assertThat(r7).contains("No promo applied");

        // 8) confirm booking
        u.setStage("CONFIRM_BIKE"); when(userRepo.findByPhoneNumber(wa)).thenReturn(Optional.of(u));
        String r8 = service.handleMessage(wa, "1");
        assertThat(r8).contains("Booking confirmed");

        // booking saved
        verify(bookingRepo).save(any());
    }
}
