package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.Bike;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.ChatSessionEntity;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.User;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.BikeRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.ChatSessionRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class ConversationService {

    private final UserRepository userRepo;
    private final BikeRepository bikeRepo;
    private final ChatSessionRepository chatSessionRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.shop-address}")
    private String shopAddress;

    public ConversationService(UserRepository userRepo, BikeRepository bikeRepo, ChatSessionRepository chatSessionRepo) {
        this.userRepo = userRepo;
        this.bikeRepo = bikeRepo;
        this.chatSessionRepo = chatSessionRepo;
    }

    public String handleMessage(String from, String text) {
        text = text.trim();
        log.info("📨 Message from {}: '{}'", from, text);

        User user = userRepo.findByPhoneNumber(from)
                .orElseGet(() -> {
                    User u = new User();
                    u.setPhoneNumber(from);
                    u.setStage("START");
                    return userRepo.save(u);
                });

        ChatSessionEntity session = chatSessionRepo.findByWaId(from)
                .orElseGet(() -> chatSessionRepo.save(new ChatSessionEntity(from, "START", new HashMap<>())));

        Map<String, Object> sessionData = readSessionData(session);
        String stage = user.getStage();
        log.info("➡️ Stage: {}", stage);

        switch (stage) {

            case "START":
                user.setStage("ASK_NAME");
                save(user, session, "ASK_NAME", sessionData);
                return "👋 Hello! Welcome to *ZoomiGo MotoRent*.\nPlease tell me your *name* to start your booking.";

            case "ASK_NAME":
                if (text.equalsIgnoreCase("hi") || text.equalsIgnoreCase("hello")) {
                    return "😊 Please tell me your *name* to continue.";
                }
                user.setName(text);
                save(user, session, "ASK_DAYS", sessionData);
                return "Thanks, " + text + "! 🙏\nHow many *days* would you like to rent the bike? (Enter a number)";

            case "ASK_DAYS":
                try {
                    int days = Integer.parseInt(text);
                    if (days <= 0) throw new NumberFormatException();
                    user.setDays(days);
                    save(user, session, "ASK_PICKUP", sessionData);
                    return "Got it! How would you like to pick up your bike?\n1️⃣ Pickup at shop\n2️⃣ Home delivery";
                } catch (NumberFormatException e) {
                    return "❌ Please enter a valid number of days.";
                }

            case "ASK_PICKUP":
                if ("1".equals(text)) {
                    user.setPickupType("Pickup at shop");
                    save(user, session, "ASK_BIKE", sessionData);
                } else if ("2".equals(text)) {
                    user.setPickupType("Home delivery");
                    save(user, session, "ASK_ADDRESS", sessionData);
                    return "Since you selected *home delivery*, please enter your *delivery address* (if unsure, just give the nearest town name).";
                } else {
                    return "❌ Please reply with 1 or 2 to choose pickup method.";
                }
                // fallthrough

            case "ASK_ADDRESS":
                if ("ASK_ADDRESS".equals(stage)) {
                    user.setDeliveryAddress(text);
                    save(user, session, "ASK_BIKE", sessionData);
                }

            case "ASK_BIKE":
                List<Bike> availableBikes = bikeRepo.findByIsAvailableTrue();
                if (availableBikes.isEmpty()) return "⚠️ Sorry, no bikes are available now.";

                // Get bikeMap from session or create a new one
                Map<String, Object> bikeMapRaw = (Map<String, Object>) sessionData.get("bikeMap");
                if (bikeMapRaw == null) {
                    bikeMapRaw = new LinkedHashMap<>();
                    StringBuilder list = new StringBuilder("🏍️ Available bikes:\n\n");
                    for (int i = 0; i < availableBikes.size(); i++) {
                        Bike b = availableBikes.get(i);
                        String id = String.valueOf(i + 1);
                        bikeMapRaw.put(id, b.getId());  // store as Number (Integer or Long)
                        list.append(id).append(". ").append(b.getName())
                                .append(" - Rs.").append(b.getPricePerDay()).append("/day\n");
                    }
                    sessionData.put("bikeMap", bikeMapRaw);
                    saveSession(session, "ASK_BIKE", sessionData);
                    return list + "\nPlease reply with the *bike number* to continue.";
                }

                // Convert all numbers to Long safely
                Map<String, Long> bikeMap = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : bikeMapRaw.entrySet()) {
                    bikeMap.put(entry.getKey(), ((Number) entry.getValue()).longValue());
                }

                // Now handle user input
                if (!bikeMap.containsKey(text)) {
                    return "❌ Invalid bike number. Please choose again.";
                }

                Long selectedBikeId = bikeMap.get(text);
                Optional<Bike> optBike = bikeRepo.findById(selectedBikeId);
                if (optBike.isEmpty()) return "❌ Invalid selection. Please choose again.";
                Bike selectedBike = optBike.get();

                user.setSelectedBikeId(selectedBikeId);
                save(user, session, "CONFIRM_BIKE", sessionData);

                double total = selectedBike.getPricePerDay().doubleValue() * user.getDays();
                double deposit = selectedBike.getDeposit().doubleValue(); // if deposit is Integer
                String pickupMsg = user.getPickupType() != null && user.getPickupType().equals("Pickup at shop")
                        ? "\n🏠 Shop address: *" + shopAddress + "*" : "";

                return String.format(
                        "You selected *%s* for %d days.\nTotal: Rs%.2f + deposit Rs%.2f\n\nConfirm booking?\n1️⃣ Yes\n2️⃣ No%s",
                        selectedBike.getName(), user.getDays(), total, deposit, pickupMsg);


            case "CONFIRM_BIKE":
                if ("1".equals(text)) {
                    save(user, session, "WAITING_DOCUMENTS", sessionData);
                    String pickupMsg2 = user.getPickupType().equals("Pickup at shop")
                            ? "\n🏠 Shop address: *" + shopAddress + "*"
                            : "";
                    return "✅ Great choice!\nPlease prepare the following documents:\n📸 Driver’s or international license\n📄 Passport & visa QR\n\nOur team will reach out soon to confirm delivery or pickup."
                            + pickupMsg2;
                } else if ("2".equals(text)) {
                    save(user, session, "ASK_BIKE", sessionData);
                    return "No problem! Please choose another bike number.";
                }
                return "❌ Please reply 1️⃣ to confirm or 2️⃣ to reselect.";

            case "WAITING_DOCUMENTS":
                return "📩 We’ve already received your booking. Please prepare your documents — our team will contact you soon.";

            default:
                if (session.getState().equals("ASK_BIKE")) {
                    Map<String, Long> bikes = (Map<String, Long>) sessionData.get("bikeMap");
                    if (bikes == null || !bikes.containsKey(text))
                        return "❌ Invalid bike number. Please choose again.";

                    Long bikeId = bikes.get(text);
                    Optional<Bike> opt = bikeRepo.findById(bikeId);
                    if (opt.isEmpty()) return "❌ Invalid selection. Please choose again.";
                    Bike bike = opt.get();

                    user.setSelectedBikeId(bikeId);
                    save(user, session, "CONFIRM_BIKE", sessionData);
                    double total2 = bike.getPricePerDay() * user.getDays();
                    return String.format("You selected *%s* for %d days.\nTotal: ¥%.2f + deposit ¥%.2f\n\nConfirm booking?\n1️⃣ Yes\n2️⃣ No",
                            bike.getName(), user.getDays(), total2, bike.getDeposit());
                }

                return "👋 Hi again! Type *Hi* to start a new booking.";
        }
    }

    private void save(User user, ChatSessionEntity session, String nextState, Map<String, Object> sessionData) {
        user.setStage(nextState);
        userRepo.save(user);
        saveSession(session, nextState, sessionData);
    }

    private void saveSession(ChatSessionEntity session, String nextState, Map<String, Object> sessionData) {
        try {
            session.setState(nextState);
            session.setDataJson(sessionData);  // ✅ store as Map directly
            session.setLastUpdated(Instant.now());
            chatSessionRepo.save(session);
            log.debug("💾 Session saved for {} with state {} and data {}", session.getWaId(), nextState, sessionData);
        } catch (Exception e) {
            log.error("⚠️ Failed to save session for {}: {}", session.getWaId(), e.getMessage(), e);
        }
    }

    //@SuppressWarnings("unchecked")
    private Map<String, Object> readSessionData(ChatSessionEntity session) {
        try {
            if (session.getDataJson() == null) return new HashMap<>();
            return session.getDataJson();  // ✅ already a Map due to JsonbConverter
        } catch (Exception e) {
            log.error("⚠️ Failed to read session data for {}: {}", session.getWaId(), e.getMessage(), e);
            return new HashMap<>();
        }
    }

}
