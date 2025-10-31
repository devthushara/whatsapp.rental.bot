package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Bike;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.Booking;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.ChatSessionEntity;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.PromoCode;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.User;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.BikeRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.BookingRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.ChatSessionRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.PromoCodeRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationServiceTest {

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
    void startFlowAsksName() {
        when(userRepo.findByPhoneNumber("u1")).thenReturn(Optional.empty());
        when(chatSessionRepo.findByWaId("u1")).thenReturn(Optional.empty());
        // when repos are empty, ConversationService will call save(...); make save return the passed entity
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatSessionRepo.save(any(ChatSessionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        String reply = service.handleMessage("u1", "Hi");
        assertThat(reply).contains("Welcome");
    }

    @Test
    void askDaysThenDateThenPickup() {
        User u = new User();
        u.setPhoneNumber("u2");
        u.setStage("ASK_NAME");
        when(userRepo.findByPhoneNumber("u2")).thenReturn(Optional.of(u));
        when(chatSessionRepo.findByWaId("u2")).thenReturn(Optional.of(new ChatSessionEntity("u2", "ASK_NAME", new HashMap<>())));

        String resp = service.handleMessage("u2", "Alice");
        assertThat(resp).contains("How many *days*");

        // simulate days
        u.setStage("ASK_DAYS");
        when(userRepo.findByPhoneNumber("u2")).thenReturn(Optional.of(u));
        resp = service.handleMessage("u2", "3");
        assertThat(resp).contains("rental start date");

        // simulate start date
        u.setStage("ASK_START_DATE");
        when(userRepo.findByPhoneNumber("u2")).thenReturn(Optional.of(u));
        resp = service.handleMessage("u2", "today");
        assertThat(resp).contains("pick up");
    }

    @Test
    void applyPromoAndConfirmBooking() {
        User u = new User();
        u.setPhoneNumber("u3");
        u.setStage("ASK_BIKE");
        u.setStartDate(LocalDate.now());
        u.setDays(2);
        u.setPickupType("Pickup at shop");

        when(userRepo.findByPhoneNumber("u3")).thenReturn(Optional.of(u));

        ChatSessionEntity session = new ChatSessionEntity("u3", "ASK_BIKE", new HashMap<>());
        Map<String, Object> data = new HashMap<>();
        data.put("bikeMap", Map.of("1", 101L));
        session.setDataJson(data);
        when(chatSessionRepo.findByWaId("u3")).thenReturn(Optional.of(session));

        Bike bike = new Bike();
        bike.setId(101L);
        bike.setName("Zoomer");
        bike.setPricePerDay(500);
        bike.setDeposit(100);
        when(bikeRepo.findById(101L)).thenReturn(Optional.of(bike));
        when(bikeRepo.findByIsAvailableTrue()).thenReturn(List.of(bike));

        // choose bike 1
        String resp = service.handleMessage("u3", "1");
        assertThat(resp).contains("promo code");

        // now provide promo code
        u.setStage("ASK_PROMO");
        when(userRepo.findByPhoneNumber("u3")).thenReturn(Optional.of(u));

        PromoCode promo = new PromoCode();
        promo.setId(5L);
        promo.setCode("TEST50");
        promo.setDiscountAmount(50);
        promo.setTotalAllocation(10);
        promo.setUsedCount(0);
        promo.setActive(true);
        when(promoRepo.findByCodeIgnoreCase("TEST50")).thenReturn(Optional.of(promo));

        resp = service.handleMessage("u3", "TEST50");
        assertThat(resp).contains("applied");

        // confirm
        u.setStage("CONFIRM_BIKE");
        when(userRepo.findByPhoneNumber("u3")).thenReturn(Optional.of(u));

        // ensure promoRepo.findById returns the same promo so the service can increment and save it
        when(promoRepo.findById(5L)).thenReturn(Optional.of(promo));

        resp = service.handleMessage("u3", "1");
        assertThat(resp).contains("Booking confirmed");

        // verify promo used count incremented and booking saved
        verify(promoRepo).save(any(PromoCode.class));
        verify(bookingRepo).save(any());
    }

    @Test
    void pickupInferenceAndBikeNameSelection() {
        // user at ASK_PICKUP stage
        User u = new User();
        u.setPhoneNumber("u4");
        u.setStage("ASK_PICKUP");
        when(userRepo.findByPhoneNumber("u4")).thenReturn(Optional.of(u));
        when(chatSessionRepo.findByWaId("u4")).thenReturn(Optional.of(new ChatSessionEntity("u4", "ASK_PICKUP", new HashMap<>())));

        // user types 'pickup at store'
        String r1 = service.handleMessage("u4", "I will pickup from your store");
        assertThat(r1).contains("pickup at shop");

        // Now move to ASK_BIKE: prepare available bikes and session
        u.setStage("ASK_BIKE");
        // ensure we have startDate and days set for later price/endDate calculations
        u.setStartDate(LocalDate.now());
        u.setDays(2);
        Bike b1 = new Bike(); b1.setId(201L); b1.setName("Zoomer Pro"); b1.setPricePerDay(600); b1.setDeposit(200);
        when(bikeRepo.findByIsAvailableTrue()).thenReturn(List.of(b1));

        // chat session without bikeMap so service will emit list
        when(chatSessionRepo.findByWaId("u4")).thenReturn(Optional.of(new ChatSessionEntity("u4","ASK_BIKE",new HashMap<>())));

        String r2 = service.handleMessage("u4", "");
        assertThat(r2).contains("Available bikes");

        // prepare session with bikeMap so user can select by free-text name
        ChatSessionEntity session = new ChatSessionEntity("u4","ASK_BIKE", new HashMap<>());
        session.getDataJson().put("bikeMap", Map.of("1", 201L));
        when(chatSessionRepo.findByWaId("u4")).thenReturn(Optional.of(session));
        when(bikeRepo.findById(201L)).thenReturn(Optional.of(b1));

        // user types partial bike name
        u.setStage("ASK_BIKE");
        when(userRepo.findByPhoneNumber("u4")).thenReturn(Optional.of(u));
        String r3 = service.handleMessage("u4", "Zoomer");
        assertThat(r3).contains("You selected");
    }

    @Test
    void promoNoAndConfirmReselectFlow() {
        // Simulate selecting bike and replying 'no' to promo, then choosing to reselect (2)
        User u = new User();
        u.setPhoneNumber("u5");
        u.setStage("ASK_BIKE");
        u.setStartDate(LocalDate.now());
        u.setDays(1);
        u.setPickupType("Pickup at shop");
        when(userRepo.findByPhoneNumber("u5")).thenReturn(Optional.of(u));

        Bike b = new Bike(); b.setId(301L); b.setName("Scoot"); b.setPricePerDay(300); b.setDeposit(50);
        when(bikeRepo.findByIsAvailableTrue()).thenReturn(List.of(b));
        ChatSessionEntity session = new ChatSessionEntity("u5","ASK_BIKE", new HashMap<>());
        session.getDataJson().put("bikeMap", Map.of("1", 301L));
        when(chatSessionRepo.findByWaId("u5")).thenReturn(Optional.of(session));
        when(bikeRepo.findById(301L)).thenReturn(Optional.of(b));

        // choose bike 1
        String r1 = service.handleMessage("u5", "1");
        assertThat(r1).contains("Do you have a promo code");

        // reply 'no' to promo
        u.setStage("ASK_PROMO");
        when(userRepo.findByPhoneNumber("u5")).thenReturn(Optional.of(u));
        String r2 = service.handleMessage("u5", "no");
        assertThat(r2).contains("No promo applied");

        // confirm flow: reply '2' to reselect
        u.setStage("CONFIRM_BIKE");
        when(userRepo.findByPhoneNumber("u5")).thenReturn(Optional.of(u));
        String r3 = service.handleMessage("u5", "2");
        assertThat(r3).contains("choose another bike");
    }

    @Test
    void cancelConfirmedBookingFlow() {
        // User already has a confirmed booking
        User u = new User();
        u.setPhoneNumber("u6");
        u.setStage("BOOKING_CONFIRMED");
        when(userRepo.findByPhoneNumber("u6")).thenReturn(Optional.of(u));

        // Simulate existing booking returned by repository
        Booking existing = new Booking();
        existing.setId(900L);
        existing.setWaId("u6");
        existing.setStatus("CONFIRMED");
        when(bookingRepo.findTopByWaIdOrderByCreatedAtDesc("u6")).thenReturn(Optional.of(existing));

        // send cancel
        String r1 = service.handleMessage("u6", "cancel");
        assertThat(r1).contains("Are you sure");

        // now confirm cancellation by sending '1'
        u.setStage("CANCEL_CONFIRM");
        when(userRepo.findByPhoneNumber("u6")).thenReturn(Optional.of(u));

        String r2 = service.handleMessage("u6", "1");
        assertThat(r2).contains("cancelled");

        // verify that booking status updated and saved
        verify(bookingRepo).save(any(Booking.class));
        // verify session reset invoked
        verify(sessionResetService).resetUserAndSession("u6");
    }

    @Test
    void invalidBikeNumberShowsError() {
        User u = new User();
        u.setPhoneNumber("u11");
        u.setStage("ASK_BIKE");
        u.setStartDate(LocalDate.now());
        u.setDays(1);
        when(userRepo.findByPhoneNumber("u11")).thenReturn(Optional.of(u));

        Bike b = new Bike(); b.setId(601L); b.setName("Mini"); b.setPricePerDay(150); b.setDeposit(30);
        when(bikeRepo.findByIsAvailableTrue()).thenReturn(List.of(b));
        ChatSessionEntity session = new ChatSessionEntity("u11","ASK_BIKE", new HashMap<>());
        session.getDataJson().put("bikeMap", Map.of("1", 601L));
        when(chatSessionRepo.findByWaId("u11")).thenReturn(Optional.of(session));
        when(bikeRepo.findById(601L)).thenReturn(Optional.of(b));

        String r = service.handleMessage("u11", "99");
        // service returns a localized invalid message; assert general 'Invalid' to be robust
        assertThat(r).contains("Invalid");
    }

    @Test
    void invalidDaysAndDateHandling() {
        User u = new User();
        u.setPhoneNumber("ux1");
        u.setStage("ASK_DAYS");
        when(userRepo.findByPhoneNumber("ux1")).thenReturn(Optional.of(u));
        when(chatSessionRepo.findByWaId("ux1")).thenReturn(Optional.of(new ChatSessionEntity("ux1","ASK_DAYS", new HashMap<>())));

        String r1 = service.handleMessage("ux1", "zero");
        assertThat(r1).contains("Please enter a valid number of days");

        // now valid days but invalid date
        u.setStage("ASK_START_DATE");
        u.setDays(2);
        when(userRepo.findByPhoneNumber("ux1")).thenReturn(Optional.of(u));
        // use an obviously invalid free-text date that Natty won't parse
        String r2 = service.handleMessage("ux1", "notadate");
        assertThat(r2).contains("Please enter a valid date");
    }

    @Test
    void invalidAndExhaustedPromo() {
        User u = new User();
        u.setPhoneNumber("ux2");
        u.setStage("ASK_PROMO");
        when(userRepo.findByPhoneNumber("ux2")).thenReturn(Optional.of(u));
        when(chatSessionRepo.findByWaId("ux2")).thenReturn(Optional.of(new ChatSessionEntity("ux2","ASK_PROMO", new HashMap<>())));

        // promo not found
        when(promoRepo.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());
        String r1 = service.handleMessage("ux2", "NOPE");
        assertThat(r1).contains("Promo code not found");

        // exhausted promo
        PromoCode p = new PromoCode(); p.setId(7L); p.setCode("LIMITED"); p.setActive(true); p.setTotalAllocation(1); p.setUsedCount(1);
        when(promoRepo.findByCodeIgnoreCase("LIMITED")).thenReturn(Optional.of(p));
        String r2 = service.handleMessage("ux2", "LIMITED");
        assertThat(r2).contains("fully used");
    }

    @Test
    void askNameGreetingRespondsProperly() {
        User u = new User();
        u.setPhoneNumber("u7");
        u.setStage("ASK_NAME");
        when(userRepo.findByPhoneNumber("u7")).thenReturn(Optional.of(u));
        when(chatSessionRepo.findByWaId("u7")).thenReturn(Optional.of(new ChatSessionEntity("u7", "ASK_NAME", new HashMap<>())));

        String r = service.handleMessage("u7", "hi");
        assertThat(r).contains("Please tell me your *name*");
    }

    @Test
    void askPickupNumericOptionTwoRequestsAddress() {
        User u = new User();
        u.setPhoneNumber("u8");
        u.setStage("ASK_PICKUP");
        when(userRepo.findByPhoneNumber("u8")).thenReturn(Optional.of(u));
        when(chatSessionRepo.findByWaId("u8")).thenReturn(Optional.of(new ChatSessionEntity("u8","ASK_PICKUP", new HashMap<>())));

        String r = service.handleMessage("u8", "2");
        assertThat(r).contains("delivery address");
    }

    @Test
    void askAddressLeadsToBikeList() {
        User u = new User();
        u.setPhoneNumber("u9");
        u.setStage("ASK_ADDRESS");
        when(userRepo.findByPhoneNumber("u9")).thenReturn(Optional.of(u));
        ChatSessionEntity s = new ChatSessionEntity("u9","ASK_ADDRESS", new HashMap<>());
        when(chatSessionRepo.findByWaId("u9")).thenReturn(Optional.of(s));

        Bike b = new Bike(); b.setId(401L); b.setName("City"); b.setPricePerDay(200); b.setDeposit(50);
        when(bikeRepo.findByIsAvailableTrue()).thenReturn(List.of(b));

        String r = service.handleMessage("u9", "My street 123");
        assertThat(r).contains("Available bikes");
    }

    @Test
    void askBikeNumberSelectProducesPromoPrompt() {
        User u = new User();
        u.setPhoneNumber("u10");
        u.setStage("ASK_BIKE");
        u.setStartDate(LocalDate.now());
        u.setDays(1);
        u.setPickupType("Pickup at shop");
        when(userRepo.findByPhoneNumber("u10")).thenReturn(Optional.of(u));

        Bike b = new Bike(); b.setId(501L); b.setName("Roadster"); b.setPricePerDay(400); b.setDeposit(80);
        when(bikeRepo.findByIsAvailableTrue()).thenReturn(List.of(b));
        ChatSessionEntity session = new ChatSessionEntity("u10","ASK_BIKE", new HashMap<>());
        session.getDataJson().put("bikeMap", Map.of("1", 501L));
        when(chatSessionRepo.findByWaId("u10")).thenReturn(Optional.of(session));
        when(bikeRepo.findById(501L)).thenReturn(Optional.of(b));

        String r = service.handleMessage("u10", "1");
        assertThat(r).contains("Do you have a promo code");
    }

    @Test
    void defaultUnknownInputSuggestsStart() {
        User u = new User(); u.setPhoneNumber("ux5"); u.setStage("UNKNOWN_STATE");
        when(userRepo.findByPhoneNumber("ux5")).thenReturn(Optional.of(u));
        when(chatSessionRepo.findByWaId("ux5")).thenReturn(Optional.of(new ChatSessionEntity("ux5","UNKNOWN_STATE", new HashMap<>())));

        String r = service.handleMessage("ux5", "blabla");
        assertThat(r).contains("didn't understand");
    }

    @Test
    void askPickupInvalidOptionShowsHelp() {
        User u = new User(); u.setPhoneNumber("ux3"); u.setStage("ASK_PICKUP");
        when(userRepo.findByPhoneNumber("ux3")).thenReturn(Optional.of(u));
        when(chatSessionRepo.findByWaId("ux3")).thenReturn(Optional.of(new ChatSessionEntity("ux3","ASK_PICKUP", new HashMap<>())));

        String r = service.handleMessage("ux3", "maybe later");
        assertThat(r).contains("Please reply with 1 or 2");
    }

    @Test
    void askBikeNoAvailableShowsMessage() {
        User u = new User(); u.setPhoneNumber("ux4"); u.setStage("ASK_BIKE");
        when(userRepo.findByPhoneNumber("ux4")).thenReturn(Optional.of(u));
        when(chatSessionRepo.findByWaId("ux4")).thenReturn(Optional.of(new ChatSessionEntity("ux4","ASK_BIKE", new HashMap<>())));

        when(bikeRepo.findByIsAvailableTrue()).thenReturn(List.of());
        String r = service.handleMessage("ux4", "");
        assertThat(r).contains("no bikes are available");
    }

    @Test
    void percentagePromoAppliedCorrectly() {
        User u = new User();
        u.setPhoneNumber("up1");
        u.setStage("ASK_BIKE");
        u.setStartDate(LocalDate.now());
        u.setDays(2);
        u.setPickupType("Pickup at shop");
        when(userRepo.findByPhoneNumber("up1")).thenReturn(Optional.of(u));

        ChatSessionEntity session = new ChatSessionEntity("up1", "ASK_BIKE", new HashMap<>());
        Map<String, Object> data = new HashMap<>(); data.put("bikeMap", Map.of("1", 701L));
        session.setDataJson(data);
        when(chatSessionRepo.findByWaId("up1")).thenReturn(Optional.of(session));

        Bike b = new Bike(); b.setId(701L); b.setName("PercentBike"); b.setPricePerDay(1000); b.setDeposit(100);
        when(bikeRepo.findById(701L)).thenReturn(Optional.of(b));
        when(bikeRepo.findByIsAvailableTrue()).thenReturn(List.of(b));

        // choose bike
        String r1 = service.handleMessage("up1", "1");
        assertThat(r1).contains("promo code");

        // now apply percent promo
        u.setStage("ASK_PROMO"); when(userRepo.findByPhoneNumber("up1")).thenReturn(Optional.of(u));
        PromoCode pc = new PromoCode(); pc.setId(11L); pc.setCode("PERC10"); pc.setDiscountPercent(10); pc.setActive(true);
        when(promoRepo.findByCodeIgnoreCase("PERC10")).thenReturn(Optional.of(pc));

        String r2 = service.handleMessage("up1", "PERC10");
        assertThat(r2).contains("applied");
        // confirm
        u.setStage("CONFIRM_BIKE"); when(userRepo.findByPhoneNumber("up1")).thenReturn(Optional.of(u));
        when(promoRepo.findById(11L)).thenReturn(Optional.of(pc));

        String r3 = service.handleMessage("up1", "1");
        assertThat(r3).contains("Booking confirmed");
        // price should be 1000*2 = 2000, 10% -> 200 discount -> final 1800
        assertThat(r3).contains("Rs1800");
    }

    @Test
    void bikeSpecificPromoOnlyAppliesToMappedBike() {
        User u = new User();
        u.setPhoneNumber("up2");
        u.setStage("ASK_BIKE");
        u.setStartDate(LocalDate.now());
        u.setDays(1);
        u.setPickupType("Pickup at shop");
        when(userRepo.findByPhoneNumber("up2")).thenReturn(Optional.of(u));

        ChatSessionEntity session = new ChatSessionEntity("up2", "ASK_BIKE", new HashMap<>());
        Map<String, Object> data = new HashMap<>(); data.put("bikeMap", Map.of("1", 801L));
        session.setDataJson(data);
        when(chatSessionRepo.findByWaId("up2")).thenReturn(Optional.of(session));

        Bike b = new Bike(); b.setId(801L); b.setName("MappedBike"); b.setPricePerDay(500); b.setDeposit(50);
        when(bikeRepo.findById(801L)).thenReturn(Optional.of(b));
        when(bikeRepo.findByIsAvailableTrue()).thenReturn(List.of(b));

        // choose bike
        String r1 = service.handleMessage("up2", "1");
        assertThat(r1).contains("promo code");

        // create promo that is only valid for bike id 999 (different)
        PromoCode pc = new PromoCode(); pc.setId(20L); pc.setCode("BIKEONLY"); pc.setDiscountAmount(100); pc.setActive(true);
        when(promoRepo.findByCodeIgnoreCase("BIKEONLY")).thenReturn(Optional.of(pc));
        // promoBikeRepo has mapping only to bike id 999
        com.zoomigo.whatsapp.whatsapprentalbot.entity.PromoCodeBike mapping = new com.zoomigo.whatsapp.whatsapprentalbot.entity.PromoCodeBike();
        mapping.setId(1L);
        mapping.setPromoCode(pc);
        Bike other = new Bike(); other.setId(999L);
        mapping.setBike(other);
        when(promoBikeRepo.findByPromoCode_Id(20L)).thenReturn(List.of(mapping));

        // go to promo stage
        u.setStage("ASK_PROMO"); when(userRepo.findByPhoneNumber("up2")).thenReturn(Optional.of(u));
        String r2 = service.handleMessage("up2", "BIKEONLY");
        // promo entered but mapping doesn't match selected bike — user should be informed it does not apply
        assertThat(r2).contains("does not apply");

        u.setStage("CONFIRM_BIKE"); when(userRepo.findByPhoneNumber("up2")).thenReturn(Optional.of(u));
        when(promoRepo.findById(20L)).thenReturn(Optional.of(pc));
        String r3 = service.handleMessage("up2", "1");
        assertThat(r3).contains("Booking confirmed");
        // booking should not include promo applied details because it's not applicable
        assertThat(r3).doesNotContain("Promo applied: BIKEONLY");
    }


}
